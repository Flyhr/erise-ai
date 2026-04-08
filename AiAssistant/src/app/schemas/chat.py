from __future__ import annotations

from typing import Literal

from pydantic import Field

from src.app.schemas.common import CamelModel
from src.app.schemas.message import CitationView


class AttachmentContext(CamelModel):
    attachment_type: Literal['DOCUMENT', 'FILE', 'TEMP_FILE']
    source_id: int
    project_id: int | None = None
    session_id: int | None = None
    title: str | None = None


class ChatContext(CamelModel):
    project_id: int | None = None
    document_id: int | None = None
    attachments: list[AttachmentContext] = Field(default_factory=list)


class ChatCompletionRequest(CamelModel):
    session_id: int | None = None
    scene: str = 'general_chat'
    model_code: str | None = None
    message: str
    context: ChatContext = Field(default_factory=ChatContext)
    temperature: float | None = 0.3
    max_tokens: int | None = 2048
    mode: str | None = None
    web_search_enabled: bool | None = None
    similarity_threshold: float | None = None
    top_k: int | None = None


class UsageView(CamelModel):
    prompt_tokens: int = 0
    completion_tokens: int = 0
    total_tokens: int = 0


class ChatCompletionView(CamelModel):
    request_id: str
    session_id: int
    user_message_id: int
    assistant_message_id: int
    answer: str
    citations: list[CitationView] = Field(default_factory=list)
    used_tools: list[str] = Field(default_factory=list)
    confidence: float | None = None
    refused_reason: str | None = None
    scene: str
    model_code: str
    provider_code: str
    message_status: str
    usage: UsageView
    latency_ms: int


class CancelResponse(CamelModel):
    request_id: str
    cancelled: bool
