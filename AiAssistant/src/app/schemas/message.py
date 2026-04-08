from __future__ import annotations

from datetime import datetime

from pydantic import Field

from src.app.schemas.common import CamelModel


class CitationView(CamelModel):
    source_type: str
    source_id: int
    source_title: str
    snippet: str | None = None
    page_no: int | None = None
    score: float | None = None
    url: str | None = None


class MessageView(CamelModel):
    id: int
    role: str
    content: str
    confidence: float | None = None
    refused_reason: str | None = None
    citations: list[CitationView] = Field(default_factory=list)
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
