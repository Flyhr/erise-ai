from __future__ import annotations

import logging
import re
from dataclasses import dataclass, field
from io import BytesIO
from typing import Iterable

from src.app.core.exceptions import AiServiceError
from src.app.extractor import unstructured_adapter
from src.app.schemas.file_extract import FileCapabilityMatrixView, FileTypeCapabilityView
from src.app.schemas.rag import RagChunkPayload
from src.app.services.ocr_service import ocr_service


logger = logging.getLogger(__name__)


@dataclass(slots=True)
class SemanticBlock:
    text: str
    section_path: str | None
    is_heading: bool = False


@dataclass(slots=True)
class FileExtractResult:
    plain_text: str
    chunks: list[RagChunkPayload]
    parser: str
    used_ocr: bool
    page_count: int
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
    monitoring_tags: list[str] = field(default_factory=list)


@dataclass(slots=True)
class PrimaryExtractOutcome:
    result: FileExtractResult | None = None
    error: AiServiceError | None = None
    attempts: int = 0


class FileExtractService:
    PARSE_STATUS_SUCCESS = 'SUCCESS'
    PARSE_STATUS_FALLBACK = 'FALLBACK'
    PARSE_STATUS_FAILED = 'FAILED'
    PRIMARY_PARSER = 'unstructured'
    PRIMARY_MAX_ATTEMPTS = 2

    TARGET_CHUNK_SIZE = 900
    MAX_CHUNK_SIZE = 1200
    OVERLAP_SIZE = 120

    PARAGRAPH_SPLITTER = re.compile(r'\n\s*\n+')
    LINE_SPLITTER = re.compile(r'\n+')
    SENTENCE_SPLITTER = re.compile(r'(?<=[。！？?!；;])')
    CLAUSE_SPLITTER = re.compile(r'(?<=[，,:：])')
    WORD_SPLITTER = re.compile(r'\s+')
    MARKDOWN_CODE_BLOCK = re.compile(r'```[\s\S]*?```')
    MARKDOWN_IMAGE = re.compile(r'!\[[^\]]*]\([^)]*\)')
    MARKDOWN_LINK = re.compile(r'\[[^\]]*]\([^)]*\)')
    MARKDOWN_SYMBOL = re.compile(r'[#>*`_\-]')
    DOC_TEXT_RUN = re.compile(r'[\u4e00-\u9fffA-Za-z0-9][\u4e00-\u9fffA-Za-z0-9\s，。！？；：、“”‘’（）()《》【】\-_,.;:/]{11,}')

    def extract(self, file_bytes: bytes, file_name: str | None, file_ext: str | None) -> FileExtractResult:
        if not file_bytes:
            raise AiServiceError('AI_FILE_EMPTY', 'File payload is empty', status_code=400)

        extension = self._normalize_extension(file_ext, file_name)
        primary_outcome = self._extract_with_primary_error(file_bytes, file_name, extension)
        if primary_outcome is not None and primary_outcome.result is not None:
            return primary_outcome.result
        legacy_result = self._extract_with_legacy_parsers(file_bytes, extension)
        if primary_outcome is None or primary_outcome.error is None:
            return legacy_result
        return self._mark_fallback_result(legacy_result, primary_outcome.error, primary_outcome.attempts, extension)

    def _extract_with_primary_error(
        self,
        file_bytes: bytes,
        file_name: str | None,
        extension: str,
    ) -> PrimaryExtractOutcome | None:
        if not unstructured_adapter.supports(extension):
            return None
        last_error: AiServiceError | None = None
        attempts = 0
        for attempt in range(1, self.PRIMARY_MAX_ATTEMPTS + 1):
            attempts = attempt
            try:
                extracted = unstructured_adapter.extract(file_bytes, file_name, extension)
                return PrimaryExtractOutcome(
                    result=self._result_from_text(
                        extracted.plain_text,
                        parser=extracted.parser,
                        used_ocr=extracted.used_ocr,
                        page_count=extracted.page_count,
                        parse_status=self.PARSE_STATUS_SUCCESS,
                        primary_parser=self.PRIMARY_PARSER,
                        primary_attempts=attempt,
                        monitoring_tags=self._build_monitoring_tags(
                            extension=extension,
                            parser=extracted.parser,
                            parse_status=self.PARSE_STATUS_SUCCESS,
                            primary_parser=self.PRIMARY_PARSER,
                        ),
                    ),
                    attempts=attempt,
                )
            except AiServiceError as exc:
                last_error = exc
                stage, category = self._classify_primary_error(exc)
                if not self._should_retry_primary(exc, attempt):
                    logger.warning(
                        'file_extract_primary_failed parser=%s file_ext=%s attempt=%s/%s status_code=%s error_code=%s stage=%s category=%s message=%s',
                        self.PRIMARY_PARSER,
                        extension or 'unknown',
                        attempt,
                        self.PRIMARY_MAX_ATTEMPTS,
                        exc.status_code,
                        exc.error_code,
                        stage,
                        category,
                        exc.message,
                    )
                    break
                logger.warning(
                    'file_extract_primary_retry parser=%s file_ext=%s attempt=%s/%s status_code=%s error_code=%s stage=%s category=%s message=%s',
                    self.PRIMARY_PARSER,
                    extension or 'unknown',
                    attempt,
                    self.PRIMARY_MAX_ATTEMPTS,
                    exc.status_code,
                    exc.error_code,
                    stage,
                    category,
                    exc.message,
                )
            except Exception as exc:
                last_error = AiServiceError('AI_FILE_PARSE_FAILED', f'Unstructured parsing failed: {exc}', status_code=422)
                if not self._should_retry_primary(last_error, attempt):
                    break
                logger.warning(
                    'Primary extractor raised unexpected error for `%s` on attempt %s/%s, retrying: %s',
                    extension or 'unknown',
                    attempt,
                    self.PRIMARY_MAX_ATTEMPTS,
                    exc,
                )
        if last_error is not None:
            stage, category = self._classify_primary_error(last_error)
            logger.warning(
                'file_extract_fallback parser=%s file_ext=%s attempts=%s fallback_parser=legacy status_code=%s error_code=%s stage=%s category=%s message=%s',
                self.PRIMARY_PARSER,
                extension or 'unknown',
                attempts,
                last_error.status_code,
                last_error.error_code,
                stage,
                category,
                last_error.message,
            )
        return PrimaryExtractOutcome(error=last_error, attempts=attempts)

    def _should_retry_primary(self, exc: AiServiceError, attempt: int) -> bool:
        if attempt >= self.PRIMARY_MAX_ATTEMPTS:
            return False
        normalized = (exc.message or '').lower()
        return (
            exc.status_code >= 500
            and 'dependency is unavailable' not in normalized
        ) or any(token in normalized for token in ('timeout', 'temporarily unavailable', 'connection reset'))

    def _mark_fallback_result(
        self,
        result: FileExtractResult,
        primary_error: AiServiceError,
        primary_attempts: int,
        extension: str,
    ) -> FileExtractResult:
        primary_error_stage, primary_error_category = self._classify_primary_error(primary_error)
        return FileExtractResult(
            plain_text=result.plain_text,
            chunks=result.chunks,
            parser=result.parser,
            used_ocr=result.used_ocr,
            page_count=result.page_count,
            parse_status=self.PARSE_STATUS_FALLBACK,
            primary_parser=self.PRIMARY_PARSER,
            fallback_parser=result.parser,
            fallback_used=True,
            error_code=primary_error.error_code,
            error_message=primary_error.message,
            retryable=self._is_retryable_status(primary_error),
            primary_attempts=primary_attempts,
            primary_error_status_code=primary_error.status_code,
            primary_error_stage=primary_error_stage,
            primary_error_category=primary_error_category,
            fallback_reason=self._fallback_reason(primary_error_category),
            monitoring_tags=self._build_monitoring_tags(
                extension=extension,
                parser=result.parser,
                parse_status=self.PARSE_STATUS_FALLBACK,
                primary_parser=self.PRIMARY_PARSER,
                fallback_parser=result.parser,
                error_code=primary_error.error_code,
                error_category=primary_error_category,
            ),
        )

    def _is_retryable_status(self, exc: AiServiceError) -> bool:
        return exc.status_code >= 500 and exc.error_code != 'AI_FILE_UNAVAILABLE'

    def _extract_with_legacy_parsers(self, file_bytes: bytes, extension: str) -> FileExtractResult:
        if extension == 'pdf':
            return self._extract_pdf(file_bytes)
        if extension == 'docx':
            return self._extract_docx(file_bytes)
        if extension == 'doc':
            return self._extract_doc(file_bytes)
        if extension == 'txt':
            return self._extract_plain_text_file(file_bytes, parser='text-decoder')
        if extension in {'md', 'markdown'}:
            return self._extract_markdown_file(file_bytes)
        raise AiServiceError('AI_FILE_UNSUPPORTED', f'Unsupported file type: {extension or "unknown"}', status_code=400)

    def _extract_plain_text_file(self, file_bytes: bytes, parser: str) -> FileExtractResult:
        plain_text = self._decode_text_bytes(file_bytes)
        return self._result_from_text(plain_text, parser=parser)

    def _extract_markdown_file(self, file_bytes: bytes) -> FileExtractResult:
        markdown = self._decode_text_bytes(file_bytes)
        plain_text = self._strip_markdown(markdown)
        return self._result_from_text(plain_text, parser='markdown-decoder')

    def _extract_pdf(self, file_bytes: bytes) -> FileExtractResult:
        result = ocr_service.extract_pdf_text(file_bytes)
        cleaned_pages = self._clean_pdf_pages(result.page_texts)
        if not any(cleaned_pages):
            cleaned_pages = [self._normalize_text(page) for page in result.page_texts]

        chunks: list[RagChunkPayload] = []
        next_chunk_num = 0
        for page_no, page_text in enumerate(cleaned_pages, start=1):
            if not page_text:
                continue
            page_chunks = self._chunks_from_text(page_text, page_no=page_no)
            for chunk in page_chunks:
                chunks.append(chunk.model_copy(update={'chunk_num': next_chunk_num}))
                next_chunk_num += 1

        plain_text = '\n\n'.join(page for page in cleaned_pages if page).strip()
        if not plain_text:
            plain_text = self._normalize_text(result.text)
        if not plain_text:
            raise AiServiceError(
                'AI_FILE_PARSE_FAILED',
                'PDF parsing completed but no readable text was extracted',
                status_code=422,
            )
        if not chunks:
            chunks = self._chunks_from_text(plain_text)
        return FileExtractResult(
            plain_text=plain_text,
            chunks=chunks,
            parser='pymupdf-text+rapidocr' if result.used_ocr else 'pymupdf-text',
            used_ocr=result.used_ocr,
            page_count=result.page_count,
            parse_status=self.PARSE_STATUS_SUCCESS,
            primary_parser='legacy',
        )

    def _extract_docx(self, file_bytes: bytes) -> FileExtractResult:
        try:
            from docx import Document
        except Exception as exc:  # pragma: no cover - runtime dependency guard
            raise AiServiceError('AI_FILE_UNAVAILABLE', f'DOCX parser dependency is unavailable: {exc}', status_code=503) from exc

        try:
            document = Document(BytesIO(file_bytes))
        except Exception as exc:
            raise AiServiceError('AI_FILE_INVALID_DOCX', f'Failed to open DOCX: {exc}', status_code=400) from exc

        blocks = self._extract_docx_blocks(document)
        if not blocks:
            raise AiServiceError('AI_FILE_PARSE_FAILED', 'DOCX parsing produced no readable content', status_code=422)

        plain_text = '\n\n'.join(block.text for block in blocks if block.text).strip()
        chunks = self._chunks_from_blocks(blocks)
        if not chunks:
            raise AiServiceError('AI_FILE_PARSE_FAILED', 'DOCX chunking produced no readable content', status_code=422)
        return FileExtractResult(
            plain_text=plain_text,
            chunks=chunks,
            parser='python-docx',
            used_ocr=False,
            page_count=0,
            parse_status=self.PARSE_STATUS_SUCCESS,
            primary_parser='legacy',
        )

    def _extract_doc(self, file_bytes: bytes) -> FileExtractResult:
        try:
            import olefile
        except Exception as exc:  # pragma: no cover - runtime dependency guard
            raise AiServiceError('AI_FILE_UNAVAILABLE', f'DOC parser dependency is unavailable: {exc}', status_code=503) from exc

        try:
            ole = olefile.OleFileIO(BytesIO(file_bytes))
        except Exception as exc:
            raise AiServiceError('AI_FILE_INVALID_DOC', f'Failed to open DOC: {exc}', status_code=400) from exc

        try:
            candidates: list[str] = []
            for stream_path in ole.listdir(streams=True, storages=False):
                if stream_path and stream_path[0].startswith('\x05'):
                    continue
                try:
                    raw = ole.openstream(stream_path).read()
                except Exception:
                    continue
                candidates.extend(self._extract_doc_stream_candidates(raw))
        finally:
            ole.close()

        plain_text = self._best_doc_text(candidates)
        if not self._is_meaningful_text(plain_text):
            raise AiServiceError(
                'AI_FILE_PARSE_FAILED',
                'DOC parsing produced no readable text. Please convert the file to DOCX or PDF and retry.',
                status_code=422,
            )
        chunks = self._chunks_from_text(plain_text)
        if not chunks:
            raise AiServiceError('AI_FILE_PARSE_FAILED', 'DOC chunking produced no readable content', status_code=422)
        return FileExtractResult(
            plain_text=plain_text,
            chunks=chunks,
            parser='olefile',
            used_ocr=False,
            page_count=0,
            parse_status=self.PARSE_STATUS_SUCCESS,
            primary_parser='legacy',
        )

    def _result_from_text(
        self,
        plain_text: str,
        parser: str,
        *,
        used_ocr: bool = False,
        page_count: int = 0,
        parse_status: str = PARSE_STATUS_SUCCESS,
        primary_parser: str = 'legacy',
        primary_attempts: int = 0,
        monitoring_tags: list[str] | None = None,
    ) -> FileExtractResult:
        normalized = self._normalize_text(plain_text)
        if not normalized:
            raise AiServiceError('AI_FILE_PARSE_FAILED', 'No readable text content was extracted', status_code=422)
        chunks = self._chunks_from_text(normalized)
        if not chunks:
            raise AiServiceError('AI_FILE_PARSE_FAILED', 'No readable text content was chunked', status_code=422)
        return FileExtractResult(
            plain_text=normalized,
            chunks=chunks,
            parser=parser,
            used_ocr=used_ocr,
            page_count=page_count,
            parse_status=parse_status,
            primary_parser=primary_parser,
            primary_attempts=primary_attempts,
            monitoring_tags=list(monitoring_tags or []),
        )

    def _classify_primary_error(self, exc: AiServiceError) -> tuple[str, str]:
        normalized = (exc.message or '').lower()
        if exc.error_code == 'AI_FILE_UNAVAILABLE':
            if any(token in normalized for token in ('libmagic', 'tesseract', 'poppler', 'pdftoppm')):
                return 'runtime_dependency', 'runtime_dependency_missing'
            if any(token in normalized for token in ('no module named', 'modulenotfounderror', 'dependency is unavailable')):
                return 'dependency_load', 'python_dependency_missing'
            return 'dependency_load', 'parser_unavailable'
        if 'produced no readable content' in normalized or 'no readable text content' in normalized:
            return 'content_validation', 'empty_output'
        if exc.error_code == 'AI_FILE_PARSE_FAILED':
            return 'partition', 'parser_failed'
        if exc.error_code == 'AI_FILE_UNSUPPORTED':
            return 'compatibility', 'unsupported_type'
        return 'unknown', 'unknown_failure'

    def _fallback_reason(self, category: str) -> str:
        mapping = {
            'runtime_dependency_missing': 'primary_runtime_dependency_missing',
            'python_dependency_missing': 'primary_python_dependency_missing',
            'parser_unavailable': 'primary_parser_unavailable',
            'empty_output': 'primary_empty_output',
            'parser_failed': 'primary_parser_failed',
            'unsupported_type': 'primary_unsupported_type',
            'unknown_failure': 'primary_unknown_failure',
        }
        return mapping.get(category, 'primary_unknown_failure')

    def _build_monitoring_tags(
        self,
        *,
        extension: str,
        parser: str,
        parse_status: str,
        primary_parser: str,
        fallback_parser: str | None = None,
        error_code: str | None = None,
        error_category: str | None = None,
    ) -> list[str]:
        tags = [
            f'file_ext:{extension or "unknown"}',
            f'parse_status:{parse_status.lower()}',
            f'primary_parser:{primary_parser}',
            f'parser:{parser}',
        ]
        if fallback_parser:
            tags.append(f'fallback_parser:{fallback_parser}')
        if error_code:
            tags.append(f'primary_error_code:{error_code}')
        if error_category:
            tags.append(f'primary_error_category:{error_category}')
        return tags

    def capability_matrix(self) -> FileCapabilityMatrixView:
        statuses = [self.PARSE_STATUS_SUCCESS, self.PARSE_STATUS_FALLBACK, self.PARSE_STATUS_FAILED]
        return FileCapabilityMatrixView(
            parser_order=[self.PRIMARY_PARSER, 'legacy'],
            parse_statuses=statuses,
            file_types=[
                FileTypeCapabilityView(
                    extension='pdf',
                    label='PDF',
                    primary_parser=self.PRIMARY_PARSER,
                    fallback_parser='pymupdf-text+rapidocr',
                    supports_ocr=True,
                    supports_pages=True,
                    parse_statuses=statuses,
                    retry_policy='Retry primary parser for transient 5xx/timeout errors, then fallback to legacy PDF text/OCR parser.',
                    notes='Fallback preserves page numbers when possible.',
                ),
                FileTypeCapabilityView(
                    extension='docx',
                    label='Word DOCX',
                    primary_parser=self.PRIMARY_PARSER,
                    fallback_parser='python-docx',
                    supports_ocr=False,
                    supports_pages=False,
                    parse_statuses=statuses,
                    retry_policy='Retry primary parser for transient 5xx/timeout errors, then fallback to python-docx.',
                    notes='Fallback preserves heading-derived section paths.',
                ),
                FileTypeCapabilityView(
                    extension='txt',
                    label='Plain Text',
                    primary_parser=self.PRIMARY_PARSER,
                    fallback_parser='text-decoder',
                    supports_ocr=False,
                    supports_pages=False,
                    parse_statuses=statuses,
                    retry_policy='Retry primary parser for transient 5xx/timeout errors, then fallback to deterministic text decoding.',
                ),
                FileTypeCapabilityView(
                    extension='md',
                    label='Markdown',
                    primary_parser=self.PRIMARY_PARSER,
                    fallback_parser='markdown-decoder',
                    supports_ocr=False,
                    supports_pages=False,
                    parse_statuses=statuses,
                    retry_policy='Retry primary parser for transient 5xx/timeout errors, then fallback to markdown stripping.',
                    notes='`markdown` extension follows the same path as `md`.',
                ),
            ],
        )

    def chunk_text(self, plain_text: str, page_no: int | None = None) -> list[RagChunkPayload]:
        normalized = self._normalize_text(plain_text)
        if not normalized:
            return []
        return self._chunks_from_text(normalized, page_no=page_no)

    def _chunks_from_text(self, plain_text: str, page_no: int | None = None) -> list[RagChunkPayload]:
        blocks = self._merge_heading_blocks(self._build_semantic_blocks(plain_text))
        return self._build_chunks(blocks, page_no=page_no)

    def _chunks_from_blocks(self, blocks: list[SemanticBlock], page_no: int | None = None) -> list[RagChunkPayload]:
        merged = self._merge_heading_blocks(blocks)
        return self._build_chunks(merged, page_no=page_no)

    def _build_semantic_blocks(self, text: str) -> list[SemanticBlock]:
        normalized = self._normalize_text(text)
        if not normalized:
            return []
        blocks: list[SemanticBlock] = []
        current_section: str | None = None
        for raw_paragraph in self.PARAGRAPH_SPLITTER.split(normalized):
            paragraph = self._normalize_text(raw_paragraph)
            if not paragraph:
                continue
            if self._is_likely_heading(paragraph):
                current_section = paragraph
                blocks.append(SemanticBlock(text=paragraph, section_path=current_section, is_heading=True))
                continue
            self._split_recursively(paragraph, current_section, 0, blocks)
        if not blocks:
            blocks.append(SemanticBlock(text=normalized, section_path=None))
        return blocks

    def _merge_heading_blocks(self, blocks: list[SemanticBlock]) -> list[SemanticBlock]:
        if not blocks:
            return []
        merged: list[SemanticBlock] = []
        index = 0
        while index < len(blocks):
            current = blocks[index]
            if current.is_heading and len(current.text) <= 80 and index + 1 < len(blocks):
                following = blocks[index + 1]
                if current.section_path == following.section_path:
                    merged.append(
                        SemanticBlock(
                            text=f'{current.text}\n{following.text}',
                            section_path=following.section_path,
                            is_heading=False,
                        )
                    )
                    index += 2
                    continue
            merged.append(current)
            index += 1
        return merged

    def _split_recursively(
        self,
        text: str,
        section_path: str | None,
        level: int,
        output: list[SemanticBlock],
    ) -> None:
        normalized = self._normalize_text(text)
        if not normalized:
            return
        if len(normalized) <= self.MAX_CHUNK_SIZE:
            output.append(SemanticBlock(text=normalized, section_path=section_path))
            return
        split_plan = self._split_plan(level)
        if split_plan is None:
            self._hard_split(normalized, section_path, output)
            return

        pattern, joiner = split_plan
        parts = [self._normalize_text(part) for part in pattern.split(normalized)]
        parts = [part for part in parts if part]
        if len(parts) <= 1:
            self._split_recursively(normalized, section_path, level + 1, output)
            return

        buffer = ''
        for part in parts:
            if len(part) > self.MAX_CHUNK_SIZE:
                self._flush_buffer(output, buffer, section_path)
                buffer = ''
                self._split_recursively(part, section_path, level + 1, output)
                continue
            if not buffer:
                buffer = part
                continue
            candidate = f'{buffer}{joiner}{part}'
            if len(candidate) <= self.MAX_CHUNK_SIZE:
                buffer = candidate
                continue
            self._flush_buffer(output, buffer, section_path)
            buffer = part
        self._flush_buffer(output, buffer, section_path)

    def _flush_buffer(self, output: list[SemanticBlock], buffer: str, section_path: str | None) -> None:
        value = self._normalize_text(buffer)
        if value:
            output.append(SemanticBlock(text=value, section_path=section_path))

    def _split_plan(self, level: int) -> tuple[re.Pattern[str], str] | None:
        if level == 0:
            return self.PARAGRAPH_SPLITTER, '\n\n'
        if level == 1:
            return self.LINE_SPLITTER, '\n'
        if level == 2:
            return self.SENTENCE_SPLITTER, ' '
        if level == 3:
            return self.CLAUSE_SPLITTER, ' '
        if level == 4:
            return self.WORD_SPLITTER, ' '
        return None

    def _hard_split(self, text: str, section_path: str | None, output: list[SemanticBlock]) -> None:
        remaining = self._normalize_text(text)
        while remaining:
            if len(remaining) <= self.MAX_CHUNK_SIZE:
                output.append(SemanticBlock(text=remaining, section_path=section_path))
                return
            split_position = self._find_split_position(remaining)
            piece = self._normalize_text(remaining[:split_position])
            if not piece:
                piece = self._normalize_text(remaining[: self.MAX_CHUNK_SIZE])
                split_position = min(self.MAX_CHUNK_SIZE, len(remaining))
            if piece:
                output.append(SemanticBlock(text=piece, section_path=section_path))
            remaining = self._normalize_text(remaining[split_position:])

    def _build_chunks(self, blocks: list[SemanticBlock], page_no: int | None) -> list[RagChunkPayload]:
        if not blocks:
            return []
        chunks: list[RagChunkPayload] = []
        buffer = ''
        current_section: str | None = None
        for block in blocks:
            text = self._normalize_text(block.text)
            if not text:
                continue
            if not buffer:
                buffer = text
                current_section = block.section_path
                continue
            candidate = f'{buffer}\n\n{text}'
            if len(candidate) <= self.MAX_CHUNK_SIZE and (len(buffer) < self.TARGET_CHUNK_SIZE or len(text) < self.TARGET_CHUNK_SIZE // 2):
                buffer = candidate
                current_section = block.section_path or current_section
                continue

            self._append_chunk(chunks, buffer, page_no, current_section)
            overlap = self._overlap_tail(buffer)
            buffer = f'{overlap}\n{text}'.strip() if overlap else text
            current_section = block.section_path or current_section
            while len(buffer) > self.MAX_CHUNK_SIZE:
                split_position = self._find_split_position(buffer)
                piece = self._normalize_text(buffer[:split_position])
                self._append_chunk(chunks, piece, page_no, current_section)
                overlap_seed = self._overlap_tail(piece)
                remainder = self._normalize_text(buffer[split_position:])
                if overlap_seed and remainder:
                    buffer = f'{overlap_seed}\n{remainder}'
                else:
                    buffer = overlap_seed or remainder
        self._append_chunk(chunks, buffer, page_no, current_section)
        return [chunk.model_copy(update={'chunk_num': index}) for index, chunk in enumerate(chunks)]

    def _append_chunk(
        self,
        chunks: list[RagChunkPayload],
        raw_text: str,
        page_no: int | None,
        section_path: str | None,
    ) -> None:
        final_text = self._normalize_text(raw_text)
        if not final_text:
            return
        if chunks and chunks[-1].chunk_text == final_text:
            return
        chunks.append(
            RagChunkPayload(
                chunk_num=len(chunks),
                chunk_text=final_text,
                page_no=page_no,
                section_path=section_path,
            )
        )

    def _find_split_position(self, value: str) -> int:
        upper_bound = min(self.MAX_CHUNK_SIZE, len(value))
        lower_bound = min(self.TARGET_CHUNK_SIZE, upper_bound)
        for index in range(upper_bound, lower_bound - 1, -1):
            if self._is_boundary_character(value[index - 1]):
                return index
        return upper_bound

    def _overlap_tail(self, text: str) -> str:
        normalized = self._normalize_text(text)
        if not normalized:
            return ''
        if len(normalized) <= self.OVERLAP_SIZE:
            return normalized
        start = len(normalized) - self.OVERLAP_SIZE
        while start > 0 and not self._is_boundary_character(normalized[start - 1]):
            start -= 1
        return normalized[start:].strip()

    def _is_boundary_character(self, value: str) -> bool:
        return value.isspace() or value in '。！？?!；;，,:：)]}》】'

    def _is_likely_heading(self, paragraph: str) -> bool:
        normalized = self._normalize_text(paragraph)
        if not normalized or len(normalized) > 80:
            return False
        if re.match(r'^(#{1,6}\s+.+|第[0-9一二三四五六七八九十百]+[章节篇部卷].*|[0-9一二三四五六七八九十]+[.、)\s].+)$', normalized):
            return True
        return not re.search(r'[。！？?!；;.]', normalized) and len(normalized.split()) <= 8

    def _clean_pdf_pages(self, raw_page_texts: list[str]) -> list[str]:
        if not raw_page_texts:
            return []
        page_lines = [self._normalize_pdf_lines(page) for page in raw_page_texts]
        repeated_headers = self._detect_repeated_boundary_lines(page_lines, header=True)
        repeated_footers = self._detect_repeated_boundary_lines(page_lines, header=False)
        cleaned_pages: list[str] = []
        for lines in page_lines:
            filtered = [
                line
                for index, line in enumerate(lines)
                if not self._should_drop_pdf_line(line, index, len(lines), repeated_headers, repeated_footers)
            ]
            cleaned_pages.append(self._normalize_text('\n'.join(filtered)))
        return cleaned_pages

    def _normalize_pdf_lines(self, raw_page_text: str) -> list[str]:
        if not raw_page_text:
            return []
        lines: list[str] = []
        for raw_line in raw_page_text.replace('\x00', ' ').splitlines():
            normalized = self._normalize_line(raw_line)
            if normalized:
                lines.append(normalized)
        return lines

    def _detect_repeated_boundary_lines(self, page_lines: list[list[str]], header: bool) -> set[str]:
        populated_pages = [lines for lines in page_lines if lines]
        if len(populated_pages) < 2:
            return set()
        threshold = max(2, int(len(populated_pages) * 0.6 + 0.999))
        counts: dict[str, int] = {}
        for lines in populated_pages:
            candidates = lines[:2] if header else lines[-2:]
            unique_candidates = {
                candidate
                for candidate in candidates
                if len(candidate) <= 80 and not self._is_standalone_page_number(candidate)
            }
            for candidate in unique_candidates:
                counts[candidate] = counts.get(candidate, 0) + 1
        return {candidate for candidate, count in counts.items() if count >= threshold}

    def _should_drop_pdf_line(
        self,
        line: str,
        index: int,
        total_lines: int,
        repeated_headers: set[str],
        repeated_footers: set[str],
    ) -> bool:
        if not line or self._is_standalone_page_number(line):
            return True
        if index < 2 and line in repeated_headers:
            return True
        return index >= max(0, total_lines - 2) and line in repeated_footers

    def _is_standalone_page_number(self, line: str) -> bool:
        normalized = self._normalize_line(line).lower()
        return bool(
            re.match(r'^第\s*\d+\s*页\s*/\s*第\s*\d+\s*页$', normalized)
            or re.match(r'^page\s*\d+(?:\s*of\s*\d+)?$', normalized)
            or re.match(r'^[\-\u2013\u2014]?\s*\d+\s*[\-\u2013\u2014]?$', normalized)
            or re.match(r'^\d+\s*/\s*\d+$', normalized)
        )

    def _extract_docx_blocks(self, document) -> list[SemanticBlock]:
        blocks: list[SemanticBlock] = []
        heading_stack: list[str] = []
        for element in self._iter_docx_blocks(document):
            if element.__class__.__name__ == 'Paragraph':
                text = self._paragraph_plain_text(element)
                if not text:
                    continue
                heading_level = self._docx_heading_level(element, text)
                if heading_level is not None:
                    heading_stack = heading_stack[: max(0, heading_level - 1)]
                    heading_stack.append(text)
                    section_path = self._section_path(heading_stack)
                    blocks.append(SemanticBlock(text=text, section_path=section_path, is_heading=True))
                    continue
                blocks.extend(self._paragraph_blocks(text, self._section_path(heading_stack)))
                continue

            if element.__class__.__name__ == 'Table':
                section_path = self._section_path(heading_stack)
                for row in element.rows:
                    cells = [self._normalize_text(cell.text.replace('\n', ' ')) for cell in row.cells]
                    cells = [cell for cell in cells if cell]
                    if cells:
                        blocks.extend(self._paragraph_blocks(' | '.join(cells), section_path))
        return blocks

    def _iter_docx_blocks(self, document) -> Iterable[object]:
        from docx.document import Document as DocumentType
        from docx.oxml.table import CT_Tbl
        from docx.oxml.text.paragraph import CT_P
        from docx.table import Table
        from docx.text.paragraph import Paragraph

        parent = document.element.body if isinstance(document, DocumentType) else document._tc
        for child in parent.iterchildren():
            if isinstance(child, CT_P):
                yield Paragraph(child, document)
            elif isinstance(child, CT_Tbl):
                yield Table(child, document)

    def _paragraph_plain_text(self, paragraph) -> str:
        text = self._normalize_text(paragraph.text)
        if not text:
            return ''
        numbering = getattr(getattr(getattr(paragraph._p, 'pPr', None), 'numPr', None), 'numId', None)
        if numbering is not None and not text.startswith('- '):
            return f'- {text}'
        return text

    def _docx_heading_level(self, paragraph, text: str) -> int | None:
        style_name = ''
        try:
            style_name = paragraph.style.name or ''
        except Exception:
            style_name = ''
        match = re.match(r'heading\s*([1-6])$', style_name.strip(), re.IGNORECASE) or re.match(
            r'标题\s*([1-6])$',
            style_name.strip(),
            re.IGNORECASE,
        )
        if match:
            return int(match.group(1))
        if self._is_likely_heading(text):
            numbering_match = re.match(r'^(\d+(?:[.\-]\d+)*)', text)
            if numbering_match:
                return min(6, numbering_match.group(1).count('.') + numbering_match.group(1).count('-') + 1)
            return 1
        return None

    def _paragraph_blocks(self, text: str, section_path: str | None) -> list[SemanticBlock]:
        blocks: list[SemanticBlock] = []
        self._split_recursively(text, section_path, 0, blocks)
        return blocks

    def _section_path(self, heading_stack: list[str]) -> str | None:
        values = [value for value in heading_stack if value]
        return ' > '.join(values) if values else None

    def _extract_doc_stream_candidates(self, raw: bytes) -> list[str]:
        if not raw:
            return []
        candidates: list[str] = []
        for encoding in ('utf-16le', 'utf-8', 'gb18030', 'latin1'):
            try:
                decoded = raw.decode(encoding, errors='ignore')
            except Exception:
                continue
            candidates.extend(self._extract_readable_runs(decoded))
        return candidates

    def _extract_readable_runs(self, decoded: str) -> list[str]:
        if not decoded:
            return []
        cleaned = decoded.replace('\r', '\n').replace('\x00', ' ')
        runs = [self._normalize_text(match.group(0)) for match in self.DOC_TEXT_RUN.finditer(cleaned)]
        deduped: list[str] = []
        seen: set[str] = set()
        for run in runs:
            if not run or run in seen or not self._is_meaningful_text(run):
                continue
            seen.add(run)
            deduped.append(run)
        return deduped

    def _best_doc_text(self, candidates: list[str]) -> str:
        ranked = sorted(
            (self._normalize_text(candidate) for candidate in candidates),
            key=self._text_quality_score,
            reverse=True,
        )
        selected: list[str] = []
        for candidate in ranked:
            if not candidate or any(candidate in existing for existing in selected):
                continue
            if not self._is_meaningful_text(candidate):
                continue
            selected.append(candidate)
            if len(selected) >= 12 or len('\n\n'.join(selected)) >= 5000:
                break
        return self._normalize_text('\n\n'.join(selected))

    def _decode_text_bytes(self, file_bytes: bytes) -> str:
        if not file_bytes:
            return ''
        null_ratio = file_bytes.count(0) / max(1, len(file_bytes))
        encodings = ['utf-8-sig', 'utf-8', 'gb18030', 'big5', 'latin1']
        if file_bytes.startswith((b'\xff\xfe', b'\xfe\xff')) or null_ratio > 0.1:
            encodings = ['utf-16', 'utf-16le', 'utf-16be', 'utf-8-sig', 'utf-8', 'gb18030', 'latin1']

        candidates: list[str] = []
        for encoding in encodings:
            try:
                decoded = file_bytes.decode(encoding)
            except Exception:
                continue
            normalized = self._normalize_text(decoded)
            if not normalized:
                continue
            if encoding != 'latin1':
                return normalized
            candidates.append(normalized)
        if not candidates:
            return self._normalize_text(file_bytes.decode('utf-8', errors='ignore'))
        return max(candidates, key=self._text_quality_score)

    def _text_quality_score(self, value: str) -> tuple[int, int]:
        normalized = self._normalize_text(value)
        return self._meaningful_char_count(normalized), len(normalized)

    def _is_meaningful_text(self, value: str) -> bool:
        normalized = self._normalize_text(value)
        if not normalized:
            return False
        compact = ''.join(normalized.split())
        if len(compact) >= 48:
            return True
        if len(compact) < 24:
            return False
        return self._meaningful_char_count(compact) >= max(12, round(len(compact) * 0.45))

    def _meaningful_char_count(self, value: str) -> int:
        return sum(1 for char in value if char.isalnum() or self._is_cjk(char))

    def _is_cjk(self, char: str) -> bool:
        code_point = ord(char)
        return (
            0x3400 <= code_point <= 0x4DBF
            or 0x4E00 <= code_point <= 0x9FFF
            or 0xF900 <= code_point <= 0xFAFF
        )

    def _normalize_extension(self, file_ext: str | None, file_name: str | None) -> str:
        candidate = (file_ext or '').strip().lower().lstrip('.')
        if candidate:
            return candidate
        if not file_name or '.' not in file_name:
            return ''
        return file_name.rsplit('.', 1)[-1].strip().lower()

    def _normalize_line(self, value: str) -> str:
        if value is None:
            return ''
        return re.sub(r'\s+', ' ', str(value).replace('\u00A0', ' ')).strip()

    def _normalize_text(self, value: str | None) -> str:
        if value is None:
            return ''
        normalized = (
            str(value)
            .replace('\r', '')
            .replace('\u00A0', ' ')
        )
        normalized = re.sub(r'[ \t\x0B\f]+', ' ', normalized)
        normalized = re.sub(r'\n{3,}', '\n\n', normalized)
        return normalized.strip()

    def _strip_markdown(self, markdown: str) -> str:
        stripped = self.MARKDOWN_CODE_BLOCK.sub(' ', markdown)
        stripped = self.MARKDOWN_IMAGE.sub(' ', stripped)
        stripped = self.MARKDOWN_LINK.sub(' ', stripped)
        stripped = self.MARKDOWN_SYMBOL.sub(' ', stripped)
        stripped = re.sub(r'\s+', ' ', stripped)
        return stripped.strip()


file_extract_service = FileExtractService()
