from __future__ import annotations

import unittest
from types import SimpleNamespace

from src.app.schemas.chat import ChatCompletionRequest, ChatContext
from src.app.services.rag_service import rag_service


class RagMetadataTest(unittest.TestCase):
    def test_response_to_hits_preserves_unified_metadata_and_citation_url(self) -> None:
        point = SimpleNamespace(
            score=0.91,
            payload={
                'source_type': 'DOCUMENT',
                'source_id': 101,
                'source_name': 'Requirements Doc',
                'chunk_text': 'The release gate requires a security review.',
                'page_no': 4,
                'section_path': 'Release > Security',
                'project_id': 88,
                'session_id': None,
                'chunk_num': 7,
                'chunk_id': 'DOCUMENT:101:7',
                'chunk_hash': 'abc123',
                'task_id': 'task-index-1',
                'source_version': 'v3',
                'metadata': {'source_language': 'en'},
            },
        )

        hit = rag_service._response_to_hits([point])[0]
        citation = rag_service._to_citation(hit)

        self.assertEqual('DOCUMENT:101:7', hit.chunk_id)
        self.assertEqual('abc123', hit.chunk_hash)
        self.assertEqual('task-index-1', hit.task_id)
        self.assertEqual('v3', hit.source_version)
        self.assertEqual({'source_language': 'en'}, hit.metadata)
        self.assertEqual('/documents/101/edit?mode=preview&pageNo=4&sectionPath=Release > Security&chunkNum=7', citation.url)

    def test_project_filter_uses_user_and_project_scope(self) -> None:
        request = ChatCompletionRequest(message='release gate', context=ChatContext(project_id=88))
        queries = rag_service._vector_queries(request, SimpleNamespace(user_id=1), mode='GENERAL')

        self.assertEqual(1, len(queries))
        query_filter = queries[0][1]
        fields = {condition.key: condition.match for condition in query_filter.must}
        self.assertIn('user_id', fields)
        self.assertIn('project_id', fields)


if __name__ == '__main__':
    unittest.main()
