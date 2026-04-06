from __future__ import annotations

from openai import AsyncOpenAI

from src.app.core.config import get_settings
from src.app.core.exceptions import AiServiceError


class EmbeddingService:
    def __init__(self) -> None:
        self.settings = get_settings()
        self.client = AsyncOpenAI(
            api_key=self.settings.resolved_embedding_api_key,
            base_url=self.settings.embedding_base_url,
            timeout=self.settings.provider_timeout_seconds,
        )

    def _ensure_available(self) -> None:
        if not self.settings.resolved_embedding_api_key:
            raise AiServiceError('AI_EMBEDDING_NOT_CONFIGURED', 'Embedding api key is not configured', status_code=503)

    async def embed(self, texts: list[str]) -> list[list[float]]:
        self._ensure_available()
        if not texts:
            return []
        response = await self.client.embeddings.create(
            model=self.settings.embedding_model,
            input=texts,
        )
        return [item.embedding for item in response.data]


embedding_service = EmbeddingService()
