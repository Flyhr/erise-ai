from __future__ import annotations

from abc import ABC, abstractmethod
from collections.abc import AsyncGenerator
import logging
from time import perf_counter

from openai import APIConnectionError, APIStatusError, APITimeoutError, AsyncOpenAI, AuthenticationError, BadRequestError, OpenAIError, RateLimitError

from src.app.adapters.llm.base import AdapterResult, AdapterStreamEvent, AdapterUsage
from src.app.core.exceptions import AiServiceError
from src.app.core.request_context import get_current_request_id

PLACEHOLDER_API_KEY = 'not-required'
logger = logging.getLogger(__name__)


class ModelProvider(ABC):
    provider_code: str

    @abstractmethod
    async def chat(
        self,
        model_code: str,
        messages: list[dict[str, str]],
        temperature: float | None,
        max_tokens: int | None,
    ) -> AdapterResult:
        raise NotImplementedError

    @abstractmethod
    async def stream(
        self,
        model_code: str,
        messages: list[dict[str, str]],
        temperature: float | None,
        max_tokens: int | None,
    ) -> AsyncGenerator[AdapterStreamEvent, None]:
        raise NotImplementedError

    async def stream_chat(
        self,
        model_code: str,
        messages: list[dict[str, str]],
        temperature: float | None,
        max_tokens: int | None,
    ) -> AsyncGenerator[AdapterStreamEvent, None]:
        async for event in self.stream(model_code, messages, temperature, max_tokens):
            yield event

    @abstractmethod
    async def embed(self, model_code: str, inputs: list[str]) -> list[list[float]]:
        raise NotImplementedError


class OpenAiCompatibleProvider(ModelProvider):
    def __init__(
        self,
        *,
        provider_code: str,
        api_key: str,
        base_url: str,
        timeout_seconds: int,
        require_api_key: bool = True,
    ) -> None:
        self.provider_code = provider_code
        self.api_key = api_key or ''
        self.base_url = base_url or ''
        self.require_api_key = require_api_key
        self.client = AsyncOpenAI(
            api_key=self.api_key or PLACEHOLDER_API_KEY,
            base_url=self.base_url,
            timeout=timeout_seconds,
        )

    def _ensure_available(self) -> None:
        if not self.base_url:
            raise AiServiceError(
                'AI_MODEL_NOT_FOUND',
                f'{self.provider_code} base url is not configured',
                status_code=503,
            )
        if self.require_api_key and not self.api_key:
            raise AiServiceError(
                'AI_MODEL_NOT_FOUND',
                f'{self.provider_code} api key is not configured',
                status_code=503,
                provider_code=self.provider_code,
            )

    def _request_headers(self) -> dict[str, str]:
        request_id = get_current_request_id()
        return {'X-Request-Id': request_id} if request_id else {}

    def _normalize_provider_error(self, exc: Exception, model_code: str, operation: str) -> AiServiceError:
        upstream_status_code = getattr(exc, 'status_code', None)
        message = str(exc) or exc.__class__.__name__
        status_code = 502
        error_code = 'AI_PROVIDER_ERROR'

        if isinstance(exc, APITimeoutError) or 'timeout' in message.lower():
            error_code = 'AI_PROVIDER_TIMEOUT'
            status_code = 504
            message = f'{self.provider_code} {operation} request timed out'
        elif isinstance(exc, APIConnectionError):
            error_code = 'AI_PROVIDER_UNAVAILABLE'
            status_code = 503
            message = f'{self.provider_code} is unavailable or unreachable'
        elif isinstance(exc, AuthenticationError):
            error_code = 'AI_PROVIDER_AUTH_FAILED'
            status_code = 502
            message = f'{self.provider_code} authentication failed'
        elif isinstance(exc, RateLimitError):
            error_code = 'AI_PROVIDER_RATE_LIMITED'
            status_code = 429
            message = f'{self.provider_code} rate limit exceeded'
        elif isinstance(exc, BadRequestError):
            error_code = 'AI_PROVIDER_BAD_REQUEST'
            status_code = 400
            message = f'{self.provider_code} rejected the {operation} request: {message}'
        elif isinstance(exc, APIStatusError):
            if upstream_status_code in {502, 503, 504}:
                error_code = 'AI_PROVIDER_UNAVAILABLE'
                status_code = 503
            elif upstream_status_code == 429:
                error_code = 'AI_PROVIDER_RATE_LIMITED'
                status_code = 429
            else:
                error_code = 'AI_PROVIDER_UPSTREAM_ERROR'
            message = f'{self.provider_code} returned upstream status {upstream_status_code}: {message}'
        elif isinstance(exc, OpenAIError):
            message = f'{self.provider_code} {operation} request failed: {message}'

        return AiServiceError(
            error_code,
            message,
            status_code=status_code,
            provider_code=self.provider_code,
            model_code=model_code,
            upstream_status_code=upstream_status_code,
        )

    def _log_provider_call(
        self,
        *,
        operation: str,
        model_code: str,
        started_at: float,
        success: bool,
        error_code: str | None = None,
    ) -> None:
        logger.info(
            'model_provider_call request_id=%s provider=%s model=%s operation=%s base_url=%s latency_ms=%s success=%s error_code=%s',
            get_current_request_id('-'),
            self.provider_code,
            model_code,
            operation,
            self.base_url,
            max(1, int((perf_counter() - started_at) * 1000)),
            success,
            error_code or '',
        )

    async def chat(
        self,
        model_code: str,
        messages: list[dict[str, str]],
        temperature: float | None,
        max_tokens: int | None,
    ) -> AdapterResult:
        self._ensure_available()
        started_at = perf_counter()
        try:
            response = await self.client.chat.completions.create(
                model=model_code,
                messages=messages,
                temperature=temperature,
                max_tokens=max_tokens,
                extra_headers=self._request_headers(),
            )
        except Exception as exc:
            normalized = self._normalize_provider_error(exc, model_code, 'chat')
            self._log_provider_call(operation='chat', model_code=model_code, started_at=started_at, success=False, error_code=normalized.error_code)
            raise normalized from exc
        self._log_provider_call(operation='chat', model_code=model_code, started_at=started_at, success=True)
        usage = AdapterUsage(
            prompt_tokens=response.usage.prompt_tokens if response.usage else 0,
            completion_tokens=response.usage.completion_tokens if response.usage else 0,
            total_tokens=response.usage.total_tokens if response.usage else 0,
        )
        content = response.choices[0].message.content or ''
        return AdapterResult(
            text=content.strip(),
            usage=usage,
            provider_code=self.provider_code,
            model_code=model_code,
            raw_response=response.model_dump(),
        )

    async def stream(
        self,
        model_code: str,
        messages: list[dict[str, str]],
        temperature: float | None,
        max_tokens: int | None,
    ) -> AsyncGenerator[AdapterStreamEvent, None]:
        self._ensure_available()
        started_at = perf_counter()
        try:
            stream = await self.client.chat.completions.create(
                model=model_code,
                messages=messages,
                temperature=temperature,
                max_tokens=max_tokens,
                stream=True,
                stream_options={'include_usage': True},
                extra_headers=self._request_headers(),
            )
            async for chunk in stream:
                delta = ''
                if chunk.choices:
                    delta = chunk.choices[0].delta.content or ''
                usage = None
                if chunk.usage:
                    usage = AdapterUsage(
                        prompt_tokens=chunk.usage.prompt_tokens or 0,
                        completion_tokens=chunk.usage.completion_tokens or 0,
                        total_tokens=chunk.usage.total_tokens or 0,
                    )
                if delta or usage:
                    yield AdapterStreamEvent(delta=delta, usage=usage)
        except Exception as exc:
            normalized = self._normalize_provider_error(exc, model_code, 'stream')
            self._log_provider_call(operation='stream', model_code=model_code, started_at=started_at, success=False, error_code=normalized.error_code)
            raise normalized from exc
        self._log_provider_call(operation='stream', model_code=model_code, started_at=started_at, success=True)

    async def embed(self, model_code: str, inputs: list[str]) -> list[list[float]]:
        self._ensure_available()
        if not inputs:
            return []
        started_at = perf_counter()
        try:
            response = await self.client.embeddings.create(
                model=model_code,
                input=inputs,
                extra_headers=self._request_headers(),
            )
        except Exception as exc:
            normalized = self._normalize_provider_error(exc, model_code, 'embedding')
            self._log_provider_call(operation='embedding', model_code=model_code, started_at=started_at, success=False, error_code=normalized.error_code)
            raise normalized from exc
        self._log_provider_call(operation='embedding', model_code=model_code, started_at=started_at, success=True)
        return [item.embedding for item in response.data]
