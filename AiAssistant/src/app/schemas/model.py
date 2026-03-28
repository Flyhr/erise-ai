from __future__ import annotations

from src.app.schemas.common import CamelModel


class ModelView(CamelModel):
    provider_code: str
    model_code: str
    model_name: str
    support_stream: bool
    max_context_tokens: int | None = None
