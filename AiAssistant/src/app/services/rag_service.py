from __future__ import annotations

import hashlib
from dataclasses import dataclass
from typing import Any

from qdrant_client import QdrantClient
from qdrant_client.models import Distance, FieldCondition, Filter, FilterSelector, MatchAny, MatchValue, PointStruct, VectorParams

from src.app.adapters.java.knowledge_client import query_knowledge
from src.app.api.deps import RequestContext
from src.app.core.config import get_settings
from src.app.schemas.chat import AttachmentContext, ChatCompletionRequest
from src.app.schemas.message import CitationView
from src.app.schemas.rag import (
    RagIndexDeleteRequest,
    RagIndexUpsertRequest,
    RagQueryHit,
    RagQueryResponse,
    build_debug_chat_context,
)
from src.app.services.embedding_service import embedding_service
from src.app.services.web_search_service import search_web


@dataclass(slots=True)
class RetrievalDecision:
    answer_source: str
    citations: list[CitationView]
    used_tools: list[str]
    confidence: float | None
    context_messages: list[dict[str, str]]
    fallback_answer: str | None = None


class RagService:
    def __init__(self) -> None:
        self.settings = get_settings()
        self.client = QdrantClient(
            url=self.settings.qdrant_url,
            api_key=self.settings.qdrant_api_key or None,
            check_compatibility=False,
        )
        self._ready_collections: set[str] = set()

    def _ensure_collection(self, collection_name: str) -> None:
        if collection_name in self._ready_collections:
            return
        collections = {item.name for item in self.client.get_collections().collections}
        if collection_name not in collections:
            self.client.create_collection(
                collection_name=collection_name,
                vectors_config=VectorParams(
                    size=self.settings.embedding_dimensions,
                    distance=Distance.COSINE,
                ),
            )
        self._ready_collections.add(collection_name)

    async def upsert(self, request: RagIndexUpsertRequest) -> dict[str, Any]:
        collection_name = self._collection_name(request.scope_type)
        self._ensure_collection(collection_name)
        await self.delete(
            RagIndexDeleteRequest(
                user_id=request.user_id,
                scope_type=request.scope_type,
                project_id=request.project_id,
                session_id=request.session_id,
                source_type=request.source_type,
                source_id=request.source_id,
            )
        )
        if not request.chunks:
            return self._upsert_result(collection_name, 0)

        vectors = await embedding_service.embed([item.chunk_text for item in request.chunks])
        points = [
            PointStruct(
                id=self._point_id(
                    request.scope_type,
                    request.source_type,
                    request.source_id,
                    request.session_id,
                    chunk.chunk_num,
                ),
                vector=vectors[index],
                payload={
                    'scope_type': request.scope_type.upper(),
                    'user_id': request.user_id,
                    'project_id': request.project_id,
                    'session_id': request.session_id,
                    'source_type': request.source_type,
                    'source_id': request.source_id,
                    'source_name': request.source_name,
                    'chunk_num': chunk.chunk_num,
                    'chunk_text': chunk.chunk_text,
                    'page_no': chunk.page_no,
                    'section_path': chunk.section_path,
                    'updated_at': request.updated_at.isoformat() if request.updated_at else None,
                },
            )
            for index, chunk in enumerate(request.chunks)
        ]
        self.client.upsert(collection_name=collection_name, points=points)
        return self._upsert_result(collection_name, len(points))

    async def delete(self, request: RagIndexDeleteRequest) -> dict[str, Any]:
        collection_name = self._collection_name(request.scope_type)
        self._ensure_collection(collection_name)
        must: list[FieldCondition] = [
            FieldCondition(key='user_id', match=MatchValue(value=request.user_id)),
            FieldCondition(key='source_type', match=MatchValue(value=request.source_type)),
            FieldCondition(key='source_id', match=MatchValue(value=request.source_id)),
        ]
        if request.session_id is not None:
            must.append(FieldCondition(key='session_id', match=MatchValue(value=request.session_id)))
        self.client.delete(
            collection_name=collection_name,
            points_selector=FilterSelector(filter=Filter(must=must)),
        )
        return {'deleted': True, 'collectionName': collection_name}

    async def query(self, request: ChatCompletionRequest, context: RequestContext) -> RetrievalDecision:
        top_k = self._resolve_top_k(request)
        threshold = self._resolve_threshold(request)
        mode = self._normalized_mode(request.mode)

        private_hits = self._merge_hits(
            await self._safe_vector_search(request, context, top_k, mode),
            await self._safe_keyword_search(request, context, top_k, mode),
        )
        confidence = private_hits[0].score if private_hits else None
        top_hits = private_hits[:top_k]

        if top_hits and confidence is not None and confidence >= threshold:
            return RetrievalDecision(
                answer_source='PRIVATE_KNOWLEDGE',
                citations=[self._to_citation(hit) for hit in top_hits],
                used_tools=['private_knowledge'],
                confidence=confidence,
                context_messages=[{'role': 'system', 'content': self._build_private_context(top_hits, mode)}],
            )

        if mode == 'SCOPED':
            return RetrievalDecision(
                answer_source='PRIVATE_KNOWLEDGE',
                citations=[],
                used_tools=['private_knowledge'],
                confidence=confidence,
                context_messages=[],
                fallback_answer='范围内依据不足，当前选定范围内没有足够依据支持回答。',
            )

        if bool(request.web_search_enabled):
            web_hits = await search_web(request.message)
            if web_hits:
                citations = [
                    CitationView(
                        source_type='WEB',
                        source_id=index + 1,
                        source_title=item.title,
                        snippet=item.snippet,
                        score=None,
                        url=item.url,
                    )
                    for index, item in enumerate(web_hits)
                ]
                return RetrievalDecision(
                    answer_source='WEB_SEARCH',
                    citations=citations,
                    used_tools=['web_search'],
                    confidence=confidence,
                    context_messages=[{'role': 'system', 'content': self._build_web_context(web_hits)}],
                )

        return RetrievalDecision(
            answer_source='GENERAL_KNOWLEDGE',
            citations=[],
            used_tools=['general_knowledge'],
            confidence=confidence,
            context_messages=[{
                'role': 'system',
                'content': '当前未命中足够可靠的私有知识，也没有可用的联网搜索结果。请明确以通用知识回答，不要伪造引用。',
            }],
        )

    async def debug_query(
        self,
        user_id: int,
        query: str,
        project_scope_ids: list[int],
        attachments: list[AttachmentContext],
        limit: int,
    ) -> RagQueryResponse:
        fake_request = ChatCompletionRequest(
            message=query,
            context=build_debug_chat_context(project_scope_ids, attachments),
            mode='GENERAL',
            web_search_enabled=False,
            top_k=limit,
        )
        context = RequestContext(user_id=user_id, org_id=0, request_id='rag-debug')
        decision = await self.query(fake_request, context)
        hits = self._merge_hits(
            await self._safe_vector_search(fake_request, context, limit, 'GENERAL'),
            await self._safe_keyword_search(fake_request, context, limit, 'GENERAL'),
        )
        return RagQueryResponse(
            hits=hits[:limit],
            citations=decision.citations,
            confidence=decision.confidence,
            answer_source=decision.answer_source,
            used_tools=decision.used_tools,
        )

    async def _safe_vector_search(
        self,
        request: ChatCompletionRequest,
        context: RequestContext,
        top_k: int,
        mode: str,
    ) -> list[RagQueryHit]:
        try:
            return await self._vector_search(request, context, top_k, mode)
        except Exception:
            return []

    async def _safe_keyword_search(
        self,
        request: ChatCompletionRequest,
        context: RequestContext,
        top_k: int,
        mode: str,
    ) -> list[RagQueryHit]:
        try:
            return await self._keyword_search(request, context, top_k, mode)
        except Exception:
            return []

    async def _vector_search(
        self,
        request: ChatCompletionRequest,
        context: RequestContext,
        top_k: int,
        mode: str,
    ) -> list[RagQueryHit]:
        vector = (await embedding_service.embed([request.message]))[0]
        hits: list[RagQueryHit] = []
        for collection_name, query_filter in self._vector_queries(request, context, mode):
            self._ensure_collection(collection_name)
            response = self.client.search(
                collection_name=collection_name,
                query_vector=vector,
                query_filter=query_filter,
                limit=top_k,
                with_payload=True,
            )
            hits.extend(self._response_to_hits(response))
        return self._merge_hits(hits)[:top_k]

    async def _keyword_search(
        self,
        request: ChatCompletionRequest,
        context: RequestContext,
        top_k: int,
        mode: str,
    ) -> list[RagQueryHit]:
        attachments = self._keyword_attachments(request)
        project_scope_ids = self._project_scope_ids(request)
        if mode == 'SCOPED' and attachments:
            project_scope_ids = []

        payload = await query_knowledge(
            user_id=context.user_id,
            keyword=request.message,
            request_id=context.request_id,
            project_scope_ids=project_scope_ids,
            attachments=attachments,
            limit=max(1, top_k),
        )
        return [
            RagQueryHit(
                score=0.56,
                source_type=str(item.get('sourceType') or ''),
                source_id=int(item.get('sourceId') or 0),
                source_title=str(item.get('title') or '知识片段'),
                snippet=str(item.get('snippet') or ''),
                page_no=None,
            )
            for item in payload
            if item.get('sourceId') is not None
        ]

    def _vector_queries(
        self,
        request: ChatCompletionRequest,
        context: RequestContext,
        mode: str,
    ) -> list[tuple[str, Filter]]:
        project_scope_ids = self._project_scope_ids(request)
        kb_attachments = [item for item in self._knowledge_attachments(request) if item.attachment_type in {'DOCUMENT', 'FILE'}]
        temp_attachments = [item for item in self._knowledge_attachments(request) if item.attachment_type == 'TEMP_FILE']
        queries: list[tuple[str, Filter]] = []

        if mode == 'SCOPED':
            if kb_attachments or temp_attachments:
                for attachment in kb_attachments:
                    queries.append((self.settings.qdrant_kb_collection, self._build_attachment_filter(context.user_id, attachment)))
                for attachment in temp_attachments:
                    queries.append((self.settings.qdrant_temp_collection, self._build_attachment_filter(context.user_id, attachment)))
            elif project_scope_ids:
                queries.append((self.settings.qdrant_kb_collection, self._build_project_filter(context.user_id, project_scope_ids)))
            return queries

        if project_scope_ids:
            queries.append((self.settings.qdrant_kb_collection, self._build_project_filter(context.user_id, project_scope_ids)))
        else:
            queries.append((self.settings.qdrant_kb_collection, self._build_user_filter(context.user_id)))

        for attachment in kb_attachments:
            queries.append((self.settings.qdrant_kb_collection, self._build_attachment_filter(context.user_id, attachment)))
        for attachment in temp_attachments:
            queries.append((self.settings.qdrant_temp_collection, self._build_attachment_filter(context.user_id, attachment)))
        return queries

    def _knowledge_attachments(self, request: ChatCompletionRequest) -> list[AttachmentContext]:
        return [item for item in request.context.attachments if item.attachment_type in {'DOCUMENT', 'FILE', 'TEMP_FILE'}]

    def _keyword_attachments(self, request: ChatCompletionRequest) -> list[dict[str, int | str | None]]:
        return [
            {
                'attachmentType': item.attachment_type,
                'sourceId': item.source_id,
                'sessionId': item.session_id or (request.session_id if item.attachment_type == 'TEMP_FILE' else None),
            }
            for item in self._knowledge_attachments(request)
        ]

    def _project_scope_ids(self, request: ChatCompletionRequest) -> list[int]:
        project_ids = {item.project_id for item in request.context.attachments if item.project_id is not None}
        if request.context.project_id is not None:
            project_ids.add(request.context.project_id)
        return sorted(project_id for project_id in project_ids if project_id is not None)

    def _build_user_filter(self, user_id: int) -> Filter:
        return Filter(must=[FieldCondition(key='user_id', match=MatchValue(value=user_id))])

    def _build_project_filter(self, user_id: int, project_scope_ids: list[int]) -> Filter:
        must = [FieldCondition(key='user_id', match=MatchValue(value=user_id))]
        if project_scope_ids:
            must.append(FieldCondition(key='project_id', match=MatchAny(any=project_scope_ids)))
        return Filter(must=must)

    def _build_attachment_filter(self, user_id: int, attachment: AttachmentContext) -> Filter:
        must: list[FieldCondition] = [
            FieldCondition(key='user_id', match=MatchValue(value=user_id)),
            FieldCondition(key='source_type', match=MatchValue(value=attachment.attachment_type)),
            FieldCondition(key='source_id', match=MatchValue(value=attachment.source_id)),
        ]
        session_id = attachment.session_id
        if attachment.attachment_type == 'TEMP_FILE' and session_id is not None:
            must.append(FieldCondition(key='session_id', match=MatchValue(value=session_id)))
        return Filter(must=must)

    def _response_to_hits(self, response: list[Any]) -> list[RagQueryHit]:
        hits: list[RagQueryHit] = []
        for item in response:
            payload = item.payload or {}
            hits.append(
                RagQueryHit(
                    score=float(item.score),
                    source_type=str(payload.get('source_type') or ''),
                    source_id=int(payload.get('source_id') or 0),
                    source_title=str(payload.get('source_name') or '知识片段'),
                    snippet=str(payload.get('chunk_text') or ''),
                    page_no=int(payload['page_no']) if payload.get('page_no') is not None else None,
                )
            )
        return hits

    def _merge_hits(self, *hit_groups: list[RagQueryHit]) -> list[RagQueryHit]:
        merged: dict[str, RagQueryHit] = {}
        for item in [hit for group in hit_groups for hit in group]:
            key = f'{item.source_type}:{item.source_id}:{item.page_no or 0}:{item.snippet[:120]}:{item.url or ""}'
            existing = merged.get(key)
            if existing is None or item.score > existing.score:
                merged[key] = item
        return sorted(merged.values(), key=lambda item: item.score, reverse=True)

    def _to_citation(self, hit: RagQueryHit) -> CitationView:
        return CitationView(
            source_type=hit.source_type,
            source_id=hit.source_id,
            source_title=hit.source_title,
            snippet=hit.snippet,
            page_no=hit.page_no,
            score=hit.score,
            url=hit.url,
        )

    def _build_private_context(self, hits: list[RagQueryHit], mode: str) -> str:
        lines = [
            '以下资料来自私有知识库，请优先依据这些资料回答。',
            '如果资料不足，请明确说明依据不足，不要补造引用。',
        ]
        if mode == 'SCOPED':
            lines.append('当前为指定范围模式，只能使用当前范围内的资料。')
        lines.append('')
        for index, item in enumerate(hits, start=1):
            lines.append(f'资料 {index}: {item.source_title}')
            if item.page_no is not None:
                lines.append(f'页码: {item.page_no}')
            lines.append(f'相关片段: {item.snippet}')
            lines.append(f'相似度: {item.score:.3f}')
            lines.append('')
        return '\n'.join(lines).strip()

    def _build_web_context(self, hits: list[Any]) -> str:
        lines = [
            '以下资料来自联网搜索，请优先依据这些网页摘要回答。',
            '请只引用真实链接，不要编造站点来源。',
            '',
        ]
        for index, item in enumerate(hits, start=1):
            lines.append(f'网页 {index}: {item.title}')
            lines.append(f'链接: {item.url}')
            lines.append(f'摘要: {item.snippet}')
            lines.append('')
        return '\n'.join(lines).strip()

    def _normalized_mode(self, mode: str | None) -> str:
        normalized = (mode or 'GENERAL').strip().upper()
        return 'SCOPED' if normalized == 'SCOPED' else 'GENERAL'

    def _resolve_top_k(self, request: ChatCompletionRequest) -> int:
        requested = request.top_k if request.top_k is not None else self.settings.rag_top_k
        return max(1, min(requested, 10))

    def _resolve_threshold(self, request: ChatCompletionRequest) -> float:
        threshold = request.similarity_threshold if request.similarity_threshold is not None else self.settings.rag_similarity_threshold
        return max(0.0, min(threshold, 1.0))

    def _collection_name(self, scope_type: str) -> str:
        return self.settings.qdrant_temp_collection if scope_type.upper() == 'TEMP' else self.settings.qdrant_kb_collection

    def _upsert_result(self, collection_name: str, upserted: int) -> dict[str, Any]:
        return {
            'upserted': upserted,
            'collectionName': collection_name,
            'embeddingModelCode': self.settings.embedding_model,
            'embeddingVersion': self.settings.embedding_version,
            'embeddingDimension': self.settings.embedding_dimensions,
        }

    def _point_id(
        self,
        scope_type: str,
        source_type: str,
        source_id: int,
        session_id: int | None,
        chunk_num: int,
    ) -> str:
        raw = f'{scope_type}:{source_type}:{source_id}:{session_id or 0}:{chunk_num}'
        return hashlib.md5(raw.encode('utf-8')).hexdigest()


rag_service = RagService()
