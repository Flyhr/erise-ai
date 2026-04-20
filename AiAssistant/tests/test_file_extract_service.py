from __future__ import annotations

import unittest
from unittest.mock import patch

from src.app.core.exceptions import AiServiceError
from src.app.extractor import UnstructuredExtractResult
from src.app.services.file_extract_service import file_extract_service


class FileExtractServiceTest(unittest.TestCase):
    def test_unstructured_primary_result_schema(self) -> None:
        with patch(
            'src.app.services.file_extract_service.unstructured_adapter.extract',
            return_value=UnstructuredExtractResult(
                plain_text='Primary parser text for smoke validation.',
                parser='unstructured',
                page_count=0,
                used_ocr=False,
            ),
        ):
            result = file_extract_service.extract(b'primary text', 'notes.docx', 'docx')

        self.assertEqual('SUCCESS', result.parse_status)
        self.assertEqual('unstructured', result.primary_parser)
        self.assertEqual('unstructured', result.parser)
        self.assertFalse(result.fallback_used)
        self.assertIsNone(result.error_code)
        self.assertEqual(1, result.primary_attempts)
        self.assertEqual(['file_ext:docx', 'parse_status:success', 'primary_parser:unstructured', 'parser:unstructured'], result.monitoring_tags)
        self.assertGreaterEqual(len(result.chunks), 1)

    def test_txt_bypasses_unstructured_and_uses_lightweight_decoder(self) -> None:
        with patch(
            'src.app.services.file_extract_service.unstructured_adapter.extract',
            side_effect=AiServiceError(
                'AI_FILE_UNAVAILABLE',
                'Unstructured parser dependency is unavailable',
                status_code=503,
            ),
        ) as mocked_extract:
            result = file_extract_service.extract('fallback text'.encode('utf-8'), 'notes.txt', 'txt')

        mocked_extract.assert_not_called()
        self.assertEqual('SUCCESS', result.parse_status)
        self.assertEqual('legacy', result.primary_parser)
        self.assertEqual('text-decoder', result.parser)
        self.assertIsNone(result.fallback_parser)
        self.assertFalse(result.fallback_used)
        self.assertIsNone(result.error_code)
        self.assertIsNone(result.error_message)
        self.assertFalse(result.retryable)
        self.assertEqual(0, result.primary_attempts)
        self.assertIsNone(result.primary_error_status_code)
        self.assertIsNone(result.primary_error_stage)
        self.assertIsNone(result.primary_error_category)
        self.assertIsNone(result.fallback_reason)

    def test_large_txt_uses_coarser_chunks(self) -> None:
        result = file_extract_service.extract(('abcdefghij ' * 450000).encode('utf-8'), 'large.txt', 'txt')

        self.assertLess(len(result.chunks), 500)
        self.assertGreater(len(result.chunks), 100)
        self.assertTrue(all(len(chunk.chunk_text) <= file_extract_service.PLAIN_TEXT_COARSE_MAX_CHUNK_SIZE for chunk in result.chunks))

    def test_capability_matrix_covers_required_types(self) -> None:
        matrix = file_extract_service.capability_matrix()
        extensions = {item.extension for item in matrix.file_types}
        runtime_codes = {item.parser_code for item in matrix.parser_runtimes}

        self.assertEqual(['unstructured', 'legacy', 'text-decoder', 'markdown-decoder'], matrix.parser_order)
        self.assertTrue({'pdf', 'docx', 'txt', 'md'}.issubset(extensions))
        self.assertIn('SUCCESS', matrix.parse_statuses)
        self.assertIn('FALLBACK', matrix.parse_statuses)
        self.assertIn('FAILED', matrix.parse_statuses)
        self.assertTrue({'unstructured', 'rapidocr', 'text-decoder', 'markdown-decoder'}.issubset(runtime_codes))


if __name__ == '__main__':
    unittest.main()
