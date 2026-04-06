from __future__ import annotations

from datetime import datetime

from pydantic import Field

from src.app.schemas.common import CamelModel
from src.app.schemas.message import CitationView


class RagChunkPayload(CamelModel):
    chunk_num: int
    chunk_text: str
    page_no: int | None = None
    section_path: str | None = None


class RagIndexUpsertRequest(CamelModel):
    user_id: int
    project_id: int | None = None
    session_id: int | None = None
    source_type: str
    source_id: int
    source_name: str
    chunks: list[RagChunkPayload] = Field(default_factory=list)
    updated_at: datetime | None = None


class RagIndexDeleteRequest(CamelModel):
    user_id: int
    project_id: int | None = None
    session_id: int | None = None
    source_type: str
    source_id: int


class RagQueryRequest(CamelModel):
    user_id: int
    query: str
    project_scope_ids: list[int] = Field(default_factory=list)
    attachments: list[dict] = Field(default_factory=list)
    limit: int = 6


class RagQueryHit(CamelModel):
    score: float
    source_type: str
    source_id: int
    source_title: str
    snippet: str
    page_no: int | None = None


class RagQueryResponse(CamelModel):
    hits: list[RagQueryHit] = Field(default_factory=list)
    citations: list[CitationView] = Field(default_factory=list)
    confidence: float | None = None
    answer_source: str | None = None
