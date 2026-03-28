from __future__ import annotations

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


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
    context_history_limit: int = 8
    request_body_char_limit: int = 12000
    session_page_size: int = 20
    message_page_size: int = 50
    blocked_terms: str = ''
    stream_cancel_ttl_seconds: int = 600
    provider_timeout_seconds: int = 120
    connect_timeout_seconds: int = 30
    default_org_id: int = 0
    sqlite_echo: bool = False

    model_config = SettingsConfigDict(env_file='.env', env_file_encoding='utf-8', extra='ignore')

    @property
    def blocked_term_list(self) -> list[str]:
        return [item.strip().lower() for item in self.blocked_terms.split(',') if item.strip()]

    @property
    def java_api_key(self) -> str:
        return self.java_internal_api_key or self.internal_service_token


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
