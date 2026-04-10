from __future__ import annotations

from pydantic import Field

from src.app.schemas.common import CamelModel
from src.app.schemas.rag import RagChunkPayload


class FileExtractView(CamelModel):
    plain_text: str = ''
    chunks: list[RagChunkPayload] = Field(default_factory=list)
    parser: str = ''
    used_ocr: bool = False
    page_count: int = 0


class TextChunkRequest(CamelModel):
    plain_text: str = ''
    page_no: int | None = None


class TextChunkView(CamelModel):
    chunks: list[RagChunkPayload] = Field(default_factory=list)
