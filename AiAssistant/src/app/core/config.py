from __future__ import annotations

from functools import lru_cache

from pydantic import model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


SUPPORTED_WEB_SEARCH_PROVIDERS = {'duckduckgo', 'tavily'}


class Settings(BaseSettings):
    app_name: str = 'erise-ai-chat-service'
    app_env: str = 'dev'
    api_prefix: str = '/internal/ai/chat'

    mysql_dsn: str = 'sqlite:///./ai_chat.db'
    redis_url: str = 'redis://localhost:6379/0'
    internal_service_token: str = 'change-this-in-production'
    java_internal_base_url: str = 'http://localhost:8080/internal/v1'
    java_internal_api_key: str | None = None
    java_public_base_url: str | None = None

    model_provider: str = ''
    model_base_url: str | None = None
    model_api_key: str = ''
    openai_api_key: str = ''
    openai_base_url: str = 'https://api.openai.com/v1'
    openai_model: str = 'gpt-4.1-mini'
    deepseek_api_key: str = ''
    deepseek_base_url: str = 'https://api.deepseek.com/v1'
    ollama_api_key: str = ''
    ollama_base_url: str = 'http://localhost:11434/v1'
    ollama_chat_model: str = 'qwen2.5:7b'
    ollama_fast_chat_model: str = 'qwen3:1.7b'
    ollama_embedding_model: str = 'nomic-embed-text'
    vllm_api_key: str = ''
    vllm_base_url: str = 'http://localhost:8000/v1'
    vllm_model: str = 'Qwen/Qwen2.5-7B-Instruct'
    litellm_api_key: str = ''
    litellm_base_url: str = 'http://localhost:4000/v1'
    litellm_model: str = 'deepseek/deepseek-chat'
    deepseek_model: str = 'deepseek-chat'
    default_model_code: str = 'deepseek-chat'

    qdrant_url: str = 'http://localhost:6333'
    qdrant_api_key: str = ''
    qdrant_kb_collection: str = 'kb_chunks'
    qdrant_temp_collection: str = 'temp_chunks'

    embedding_model: str = 'text-embedding-3-small'
    embedding_provider_code: str = ''
    embed_base_url: str | None = None
    embedding_base_url: str | None = None
    embedding_api_key: str = ''
    embedding_version: str = 'v1'
    embedding_dimensions: int = 1536
    embedding_local_fallback_enabled: bool = False

    rag_top_k: int = 5
    rag_keyword_top_k: int = 5
    rag_similarity_threshold: float = 0.75
    rag_query_rewrite_enabled: bool = True
    rag_query_rewrite_llm_enabled: bool = True
    rag_query_rewrite_provider: str = 'DEEPSEEK'
    rag_query_rewrite_model: str = ''
    rag_vector_weight: float = 0.7
    rag_keyword_weight: float = 0.3
    rag_keyword_heavy_weight: float = 0.5
    rag_rerank_enabled: bool = False
    rag_llm_rerank_enabled: bool = True
    rag_llm_rerank_provider: str = 'DEEPSEEK'
    rag_llm_rerank_model: str = ''
    rag_llm_rerank_candidate_limit: int = 20
    rag_strict_citation_enabled: bool = True

    web_search_provider: str = ''
    web_search_max_results: int = 5
    tavily_api_key: str = ''

    context_history_limit: int = 8
    request_body_char_limit: int = 12000
    session_page_size: int = 20
    message_page_size: int = 50
    blocked_terms: str = ''
    stream_cancel_ttl_seconds: int = 600
    provider_timeout_seconds: int = 300
    connect_timeout_seconds: int = 60
    qdrant_timeout_seconds: int = 300
    embedding_max_retries: int = 2
    embedding_batch_size: int = 128
    qdrant_max_retries: int = 2
    qdrant_upsert_batch_size: int = 256
    retry_backoff_seconds: float = 2.0
    dev_fast_fail_timeout_seconds: int = 20
    dev_retry_backoff_seconds: float = 0.5
    dev_embedding_max_retries: int = 0
    dev_qdrant_max_retries: int = 0
    dev_embedding_local_fallback_enabled: bool = True
    dev_rag_degrade_enabled: bool = True
    dev_ollama_context_fallback_chars: int = 700
    default_org_id: int = 0
    sqlite_echo: bool = False
    auto_init_sqlite_schema: bool = True
    db_pool_size: int = 20
    db_max_overflow: int = 20
    db_pool_timeout_seconds: int = 10
    db_pool_recycle_seconds: int = 1800
    action_confirmation_required: bool = True
    n8n_enabled: bool = False
    n8n_webhook_base_url: str | None = None
    n8n_webhook_secret: str = ''
    n8n_event_timeout_seconds: int = 15
    n8n_event_max_retries: int = 2
    n8n_event_retry_backoff_seconds: float = 1.0

    model_config = SettingsConfigDict(
        env_file='.env',
        env_file_encoding='utf-8',
        extra='ignore',
    )

    @staticmethod
    def _sanitize_optional_string(value: str | None) -> str:
        normalized = (value or '').strip()
        return '' if normalized.startswith('#') else normalized

    @model_validator(mode='after')
    def validate_web_search_settings(self) -> 'Settings':
        self.model_provider = self._sanitize_optional_string(self.model_provider)
        self.model_api_key = self._sanitize_optional_string(self.model_api_key)
        self.openai_api_key = self._sanitize_optional_string(self.openai_api_key)
        self.deepseek_api_key = self._sanitize_optional_string(self.deepseek_api_key)
        self.ollama_api_key = self._sanitize_optional_string(self.ollama_api_key)
        self.vllm_api_key = self._sanitize_optional_string(self.vllm_api_key)
        self.litellm_api_key = self._sanitize_optional_string(self.litellm_api_key)
        self.embedding_provider_code = self._sanitize_optional_string(self.embedding_provider_code)
        self.embedding_api_key = self._sanitize_optional_string(self.embedding_api_key)
        self.embed_base_url = self._sanitize_optional_string(self.embed_base_url) or None
        self.embedding_base_url = self._sanitize_optional_string(self.embedding_base_url) or None
        self.model_base_url = self._sanitize_optional_string(self.model_base_url) or None
        provider = (self.web_search_provider or '').strip().lower()
        if not provider:
            self.web_search_provider = ''
            return self
        if provider not in SUPPORTED_WEB_SEARCH_PROVIDERS:
            raise ValueError(
                "WEB_SEARCH_PROVIDER must be one of: duckduckgo, tavily. "
                "Use the provider name, not an API key."
            )
        if provider == 'tavily' and not (self.tavily_api_key or '').strip():
            raise ValueError('TAVILY_API_KEY is required when WEB_SEARCH_PROVIDER=tavily')
        self.web_search_provider = provider
        return self

    @property
    def java_api_key(self) -> str:
        return self.java_internal_api_key or self.internal_service_token

    @property
    def resolved_java_public_base_url(self) -> str:
        if (self.java_public_base_url or '').strip():
            return self.java_public_base_url.strip().rstrip('/')
        internal = (self.java_internal_base_url or '').strip()
        if internal.endswith('/internal/v1'):
            return internal[: -len('/internal/v1')] + '/api/v1'
        return internal.rstrip('/') + '/api/v1'

    @property
    def is_dev_env(self) -> bool:
        return (self.app_env or '').strip().lower() in {'dev', 'development', 'local', 'test'}

    @property
    def resolved_embedding_api_key(self) -> str:
        return (
            self.embedding_api_key
            or self.model_api_key
            or self.openai_api_key
            or self.deepseek_api_key
        )

    @property
    def resolved_embedding_base_url(self) -> str:
        return (
            self.embed_base_url
            or self.embedding_base_url
            or self.model_base_url
            or self.openai_base_url
        )

    @property
    def resolved_provider_timeout_seconds(self) -> int:
        return self.provider_timeout_seconds

    @property
    def resolved_embedding_provider_timeout_seconds(self) -> int:
        if self.is_dev_env and self.dev_fast_fail_timeout_seconds > 0:
            return min(self.provider_timeout_seconds, self.dev_fast_fail_timeout_seconds)
        return self.provider_timeout_seconds

    @property
    def resolved_qdrant_timeout_seconds(self) -> int:
        if self.is_dev_env and self.dev_fast_fail_timeout_seconds > 0:
            return min(self.qdrant_timeout_seconds, self.dev_fast_fail_timeout_seconds)
        return self.qdrant_timeout_seconds

    @property
    def resolved_retry_backoff_seconds(self) -> float:
        if self.is_dev_env:
            return max(0.0, min(self.retry_backoff_seconds, self.dev_retry_backoff_seconds))
        return self.retry_backoff_seconds

    @property
    def resolved_embedding_max_retries(self) -> int:
        if self.is_dev_env:
            return max(0, self.dev_embedding_max_retries)
        return self.embedding_max_retries

    @property
    def resolved_qdrant_max_retries(self) -> int:
        if self.is_dev_env:
            return max(0, self.dev_qdrant_max_retries)
        return self.qdrant_max_retries

    @property
    def resolved_embedding_local_fallback_enabled(self) -> bool:
        return self.embedding_local_fallback_enabled or (self.is_dev_env and self.dev_embedding_local_fallback_enabled)

    @property
    def blocked_term_list(self) -> list[str]:
        return [item.strip() for item in self.blocked_terms.split(',') if item.strip()]

    def resolve_embedding_model_code(self, provider_code: str | None) -> str:
        normalized = (provider_code or '').strip().upper()
        if normalized == 'OLLAMA' and (self.ollama_embedding_model or '').strip():
            return self.ollama_embedding_model.strip()
        return self.embedding_model


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
