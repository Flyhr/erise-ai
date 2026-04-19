from __future__ import annotations

from pydantic import Field

from src.app.schemas.common import CamelModel


class PdfOcrView(CamelModel):
    text: str = ''
    page_texts: list[str] = Field(default_factory=list)
    page_count: int = 0
    used_ocr: bool = True
    engine: str = 'rapidocr'
