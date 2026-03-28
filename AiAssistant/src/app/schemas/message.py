from __future__ import annotations

from datetime import datetime

from src.app.schemas.common import CamelModel


class MessageView(CamelModel):
    id: int
    role: str
    content: str
    message_status: str
    sequence_no: int
    model_code: str | None = None
    provider_code: str | None = None
    prompt_tokens: int | None = None
    completion_tokens: int | None = None
    total_tokens: int | None = None
    latency_ms: int | None = None
    error_code: str | None = None
    error_message: str | None = None
    request_id: str | None = None
    created_at: datetime
