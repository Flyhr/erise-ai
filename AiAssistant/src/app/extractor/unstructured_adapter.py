from __future__ import annotations

from dataclasses import dataclass
from io import BytesIO
from typing import Any

from src.app.core.exceptions import AiServiceError


@dataclass(slots=True)
class UnstructuredExtractResult:
    plain_text: str
    parser: str
    page_count: int
    used_ocr: bool


class UnstructuredAdapter:
    SUPPORTED_EXTENSIONS = {'doc', 'docx', 'pdf'}
    RUNTIME_DEPENDENCY_HINTS = {
        'libmagic': 'Install the libmagic runtime package in the container image.',
        'magic': 'Install the libmagic runtime package in the container image.',
        'tesseract': 'Install Tesseract OCR and the required language packs in the container image.',
        'poppler': 'Install poppler-utils in the container image.',
        'pdftoppm': 'Install poppler-utils in the container image.',
        'no module named': 'Install the Python Unstructured dependency chain from requirements.txt.',
        'modulenotfounderror': 'Install the Python Unstructured dependency chain from requirements.txt.',
    }

    def supports(self, extension: str) -> bool:
        return extension in self.SUPPORTED_EXTENSIONS

    def runtime_status(self) -> tuple[str, str | None, str | None]:
        try:
            self._load_partition()
        except AiServiceError as exc:
            return 'DOWN', exc.error_code, exc.message
        return 'UP', None, 'Unstructured primary parser is available'

    def extract(
        self,
        file_bytes: bytes,
        file_name: str | None,
        extension: str,
    ) -> UnstructuredExtractResult:
        if not file_bytes:
            raise AiServiceError('AI_FILE_EMPTY', 'File payload is empty', status_code=400)
        if not self.supports(extension):
            raise AiServiceError(
                'AI_FILE_UNSUPPORTED',
                f'Unsupported file type for unstructured parser: {extension or "unknown"}',
                status_code=400,
            )

        partition = self._load_partition()
        file_like = BytesIO(file_bytes)
        kwargs: dict[str, Any] = {'file': file_like}
        if file_name:
            kwargs['file_filename'] = file_name

        try:
            elements = partition(**kwargs)
        except Exception as exc:
            dependency_hint = self._runtime_dependency_hint(exc)
            if dependency_hint:
                raise AiServiceError(
                    'AI_FILE_UNAVAILABLE',
                    f'Unstructured runtime dependency is unavailable: {exc}. {dependency_hint}',
                    status_code=503,
                ) from exc
            raise AiServiceError(
                'AI_FILE_PARSE_FAILED',
                f'Unstructured parsing failed during partition stage: {exc}',
                status_code=422,
            ) from exc

        plain_text = self._elements_to_text(elements)
        if not plain_text:
            raise AiServiceError('AI_FILE_PARSE_FAILED', 'Unstructured parsing produced no readable content', status_code=422)

        used_ocr = self._detect_used_ocr(elements)
        page_count = self._detect_page_count(elements)
        return UnstructuredExtractResult(
            plain_text=plain_text,
            parser='unstructured+ocr' if used_ocr else 'unstructured',
            page_count=page_count,
            used_ocr=used_ocr,
        )

    def _load_partition(self):
        try:
            from unstructured.partition.auto import partition
        except Exception as exc:
            raise AiServiceError(
                'AI_FILE_UNAVAILABLE',
                f'Unstructured parser dependency is unavailable: {exc}',
                status_code=503,
            ) from exc
        return partition

    def _runtime_dependency_hint(self, exc: Exception) -> str | None:
        normalized = f'{type(exc).__name__}: {exc}'.lower()
        for token, hint in self.RUNTIME_DEPENDENCY_HINTS.items():
            if token in normalized:
                return hint
        return None

    def _elements_to_text(self, elements: list[object]) -> str:
        texts: list[str] = []
        for element in elements or []:
            text = self._normalize_text(getattr(element, 'text', ''))
            if text:
                texts.append(text)
        return '\n\n'.join(texts).strip()

    def _detect_page_count(self, elements: list[object]) -> int:
        page_numbers: set[int] = set()
        for element in elements or []:
            metadata = self._metadata_dict(element)
            page_number = metadata.get('page_number')
            if isinstance(page_number, int) and page_number > 0:
                page_numbers.add(page_number)
        return len(page_numbers)

    def _detect_used_ocr(self, elements: list[object]) -> bool:
        for element in elements or []:
            metadata = self._metadata_dict(element)
            haystack = ' '.join(str(value).lower() for value in metadata.values() if value is not None)
            if 'ocr' in haystack:
                return True
        return False

    def _metadata_dict(self, element: object) -> dict[str, Any]:
        metadata = getattr(element, 'metadata', None)
        if metadata is None:
            return {}
        to_dict = getattr(metadata, 'to_dict', None)
        if callable(to_dict):
            try:
                payload = to_dict()
                if isinstance(payload, dict):
                    return payload
            except Exception:
                return {}
        if isinstance(metadata, dict):
            return metadata
        payload: dict[str, Any] = {}
        for key in dir(metadata):
            if key.startswith('_'):
                continue
            try:
                value = getattr(metadata, key)
            except Exception:
                continue
            if callable(value):
                continue
            payload[key] = value
        return payload

    def _normalize_text(self, value: str | None) -> str:
        if value is None:
            return ''
        return ' '.join(str(value).replace('\r', '\n').split()).strip()


unstructured_adapter = UnstructuredAdapter()
