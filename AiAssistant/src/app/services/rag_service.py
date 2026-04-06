from __future__ import annotations

import hashlib
from dataclasses import dataclass
from typing import Any

from qdrant_client import QdrantClient
from qdrant_client.models import Distance, FieldCondition, Filter, FilterSelector, MatchAny, MatchValue, PointStruct, VectorParams

from src.app.adapters.java.attachment_client import fetch_document_context, fetch_file_context, fetch_temp_file_context
from src.app.adapters.java.knowledge_client import query_knowledge
from src.app.api.deps import RequestContext
from src.app.core.config import get_settings
from src.app.schemas.chat import AttachmentContext, ChatCompletionRequest
from src.app.schemas.message import CitationView
from src.app.schemas.rag import RagIndexDeleteRequest, RagIndexUpsertRequest, RagQueryHit, RagQueryResponse
from src.app.services.embedding_service import embedding_service
from src.app.services.web_search_service import search_web


@dataclass(slots=True)
class RetrievalDecision:
    answer_source: str
    citations: list[CitationView]
    used_tools: list[str]
    confidence: float | None
    context_messages: list[dict[str, str]]


@dataclass(slots=True)
class DirectAttachmentContext:
    hit: RagQueryHit
    context_text: str


class RagService:
    def __init__(self) -> None:
        self.settings = get_settings()
        self.client = QdrantClient(url=self.settings.qdrant_url, api_key=self.settings.qdrant_api_key or None)
        self._collection_ready = False

    def _ensure_collection(self) -> None:
        if self._collection_ready:
            return
        collections = {item.name for item in self.client.get_collections().collections}
        if self.settings.qdrant_collection not in collections:
            self.client.create_collection(
                collection_name=self.settings.qdrant_collection,
                vectors_config=VectorParams(size=self.settings.embedding_dimensions, distance=Distance.COSINE),
            )
        self._collection_ready = True

    async def upsert(self, request: RagIndexUpsertRequest) -> dict[str, Any]:
        self._ensure_collection()
        await self.delete(RagIndexDeleteRequest(
            user_id=request.user_id,
            project_id=request.project_id,
            session_id=request.session_id,
            source_type=request.source_type,
            source_id=request.source_id,
        ))
        if not request.chunks:
            return {'upserted': 0}

        vectors = await embedding_service.embed([item.chunk_text for item in request.chunks])
        points = [
            PointStruct(
                id=self._point_id(request.source_type, request.source_id, request.session_id, chunk.chunk_num),
                vector=vectors[index],
                payload={
                    'chunk_id': self._point_id(request.source_type, request.source_id, request.session_id, chunk.chunk_num),
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
                    'created_at': request.updated_at.isoformat() if request.updated_at else None,
                    'updated_at': request.updated_at.isoformat() if request.updated_at else None,
                },
            )
            for index, chunk in enumerate(request.chunks)
        ]
        self.client.upsert(collection_name=self.settings.qdrant_collection, points=points)
        return {'upserted': len(points)}

    async def delete(self, request: RagIndexDeleteRequest) -> dict[str, Any]:
        self._ensure_collection()
        query_filter = Filter(must=[
            FieldCondition(key='user_id', match=MatchValue(value=request.user_id)),
            FieldCondition(key='source_type', match=MatchValue(value=request.source_type)),
            FieldCondition(key='source_id', match=MatchValue(value=request.source_id)),
            *([FieldCondition(key='session_id', match=MatchValue(value=request.session_id))] if request.session_id is not None else []),
        ])
        self.client.delete(collection_name=self.settings.qdrant_collection, points_selector=FilterSelector(filter=query_filter))
        return {'deleted': True}

    async def query(self, request: ChatCompletionRequest, context: RequestContext) -> RetrievalDecision:
        self._ensure_collection()
        threshold = request.similarity_threshold if request.similarity_threshold is not None else 0.75
        direct_attachment_contexts = await self._load_direct_attachment_contexts(request, context.request_id)
        direct_hits = [item.hit for item in direct_attachment_contexts]
        vector_hits = await self._safe_vector_search(request, context)
        keyword_hits = await self._safe_keyword_search(request, context)
        merged_hits = self._merge_hits(direct_hits, vector_hits, keyword_hits)
        confidence = merged_hits[0].score if merged_hits else None

        if direct_attachment_contexts or (merged_hits and confidence is not None and confidence >= threshold):
            top_hits = merged_hits[:4]
            return RetrievalDecision(
                answer_source='PRIVATE_KNOWLEDGE',
                citations=[self._to_citation(hit) for hit in top_hits],
                used_tools=['private_knowledge'],
                confidence=confidence,
                context_messages=[{'role': 'system', 'content': self._build_private_context(top_hits, direct_attachment_contexts)}],
            )

        if bool(request.allow_web_search):
            web_hits = await search_web(request.message)
            if web_hits:
                citations = [
                    CitationView(source_type='WEB', source_id=index + 1, source_title=item.title, snippet=item.snippet, source_url=item.url, site_name=item.site_name)
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
            used_tools=['general_answer'],
            confidence=confidence,
            context_messages=[{'role': 'system', 'content': '本轮未命中足够可靠的私有知识库结果，也未启用联网搜索。请明确说明来源为“通用知识”，不要伪造引用。'}],
        )

    async def debug_query(self, user_id: int, query: str, project_scope_ids: list[int], attachments: list[dict], limit: int) -> RagQueryResponse:
        fake_request = ChatCompletionRequest(message=query, context={'scopeProjectIds': project_scope_ids, 'attachments': attachments}, similarity_threshold=0.75, allow_web_search=False)
        context = RequestContext(user_id=user_id, org_id=0, request_id='rag-debug')
        decision = await self.query(fake_request, context)
        hits = await self._safe_vector_search(fake_request, context)
        return RagQueryResponse(hits=[RagQueryHit(**hit.model_dump()) for hit in hits[:limit]], citations=decision.citations, confidence=decision.confidence, answer_source=decision.answer_source)

    async def _safe_vector_search(self, request: ChatCompletionRequest, context: RequestContext) -> list[RagQueryHit]:
        try:
            return await self._vector_search(request, context)
        except Exception:
            return []

    async def _safe_keyword_search(self, request: ChatCompletionRequest, context: RequestContext) -> list[RagQueryHit]:
        try:
            return await self._keyword_search(request, context)
        except Exception:
            return []

    async def _vector_search(self, request: ChatCompletionRequest, context: RequestContext) -> list[RagQueryHit]:
        vector = (await embedding_service.embed([request.message]))[0]
        attachments = self._knowledge_attachments(request)
        if attachments:
            collected: list[RagQueryHit] = []
            for attachment in attachments:
                response = self.client.search(
                    collection_name=self.settings.qdrant_collection,
                    query_vector=vector,
                    query_filter=self._build_attachment_filter(context.user_id, attachment),
                    limit=max(2, self.settings.rag_top_k),
                    with_payload=True,
                )
                collected.extend(self._response_to_hits(response))
            return self._merge_hits(collected)[:self.settings.rag_top_k]

        response = self.client.search(
            collection_name=self.settings.qdrant_collection,
            query_vector=vector,
            query_filter=self._build_scope_filter(request, context),
            limit=self.settings.rag_top_k,
            with_payload=True,
        )
        return self._response_to_hits(response)
    async def _keyword_search(self, request: ChatCompletionRequest, context: RequestContext) -> list[RagQueryHit]:
        payload = await query_knowledge(
            user_id=context.user_id,
            keyword=request.message,
            request_id=context.request_id,
            project_scope_ids=request.context.scope_project_ids or ([request.context.project_id] if request.context.project_id else []),
            attachments=[{'attachmentType': item.attachment_type, 'sourceId': item.source_id, 'sessionId': item.session_id} for item in self._knowledge_attachments(request)],
            limit=self.settings.rag_keyword_top_k,
        )
        return [
            RagQueryHit(
                score=0.55,
                source_type=str(item.get('sourceType') or ''),
                source_id=int(item.get('sourceId') or 0),
                source_title=str(item.get('title') or '知识片段'),
                snippet=str(item.get('snippet') or ''),
                page_no=None,
            )
            for item in payload
        ]

    async def _load_direct_attachment_contexts(self, request: ChatCompletionRequest, request_id: str) -> list[DirectAttachmentContext]:
        contexts: list[DirectAttachmentContext] = []
        for attachment in self._knowledge_attachments(request):
            payload = await self._fetch_attachment_context(attachment, request_id)
            plain_text = str(payload.get('plainText') or '').strip() if payload else ''
            if not plain_text:
                continue
            contexts.append(DirectAttachmentContext(
                hit=RagQueryHit(
                    score=0.99,
                    source_type=attachment.attachment_type,
                    source_id=attachment.source_id,
                    source_title=self._attachment_title(attachment, payload),
                    snippet=plain_text[:240],
                    page_no=None,
                ),
                context_text=plain_text[:2000],
            ))
        return contexts

    async def _fetch_attachment_context(self, attachment: AttachmentContext, request_id: str) -> dict[str, object] | None:
        if attachment.attachment_type == 'DOCUMENT':
            return await fetch_document_context(attachment.source_id, request_id)
        if attachment.attachment_type == 'FILE':
            return await fetch_file_context(attachment.source_id, request_id)
        if attachment.attachment_type == 'TEMP_FILE':
            return await fetch_temp_file_context(attachment.source_id, request_id)
        return None

    def _attachment_title(self, attachment: AttachmentContext, payload: dict[str, object] | None) -> str:
        if payload:
            return str(payload.get('title') or payload.get('fileName') or attachment.title or f'{attachment.attachment_type} #{attachment.source_id}')
        return attachment.title or f'{attachment.attachment_type} #{attachment.source_id}'

    def _response_to_hits(self, response: list[Any]) -> list[RagQueryHit]:
        hits: list[RagQueryHit] = []
        for item in response:
            payload = item.payload or {}
            hits.append(RagQueryHit(
                score=float(item.score),
                source_type=str(payload.get('source_type') or ''),
                source_id=int(payload.get('source_id') or 0),
                source_title=str(payload.get('source_name') or '知识片段'),
                snippet=str(payload.get('chunk_text') or ''),
                page_no=int(payload['page_no']) if payload.get('page_no') is not None else None,
            ))
        return hits

    def _knowledge_attachments(self, request: ChatCompletionRequest) -> list[AttachmentContext]:
        return [item for item in request.context.attachments if item.attachment_type in {'DOCUMENT', 'FILE', 'TEMP_FILE'}]

    def _build_scope_filter(self, request: ChatCompletionRequest, context: RequestContext) -> Filter:
        must: list[FieldCondition] = [FieldCondition(key='user_id', match=MatchValue(value=context.user_id))]
        scope_project_ids = request.context.scope_project_ids or ([request.context.project_id] if request.context.project_id else [])
        if scope_project_ids:
            must.append(FieldCondition(key='project_id', match=MatchAny(any=scope_project_ids)))
        return Filter(must=must)

    def _build_attachment_filter(self, user_id: int, attachment: AttachmentContext) -> Filter:
        must: list[FieldCondition] = [
            FieldCondition(key='user_id', match=MatchValue(value=user_id)),
            FieldCondition(key='source_type', match=MatchValue(value=attachment.attachment_type)),
            FieldCondition(key='source_id', match=MatchValue(value=attachment.source_id)),
        ]
        if attachment.session_id is not None:
            must.append(FieldCondition(key='session_id', match=MatchValue(value=attachment.session_id)))
        return Filter(must=must)

    def _merge_hits(self, *hit_groups: list[RagQueryHit]) -> list[RagQueryHit]:
        merged: dict[str, RagQueryHit] = {}
        for item in [hit for group in hit_groups for hit in group]:
            key = f'{item.source_type}:{item.source_id}:{item.page_no or 0}:{item.snippet[:80]}'
            existing = merged.get(key)
            if existing is None or item.score > existing.score:
                merged[key] = item
        return sorted(merged.values(), key=lambda item: item.score, reverse=True)

    def _to_citation(self, hit: RagQueryHit) -> CitationView:
        return CitationView(source_type=hit.source_type, source_id=hit.source_id, source_title=hit.source_title, snippet=hit.snippet, page_no=hit.page_no)

    def _build_private_context(self, hits: list[RagQueryHit], direct_attachment_contexts: list[DirectAttachmentContext] | None = None) -> str:
        direct_context_map = {f'{item.hit.source_type}:{item.hit.source_id}': item.context_text for item in direct_attachment_contexts or []}
        lines = [
            '本轮回答来源类型：私有知识库。',
            '请在答案开头明确写出“来源：私有知识库”，并严格基于下列资料回答，不要补造引用。',
            '',
        ]
        for index, item in enumerate(hits, start=1):
            source_key = f'{item.source_type}:{item.source_id}'
            lines.append(f'资料 {index}: {item.source_title}')
            lines.append(f'相关片段: {direct_context_map.get(source_key) or item.snippet}')
            lines.append(f'相似度分数: {item.score:.3f}')
            if item.page_no is not None:
                lines.append(f'页码: {item.page_no}')
            lines.append('')
        return '\n'.join(lines).strip()

    def _build_web_context(self, hits: list[Any]) -> str:
        lines = [
            '本轮回答来源类型：联网搜索。',
            '请在答案开头明确写出“来源：联网搜索”，并优先引用下面的网页摘要。',
            '',
        ]
        for index, item in enumerate(hits, start=1):
            lines.append(f'网页 {index}: {item.title}')
            lines.append(f'链接: {item.url}')
            lines.append(f'摘要: {item.snippet}')
            lines.append('')
        return '\n'.join(lines).strip()

    def _point_id(self, source_type: str, source_id: int, session_id: int | None, chunk_num: int) -> str:
        raw = f'{source_type}:{source_id}:{session_id or 0}:{chunk_num}'
        return hashlib.md5(raw.encode('utf-8')).hexdigest()


rag_service = RagService()
