from __future__ import annotations

from src.app.providers.base import OpenAiCompatibleProvider


class OllamaProvider(OpenAiCompatibleProvider):
    def __init__(self, *, api_key: str, base_url: str, timeout_seconds: int) -> None:
        super().__init__(
            provider_code='OLLAMA',
            api_key=api_key,
            base_url=base_url,
            timeout_seconds=timeout_seconds,
            require_api_key=False,
        )
