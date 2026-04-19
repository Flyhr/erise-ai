from __future__ import annotations

import logging
from time import perf_counter
from urllib.parse import urljoin

import httpx
from sqlalchemy.orm import Session

from src.app.core.config import get_settings
from src.app.core.request_context import get_current_request_id
from src.app.schemas.model import ModelHealthView, ProviderHealthView
from src.app.services.model_registry import ProviderRoute, ProviderRegistry, get_embedding_route, get_model_config


logger = logging.getLogger(__name__)


class ModelHealthService:
    def __init__(self) -> None:
        self.settings = get_settings()

    def _models_url(self, base_url: str) -> str:
        normalized = (base_url or '').rstrip('/') + '/'
        if normalized.endswith('/v1/'):
            return urljoin(normalized, 'models')
        return urljoin(normalized, 'v1/models')

    def _headers(self, route: ProviderRoute) -> dict[str, str]:
        headers: dict[str, str] = {}
        request_id = get_current_request_id()
        if request_id:
            headers['X-Request-Id'] = request_id
        if route.api_key:
            headers['Authorization'] = f'Bearer {route.api_key}'
        return headers

    async def _probe_route(self, role: str, route: ProviderRoute) -> ProviderHealthView:
        if not route.configured:
            return ProviderHealthView(
                role=role,
                provider_code=route.provider_code,
                model_code=route.model_code,
                base_url=route.base_url,
                configured=False,
                status='DOWN',
                error_code='AI_PROVIDER_NOT_CONFIGURED',
                message='Provider route is not configured',
            )

        started_at = perf_counter()
        status = 'UP'
        error_code = None
        message = None
        try:
            async with httpx.AsyncClient(timeout=min(self.settings.connect_timeout_seconds, route.timeout_seconds)) as client:
                response = await client.get(self._models_url(route.base_url), headers=self._headers(route))
                response.raise_for_status()
        except httpx.TimeoutException:
            status = 'DOWN'
            error_code = 'AI_PROVIDER_TIMEOUT'
            message = 'Provider health check timed out'
        except httpx.HTTPStatusError as exc:
            status = 'DOWN'
            error_code = 'AI_PROVIDER_HEALTH_FAILED'
            message = f'Provider health check returned HTTP {exc.response.status_code}'
        except httpx.RequestError as exc:
            status = 'DOWN'
            error_code = 'AI_PROVIDER_UNAVAILABLE'
            message = str(exc)

        latency_ms = max(1, int((perf_counter() - started_at) * 1000))
        logger.info(
            'model_provider_health request_id=%s role=%s provider=%s model=%s base_url=%s latency_ms=%s status=%s error_code=%s',
            get_current_request_id('-'),
            role,
            route.provider_code,
            route.model_code,
            route.base_url,
            latency_ms,
            status,
            error_code or '',
        )
        return ProviderHealthView(
            role=role,
            provider_code=route.provider_code,
            model_code=route.model_code,
            base_url=route.base_url,
            configured=True,
            status=status,
            latency_ms=latency_ms,
            error_code=error_code,
            message=message,
        )

    async def check(self, db: Session) -> ModelHealthView:
        registry = ProviderRegistry()
        chat_model = get_model_config(db, None)
        routes = [
            await self._probe_route('chat', registry.resolve_model_route(chat_model)),
            await self._probe_route('embedding', get_embedding_route()),
        ]
        status = 'UP' if all(item.status == 'UP' for item in routes) else 'DEGRADED'
        return ModelHealthView(status=status, routes=routes)


model_health_service = ModelHealthService()
