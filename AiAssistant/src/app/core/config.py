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

    openai_api_key: str = ''
    openai_base_url: str = 'https://api.openai.com/v1'
    openai_model: str = 'gpt-4.1-mini'
    deepseek_api_key: str = ''
    deepseek_base_url: str = 'https://api.deepseek.com/v1'
    deepseek_model: str = 'deepseek-chat'
    default_model_code: str = 'deepseek-chat'

    qdrant_url: str = 'http://localhost:6333'
    qdrant_api_key: str = ''
    qdrant_kb_collection: str = 'kb_chunks'
    qdrant_temp_collection: str = 'temp_chunks'

    embedding_model: str = 'text-embedding-3-small'
    embedding_base_url: str | None = None
    embedding_api_key: str = ''
    embedding_version: str = 'v1'
    embedding_dimensions: int = 1536
    embedding_local_fallback_enabled: bool = False

    rag_top_k: int = 5
    rag_keyword_top_k: int = 5
    rag_similarity_threshold: float = 0.75
    rag_query_rewrite_enabled: bool = True
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
    default_org_id: int = 0
    sqlite_echo: bool = False
    auto_init_sqlite_schema: bool = True

    model_config = SettingsConfigDict(
        env_file='.env',
        env_file_encoding='utf-8',
        extra='ignore',
    )

    @model_validator(mode='after')
    def validate_web_search_settings(self) -> 'Settings':
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
    def resolved_embedding_api_key(self) -> str:
        return self.embedding_api_key or self.openai_api_key or self.deepseek_api_key

    @property
    def resolved_embedding_base_url(self) -> str:
        return self.embedding_base_url or self.openai_base_url

    @property
    def blocked_term_list(self) -> list[str]:
        return [item.strip() for item in self.blocked_terms.split(',') if item.strip()]


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
