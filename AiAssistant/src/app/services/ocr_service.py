from __future__ import annotations

from dataclasses import dataclass

from src.app.core.exceptions import AiServiceError


@dataclass(slots=True)
class PdfOcrResult:
    text: str
    page_texts: list[str]
    page_count: int
    used_ocr: bool
    engine: str


class OcrService:
    def __init__(self) -> None:
        self._engine = None

    def _get_engine(self):
        if self._engine is not None:
            return self._engine
        try:
            from rapidocr_onnxruntime import RapidOCR
        except Exception as exc:  # pragma: no cover - runtime dependency guard
            raise AiServiceError('AI_OCR_UNAVAILABLE', f'OCR engine is unavailable: {exc}', status_code=503) from exc
        self._engine = RapidOCR()
        return self._engine

    def extract_pdf_text(self, pdf_bytes: bytes) -> PdfOcrResult:
        if not pdf_bytes:
            raise AiServiceError('AI_OCR_INVALID_PDF', 'PDF payload is empty', status_code=400)
        try:
            import fitz
        except Exception as exc:  # pragma: no cover - runtime dependency guard
            raise AiServiceError('AI_OCR_UNAVAILABLE', f'PDF OCR dependency is unavailable: {exc}', status_code=503) from exc

        try:
            document = fitz.open(stream=pdf_bytes, filetype='pdf')
        except Exception as exc:
            raise AiServiceError('AI_OCR_INVALID_PDF', f'Failed to open PDF: {exc}', status_code=400) from exc

        page_texts: list[str] = []
        used_ocr = False
        try:
            for page in document:
                page_text, page_used_ocr = self._extract_page_text(page, fitz)
                page_texts.append(page_text)
                used_ocr = used_ocr or page_used_ocr
        finally:
            document.close()

        text = self._merge_page_texts(page_texts)
        if not text:
            raise AiServiceError(
                'AI_OCR_EMPTY_TEXT',
                'OCR completed but no readable text was extracted from the PDF',
                status_code=422,
            )
        return PdfOcrResult(
            text=text,
            page_texts=page_texts,
            page_count=len(page_texts),
            used_ocr=used_ocr,
            engine='rapidocr' if used_ocr else 'pymupdf-text',
        )

    def _extract_page_text(self, page, fitz_module) -> tuple[str, bool]:
        native_text = self._normalize_text(page.get_text('text'))
        if native_text:
            return native_text, False

        engine = self._get_engine()
        for scale in (2, 3):
            try:
                pix = page.get_pixmap(matrix=fitz_module.Matrix(scale, scale), alpha=False)
                result, _ = engine(pix.tobytes('png'))
            except Exception:
                continue

            ocr_text = self._normalize_ocr_result(result)
            if ocr_text:
                return ocr_text, True

        return '', True

    def _normalize_ocr_result(self, result: object) -> str:
        page_lines: list[str] = []
        for item in result or []:
            if not item or len(item) < 2:
                continue
            text = self._normalize_text(item[1])
            if text:
                page_lines.append(text)
        return '\n'.join(page_lines).strip()

    def _merge_page_texts(self, page_texts: list[str]) -> str:
        return '\n\n'.join(text for text in page_texts if text).strip()

    def _normalize_text(self, value: object) -> str:
        if value is None:
            return ''
        lines = [line.strip() for line in str(value).splitlines() if line.strip()]
        return '\n'.join(lines).strip()


ocr_service = OcrService()
