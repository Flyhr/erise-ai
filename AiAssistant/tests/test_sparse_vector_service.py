from __future__ import annotations

import unittest

from src.app.services.sparse_vector_service import sparse_vector_service


class SparseVectorServiceTest(unittest.TestCase):
    def test_document_and_query_vectors_share_common_term_index(self) -> None:
        document = sparse_vector_service.build_document_vector(
            source_title='Aomei 分区助手',
            section_path='故障排查',
            chunk_text='C盘空间不足导致分区失败，重启 PE 模式后仍然报错。',
        )
        query = sparse_vector_service.build_query_vector('分区失败 PE 模式 报错')

        self.assertTrue(document.indices)
        self.assertTrue(query.indices)
        self.assertTrue(set(document.indices).intersection(query.indices))

    def test_query_vector_limits_term_count(self) -> None:
        query = ' '.join(f'term{i}' for i in range(100))
        vector = sparse_vector_service.build_query_vector(query)

        self.assertLessEqual(len(vector.indices), sparse_vector_service.MAX_QUERY_TERMS)


if __name__ == '__main__':
    unittest.main()
