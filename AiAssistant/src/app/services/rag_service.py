from __future__ import annotations

import hashlib
import logging
import math
import re
from dataclasses import dataclass, field
from time import sleep
from time import perf_counter
from typing import Any

from qdrant_client import QdrantClient
from qdrant_client.models import (
    Distance,
    FieldCondition,
    Filter,
    FilterSelector,
    MatchAny,
    MatchValue,
    Modifier,
    PointIdsList,
    PointStruct,
    SparseIndexParams,
    SparseVector,
    SparseVectorParams,
    VectorParams,
)
from src.app.api.deps import RequestContext
from src.app.core.config import get_settings
from src.app.schemas.chat import AttachmentContext, ChatCompletionRequest, ChatContext
from src.app.schemas.message import CitationView
from src.app.schemas.rag import (
    RagIndexDeleteRequest,
    RagIndexUpsertRequest,
    RagQueryHit,
    RagQueryResponse,
    build_debug_chat_context,
)
from src.app.services.embedding_service import embedding_service
from src.app.services.model_registry import get_embedding_model_code
from src.app.services.query_rewrite_service import QueryRewritePlan, QueryVariant, query_rewrite_service
from src.app.services.retrieval_llm_service import retrieval_llm_service
from src.app.services.sparse_vector_service import sparse_vector_service
from src.app.services.web_search_service import search_web


logger = logging.getLogger(__name__)
MAX_PRIVATE_CONTEXT_HITS = 3
MAX_PRIVATE_CONTEXT_TOTAL_CHARS = 1200
MAX_WEB_CONTEXT_ITEMS = 3
MAX_WEB_CONTEXT_TOTAL_CHARS = 900
MAX_CONTEXT_SNIPPET_CHARS = 320
MAX_CONTEXT_TITLE_CHARS = 80
MAX_CONTEXT_SECTION_CHARS = 80

WEB_SEARCH_EXPLICIT_PATTERNS = [
    re.compile(pattern, re.IGNORECASE)
    for pattern in (
        r'联网',
        r'上网',
        r'搜(?:一下|一搜|索)?',
        r'搜索',
        r'查(?:一下|一查|查一下|查看|找一下|找找)?',
        r'检索',
        r'官网',
        r'链接',
        r'来源',
        r'\bsearch\b',
        r'\blook\s+up\b',
        r'\bbrowse\b',
        r'\bgoogle\b',
        r'\bbing\b',
        r'\bofficial\s+site\b',
        r'\bsource\b',
        r'\blink\b',
    )
]
WEB_SEARCH_FRESHNESS_PATTERNS = [
    re.compile(pattern, re.IGNORECASE)
    for pattern in (
        r'最新',
        r'最近',
        r'当前',
        r'目前',
        r'现在',
        r'实时',
        r'今天',
        r'今日',
        r'昨天',
        r'明天',
        r'本周',
        r'本月',
        r'今年',
        r'新闻',
        r'天气',
        r'股价',
        r'价格',
        r'汇率',
        r'市值',
        r'版本',
        r'发布',
        r'更新',
        r'\btoday\b',
        r'\bcurrent\b',
        r'\blatest\b',
        r'\brecent\b',
        r'\bnow\b',
        r'\bnews\b',
        r'\bweather\b',
        r'\bprice\b',
        r'\bversion\b',
        r'\brelease\b',
        r'\bupdate\b',
    )
]


@dataclass(slots=True)
class RetrievalDecision:
    answer_source: str
    citations: list[CitationView]
    used_tools: list[str]
    confidence: float | None
    context_messages: list[dict[str, str]]
    rewritten_queries: list[str]
    rewrite_hints: list[str]


@dataclass(slots=True)
class RetrievalStage:
    key: str
    label: str
    hits: list[RagQueryHit]
    sufficient: bool
    confidence: float | None
    used_tools: list[str] = field(default_factory=list)


@dataclass(slots=True)
class RetrievalWeights:
    vector_weight: float
    keyword_weight: float
    profile: str


@dataclass(slots=True)
class PrivateRetrievalResult:
    hits: list[RagQueryHit]
    used_tools: list[str]


class RagService:
    RRF_RANK_CONSTANT = 60
    MIN_FUSION_CANDIDATES = 20
    MAX_FUSION_CANDIDATES = 30
    FALLBACK_CONFIDENCE_CAP = 0.82
    RERANK_MODEL_NAME = 'BAAI/bge-reranker-base'
    DENSE_VECTOR_NAME = 'dense'
    SPARSE_VECTOR_NAME = 'sparse'

    def __init__(self) -> None:
        self.settings = get_settings()
        self.client = QdrantClient(
            url=self.settings.qdrant_url,
            api_key=self.settings.qdrant_api_key or None,
            check_compatibility=False,
            timeout=self.settings.resolved_qdrant_timeout_seconds,
        )
        self._ready_collections: set[str] = set()
        self._reranker: Any | None = None
        self._reranker_unavailable = False

    def _ensure_collection(self, collection_name: str) -> None:
        if collection_name in self._ready_collections:
            return
        collections = {
            item.name
            for item in self._run_qdrant('list collections', lambda: self.client.get_collections()).collections
        }
        if collection_name in collections and not self._collection_matches_embedding_dimension(collection_name):
            self._recreate_incompatible_collection(collection_name)
            self._ready_collections.add(collection_name)
            return
        if collection_name not in collections:
            self._run_qdrant(
                f'create collection `{collection_name}`',
                lambda: self._create_collection(collection_name),
            )
        self._ready_collections.add(collection_name)

    def _create_collection(self, collection_name: str) -> None:
        self.client.create_collection(
            collection_name=collection_name,
            vectors_config={
                self.DENSE_VECTOR_NAME: VectorParams(
                    size=self.settings.embedding_dimensions,
                    distance=Distance.COSINE,
                )
            },
            sparse_vectors_config={
                self.SPARSE_VECTOR_NAME: SparseVectorParams(
                    index=SparseIndexParams(),
                    modifier=Modifier.IDF,
                )
            },
        )

    def _collection_matches_embedding_dimension(self, collection_name: str) -> bool:
        info = self._run_qdrant(f'get collection `{collection_name}`', lambda: self.client.get_collection(collection_name))
        params = getattr(info.config, 'params', None)
        vector_config = getattr(params, 'vectors', None)
        sparse_config = getattr(params, 'sparse_vectors', None)
        current_size = self._extract_dense_vector_size(vector_config)
        has_sparse = self._has_sparse_vector(sparse_config)
        return current_size == self.settings.embedding_dimensions and has_sparse

    def _recreate_incompatible_collection(self, collection_name: str) -> None:
        info = self._run_qdrant(f'get collection `{collection_name}`', lambda: self.client.get_collection(collection_name))
        points_count = int(getattr(info, 'points_count', 0) or 0)
        params = getattr(info.config, 'params', None)
        current_size = self._extract_dense_vector_size(getattr(params, 'vectors', None))
        has_sparse = self._has_sparse_vector(getattr(params, 'sparse_vectors', None))
        logger.warning(
            '检测到 Qdrant collection `%s` 仍是旧索引结构，准备强制重建为 dense+sparse 一体化结构。'
            '当前点数=%s，旧 dense 维度=%s，是否包含 sparse=%s，目标 dense 维度=%s。',
            collection_name,
            points_count,
            current_size,
            self.settings.embedding_dimensions,
            has_sparse,
        )
        logger.warning(
            '由于当前环境中的历史数据均视为可丢弃测试数据，系统将直接删除并重建 `%s`。',
            collection_name,
        )
        self._run_qdrant(f'delete collection `{collection_name}`', lambda: self.client.delete_collection(collection_name))
        self._run_qdrant(f'create collection `{collection_name}`', lambda: self._create_collection(collection_name))

    def _is_retryable_qdrant_error(self, message: str) -> bool:
        normalized = (message or '').lower()
        return (
            'timeout' in normalized
            or 'timed out' in normalized
            or 'connection refused' in normalized
            or 'connection reset' in normalized
            or 'connection aborted' in normalized
            or 'network is unreachable' in normalized
            or 'temporarily unavailable' in normalized
            or 'service unavailable' in normalized
            or 'bad gateway' in normalized
            or 'gateway timeout' in normalized
        )

    def _extract_dense_vector_size(self, vector_config: Any) -> int | None:
        if vector_config is None:
            return None
        if hasattr(vector_config, 'size'):
            return getattr(vector_config, 'size', None)
        if isinstance(vector_config, dict):
            dense_config = vector_config.get(self.DENSE_VECTOR_NAME)
            return getattr(dense_config, 'size', None) if dense_config is not None else None
        dense_config = getattr(vector_config, self.DENSE_VECTOR_NAME, None)
        if dense_config is not None:
            return getattr(dense_config, 'size', None)
        if hasattr(vector_config, 'model_dump'):
            dumped = vector_config.model_dump()
            if isinstance(dumped, dict):
                dense_config = dumped.get(self.DENSE_VECTOR_NAME)
                if isinstance(dense_config, dict):
                    return dense_config.get('size')
        return None

    def _has_sparse_vector(self, sparse_config: Any) -> bool:
        if sparse_config is None:
            return False
        if isinstance(sparse_config, dict):
            return self.SPARSE_VECTOR_NAME in sparse_config
        if hasattr(sparse_config, self.SPARSE_VECTOR_NAME):
            return True
        if hasattr(sparse_config, 'model_dump'):
            dumped = sparse_config.model_dump()
            if isinstance(dumped, dict):
                return self.SPARSE_VECTOR_NAME in dumped
        return False

    def _run_qdrant(self, action: str, operation: Any) -> Any:
        attempts = max(1, self.settings.resolved_qdrant_max_retries + 1)
        last_error: Exception | None = None
        for attempt in range(1, attempts + 1):
            try:
                return operation()
            except Exception as exc:
                last_error = exc
                message = str(exc)
                if self._is_retryable_qdrant_error(message) and attempt < attempts:
                    wait_seconds = self.settings.resolved_retry_backoff_seconds * attempt
                    logger.warning(
                        'Qdrant %s failed on attempt %s/%s, retrying in %.1fs: %s',
                        action,
                        attempt,
                        attempts,
                        wait_seconds,
                        message,
                    )
                    sleep(wait_seconds)
                    continue
                raise RuntimeError(f'Qdrant {action} failed: {message}') from exc
        if last_error is not None:
            raise RuntimeError(f'Qdrant {action} failed: {last_error}') from last_error
        raise RuntimeError(f'Qdrant {action} failed')

    async def upsert(self, request: RagIndexUpsertRequest) -> dict[str, Any]:
        started_at = perf_counter()
        collection_name = self._collection_name(request.scope_type)
        self._ensure_collection(collection_name)
        if not request.chunks:
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
            return self._upsert_result(collection_name, 0)

        embedding_started_at = perf_counter()
        try:
            vectors = await embedding_service.embed([item.chunk_text for item in request.chunks])
            embedding_duration_ms = int((perf_counter() - embedding_started_at) * 1000)
        except Exception as exc:
            embedding_duration_ms = int((perf_counter() - embedding_started_at) * 1000)
            return self._handle_upsert_failure(
                request=request,
                collection_name=collection_name,
                stage='embedding',
                error=exc,
                started_at=started_at,
                embedding_duration_ms=embedding_duration_ms,
                qdrant_duration_ms=None,
            )
        points = [
            PointStruct(
                id=self._point_id(
                    request.scope_type,
                    request.source_type,
                    request.source_id,
                    request.session_id,
                    chunk.chunk_num,
                ),
                vector={
                    self.DENSE_VECTOR_NAME: vectors[index],
                    self.SPARSE_VECTOR_NAME: sparse_vector_service.build_document_vector(
                        source_title=request.source_name,
                        section_path=chunk.section_path,
                        chunk_text=chunk.chunk_text,
                    ),
                },
                payload={
                    'scope_type': request.scope_type.upper(),
                    'user_id': request.user_id,
                    'project_id': request.project_id,
                    'session_id': request.session_id,
                    'source_type': request.source_type,
                    'source_id': request.source_id,
                    'source_name': request.source_name,
                    'source_url': request.source_url or self._build_citation_url(
                        request.source_type,
                        request.source_id,
                        chunk.page_no,
                        chunk.section_path,
                        chunk.chunk_num,
                    ),
                    'source_version': request.source_version,
                    'chunk_num': chunk.chunk_num,
                    'chunk_id': chunk.chunk_id or self._chunk_id(request.source_type, request.source_id, chunk.chunk_num),
                    'chunk_hash': chunk.chunk_hash or self._chunk_hash(chunk.chunk_text),
                    'chunk_text': chunk.chunk_text,
                    'page_no': chunk.page_no,
                    'section_path': chunk.section_path,
                    'task_id': chunk.task_id or request.task_id,
                    'metadata': {
                        **(request.metadata or {}),
                        **(chunk.metadata or {}),
                        'embedding_model': get_embedding_model_code(),
                        'embedding_version': self.settings.embedding_version,
                    },
                    'updated_at': request.updated_at.isoformat() if request.updated_at else None,
                },
            )
            for index, chunk in enumerate(request.chunks)
        ]
        batch_size = max(1, self.settings.qdrant_upsert_batch_size)
        qdrant_started_at = perf_counter()
        try:
            for start in range(0, len(points), batch_size):
                batch = points[start:start + batch_size]
                self._run_qdrant(
                    f'upsert into `{collection_name}`',
                    lambda batch=batch: self.client.upsert(collection_name=collection_name, points=batch),
                )
            stale_point_ids = self._stale_point_ids(request)
            if stale_point_ids:
                self._run_qdrant(
                    f'delete stale points from `{collection_name}`',
                    lambda: self.client.delete(
                        collection_name=collection_name,
                        points_selector=PointIdsList(points=stale_point_ids),
                    ),
                )
        except Exception as exc:
            qdrant_duration_ms = int((perf_counter() - qdrant_started_at) * 1000)
            return self._handle_upsert_failure(
                request=request,
                collection_name=collection_name,
                stage='qdrant',
                error=exc,
                started_at=started_at,
                embedding_duration_ms=embedding_duration_ms,
                qdrant_duration_ms=qdrant_duration_ms,
            )
        qdrant_duration_ms = int((perf_counter() - qdrant_started_at) * 1000)
        total_duration_ms = int((perf_counter() - started_at) * 1000)
        logger.info(
            'rag_upsert_complete scope_type=%s source_type=%s source_id=%s chunk_count=%s batch_size=%s '
            'embedding_ms=%s qdrant_ms=%s total_ms=%s collection=%s',
            request.scope_type,
            request.source_type,
            request.source_id,
            len(points),
            batch_size,
            embedding_duration_ms,
            qdrant_duration_ms,
            total_duration_ms,
            collection_name,
        )
        return self._upsert_result(collection_name, len(points))

    def _handle_upsert_failure(
        self,
        request: RagIndexUpsertRequest,
        collection_name: str,
        stage: str,
        error: Exception,
        started_at: float,
        embedding_duration_ms: int | None,
        qdrant_duration_ms: int | None,
    ) -> dict[str, Any]:
        total_duration_ms = int((perf_counter() - started_at) * 1000)
        message = str(error)
        logger.warning(
            'rag_upsert_failed scope_type=%s source_type=%s source_id=%s stage=%s embedding_ms=%s qdrant_ms=%s total_ms=%s error=%s',
            request.scope_type,
            request.source_type,
            request.source_id,
            stage,
            embedding_duration_ms,
            qdrant_duration_ms,
            total_duration_ms,
            message,
        )
        if not (self.settings.is_dev_env and self.settings.dev_rag_degrade_enabled):
            raise error
        logger.warning(
            'rag_upsert_degraded scope_type=%s source_type=%s source_id=%s stage=%s mode=sparse-only',
            request.scope_type,
            request.source_type,
            request.source_id,
            stage,
        )
        return self._upsert_result(
            collection_name,
            0,
            {
                'degraded': True,
                'indexMode': 'SPARSE_ONLY',
                'warning': f'{stage} unavailable in dev, falling back to sparse-only indexing',
                'failureStage': stage,
                'failureReason': message,
                'embeddingMs': embedding_duration_ms,
                'qdrantMs': qdrant_duration_ms,
                'totalMs': total_duration_ms,
            },
        )

    async def delete(self, request: RagIndexDeleteRequest) -> dict[str, Any]:
        collection_name = self._collection_name(request.scope_type)
        self._ensure_collection(collection_name)
        self._run_qdrant(
            f'delete from `{collection_name}`',
            lambda: self.client.delete(
                collection_name=collection_name,
                points_selector=FilterSelector(filter=self._source_filter(request)),
            ),
        )
        return {'deleted': True, 'collectionName': collection_name}

    def _stale_point_ids(self, request: RagIndexUpsertRequest) -> list[str]:
        previous_chunk_count = request.previous_chunk_count or 0
        current_chunk_count = len(request.chunks)
        if previous_chunk_count <= current_chunk_count:
            return []
        return [
            self._point_id(
                request.scope_type,
                request.source_type,
                request.source_id,
                request.session_id,
                chunk_num,
            )
            for chunk_num in range(current_chunk_count, previous_chunk_count)
        ]

    def _source_filter(self, request: RagIndexDeleteRequest | RagIndexUpsertRequest) -> Filter:
        must: list[FieldCondition] = [
            FieldCondition(key='user_id', match=MatchValue(value=request.user_id)),
            FieldCondition(key='source_type', match=MatchValue(value=request.source_type)),
            FieldCondition(key='source_id', match=MatchValue(value=request.source_id)),
        ]
        if request.session_id is not None:
            must.append(FieldCondition(key='session_id', match=MatchValue(value=request.session_id)))
        return Filter(must=must)

    async def query(self, request: ChatCompletionRequest, context: RequestContext) -> RetrievalDecision:
        top_k = self._resolve_top_k(request)
        threshold = self._resolve_threshold(request)
        mode = self._normalized_mode(request.mode)
        candidate_limit = self._candidate_limit(top_k)
        priority_attachments = self._knowledge_attachments(request)
        project_scope_ids = self._explicit_project_scope_ids(request)
        query_plan = await query_rewrite_service.build_plan_async(
            request.message,
            project_scope_ids=project_scope_ids,
            attachments=priority_attachments,
            mode=mode,
            enabled=request.query_rewrite_enabled if request.query_rewrite_enabled is not None else self.settings.rag_query_rewrite_enabled,
        )
        used_tools = self._query_plan_tools(query_plan.variants)
        weak_private_hits: list[RagQueryHit] = []

        if priority_attachments:
            attachment_request = self._scoped_request(request, project_id=None, attachments=priority_attachments)
            attachment_stage = await self._private_stage(
                attachment_request,
                context,
                candidate_limit,
                top_k,
                threshold,
                query_plan,
                key='priority_source',
                label='Priority 1: temporary files and @ references',
                mode='SCOPED',
            )
            used_tools.append(attachment_stage.key)
            used_tools = self._append_used_tools(used_tools, attachment_stage.used_tools)
            if attachment_stage.sufficient:
                return self._private_decision(
                    selected_stage=attachment_stage,
                    auxiliary_hits=[],
                    used_tools=used_tools,
                    citation_limit=top_k,
                    rewritten_queries=query_plan.all_queries,
                    rewrite_hints=query_plan.hints,
                )
            weak_private_hits.extend(attachment_stage.hits)

        if project_scope_ids:
            project_request = self._scoped_request(request, project_id=project_scope_ids[0], attachments=[])
            project_stage = await self._private_stage(
                project_request,
                context,
                candidate_limit,
                top_k,
                threshold,
                query_plan,
                key='project_knowledge',
                label='Priority 2: project files',
                mode='SCOPED',
            )
            used_tools.append(project_stage.key)
            used_tools = self._append_used_tools(used_tools, project_stage.used_tools)
            if project_stage.sufficient:
                return self._private_decision(
                    selected_stage=project_stage,
                    auxiliary_hits=weak_private_hits,
                    used_tools=used_tools,
                    citation_limit=top_k,
                    rewritten_queries=query_plan.all_queries,
                    rewrite_hints=query_plan.hints,
                )
            weak_private_hits.extend(project_stage.hits)

        auxiliary_private_hits = self._limited_unique_hits(weak_private_hits, top_k)
        auxiliary_citations = [self._to_citation(hit) for hit in auxiliary_private_hits]
        auxiliary_messages = (
            [{'role': 'system', 'content': self._build_auxiliary_private_context(auxiliary_private_hits, mode)}]
            if auxiliary_private_hits
            else []
        )

        should_use_web_search = self._should_use_web_search(request, query_plan)
        if should_use_web_search:
            used_tools.append('web_search')
            web_query = query_plan.rewritten_query or query_plan.normalized_query or request.message
            web_hits = await self._safe_web_search(web_query)
            if self._web_hits_sufficient(web_hits):
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
                    confidence=None,
                    context_messages=auxiliary_messages + [{
                        'role': 'system',
                        'content': self._build_web_context(web_hits, bool(auxiliary_citations)),
                    }],
                    rewritten_queries=query_plan.all_queries,
                    rewrite_hints=query_plan.hints,
                )

        used_tools.append('general_knowledge')
        return RetrievalDecision(
            answer_source='GENERAL_KNOWLEDGE',
            citations=[],
            used_tools=used_tools,
            confidence=None,
            context_messages=[{
                'role': 'system',
                'content': self._build_general_context(should_use_web_search, bool(auxiliary_citations)),
            }],
            rewritten_queries=query_plan.all_queries,
            rewrite_hints=query_plan.hints,
        )

    async def _private_stage(
        self,
        request: ChatCompletionRequest,
        context: RequestContext,
        candidate_limit: int,
        top_k: int,
        threshold: float,
        query_plan: QueryRewritePlan,
        key: str,
        label: str,
        mode: str,
    ) -> RetrievalStage:
        private_result = await self._private_hits(request, context, candidate_limit, mode, query_plan)
        top_hits = private_result.hits[:top_k]
        confidence = top_hits[0].score if top_hits else None
        return RetrievalStage(
            key=key,
            label=label,
            hits=top_hits,
            sufficient=bool(top_hits and confidence is not None and confidence >= threshold),
            confidence=confidence,
            used_tools=private_result.used_tools,
        )

    def _private_decision(
        self,
        selected_stage: RetrievalStage,
        auxiliary_hits: list[RagQueryHit],
        used_tools: list[str],
        citation_limit: int,
        rewritten_queries: list[str],
        rewrite_hints: list[str],
    ) -> RetrievalDecision:
        selected_hits = self._limited_unique_hits(selected_stage.hits, citation_limit)
        supplemental_hits = self._limited_unique_hits(auxiliary_hits, max(0, citation_limit - len(selected_hits)))
        citation_hits = self._limited_unique_hits(selected_hits + supplemental_hits, citation_limit)
        context_messages: list[dict[str, str]] = []
        if supplemental_hits:
            context_messages.append({'role': 'system', 'content': self._build_auxiliary_private_context(supplemental_hits, 'SCOPED')})
        context_messages.append({
            'role': 'system',
            'content': self._build_priority_context(selected_hits, selected_stage.label),
        })
        return RetrievalDecision(
            answer_source='PRIVATE_KNOWLEDGE',
            citations=[self._to_citation(hit) for hit in citation_hits],
            used_tools=used_tools,
            confidence=selected_stage.confidence,
            context_messages=context_messages,
            rewritten_queries=rewritten_queries,
            rewrite_hints=rewrite_hints,
        )

    def _query_plan_tools(self, query_variants: list[QueryVariant]) -> list[str]:
        used_tools: list[str] = []
        if any(item.kind == 'rewrite' for item in query_variants):
            used_tools.append('query_rewrite')
        if any(item.kind == 'llm_rewrite' for item in query_variants):
            used_tools.append('llm_query_rewrite')
        if any(item.kind == 'expansion' for item in query_variants):
            used_tools.append('query_expansion')
        if any(item.kind == 'llm_expansion' for item in query_variants):
            used_tools.append('llm_query_expansion')
        return used_tools

    def _append_used_tools(self, existing: list[str], additions: list[str]) -> list[str]:
        merged = list(existing)
        for item in additions:
            if item and item not in merged:
                merged.append(item)
        return merged

    def _limited_unique_hits(self, hits: list[RagQueryHit], limit: int) -> list[RagQueryHit]:
        if limit <= 0:
            return []
        return self._dedupe_hits(hits)[:limit]

    def _scoped_request(
        self,
        request: ChatCompletionRequest,
        project_id: int | None,
        attachments: list[AttachmentContext],
    ) -> ChatCompletionRequest:
        return request.model_copy(
            update={
                'context': ChatContext(
                    project_id=project_id,
                    document_id=request.context.document_id,
                    attachments=attachments,
                )
            },
            deep=True,
        )

    def _web_hits_sufficient(self, hits: list[Any]) -> bool:
        for item in hits:
            url = str(getattr(item, 'url', '') or '').strip()
            text = ' '.join([
                str(getattr(item, 'title', '') or ''),
                str(getattr(item, 'snippet', '') or ''),
            ]).strip()
            if url and len(text) >= 40:
                return True
        return False

    def _should_use_web_search(self, request: ChatCompletionRequest, query_plan: Any) -> bool:
        if request.web_search_enabled is not True:
            return False
        query = (query_plan.rewritten_query or query_plan.normalized_query or request.message or '').strip()
        if not query:
            return False
        if any(pattern.search(query) for pattern in WEB_SEARCH_EXPLICIT_PATTERNS):
            return True
        if any(pattern.search(query) for pattern in WEB_SEARCH_FRESHNESS_PATTERNS):
            return True
        return False

    async def debug_query(
        self,
        user_id: int,
        query: str,
        project_scope_ids: list[int],
        attachments: list[AttachmentContext],
        limit: int,
        query_rewrite_enabled: bool | None = None,
    ) -> RagQueryResponse:
        fake_request = ChatCompletionRequest(
            message=query,
            context=build_debug_chat_context(project_scope_ids, attachments),
            mode='GENERAL',
            web_search_enabled=False,
            top_k=limit,
            query_rewrite_enabled=query_rewrite_enabled,
        )
        context = RequestContext(user_id=user_id, org_id=0, request_id='rag-debug')
        decision = await self.query(fake_request, context)
        query_plan = await query_rewrite_service.build_plan_async(
            fake_request.message,
            project_scope_ids=self._project_scope_ids(fake_request),
            attachments=self._knowledge_attachments(fake_request),
            mode='GENERAL',
            enabled=fake_request.query_rewrite_enabled if fake_request.query_rewrite_enabled is not None else self.settings.rag_query_rewrite_enabled,
        )
        private_result = await self._private_hits(fake_request, context, self._candidate_limit(limit), 'GENERAL', query_plan)
        return RagQueryResponse(
            hits=private_result.hits[:limit],
            citations=decision.citations,
            confidence=decision.confidence,
            answer_source=decision.answer_source,
            used_tools=self._append_used_tools(decision.used_tools, private_result.used_tools),
            rewritten_queries=decision.rewritten_queries,
            rewrite_hints=decision.rewrite_hints,
        )

    async def _private_hits(
        self,
        request: ChatCompletionRequest,
        context: RequestContext,
        candidate_limit: int,
        mode: str,
        query_plan: QueryRewritePlan | list[QueryVariant],
    ) -> PrivateRetrievalResult:
        if isinstance(query_plan, QueryRewritePlan):
            query_variants = query_plan.variants
            plan = query_plan
        else:
            query_variants = query_plan
            plan = QueryRewritePlan(
                original_query=request.message,
                normalized_query=request.message,
                rewritten_query=None,
                expanded_queries=[],
                hints=[],
                variants=query_variants,
            )
        vector_hits = await self._safe_vector_search(request, context, candidate_limit, mode, query_variants)
        keyword_hits = await self._safe_keyword_search(request, context, candidate_limit, mode, query_variants)
        used_tools: list[str] = []
        if vector_hits:
            used_tools.append('vector_retrieval')
        if keyword_hits:
            used_tools.append('keyword_retrieval')
        weights = self._resolve_retrieval_weights(request.message, plan)
        if vector_hits and keyword_hits:
            used_tools.append(f'hybrid_retrieval:{weights.profile.lower()}')
        fused_hits = self._fuse_hits(vector_hits, keyword_hits, weights)[:candidate_limit]
        if not fused_hits:
            return PrivateRetrievalResult(hits=[], used_tools=used_tools)
        reranked_hits, rerank_tool = await self._rerank_hits(request.message, fused_hits)
        if rerank_tool:
            used_tools.append(rerank_tool)
        return PrivateRetrievalResult(hits=reranked_hits[:candidate_limit], used_tools=used_tools)

    async def _safe_vector_search(
        self,
        request: ChatCompletionRequest,
        context: RequestContext,
        top_k: int,
        mode: str,
        query_variants: list[QueryVariant],
    ) -> list[RagQueryHit]:
        try:
            return await self._vector_search(request, context, top_k, mode, query_variants)
        except Exception as exception:
            logger.warning('Vector search failed and will be skipped: %s', exception, exc_info=True)
            return []

    async def _safe_keyword_search(
        self,
        request: ChatCompletionRequest,
        context: RequestContext,
        top_k: int,
        mode: str,
        query_variants: list[QueryVariant],
    ) -> list[RagQueryHit]:
        try:
            return await self._keyword_search(request, context, top_k, mode, query_variants)
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
        query_variants: list[QueryVariant],
    ) -> list[RagQueryHit]:
        vectors = await embedding_service.embed([variant.text for variant in query_variants])
        hits: list[RagQueryHit] = []
        for variant, vector in zip(query_variants, vectors, strict=False):
            for collection_name, query_filter in self._vector_queries(request, context, mode):
                self._ensure_collection(collection_name)
                response = self._run_qdrant(
                    f'search `{collection_name}`',
                    lambda: self._search_points(
                        collection_name=collection_name,
                        query=vector,
                        query_filter=query_filter,
                        limit=top_k,
                        using=self.DENSE_VECTOR_NAME,
                    ),
                )
                hits.extend(self._response_to_hits(response, boost=variant.boost))
        return self._dedupe_hits(hits)[:top_k]

    def _search_points(
        self,
        collection_name: str,
        query: list[float] | SparseVector,
        query_filter: Filter,
        limit: int,
        using: str,
    ) -> list[Any]:
        response = self.client.query_points(
            collection_name=collection_name,
            query=query,
            using=using,
            query_filter=query_filter,
            limit=limit,
            with_payload=True,
        )
        return list(getattr(response, 'points', response))

    async def _keyword_search(
        self,
        request: ChatCompletionRequest,
        context: RequestContext,
        top_k: int,
        mode: str,
        query_variants: list[QueryVariant],
    ) -> list[RagQueryHit]:
        hits: list[RagQueryHit] = []
        for variant in query_variants:
            sparse_query = sparse_vector_service.build_query_vector(variant.text)
            if not sparse_query.indices:
                continue
            for collection_name, query_filter in self._vector_queries(request, context, mode):
                self._ensure_collection(collection_name)
                response = self._run_qdrant(
                    f'sparse search `{collection_name}`',
                    lambda sparse_query=sparse_query: self._search_points(
                        collection_name=collection_name,
                        query=sparse_query,
                        query_filter=query_filter,
                        limit=top_k,
                        using=self.SPARSE_VECTOR_NAME,
                    ),
                )
                hits.extend(self._response_to_hits(response, boost=variant.boost))
        return self._dedupe_hits(hits)[:top_k]

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

    def _explicit_project_scope_ids(self, request: ChatCompletionRequest) -> list[int]:
        if request.context.project_id is None:
            return []
        return [request.context.project_id]

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

    def _response_to_hits(self, response: list[Any], boost: float = 1.0) -> list[RagQueryHit]:
        hits: list[RagQueryHit] = []
        for item in response:
            payload = item.payload or {}
            source_type = str(payload.get('source_type') or '')
            source_id = int(payload.get('source_id') or 0)
            page_no = int(payload['page_no']) if payload.get('page_no') is not None else None
            section_path = str(payload.get('section_path') or '') or None
            chunk_num = int(payload['chunk_num']) if payload.get('chunk_num') is not None else None
            metadata = payload.get('metadata') if isinstance(payload.get('metadata'), dict) else {}
            hits.append(
                RagQueryHit(
                    score=float(item.score) * boost,
                    source_type=source_type,
                    source_id=source_id,
                    source_title=str(payload.get('source_name') or 'knowledge-snippet'),
                    snippet=str(payload.get('chunk_text') or ''),
                    page_no=page_no,
                    section_path=section_path,
                    url=str(payload.get('source_url') or '') or self._build_citation_url(source_type, source_id, page_no, section_path, chunk_num),
                    project_id=int(payload['project_id']) if payload.get('project_id') is not None else None,
                    session_id=int(payload['session_id']) if payload.get('session_id') is not None else None,
                    chunk_num=chunk_num,
                    chunk_id=str(payload.get('chunk_id') or '') or None,
                    chunk_hash=str(payload.get('chunk_hash') or '') or None,
                    task_id=str(payload.get('task_id') or '') or None,
                    source_version=str(payload.get('source_version') or '') or None,
                    metadata=metadata,
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

    def _resolve_retrieval_weights(self, query: str, query_plan: QueryRewritePlan) -> RetrievalWeights:
        profile = (query_plan.retrieval_profile or 'BALANCED').strip().upper()
        if profile == 'KEYWORD_HEAVY':
            keyword_weight = max(0.0, min(self.settings.rag_keyword_heavy_weight, 1.0))
            return RetrievalWeights(
                vector_weight=max(0.0, 1.0 - keyword_weight),
                keyword_weight=keyword_weight,
                profile=profile,
            )
        if profile == 'VECTOR_HEAVY':
            keyword_weight = max(0.0, min(self.settings.rag_keyword_weight, 1.0))
            vector_weight = max(0.0, min(max(self.settings.rag_vector_weight, 0.8), 1.0))
            total = vector_weight + keyword_weight
            if total <= 0:
                return RetrievalWeights(vector_weight=0.8, keyword_weight=0.2, profile=profile)
            return RetrievalWeights(
                vector_weight=vector_weight / total,
                keyword_weight=keyword_weight / total,
                profile=profile,
            )
        vector_weight = max(0.0, min(self.settings.rag_vector_weight, 1.0))
        keyword_weight = max(0.0, min(self.settings.rag_keyword_weight, 1.0))
        total = vector_weight + keyword_weight
        if total <= 0:
            return RetrievalWeights(vector_weight=0.7, keyword_weight=0.3, profile='BALANCED')
        return RetrievalWeights(
            vector_weight=vector_weight / total,
            keyword_weight=keyword_weight / total,
            profile='BALANCED',
        )

    def _fuse_hits(
        self,
        vector_hits: list[RagQueryHit],
        keyword_hits: list[RagQueryHit],
        weights: RetrievalWeights | None = None,
    ) -> list[RagQueryHit]:
        weights = weights or RetrievalWeights(
            vector_weight=max(0.0, min(self.settings.rag_vector_weight, 1.0)) or 0.7,
            keyword_weight=max(0.0, min(self.settings.rag_keyword_weight, 1.0)) or 0.3,
            profile='BALANCED',
        )
        merged: dict[str, RagQueryHit] = {}
        weighted_groups = (
            (self._dedupe_hits(vector_hits), weights.vector_weight),
            (self._dedupe_hits(keyword_hits), weights.keyword_weight),
        )
        for hits, group_weight in weighted_groups:
            if group_weight <= 0:
                continue
            for rank, item in enumerate(hits, start=1):
                key = self._candidate_key(item)
                contribution = group_weight / (self.RRF_RANK_CONSTANT + rank)
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

    async def _rerank_hits(self, query: str, hits: list[RagQueryHit]) -> tuple[list[RagQueryHit], str | None]:
        if len(hits) <= 1:
            return self._fallback_hits(hits), None
        llm_result = await retrieval_llm_service.rerank_hits(
            query=query,
            hits=hits,
            top_n=max(1, min(len(hits), self._resolve_top_n_for_rerank())),
        )
        if llm_result is not None and llm_result.ranked_hits:
            return llm_result.ranked_hits, 'llm_rerank'
        reranker = self._get_reranker()
        if reranker is None:
            return self._fallback_hits(hits), None
        documents = [self._rerank_document(hit) for hit in hits]
        try:
            raw_scores = list(reranker.rerank(query, documents))
        except Exception as exception:
            logger.warning('Rerank failed, falling back to conservative RRF ordering: %s', exception, exc_info=True)
            self._reranker_unavailable = True
            self._reranker = None
            return self._fallback_hits(hits), None
        if len(raw_scores) != len(hits):
            return self._fallback_hits(hits), None
        reranked = [
            hit.model_copy(update={'score': self._sigmoid(float(raw_score))})
            for hit, raw_score in zip(hits, raw_scores, strict=False)
        ]
        return sorted(reranked, key=lambda item: item.score, reverse=True), 'cross_encoder_rerank'

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
        if not self.settings.rag_rerank_enabled:
            return None
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

    def _build_citation_url(
        self,
        source_type: str,
        source_id: int,
        page_no: int | None,
        section_path: str | None,
        chunk_num: int | None,
    ) -> str | None:
        normalized = (source_type or '').strip().upper()
        if not source_id:
            return None
        if normalized == 'DOCUMENT':
            path = f'/documents/{source_id}/edit?mode=preview'
        elif normalized == 'FILE':
            path = f'/files/{source_id}'
        elif normalized == 'TEMP_FILE':
            path = f'/ai?tempFileId={source_id}'
        else:
            path = f'/knowledge?sourceType={normalized or "UNKNOWN"}&sourceId={source_id}'
        extras: list[str] = []
        if page_no is not None:
            extras.append(f'pageNo={page_no}')
        if section_path:
            extras.append(f'sectionPath={section_path}')
        if chunk_num is not None:
            extras.append(f'chunkNum={chunk_num}')
        if not extras:
            return path
        separator = '&' if '?' in path else '?'
        return f'{path}{separator}' + '&'.join(extras)

    def _chunk_id(self, source_type: str, source_id: int, chunk_num: int) -> str:
        return f'{source_type.upper()}:{source_id}:{chunk_num}'

    def _chunk_hash(self, chunk_text: str) -> str:
        return hashlib.sha256((chunk_text or '').encode('utf-8')).hexdigest()

    def _build_priority_context(self, hits: list[RagQueryHit], stage_label: str) -> str:
        hits = self._prepare_hits_for_context(hits, MAX_PRIVATE_CONTEXT_HITS, MAX_PRIVATE_CONTEXT_TOTAL_CHARS)
        lines = [
            f'当前采用{stage_label}作为主要依据。',
            '请优先依据下面命中的证据回答；不要编造未命中的私有资料内容。',
            '如果证据只能支撑部分结论，请明确说明边界。',
            '',
        ]
        lines.extend(self._format_hit_lines(hits, prefix='证据'))
        return '\n'.join(lines).strip()

    def _build_private_context(self, hits: list[RagQueryHit], mode: str) -> str:
        hits = self._prepare_hits_for_context(hits, MAX_PRIVATE_CONTEXT_HITS, MAX_PRIVATE_CONTEXT_TOTAL_CHARS)
        lines = [
            '以下资料来自私有知识库，请优先依据这些证据回答。',
            '如证据不足，可以说明不足；不要伪造私有引用。',
        ]
        if mode == 'SCOPED':
            lines.append('当前处于指定范围检索，仅使用当前项目或显式引用资料作为私有证据。')
        lines.append('')
        lines.extend(self._format_hit_lines(hits, prefix='资料'))
        return '\n'.join(lines).strip()

    def _build_auxiliary_private_context(self, hits: list[RagQueryHit], mode: str) -> str:
        hits = self._prepare_hits_for_context(hits, MAX_PRIVATE_CONTEXT_HITS, MAX_PRIVATE_CONTEXT_TOTAL_CHARS)
        lines = [
            '以下私有资料相关性不足，只能作为辅助背景，不能作为唯一依据。',
        ]
        if mode == 'SCOPED':
            lines.append('这些资料仍来自当前指定范围，仅用于补充上下文。')
        lines.append('')
        lines.extend(self._format_hit_lines(hits, prefix='辅助资料'))
        return '\n'.join(lines).strip()

    def _build_web_context(self, hits: list[Any], has_auxiliary_private: bool) -> str:
        hits = self._prepare_web_hits_for_context(hits)
        lines = [
            '以下资料来自联网搜索，请把联网结果作为本轮回答的主要依据。',
            '只引用真实链接，不要编造来源。',
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
            lines.append('前序私有资料检索结果不足，不能作为主要依据。')
        if web_search_enabled:
            lines.append('本轮联网搜索未返回足够可用结果，请改用模型自身知识进行回答。')
        else:
            lines.append('本轮未开启联网搜索，请直接使用模型自身知识进行回答。')
        lines.append('不要伪造私有资料或网页引用；如信息可能不确定，请说明不确定性。')
        return '\n'.join(lines)

    def _format_hit_lines(self, hits: list[RagQueryHit], prefix: str) -> list[str]:
        lines: list[str] = []
        for index, item in enumerate(hits, start=1):
            lines.append(f'{prefix} {index}: {item.source_title}')
            if item.section_path:
                lines.append(f'结构: {item.section_path}')
            if item.page_no is not None:
                lines.append(f'页码: {item.page_no}')
            lines.append(f'相关片段: {item.snippet}')
            lines.append(f'置信度: {item.score:.3f}')
            lines.append('')
        return lines

    def _prepare_hits_for_context(
        self,
        hits: list[RagQueryHit],
        max_hits: int,
        total_char_budget: int,
    ) -> list[RagQueryHit]:
        prepared: list[RagQueryHit] = []
        remaining = max(0, total_char_budget)
        for hit in self._dedupe_hits(hits)[:max_hits]:
            if remaining <= 0:
                break
            snippet_limit = min(MAX_CONTEXT_SNIPPET_CHARS, remaining)
            snippet = self._trim_context_text(hit.snippet, snippet_limit)
            if not snippet:
                continue
            remaining -= len(snippet)
            prepared.append(
                hit.model_copy(update={
                    'source_title': self._trim_context_text(hit.source_title, MAX_CONTEXT_TITLE_CHARS),
                    'section_path': self._trim_context_text(hit.section_path or '', MAX_CONTEXT_SECTION_CHARS) or None,
                    'snippet': snippet,
                })
            )
        return prepared

    def _prepare_web_hits_for_context(self, hits: list[Any]) -> list[Any]:
        prepared: list[Any] = []
        remaining = MAX_WEB_CONTEXT_TOTAL_CHARS
        for item in hits[:MAX_WEB_CONTEXT_ITEMS]:
            if remaining <= 0:
                break
            snippet_limit = min(MAX_CONTEXT_SNIPPET_CHARS, remaining)
            snippet = self._trim_context_text(str(getattr(item, 'snippet', '') or ''), snippet_limit)
            if not snippet:
                continue
            remaining -= len(snippet)
            title = self._trim_context_text(str(getattr(item, 'title', '') or ''), MAX_CONTEXT_TITLE_CHARS)
            prepared.append(item.__class__(title=title, url=getattr(item, 'url', ''), snippet=snippet))
        return prepared

    def _trim_context_text(self, text: str, limit: int) -> str:
        normalized = ' '.join((text or '').split())
        if not normalized or limit <= 0:
            return ''
        if len(normalized) <= limit:
            return normalized
        if limit <= 1:
            return normalized[:limit]
        return normalized[: max(0, limit - 3)].rstrip() + '...'

    def _normalized_mode(self, mode: str | None) -> str:
        normalized = (mode or 'GENERAL').strip().upper()
        return 'SCOPED' if normalized == 'SCOPED' else 'GENERAL'

    def _candidate_limit(self, top_k: int) -> int:
        return min(self.MAX_FUSION_CANDIDATES, max(self.MIN_FUSION_CANDIDATES, top_k * 4))

    def _resolve_top_k(self, request: ChatCompletionRequest) -> int:
        requested = request.top_k if request.top_k is not None else self.settings.rag_top_k
        return max(1, min(requested, 10))

    def _resolve_top_n_for_rerank(self) -> int:
        candidate_limit = max(1, self.settings.rag_llm_rerank_candidate_limit)
        return min(candidate_limit, self.settings.rag_top_k or 5)

    def _resolve_threshold(self, request: ChatCompletionRequest) -> float:
        threshold = request.similarity_threshold if request.similarity_threshold is not None else self.settings.rag_similarity_threshold
        return max(0.0, min(threshold, 1.0))

    def _collection_name(self, scope_type: str) -> str:
        return self.settings.qdrant_temp_collection if scope_type.upper() == 'TEMP' else self.settings.qdrant_kb_collection

    def _upsert_result(self, collection_name: str, upserted: int, extra: dict[str, Any] | None = None) -> dict[str, Any]:
        payload = {
            'upserted': upserted,
            'collectionName': collection_name,
            'embeddingModelCode': get_embedding_model_code(),
            'embeddingVersion': self.settings.embedding_version,
            'embeddingDimension': self.settings.embedding_dimensions,
        }
        if extra:
            payload.update(extra)
        return payload

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
