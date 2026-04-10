from __future__ import annotations

import hashlib
import logging
import math
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


logger = logging.getLogger(__name__)


@dataclass(slots=True)
class RetrievalDecision:
    answer_source: str
    citations: list[CitationView]
    used_tools: list[str]
    confidence: float | None
    context_messages: list[dict[str, str]]


class RagService:
    RRF_RANK_CONSTANT = 60
    MIN_FUSION_CANDIDATES = 20
    MAX_FUSION_CANDIDATES = 30
    FALLBACK_CONFIDENCE_CAP = 0.74
    RERANK_MODEL_NAME = 'BAAI/bge-reranker-base'

    def __init__(self) -> None:
        self.settings = get_settings()
        self.client = QdrantClient(
            url=self.settings.qdrant_url,
            api_key=self.settings.qdrant_api_key or None,
            check_compatibility=False,
        )
        self._ready_collections: set[str] = set()
        self._reranker: Any | None = None
        self._reranker_unavailable = False

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
        candidate_limit = self._candidate_limit(top_k)

        private_hits = await self._private_hits(request, context, candidate_limit, mode)
        confidence = private_hits[0].score if private_hits else None
        top_hits = private_hits[:top_k]
        used_tools: list[str] = ['private_knowledge']

        if top_hits and confidence is not None and confidence >= threshold:
            return RetrievalDecision(
                answer_source='PRIVATE_KNOWLEDGE',
                citations=[self._to_citation(hit) for hit in top_hits],
                used_tools=used_tools,
                confidence=confidence,
                context_messages=[{'role': 'system', 'content': self._build_private_context(top_hits, mode)}],
            )

        auxiliary_citations = [self._to_citation(hit) for hit in top_hits] if top_hits else []
        auxiliary_messages = (
            [{'role': 'system', 'content': self._build_auxiliary_private_context(top_hits, mode)}]
            if top_hits
            else []
        )

        if bool(request.web_search_enabled):
            used_tools.append('web_search')
            web_hits = await self._safe_web_search(request.message)
            if web_hits:
                web_citations = [
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
                    citations=web_citations + auxiliary_citations,
                    used_tools=used_tools,
                    confidence=confidence,
                    context_messages=auxiliary_messages + [{
                        'role': 'system',
                        'content': self._build_web_context(web_hits, bool(auxiliary_citations)),
                    }],
                )

        used_tools.append('general_knowledge')
        return RetrievalDecision(
            answer_source='GENERAL_KNOWLEDGE',
            citations=auxiliary_citations,
            used_tools=used_tools,
            confidence=confidence,
            context_messages=auxiliary_messages + [{
                'role': 'system',
                'content': self._build_general_context(bool(request.web_search_enabled), bool(auxiliary_citations)),
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
        hits = await self._private_hits(fake_request, context, self._candidate_limit(limit), 'GENERAL')
        return RagQueryResponse(
            hits=hits[:limit],
            citations=decision.citations,
            confidence=decision.confidence,
            answer_source=decision.answer_source,
            used_tools=decision.used_tools,
        )

    async def _private_hits(
        self,
        request: ChatCompletionRequest,
        context: RequestContext,
        candidate_limit: int,
        mode: str,
    ) -> list[RagQueryHit]:
        vector_hits = await self._safe_vector_search(request, context, candidate_limit, mode)
        keyword_hits = await self._safe_keyword_search(request, context, candidate_limit, mode)
        fused_hits = self._fuse_hits(vector_hits, keyword_hits)[:candidate_limit]
        if not fused_hits:
            return []
        return self._rerank_hits(request.message, fused_hits)[:candidate_limit]

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

    async def _safe_web_search(self, query: str) -> list[Any]:
        try:
            return await search_web(query)
        except Exception as exception:
            logger.warning('Web search failed for query=%r: %s', query, exception, exc_info=True)
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
        return self._dedupe_hits(hits)[:top_k]

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
        hits: list[RagQueryHit] = []
        for index, item in enumerate(payload, start=1):
            if item.get('sourceId') is None:
                continue
            hits.append(
                RagQueryHit(
                    score=1.0 / index,
                    source_type=str(item.get('sourceType') or ''),
                    source_id=int(item.get('sourceId') or 0),
                    source_title=str(item.get('title') or 'knowledge-snippet'),
                    snippet=str(item.get('snippet') or ''),
                    page_no=int(item['pageNo']) if item.get('pageNo') is not None else None,
                    section_path=str(item.get('sectionPath') or '') or None,
                )
            )
        return hits

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
                    source_title=str(payload.get('source_name') or 'knowledge-snippet'),
                    snippet=str(payload.get('chunk_text') or ''),
                    page_no=int(payload['page_no']) if payload.get('page_no') is not None else None,
                    section_path=str(payload.get('section_path') or '') or None,
                )
            )
        return hits

    def _candidate_key(self, hit: RagQueryHit) -> str:
        return ':'.join([
            hit.source_type,
            str(hit.source_id),
            str(hit.page_no or 0),
            hit.section_path or '',
            (hit.url or ''),
            hit.snippet[:120],
        ])

    def _dedupe_hits(self, hits: list[RagQueryHit]) -> list[RagQueryHit]:
        deduped: dict[str, RagQueryHit] = {}
        for item in hits:
            key = self._candidate_key(item)
            existing = deduped.get(key)
            if existing is None or item.score > existing.score or len(item.snippet) > len(existing.snippet):
                deduped[key] = item
        return sorted(deduped.values(), key=lambda item: item.score, reverse=True)

    def _fuse_hits(self, vector_hits: list[RagQueryHit], keyword_hits: list[RagQueryHit]) -> list[RagQueryHit]:
        merged: dict[str, RagQueryHit] = {}
        for hits in (self._dedupe_hits(vector_hits), self._dedupe_hits(keyword_hits)):
            for rank, item in enumerate(hits, start=1):
                key = self._candidate_key(item)
                contribution = 1.0 / (self.RRF_RANK_CONSTANT + rank)
                existing = merged.get(key)
                if existing is None:
                    merged[key] = item.model_copy(update={'score': contribution})
                    continue
                merged[key] = self._merge_fused_hit(existing, item, contribution)
        return sorted(merged.values(), key=lambda item: item.score, reverse=True)

    def _merge_fused_hit(self, existing: RagQueryHit, incoming: RagQueryHit, contribution: float) -> RagQueryHit:
        snippet = incoming.snippet if len(incoming.snippet) > len(existing.snippet) else existing.snippet
        return existing.model_copy(update={
            'score': float(existing.score) + contribution,
            'snippet': snippet,
            'page_no': existing.page_no if existing.page_no is not None else incoming.page_no,
            'section_path': existing.section_path or incoming.section_path,
            'url': existing.url or incoming.url,
        })

    def _rerank_hits(self, query: str, hits: list[RagQueryHit]) -> list[RagQueryHit]:
        if len(hits) <= 1:
            return self._fallback_hits(hits)
        reranker = self._get_reranker()
        if reranker is None:
            return self._fallback_hits(hits)
        documents = [self._rerank_document(hit) for hit in hits]
        try:
            raw_scores = list(reranker.rerank(query, documents))
        except Exception as exception:
            logger.warning('Rerank failed, falling back to conservative RRF ordering: %s', exception, exc_info=True)
            self._reranker_unavailable = True
            self._reranker = None
            return self._fallback_hits(hits)
        if len(raw_scores) != len(hits):
            return self._fallback_hits(hits)
        reranked = [
            hit.model_copy(update={'score': self._sigmoid(float(raw_score))})
            for hit, raw_score in zip(hits, raw_scores, strict=False)
        ]
        return sorted(reranked, key=lambda item: item.score, reverse=True)

    def _fallback_hits(self, hits: list[RagQueryHit]) -> list[RagQueryHit]:
        if not hits:
            return []
        top_score = max(float(hit.score) for hit in hits)
        if top_score <= 0:
            return [hit.model_copy(update={'score': 0.0}) for hit in hits]
        normalized = [
            hit.model_copy(update={
                'score': min(
                    self.FALLBACK_CONFIDENCE_CAP,
                    max(0.05, float(hit.score) / top_score * self.FALLBACK_CONFIDENCE_CAP),
                )
            })
            for hit in hits
        ]
        return sorted(normalized, key=lambda item: item.score, reverse=True)

    def _get_reranker(self) -> Any | None:
        if self._reranker_unavailable:
            return None
        if self._reranker is not None:
            return self._reranker
        try:
            from fastembed.rerank.cross_encoder import TextCrossEncoder
        except Exception as exception:
            logger.warning('FastEmbed reranker is unavailable: %s', exception, exc_info=True)
            self._reranker_unavailable = True
            return None
        try:
            self._reranker = TextCrossEncoder(model_name=self.RERANK_MODEL_NAME)
        except Exception as exception:
            logger.warning('Failed to initialize reranker `%s`: %s', self.RERANK_MODEL_NAME, exception, exc_info=True)
            self._reranker_unavailable = True
            self._reranker = None
        return self._reranker

    def _rerank_document(self, hit: RagQueryHit) -> str:
        parts = [hit.source_title]
        if hit.section_path:
            parts.append(f'Section: {hit.section_path}')
        if hit.page_no is not None:
            parts.append(f'Page: {hit.page_no}')
        parts.append(hit.snippet)
        return '\n'.join(part for part in parts if part)

    def _sigmoid(self, value: float) -> float:
        if value >= 12:
            return 1.0
        if value <= -12:
            return 0.0
        return 1.0 / (1.0 + math.exp(-value))

    def _to_citation(self, hit: RagQueryHit) -> CitationView:
        return CitationView(
            source_type=hit.source_type,
            source_id=hit.source_id,
            source_title=hit.source_title,
            snippet=hit.snippet,
            page_no=hit.page_no,
            section_path=hit.section_path,
            score=hit.score,
            url=hit.url,
        )

    def _build_private_context(self, hits: list[RagQueryHit], mode: str) -> str:
        lines = [
            '以下资料来自私有知识库，请优先依据这些证据回答。',
            '如证据不足，可以参考后续补充上下文，但不要伪造私有引用。',
        ]
        if mode == 'SCOPED':
            lines.append('当前处于指定范围模式，私有检索仅限当前项目或显式附加资料。')
        lines.append('')
        for index, item in enumerate(hits, start=1):
            lines.append(f'资料 {index}: {item.source_title}')
            if item.section_path:
                lines.append(f'结构: {item.section_path}')
            if item.page_no is not None:
                lines.append(f'页码: {item.page_no}')
            lines.append(f'相关片段: {item.snippet}')
            lines.append(f'置信度: {item.score:.3f}')
            lines.append('')
        return '\n'.join(lines).strip()

    def _build_auxiliary_private_context(self, hits: list[RagQueryHit], mode: str) -> str:
        lines = [
            '以下私有资料相关性较弱，只能作为辅助背景，不可当作唯一依据。',
        ]
        if mode == 'SCOPED':
            lines.append('这些资料仍然来自当前指定范围，仅用于补充上下文。')
        lines.append('')
        for index, item in enumerate(hits, start=1):
            lines.append(f'辅助资料 {index}: {item.source_title}')
            if item.section_path:
                lines.append(f'结构: {item.section_path}')
            if item.page_no is not None:
                lines.append(f'页码: {item.page_no}')
            lines.append(f'相关片段: {item.snippet}')
            lines.append(f'置信度: {item.score:.3f}')
            lines.append('')
        return '\n'.join(lines).strip()

    def _build_web_context(self, hits: list[Any], has_auxiliary_private: bool) -> str:
        lines = [
            '以下资料来自联网搜索，请把联网结果作为本轮回答的主要依据。',
            '请只引用真实链接，不要编造来源。',
            '',
        ]
        if has_auxiliary_private:
            lines.insert(1, '当前同时附带了弱相关私有资料，它们只能作为补充背景。')
        for index, item in enumerate(hits, start=1):
            lines.append(f'网页 {index}: {item.title}')
            lines.append(f'链接: {item.url}')
            lines.append(f'摘要: {item.snippet}')
            lines.append('')
        return '\n'.join(lines).strip()

    def _build_general_context(self, web_search_enabled: bool, has_auxiliary_private: bool) -> str:
        lines: list[str] = []
        if has_auxiliary_private:
            lines.append('当前存在弱相关的私有资料，可作补充背景，但不能作为主要依据。')
        if web_search_enabled:
            lines.append('本轮联网搜索未返回可用结果，请改用通用知识作为主要依据。')
        else:
            lines.append('本轮未开启联网搜索，请直接使用通用知识回答。')
        lines.append('不要伪造引用；如确实参考了上方私有资料，只能基于已给出的片段做有限补充。')
        return '\n'.join(lines)

    def _normalized_mode(self, mode: str | None) -> str:
        normalized = (mode or 'GENERAL').strip().upper()
        return 'SCOPED' if normalized == 'SCOPED' else 'GENERAL'

    def _candidate_limit(self, top_k: int) -> int:
        return min(self.MAX_FUSION_CANDIDATES, max(self.MIN_FUSION_CANDIDATES, top_k * 4))

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
