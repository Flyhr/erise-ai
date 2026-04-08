from __future__ import annotations

from datetime import datetime

from pydantic import Field

from src.app.schemas.chat import AttachmentContext, ChatContext
from src.app.schemas.common import CamelModel
from src.app.schemas.message import CitationView


class RagChunkPayload(CamelModel):
    chunk_num: int
    chunk_text: str
    page_no: int | None = None
    section_path: str | None = None


class RagIndexUpsertRequest(CamelModel):
    user_id: int
    scope_type: str
    project_id: int | None = None
    session_id: int | None = None
    source_type: str
    source_id: int
    source_name: str
    chunks: list[RagChunkPayload] = Field(default_factory=list)
    updated_at: datetime | None = None


class RagIndexDeleteRequest(CamelModel):
    user_id: int
    scope_type: str
    project_id: int | None = None
    session_id: int | None = None
    source_type: str
    source_id: int


class RagQueryRequest(CamelModel):
    user_id: int
    query: str
    project_scope_ids: list[int] = Field(default_factory=list)
    attachments: list[AttachmentContext] = Field(default_factory=list)
    limit: int = 6


class RagQueryHit(CamelModel):
    score: float
    source_type: str
    source_id: int
    source_title: str
    snippet: str
    page_no: int | None = None
    url: str | None = None


class RagQueryResponse(CamelModel):
    hits: list[RagQueryHit] = Field(default_factory=list)
    citations: list[CitationView] = Field(default_factory=list)
    confidence: float | None = None
    answer_source: str | None = None
    used_tools: list[str] = Field(default_factory=list)


def build_debug_chat_context(project_scope_ids: list[int], attachments: list[AttachmentContext]) -> ChatContext:
    return ChatContext(
        project_id=project_scope_ids[0] if project_scope_ids else None,
        attachments=attachments,
    )
