from __future__ import annotations

import asyncio
import logging
from dataclasses import dataclass
from datetime import datetime, timedelta
from time import perf_counter
from urllib.parse import urljoin

import httpx
from sqlalchemy import func, or_, select
from sqlalchemy.orm import Session

from src.app.core.config import get_settings
from src.app.core.request_context import get_current_request_id
from src.app.models.ai_request_log import AiRequestLog
from src.app.schemas.model import (
    ModelHealthView,
    ProviderHealthInventoryView,
    ProviderHealthView,
    ProviderRecentErrorView,
    ProviderRouteHealthView,
)
from src.app.services.model_registry import ProviderRoute, ProviderRegistry, get_embedding_route, list_enabled_model_routes


logger = logging.getLogger(__name__)


@dataclass(frozen=True, slots=True)
class ProviderLogStats:
    request_count: int = 0
    error_count: int = 0
    recent_error_codes: tuple[ProviderRecentErrorView, ...] = ()


@dataclass(frozen=True, slots=True)
class RouteDescriptor:
    role: str
    model_name: str | None
    route: ProviderRoute
    is_default: bool = False
    is_effective: bool = False


class ModelHealthService:
    def __init__(self) -> None:
        self.settings = get_settings()

    def _models_url(self, base_url: str) -> str:
        normalized = (base_url or '').rstrip('/') + '/'
        if normalized.endswith('/v1/'):
            return urljoin(normalized, 'models')
        return urljoin(normalized, 'v1/models')

    def _endpoint_url(self, route: ProviderRoute, role: str) -> str:
        normalized = (route.base_url or '').rstrip('/') + '/'
        suffix = 'chat/completions' if role == 'chat' else 'embeddings'
        if normalized.endswith('/v1/'):
            return urljoin(normalized, suffix)
        return urljoin(normalized, f'v1/{suffix}')

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

    async def _probe_route_detail(self, descriptor: RouteDescriptor, stats: ProviderLogStats) -> ProviderRouteHealthView:
        summary = await self._probe_route(descriptor.role, descriptor.route)
        return ProviderRouteHealthView(
            role=descriptor.role,
            provider_code=summary.provider_code,
            model_code=summary.model_code,
            model_name=descriptor.model_name,
            base_url=summary.base_url,
            endpoint_url=self._endpoint_url(descriptor.route, descriptor.role),
            probe_url=self._models_url(descriptor.route.base_url),
            configured=summary.configured,
            status=summary.status,
            timeout_seconds=descriptor.route.timeout_seconds,
            source=descriptor.route.source,
            latency_ms=summary.latency_ms,
            error_code=summary.error_code,
            message=summary.message,
            is_default=descriptor.is_default,
            is_effective=descriptor.is_effective,
            recent_request_count_24h=stats.request_count,
            recent_error_count_24h=stats.error_count,
            recent_error_codes=list(stats.recent_error_codes),
        )

    def _recent_log_stats(self, db: Session) -> dict[tuple[str, str], ProviderLogStats]:
        since = datetime.utcnow() - timedelta(hours=24)
        request_rows = db.execute(
            select(
                AiRequestLog.provider_code,
                AiRequestLog.model_code,
                func.count(AiRequestLog.id),
            )
            .where(AiRequestLog.created_at >= since)
            .group_by(AiRequestLog.provider_code, AiRequestLog.model_code)
        ).all()
        error_rows = db.execute(
            select(
                AiRequestLog.provider_code,
                AiRequestLog.model_code,
                func.count(AiRequestLog.id),
            )
            .where(
                AiRequestLog.created_at >= since,
                or_(AiRequestLog.success_flag.is_(False), AiRequestLog.error_code.is_not(None)),
            )
            .group_by(AiRequestLog.provider_code, AiRequestLog.model_code)
        ).all()
        error_code_rows = db.execute(
            select(
                AiRequestLog.provider_code,
                AiRequestLog.model_code,
                AiRequestLog.error_code,
                func.count(AiRequestLog.id),
                func.max(AiRequestLog.created_at),
            )
            .where(
                AiRequestLog.created_at >= since,
                AiRequestLog.error_code.is_not(None),
            )
            .group_by(AiRequestLog.provider_code, AiRequestLog.model_code, AiRequestLog.error_code)
            .order_by(func.count(AiRequestLog.id).desc(), func.max(AiRequestLog.created_at).desc())
        ).all()

        request_counts = {(provider_code, model_code): int(count or 0) for provider_code, model_code, count in request_rows}
        error_counts = {(provider_code, model_code): int(count or 0) for provider_code, model_code, count in error_rows}
        grouped_errors: dict[tuple[str, str], list[ProviderRecentErrorView]] = {}
        for provider_code, model_code, error_code, count, last_seen_at in error_code_rows:
            if not error_code:
                continue
            key = (provider_code, model_code)
            bucket = grouped_errors.setdefault(key, [])
            if len(bucket) >= 3:
                continue
            bucket.append(
                ProviderRecentErrorView(
                    error_code=error_code,
                    count=int(count or 0),
                    last_seen_at=last_seen_at,
                )
            )

        stats: dict[tuple[str, str], ProviderLogStats] = {}
        for key in set(request_counts) | set(error_counts) | set(grouped_errors):
            stats[key] = ProviderLogStats(
                request_count=request_counts.get(key, 0),
                error_count=error_counts.get(key, 0),
                recent_error_codes=tuple(grouped_errors.get(key, [])),
            )
        return stats

    def _fallback_chat_route(self) -> ProviderRoute:
        registry = ProviderRegistry()
        provider_code = registry.gateway_provider_code() or registry.normalize_provider_code(self.settings.model_provider) or 'UNKNOWN'
        return ProviderRoute(
            provider_code=provider_code,
            model_code=self.settings.default_model_code,
            base_url=self.settings.model_base_url or registry.provider_base_url(provider_code),
            api_key=self.settings.model_api_key or registry.provider_api_key(provider_code),
            timeout_seconds=self.settings.provider_timeout_seconds,
            configured=False,
            source='gateway-override' if registry.gateway_override_enabled() else 'model-config',
        )

    async def check(self, db: Session) -> ModelHealthView:
        enabled_routes = list_enabled_model_routes(db)
        chat_route = next((item.route for item in enabled_routes if item.is_effective_default), None)
        if chat_route is None and enabled_routes:
            chat_route = enabled_routes[0].route
        if chat_route is None:
            chat_route = self._fallback_chat_route()
        routes = [
            await self._probe_route('chat', chat_route),
            await self._probe_route('embedding', get_embedding_route()),
        ]
        status = 'UP' if routes and all(item.status == 'UP' for item in routes) else 'DEGRADED'
        return ModelHealthView(status=status, routes=routes)

    async def provider_health(self, db: Session) -> ProviderHealthInventoryView:
        enabled_model_routes = list_enabled_model_routes(db)
        descriptors = [
            RouteDescriptor(
                role='chat',
                model_name=item.model_name,
                route=item.route,
                is_default=item.is_database_default,
                is_effective=item.is_effective_default,
            )
            for item in enabled_model_routes
        ]
        embedding_route = get_embedding_route()
        descriptors.append(
            RouteDescriptor(
                role='embedding',
                model_name='Embedding',
                route=embedding_route,
                is_default=False,
                is_effective=True,
            )
        )
        stats = self._recent_log_stats(db)
        route_views = await asyncio.gather(
            *[
                self._probe_route_detail(
                    descriptor,
                    stats.get((descriptor.route.provider_code, descriptor.route.model_code), ProviderLogStats()),
                )
                for descriptor in descriptors
            ]
        )
        effective_routes = [item for item in route_views if item.is_effective]
        enabled_routes = [item for item in route_views if item.role == 'chat']
        default_chat = next((item for item in enabled_routes if item.is_effective), None)
        overall_status = 'UP' if effective_routes and all(item.status == 'UP' for item in effective_routes) else 'DEGRADED'
        return ProviderHealthInventoryView(
            status=overall_status,
            generated_at=datetime.utcnow(),
            default_provider_code=default_chat.provider_code if default_chat is not None else None,
            default_model_code=default_chat.model_code if default_chat is not None else None,
            effective_routes=effective_routes,
            enabled_routes=enabled_routes,
        )


model_health_service = ModelHealthService()
