from __future__ import annotations

import hashlib
import logging
import math
import re

from openai import AsyncOpenAI

from src.app.core.config import get_settings
from src.app.core.exceptions import AiServiceError


logger = logging.getLogger(__name__)
TOKEN_PATTERN = re.compile(r'[\u4e00-\u9fff]+|[a-zA-Z0-9_]+')


class EmbeddingService:
    def __init__(self) -> None:
        self.settings = get_settings()
        self.client = AsyncOpenAI(
            api_key=self.settings.resolved_embedding_api_key,
            base_url=self.settings.resolved_embedding_base_url,
            timeout=self.settings.provider_timeout_seconds,
        )

    def _tokenize(self, text: str) -> list[str]:
        tokens = TOKEN_PATTERN.findall((text or '').lower())
        return tokens or ['']

    def _local_embedding(self, text: str) -> list[float]:
        dimensions = max(8, self.settings.embedding_dimensions)
        vector = [0.0] * dimensions
        for token in self._tokenize(text):
            seed = token.encode('utf-8')
            cursor = 0
            while cursor < dimensions:
                digest = hashlib.sha256(seed).digest()
                seed = digest
                for byte in digest:
                    vector[cursor] += (byte / 127.5) - 1.0
                    cursor += 1
                    if cursor >= dimensions:
                        break
        norm = math.sqrt(sum(item * item for item in vector)) or 1.0
        return [item / norm for item in vector]

    def _fallback_embeddings(self, texts: list[str], reason: str) -> list[list[float]]:
        logger.warning('Embedding provider unavailable, using local deterministic fallback: %s', reason)
        return [self._local_embedding(text) for text in texts]

    async def embed(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        if not self.settings.resolved_embedding_api_key:
            if self.settings.app_env.lower() == 'prod':
                raise AiServiceError('AI_EMBEDDING_NOT_CONFIGURED', 'Embedding api key is not configured', status_code=503)
            return self._fallback_embeddings(texts, 'embedding api key is not configured')
        try:
            response = await self.client.embeddings.create(
                model=self.settings.embedding_model,
                input=texts,
            )
            return [item.embedding for item in response.data]
        except Exception as exc:
            if self.settings.app_env.lower() == 'prod':
                raise AiServiceError('AI_EMBEDDING_ERROR', f'Embedding request failed: {exc}', status_code=502) from exc
            return self._fallback_embeddings(texts, str(exc))


embedding_service = EmbeddingService()
