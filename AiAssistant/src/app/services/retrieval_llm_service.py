from __future__ import annotations

import json
import logging
import re
from dataclasses import dataclass
from typing import Any

from src.app.core.config import get_settings
from src.app.providers.base import ModelProvider
from src.app.services.model_registry import ProviderRegistry, ProviderRoute


logger = logging.getLogger(__name__)

JSON_BLOCK_PATTERN = re.compile(r'\{.*\}', re.DOTALL)
VALID_RETRIEVAL_PROFILES = {'BALANCED', 'KEYWORD_HEAVY', 'VECTOR_HEAVY'}


@dataclass(slots=True)
class QueryRewriteLlmResult:
    intent: str | None
    retrieval_profile: str
    rewritten_query: str | None
    keyword_expansions: list[str]
    semantic_expansions: list[str]
    hints: list[str]
    provider_code: str | None = None
    model_code: str | None = None


@dataclass(slots=True)
class LlmRerankResult:
    ranked_hits: list[Any]
    provider_code: str | None
    model_code: str | None


class RetrievalLlmService:
    def __init__(self) -> None:
        self.settings = get_settings()
        self.registry = ProviderRegistry(self.settings)
        self._provider_cache: dict[str, tuple[str, ModelProvider]] = {}
        self._unavailable_tasks: set[str] = set()

    async def rewrite_query(
        self,
        *,
        query: str,
        project_scope_ids: list[int] | None,
        attachments: list[Any] | None,
        mode: str | None,
        fallback_profile: str,
    ) -> QueryRewriteLlmResult | None:
        if not self.settings.rag_query_rewrite_llm_enabled:
            return None
        provider_and_model = self._provider_for_task('rewrite')
        if provider_and_model is None:
            return None
        model_code, provider = provider_and_model
        attachment_titles = [
            str(getattr(item, 'title', '') or '').strip()
            for item in (attachments or [])
            if str(getattr(item, 'title', '') or '').strip()
        ]
        messages = [
            {
                'role': 'system',
                'content': (
                    '你是企业知识库检索改写器。'
                    '你的任务不是回答问题，而是把用户原始提问改写成更适合混合检索的查询。'
                    '请只返回 JSON，不要输出 markdown。'
                ),
            },
            {
                'role': 'user',
                'content': json.dumps(
                    {
                        'task': 'rewrite_for_retrieval',
                        'rules': [
                            '识别用户意图，只能输出简短意图标签，例如：报错排查、操作指南、概念解释、总结摘要、流程查询。',
                            '把口语化提问改写成适合知识库检索的专业查询语句。',
                            '保留产品名、模块名、错误码、日志关键词、文件名、命令名、接口名等精确信号。',
                            '如果问题偏报错、日志、代码、命令排查，retrievalProfile 设为 KEYWORD_HEAVY。',
                            '如果问题偏概念或开放式问答，retrievalProfile 设为 BALANCED 或 VECTOR_HEAVY。',
                            'keywordExpansions 用于补充专业术语、别名、常见错误说法。',
                            'semanticExpansions 用于补充更完整的检索句式，每项都应适合直接送入向量检索。',
                            '不要改写用户最终展示文本，只输出检索侧建议。',
                        ],
                        'fallbackProfile': fallback_profile,
                        'query': query,
                        'mode': (mode or 'GENERAL').upper(),
                        'projectScopeIds': project_scope_ids or [],
                        'attachments': {
                            'count': len(attachments or []),
                            'titles': attachment_titles[:5],
                        },
                        'outputSchema': {
                            'intent': 'string',
                            'retrievalProfile': 'BALANCED|KEYWORD_HEAVY|VECTOR_HEAVY',
                            'rewrittenQuery': 'string',
                            'keywordExpansions': ['string'],
                            'semanticExpansions': ['string'],
                            'hints': ['string'],
                        },
                    },
                    ensure_ascii=False,
                ),
            },
        ]
        try:
            result = await provider.chat(model_code, messages, temperature=0.1, max_tokens=700)
            payload = self._parse_json_object(result.text)
            if not isinstance(payload, dict):
                return None
            retrieval_profile = self._normalize_profile(payload.get('retrievalProfile'), fallback_profile)
            return QueryRewriteLlmResult(
                intent=self._clean_text(payload.get('intent')),
                retrieval_profile=retrieval_profile,
                rewritten_query=self._clean_text(payload.get('rewrittenQuery')),
                keyword_expansions=self._normalize_text_list(payload.get('keywordExpansions')),
                semantic_expansions=self._normalize_text_list(payload.get('semanticExpansions')),
                hints=self._normalize_text_list(payload.get('hints')),
                provider_code=result.provider_code,
                model_code=result.model_code,
            )
        except Exception as exc:
            logger.warning('LLM query rewrite failed, falling back to rule-based rewrite: %s', exc, exc_info=True)
            return None

    async def rerank_hits(
        self,
        *,
        query: str,
        hits: list[Any],
        top_n: int,
    ) -> LlmRerankResult | None:
        if not self.settings.rag_llm_rerank_enabled or not hits:
            return None
        provider_and_model = self._provider_for_task('rerank')
        if provider_and_model is None:
            return None
        model_code, provider = provider_and_model
        candidates = [
            {
                'id': index + 1,
                'title': str(getattr(hit, 'source_title', '') or '')[:120],
                'sectionPath': str(getattr(hit, 'section_path', '') or '')[:120],
                'pageNo': getattr(hit, 'page_no', None),
                'snippet': str(getattr(hit, 'snippet', '') or '')[:420],
                'sourceType': str(getattr(hit, 'source_type', '') or ''),
                'sourceId': getattr(hit, 'source_id', None),
            }
            for index, hit in enumerate(hits[: self.settings.rag_llm_rerank_candidate_limit])
        ]
        messages = [
            {
                'role': 'system',
                'content': (
                    '你是企业知识库检索结果重排序器。'
                    '请从给定候选中找出最能直接回答问题的结果，并返回 JSON。'
                    '优先匹配精确错误词、命令词、接口词、模块词、标题语义和摘要片段。'
                ),
            },
            {
                'role': 'user',
                'content': json.dumps(
                    {
                        'task': 'rerank_retrieval_hits',
                        'query': query,
                        'topN': max(1, top_n),
                        'candidates': candidates,
                        'outputSchema': {
                            'ranked': [
                                {
                                    'id': 1,
                                    'score': 0,
                                    'reason': 'string',
                                }
                            ]
                        },
                    },
                    ensure_ascii=False,
                ),
            },
        ]
        try:
            result = await provider.chat(model_code, messages, temperature=0.0, max_tokens=900)
            payload = self._parse_json_object(result.text)
            ranked = payload.get('ranked') if isinstance(payload, dict) else None
            if not isinstance(ranked, list):
                return None
            scored = self._normalize_ranked_items(ranked)
            if not scored:
                return None
            hit_map = {index + 1: hit for index, hit in enumerate(hits[: self.settings.rag_llm_rerank_candidate_limit])}
            reranked_hits: list[Any] = []
            used_ids: set[int] = set()
            for item in scored:
                candidate_id = item['id']
                hit = hit_map.get(candidate_id)
                if hit is None:
                    continue
                used_ids.add(candidate_id)
                reranked_hits.append(hit.model_copy(update={'score': item['score']}))
            for index, hit in enumerate(hits[: self.settings.rag_llm_rerank_candidate_limit], start=1):
                if index in used_ids:
                    continue
                reranked_hits.append(hit.model_copy(update={'score': min(float(getattr(hit, 'score', 0.0) or 0.0), 0.49)}))
            for hit in hits[self.settings.rag_llm_rerank_candidate_limit:]:
                reranked_hits.append(hit)
            return LlmRerankResult(
                ranked_hits=reranked_hits,
                provider_code=result.provider_code,
                model_code=result.model_code,
            )
        except Exception as exc:
            logger.warning('LLM rerank failed, falling back to local reranker: %s', exc, exc_info=True)
            return None

    def _provider_for_task(self, task: str) -> tuple[str, ModelProvider] | None:
        if task in self._unavailable_tasks:
            return None
        cached = self._provider_cache.get(task)
        if cached is not None:
            return cached
        for route in self._candidate_routes(task):
            if not route.configured:
                continue
            try:
                provider = self.registry.build_provider(route)
                resolved = (route.model_code, provider)
                self._provider_cache[task] = resolved
                return resolved
            except Exception as exc:
                logger.warning(
                    'Failed to initialize retrieval %s provider `%s` with model `%s`: %s',
                    task,
                    route.provider_code,
                    route.model_code,
                    exc,
                    exc_info=True,
                )
                continue
        self._unavailable_tasks.add(task)
        return None

    def _candidate_routes(self, task: str) -> list[ProviderRoute]:
        preferred_provider = self._preferred_provider(task)
        provider_codes = self._unique_provider_codes([
            preferred_provider,
            self.registry.gateway_provider_code(),
            'DEEPSEEK',
            'LITELLM',
            'OPENAI',
            'OLLAMA',
            'VLLM',
        ])
        routes: list[ProviderRoute] = []
        if self.registry.gateway_override_enabled():
            gateway_provider = self.registry.gateway_provider_code() or 'OPENAI'
            gateway_model = self._model_code_for_provider(task, gateway_provider)
            gateway_base_url = self.settings.model_base_url or self.registry.provider_base_url(gateway_provider)
            gateway_api_key = self.settings.model_api_key or self.registry.provider_api_key(gateway_provider)
            routes.append(
                ProviderRoute(
                    provider_code=gateway_provider,
                    model_code=gateway_model,
                    base_url=gateway_base_url,
                    api_key=gateway_api_key,
                    timeout_seconds=self.settings.provider_timeout_seconds,
                    configured=self.registry.route_configured(gateway_provider, gateway_base_url, gateway_api_key),
                    source='retrieval-gateway',
                )
            )
        for provider_code in provider_codes:
            model_code = self._model_code_for_provider(task, provider_code)
            base_url = self.registry.provider_base_url(provider_code)
            api_key = self.registry.provider_api_key(provider_code)
            routes.append(
                ProviderRoute(
                    provider_code=provider_code,
                    model_code=model_code,
                    base_url=base_url,
                    api_key=api_key,
                    timeout_seconds=self.settings.provider_timeout_seconds,
                    configured=self.registry.route_configured(provider_code, base_url, api_key),
                    source=f'retrieval-{task}',
                )
            )
        deduped: list[ProviderRoute] = []
        seen: set[tuple[str, str, str]] = set()
        for route in routes:
            key = (route.provider_code, route.model_code, route.base_url)
            if key in seen:
                continue
            seen.add(key)
            deduped.append(route)
        return deduped

    def _preferred_provider(self, task: str) -> str:
        if task == 'rewrite':
            return (self.settings.rag_query_rewrite_provider or 'DEEPSEEK').strip().upper()
        if task == 'rerank':
            return (self.settings.rag_llm_rerank_provider or 'DEEPSEEK').strip().upper()
        return ''

    def _model_code_for_provider(self, task: str, provider_code: str) -> str:
        provider_code = (provider_code or '').strip().upper()
        if task == 'rewrite' and (self.settings.rag_query_rewrite_model or '').strip():
            return self.settings.rag_query_rewrite_model.strip()
        if task == 'rerank' and (self.settings.rag_llm_rerank_model or '').strip():
            return self.settings.rag_llm_rerank_model.strip()
        if provider_code == 'DEEPSEEK':
            return self.settings.deepseek_model
        if provider_code == 'OPENAI':
            return self.settings.openai_model
        if provider_code == 'OLLAMA':
            return self.settings.ollama_fast_chat_model if task in {'rewrite', 'rerank'} else self.settings.ollama_chat_model
        if provider_code == 'VLLM':
            return self.settings.vllm_model
        if provider_code == 'LITELLM':
            return self.settings.litellm_model
        return self.settings.default_model_code

    def _parse_json_object(self, text: str) -> dict[str, Any] | None:
        candidate = (text or '').strip()
        if not candidate:
            return None
        try:
            payload = json.loads(candidate)
            return payload if isinstance(payload, dict) else None
        except json.JSONDecodeError:
            match = JSON_BLOCK_PATTERN.search(candidate)
            if not match:
                return None
            try:
                payload = json.loads(match.group(0))
                return payload if isinstance(payload, dict) else None
            except json.JSONDecodeError:
                return None

    def _normalize_profile(self, value: Any, fallback_profile: str) -> str:
        normalized = str(value or fallback_profile or 'BALANCED').strip().upper()
        return normalized if normalized in VALID_RETRIEVAL_PROFILES else (fallback_profile or 'BALANCED')

    def _normalize_text_list(self, value: Any) -> list[str]:
        if not isinstance(value, list):
            return []
        normalized: list[str] = []
        seen: set[str] = set()
        for item in value:
            text = self._clean_text(item)
            if not text or text in seen:
                continue
            seen.add(text)
            normalized.append(text)
        return normalized[:6]

    def _normalize_ranked_items(self, ranked: list[Any]) -> list[dict[str, float | int]]:
        normalized: list[dict[str, float | int]] = []
        seen: set[int] = set()
        for item in ranked:
            if not isinstance(item, dict):
                continue
            try:
                candidate_id = int(item.get('id'))
            except (TypeError, ValueError):
                continue
            if candidate_id <= 0 or candidate_id in seen:
                continue
            seen.add(candidate_id)
            try:
                raw_score = float(item.get('score'))
            except (TypeError, ValueError):
                raw_score = 60.0
            bounded_score = max(0.0, min(raw_score, 100.0)) / 100.0
            normalized.append({'id': candidate_id, 'score': bounded_score})
        return sorted(normalized, key=lambda item: float(item['score']), reverse=True)

    def _clean_text(self, value: Any) -> str | None:
        if value is None:
            return None
        text = ' '.join(str(value).split()).strip()
        return text or None

    def _unique_provider_codes(self, provider_codes: list[str]) -> list[str]:
        normalized: list[str] = []
        seen: set[str] = set()
        for item in provider_codes:
            provider_code = (item or '').strip().upper()
            if not provider_code or provider_code in seen:
                continue
            seen.add(provider_code)
            normalized.append(provider_code)
        return normalized


retrieval_llm_service = RetrievalLlmService()
