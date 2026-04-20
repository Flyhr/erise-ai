from __future__ import annotations

from datetime import datetime

from pydantic import Field

from src.app.schemas.common import CamelModel


class ModelView(CamelModel):
    provider_code: str
    model_code: str
    model_name: str
    is_default: bool = False
    support_stream: bool
    max_context_tokens: int | None = None


class ProviderHealthView(CamelModel):
    role: str
    provider_code: str
    model_code: str
    base_url: str
    configured: bool
    status: str
    latency_ms: int | None = None
    error_code: str | None = None
    message: str | None = None


class ModelHealthView(CamelModel):
    status: str
    routes: list[ProviderHealthView]


class ProviderRecentErrorView(CamelModel):
    error_code: str
    count: int
    last_seen_at: datetime | None = None


class ProviderRouteHealthView(CamelModel):
    role: str
    provider_code: str
    model_code: str
    model_name: str | None = None
    base_url: str
    endpoint_url: str
    probe_url: str
    configured: bool
    status: str
    timeout_seconds: int
    source: str
    latency_ms: int | None = None
    error_code: str | None = None
    message: str | None = None
    is_default: bool = False
    is_effective: bool = False
    recent_request_count_24h: int = 0
    recent_error_count_24h: int = 0
    recent_error_codes: list[ProviderRecentErrorView] = Field(default_factory=list)


class ProviderHealthInventoryView(CamelModel):
    status: str
    generated_at: datetime
    default_provider_code: str | None = None
    default_model_code: str | None = None
    effective_routes: list[ProviderRouteHealthView] = Field(default_factory=list)
    enabled_routes: list[ProviderRouteHealthView] = Field(default_factory=list)
