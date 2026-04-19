from __future__ import annotations

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
