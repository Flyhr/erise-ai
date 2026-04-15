from __future__ import annotations

import asyncio
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

    def _allow_fallback(self) -> bool:
        return self.settings.embedding_local_fallback_enabled and self.settings.app_env.lower() != 'prod'

    def _is_quota_error(self, message: str) -> bool:
        normalized = (message or '').lower()
        return (
            'insufficient_quota' in normalized
            or 'quota' in normalized and 'exceed' in normalized
            or 'billing' in normalized and 'inactive' in normalized
            or 'account_balance' in normalized
        )

    def _is_retryable_error(self, message: str) -> bool:
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
            or 'too many requests' in normalized
            or 'rate limit' in normalized
        )

    async def embed(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        if not self.settings.resolved_embedding_api_key:
            if not self._allow_fallback():
                raise AiServiceError('AI_EMBEDDING_NOT_CONFIGURED', 'Embedding api key is not configured', status_code=503)
            return self._fallback_embeddings(texts, 'embedding api key is not configured')
        vectors: list[list[float]] = []
        batch_size = max(1, self.settings.embedding_batch_size)
        for start in range(0, len(texts), batch_size):
            vectors.extend(await self._embed_batch(texts[start:start + batch_size]))
        return vectors

    async def _embed_batch(self, texts: list[str]) -> list[list[float]]:
        attempts = max(1, self.settings.embedding_max_retries + 1)
        for attempt in range(1, attempts + 1):
            try:
                response = await self.client.embeddings.create(
                    model=self.settings.embedding_model,
                    input=texts,
                )
                return [item.embedding for item in response.data]
            except Exception as exc:
                message = str(exc)
                if self._is_quota_error(message):
                    raise AiServiceError(
                        'AI_EMBEDDING_QUOTA_EXCEEDED',
                        'Embedding provider quota exceeded or billing is unavailable. '
                        'Please configure a valid EMBEDDING_BASE_URL/EMBEDDING_API_KEY provider.',
                        status_code=502,
                    ) from exc
                is_last_attempt = attempt >= attempts
                if self._is_retryable_error(message) and not is_last_attempt:
                    wait_seconds = self.settings.retry_backoff_seconds * attempt
                    logger.warning(
                        'Embedding request failed on attempt %s/%s, retrying in %.1fs: %s',
                        attempt,
                        attempts,
                        wait_seconds,
                        message,
                    )
                    await asyncio.sleep(wait_seconds)
                    continue
                if self._allow_fallback():
                    return self._fallback_embeddings(texts, message)
                raise AiServiceError('AI_EMBEDDING_ERROR', f'Embedding request failed: {message}', status_code=502) from exc


embedding_service = EmbeddingService()
