from __future__ import annotations

import unittest
from unittest.mock import AsyncMock, patch

from fastapi.testclient import TestClient

from src.app.api.deps import RequestContext
from src.app.schemas.message import CitationView
from src.app.services.rag_service import RetrievalDecision

from tests.support import FakeAdapter, app, fake_model, request_headers, reset_database


def _retrieval_decision() -> RetrievalDecision:
    citation = CitationView(
        source_type='DOCUMENT',
        source_id=101,
        source_title='Project Charter',
        snippet='The project must complete security review before release.',
        page_no=2,
        section_path='Release Gate',
        score=0.91,
        url='/documents/101/edit?mode=preview&pageNo=2',
    )
    return RetrievalDecision(
        answer_source='PRIVATE_KNOWLEDGE',
        citations=[citation],
        used_tools=['private_knowledge'],
        confidence=0.91,
        context_messages=[{'role': 'system', 'content': 'Use cited project knowledge.'}],
        rewritten_queries=['release security review'],
        rewrite_hints=['focused project query'],
    )


class AgentApiTest(unittest.TestCase):
    def setUp(self) -> None:
        reset_database()

    def test_project_qa_agent_runs_end_to_end(self) -> None:
        with TestClient(app) as client:
            with (
                patch('src.app.services.agent_graph_service.fetch_project_context', new=AsyncMock(return_value={'id': 88, 'name': 'Apollo', 'description': 'Ship the knowledge platform'})),
                patch('src.app.services.agent_graph_service.rag_service.query', new=AsyncMock(return_value=_retrieval_decision())),
                patch('src.app.services.agent_graph_service.get_model_config', return_value=fake_model(model_code='qwen2.5:7b', provider_code='OLLAMA')),
                patch(
                    'src.app.services.agent_graph_service.get_model_adapter',
                    return_value=FakeAdapter(
                        answer='项目要求在发布前完成安全评审。',
                        provider_code='OLLAMA',
                        model_code='qwen2.5:7b',
                    ),
                ),
            ):
                response = client.post(
                    '/internal/ai/chat/agents/run',
                    headers=request_headers('agent-project-qa-1'),
                    json={
                        'agentType': 'project_qa',
                        'message': '这个项目发布前要完成什么？',
                        'context': {'projectId': 88, 'attachments': []},
                    },
                )

        self.assertEqual(200, response.status_code, response.text)
        payload = response.json()['data']
        self.assertEqual('project_qa', payload['agentType'])
        self.assertEqual('OLLAMA', payload['providerCode'])
        self.assertFalse(payload['fallbackUsed'])
        self.assertIn('agent.project_qa', payload['usedTools'])
        self.assertIn('project_qa.retrieve', payload['executionTrace'])

    def test_document_compare_agent_runs_end_to_end(self) -> None:
        attachment_a = type('LoadedAttachmentContext', (), {
            'attachment_type': 'DOCUMENT',
            'source_id': 11,
            'title': 'Spec A',
            'summary': 'Spec A summary',
            'plain_text': 'Spec A plain text',
            'snippet': '',
            'is_ready': True,
        })()
        attachment_b = type('LoadedAttachmentContext', (), {
            'attachment_type': 'DOCUMENT',
            'source_id': 12,
            'title': 'Spec B',
            'summary': 'Spec B summary',
            'plain_text': 'Spec B plain text',
            'snippet': '',
            'is_ready': True,
        })()
        with TestClient(app) as client:
            with (
                patch('src.app.services.agent_graph_service.load_attachment_contexts', new=AsyncMock(return_value=[attachment_a, attachment_b])),
                patch('src.app.services.agent_graph_service.get_model_config', return_value=fake_model()),
                patch(
                    'src.app.services.agent_graph_service.get_model_adapter',
                    return_value=FakeAdapter(
                        answer='文档 A 和文档 B 在发布时间上存在差异。',
                        provider_code='OPENAI',
                        model_code='gpt-4.1-mini',
                    ),
                ),
            ):
                response = client.post(
                    '/internal/ai/chat/agents/run',
                    headers=request_headers('agent-doc-compare-1'),
                    json={
                        'agentType': 'document_summary_compare',
                        'message': '比较这两份文档的差异',
                        'context': {
                            'attachments': [
                                {'attachmentType': 'DOCUMENT', 'sourceId': 11, 'projectId': 55, 'title': 'Spec A'},
                                {'attachmentType': 'DOCUMENT', 'sourceId': 12, 'projectId': 55, 'title': 'Spec B'},
                            ]
                        },
                    },
                )

        self.assertEqual(200, response.status_code, response.text)
        payload = response.json()['data']
        self.assertEqual('document_summary_compare', payload['agentType'])
        self.assertEqual(2, len(payload['citations']))
        self.assertIn('agent.document_compare', payload['usedTools'])
        self.assertIn('document_summary_compare.compose', payload['executionTrace'])

    def test_agent_falls_back_to_chat_service_when_graph_fails(self) -> None:
        fallback = type(
            'FallbackView',
            (),
            {
                'request_id': 'fallback-1',
                'answer': 'fallback answer',
                'citations': [],
                'used_tools': ['general_knowledge'],
                'provider_code': 'OPENAI',
                'model_code': 'gpt-4.1-mini',
                'confidence': None,
                'latency_ms': 123,
                'usage': type('Usage', (), {'prompt_tokens': 1, 'completion_tokens': 1, 'total_tokens': 2})(),
            },
        )()
        with TestClient(app) as client:
            with (
                patch('src.app.services.agent_graph_service.fetch_project_context', new=AsyncMock(side_effect=RuntimeError('project service unavailable'))),
                patch('src.app.services.chat_service.chat_service.complete', new=AsyncMock(return_value=fallback)),
            ):
                response = client.post(
                    '/internal/ai/chat/agents/run',
                    headers=request_headers('agent-fallback-1'),
                    json={
                        'agentType': 'project_qa',
                        'message': '这个项目发布前要完成什么？',
                        'context': {'projectId': 88, 'attachments': []},
                    },
                )

        self.assertEqual(200, response.status_code, response.text)
        payload = response.json()['data']
        self.assertTrue(payload['fallbackUsed'])
        self.assertEqual('OPENAI', payload['providerCode'])
        self.assertIn('agent_graph_fallback', payload['usedTools'])
        self.assertIn('agent.fallback.chat_service', payload['executionTrace'])

    def test_agent_logs_dependency_missing_when_langgraph_is_unavailable(self) -> None:
        with TestClient(app) as client:
            with (
                patch('src.app.services.agent_graph_service.LANGGRAPH_AVAILABLE', False),
                patch('src.app.services.agent_graph_service.LANGGRAPH_IMPORT_ERROR', 'ModuleNotFoundError: No module named langgraph'),
                patch('src.app.services.agent_graph_service.logger.info') as info_log,
                patch('src.app.services.agent_graph_service.fetch_project_context', new=AsyncMock(return_value={'id': 88, 'name': 'Apollo', 'description': 'Ship the knowledge platform'})),
                patch('src.app.services.agent_graph_service.rag_service.query', new=AsyncMock(return_value=_retrieval_decision())),
                patch('src.app.services.agent_graph_service.get_model_config', return_value=fake_model(model_code='qwen2.5:7b', provider_code='OLLAMA')),
                patch(
                    'src.app.services.agent_graph_service.get_model_adapter',
                    return_value=FakeAdapter(
                        answer='项目要求在发布前完成安全评审。',
                        provider_code='OLLAMA',
                        model_code='qwen2.5:7b',
                    ),
                ),
            ):
                response = client.post(
                    '/internal/ai/chat/agents/run',
                    headers=request_headers('agent-linear-fallback-1'),
                    json={
                        'agentType': 'project_qa',
                        'message': '这个项目发布前要完成什么？',
                        'context': {'projectId': 88, 'attachments': []},
                    },
                )

        self.assertEqual(200, response.status_code, response.text)
        payload = response.json()['data']
        self.assertIn('agent.orchestration.linear.dependency_missing', payload['executionTrace'])
        info_log.assert_called()
        self.assertIn('reason=dependency_missing', info_log.call_args.args[0])

    def test_agent_logs_runtime_failure_before_linear_fallback(self) -> None:
        with TestClient(app) as client:
            with (
                patch('src.app.services.agent_graph_service.LANGGRAPH_AVAILABLE', True),
                patch('src.app.services.agent_graph_service.AgentGraphService._run_langgraph', new=AsyncMock(side_effect=RuntimeError('graph execution failed'))),
                patch('src.app.services.agent_graph_service.logger.warning') as warning_log,
                patch('src.app.services.agent_graph_service.fetch_project_context', new=AsyncMock(return_value={'id': 88, 'name': 'Apollo', 'description': 'Ship the knowledge platform'})),
                patch('src.app.services.agent_graph_service.rag_service.query', new=AsyncMock(return_value=_retrieval_decision())),
                patch('src.app.services.agent_graph_service.get_model_config', return_value=fake_model(model_code='qwen2.5:7b', provider_code='OLLAMA')),
                patch(
                    'src.app.services.agent_graph_service.get_model_adapter',
                    return_value=FakeAdapter(
                        answer='项目要求在发布前完成安全评审。',
                        provider_code='OLLAMA',
                        model_code='qwen2.5:7b',
                    ),
                ),
            ):
                response = client.post(
                    '/internal/ai/chat/agents/run',
                    headers=request_headers('agent-linear-fallback-2'),
                    json={
                        'agentType': 'project_qa',
                        'message': '这个项目发布前要完成什么？',
                        'context': {'projectId': 88, 'attachments': []},
                    },
                )

        self.assertEqual(200, response.status_code, response.text)
        payload = response.json()['data']
        self.assertIn('agent.orchestration.linear.runtime_failure', payload['executionTrace'])
        warning_log.assert_called()
        self.assertIn('reason=runtime_failure', warning_log.call_args.args[0])


if __name__ == '__main__':
    unittest.main()
