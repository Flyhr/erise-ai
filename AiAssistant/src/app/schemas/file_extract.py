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
    parse_status: str = 'SUCCESS'
    primary_parser: str = ''
    fallback_parser: str | None = None
    fallback_used: bool = False
    error_code: str | None = None
    error_message: str | None = None
    retryable: bool = False
    primary_attempts: int = 0
    primary_error_status_code: int | None = None
    primary_error_stage: str | None = None
    primary_error_category: str | None = None
    fallback_reason: str | None = None
    monitoring_tags: list[str] = Field(default_factory=list)


class FileTypeCapabilityView(CamelModel):
    extension: str
    label: str
    primary_parser: str
    fallback_parser: str
    supports_ocr: bool = False
    supports_pages: bool = False
    parse_statuses: list[str] = Field(default_factory=list)
    retry_policy: str
    notes: str = ''


class ParserRuntimeView(CamelModel):
    parser_code: str
    label: str
    status: str
    error_code: str | None = None
    message: str | None = None
    supported_extensions: list[str] = Field(default_factory=list)


class FileCapabilityMatrixView(CamelModel):
    parser_order: list[str] = Field(default_factory=list)
    parse_statuses: list[str] = Field(default_factory=list)
    parser_runtimes: list[ParserRuntimeView] = Field(default_factory=list)
    file_types: list[FileTypeCapabilityView] = Field(default_factory=list)


class TextChunkRequest(CamelModel):
    plain_text: str = ''
    page_no: int | None = None


class TextChunkView(CamelModel):
    chunks: list[RagChunkPayload] = Field(default_factory=list)
