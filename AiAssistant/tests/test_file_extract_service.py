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
            result = file_extract_service.extract(b'primary text', 'notes.txt', 'txt')

        self.assertEqual('SUCCESS', result.parse_status)
        self.assertEqual('unstructured', result.primary_parser)
        self.assertEqual('unstructured', result.parser)
        self.assertFalse(result.fallback_used)
        self.assertIsNone(result.error_code)
        self.assertEqual(1, result.primary_attempts)
        self.assertEqual(['file_ext:txt', 'parse_status:success', 'primary_parser:unstructured', 'parser:unstructured'], result.monitoring_tags)
        self.assertGreaterEqual(len(result.chunks), 1)

    def test_legacy_fallback_exposes_primary_error(self) -> None:
        with patch(
            'src.app.services.file_extract_service.unstructured_adapter.extract',
            side_effect=AiServiceError(
                'AI_FILE_UNAVAILABLE',
                'Unstructured parser dependency is unavailable',
                status_code=503,
            ),
        ):
            result = file_extract_service.extract('fallback text'.encode('utf-8'), 'notes.txt', 'txt')

        self.assertEqual('FALLBACK', result.parse_status)
        self.assertEqual('unstructured', result.primary_parser)
        self.assertEqual('text-decoder', result.parser)
        self.assertEqual('text-decoder', result.fallback_parser)
        self.assertTrue(result.fallback_used)
        self.assertEqual('AI_FILE_UNAVAILABLE', result.error_code)
        self.assertIn('Unstructured parser dependency', result.error_message or '')
        self.assertFalse(result.retryable)
        self.assertEqual(1, result.primary_attempts)
        self.assertEqual(503, result.primary_error_status_code)
        self.assertEqual('dependency_load', result.primary_error_stage)
        self.assertEqual('python_dependency_missing', result.primary_error_category)
        self.assertEqual('primary_python_dependency_missing', result.fallback_reason)
        self.assertIn('primary_error_code:AI_FILE_UNAVAILABLE', result.monitoring_tags)
        self.assertIn('primary_error_category:python_dependency_missing', result.monitoring_tags)

    def test_capability_matrix_covers_required_types(self) -> None:
        matrix = file_extract_service.capability_matrix()
        extensions = {item.extension for item in matrix.file_types}

        self.assertEqual(['unstructured', 'legacy'], matrix.parser_order)
        self.assertTrue({'pdf', 'docx', 'txt', 'md'}.issubset(extensions))
        self.assertIn('SUCCESS', matrix.parse_statuses)
        self.assertIn('FALLBACK', matrix.parse_statuses)
        self.assertIn('FAILED', matrix.parse_statuses)


if __name__ == '__main__':
    unittest.main()
