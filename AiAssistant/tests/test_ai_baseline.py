from __future__ import annotations

import unittest
from unittest.mock import AsyncMock, patch

from fastapi.testclient import TestClient

from src.app.api.deps import RequestContext
from src.app.adapters.llm.base import AdapterStreamEvent, AdapterUsage
from src.app.models.ai_request_log import AiRequestLog
from src.app.schemas.chat import ChatCompletionRequest, ChatContext
from src.app.schemas.message import CitationView
from src.app.schemas.rag import RagQueryHit, RagQueryResponse
from src.app.services.rag_service import RetrievalDecision

from tests.support import (
    FakeAdapter,
    FakeRedisClient,
    InMemoryCancellationStore,
    SessionLocal,
    app,
    chat_service,
    fake_model,
    request_headers,
    reset_database,
)


def _retrieval_decision() -> RetrievalDecision:
    citation = CitationView(
        source_type='DOCUMENT',
        source_id=101,
        source_title='测试文档',
        snippet='这是用于回归测试的知识片段',
        page_no=1,
        section_path='第一章',
        score=0.93,
        url=None,
    )
    return RetrievalDecision(
        answer_source='PRIVATE_KNOWLEDGE',
        citations=[citation],
        used_tools=['private_knowledge'],
        confidence=0.93,
        context_messages=[{'role': 'system', 'content': '请结合已检索到的知识回答。'}],
        rewritten_queries=['请总结这份资料', '资料 摘要 主要内容'],
        rewrite_hints=['生成了聚焦检索意图的重写查询'],
    )


def _rag_debug_response() -> RagQueryResponse:
    return RagQueryResponse(
        hits=[
            RagQueryHit(
                score=0.93,
                source_type='DOCUMENT',
                source_id=101,
                source_title='测试文档',
                snippet='这是用于回归测试的知识片段',
                page_no=1,
                section_path='第一章',
                url=None,
            )
        ],
        citations=_retrieval_decision().citations,
        confidence=0.93,
        answer_source='PRIVATE_KNOWLEDGE',
        used_tools=['private_knowledge'],
    )


class AiBaselineApiTest(unittest.TestCase):
    def setUp(self) -> None:
        reset_database()

    def test_health_and_model_list(self) -> None:
        with patch('src.app.api.v1.health.Redis.from_url', return_value=FakeRedisClient()):
            with TestClient(app) as client:
                health = client.get('/internal/ai/chat/health')
                self.assertEqual(200, health.status_code, health.text)
                payload = health.json()
                self.assertEqual(0, payload['code'], payload)
                self.assertEqual('UP', payload['data']['database'], payload)
                self.assertEqual('UP', payload['data']['redis'], payload)

                models = client.get('/internal/ai/chat/models', headers=request_headers('models-list'))
                self.assertEqual(200, models.status_code, models.text)
                data = models.json()['data']
                self.assertGreaterEqual(len(data), 1, data)
                self.assertTrue(any(item['modelCode'] == 'gpt-4.1-mini' for item in data), data)

    def test_session_create_message_query_and_chat_completion_logs_request_fields(self) -> None:
        with TestClient(app) as client:
            create = client.post(
                '/internal/ai/chat/sessions',
                headers=request_headers('create-session'),
                json={'projectId': 55, 'scene': 'project_chat', 'title': 'P0 回归会话'},
            )
            self.assertEqual(200, create.status_code, create.text)
            session_id = create.json()['data']['id']

            adapter = FakeAdapter(answer='这是普通对话回答')
            with (
                patch('src.app.services.chat_service.action_service.execute', new=AsyncMock(return_value=None)),
                patch('src.app.services.chat_service.load_attachment_contexts', new=AsyncMock(return_value=[])),
                patch('src.app.services.chat_service.build_prompt_messages', new=AsyncMock(return_value=[{'role': 'user', 'content': '请总结'}])),
                patch('src.app.services.chat_service.get_model_config', return_value=fake_model()),
                patch('src.app.services.chat_service.get_model_adapter', return_value=adapter),
                patch('src.app.services.chat_service.rag_service.query', new=AsyncMock(return_value=_retrieval_decision())),
            ):
                completion = client.post(
                    '/internal/ai/chat/completions',
                    headers=request_headers('complete-1'),
                    json={
                        'sessionId': session_id,
                        'scene': 'project_chat',
                        'message': '请总结这份资料',
                        'context': {'projectId': 55, 'attachments': []},
                    },
                )

            self.assertEqual(200, completion.status_code, completion.text)
            completion_payload = completion.json()['data']
            self.assertEqual('这是普通对话回答', completion_payload['answer'])
            self.assertEqual('DOCUMENT', completion_payload['citations'][0]['sourceType'])

            messages = client.get(
                f'/internal/ai/chat/sessions/{session_id}/messages?pageNum=1&pageSize=20',
                headers=request_headers('messages-1'),
            )
            self.assertEqual(200, messages.status_code, messages.text)
            message_page = messages.json()['data']
            self.assertEqual(2, message_page['total'], message_page)
            self.assertEqual('user', message_page['records'][0]['role'])
            self.assertEqual('assistant', message_page['records'][1]['role'])

            with SessionLocal() as db:
                logs = db.query(AiRequestLog).order_by(AiRequestLog.id.asc()).all()
                self.assertEqual(1, len(logs))
                log = logs[0]
                self.assertEqual('complete-1', log.request_id)
                self.assertEqual(session_id, log.session_id)
                self.assertEqual(1, log.user_id)
                self.assertEqual(7, log.org_id)
                self.assertEqual(55, log.project_id)
                self.assertEqual('PRIVATE_KNOWLEDGE', log.answer_source)
                self.assertEqual('success', log.message_status)
                self.assertEqual(12, log.input_token_count)
                self.assertEqual(8, log.output_token_count)
                self.assertEqual(20, log.total_token_count)
                self.assertIsNotNone(log.latency_ms)
                self.assertEqual(log.latency_ms, log.duration_ms)

    def test_stream_completion_endpoint(self) -> None:
        stream_adapter = FakeAdapter(
            stream_events=[
                AdapterStreamEvent(delta='第一段'),
                AdapterStreamEvent(delta='第二段'),
                AdapterStreamEvent(usage=AdapterUsage(prompt_tokens=9, completion_tokens=6, total_tokens=15)),
            ]
        )
        with TestClient(app) as client:
            with (
                patch('src.app.services.chat_service.action_service.execute', new=AsyncMock(return_value=None)),
                patch('src.app.services.chat_service.load_attachment_contexts', new=AsyncMock(return_value=[])),
                patch('src.app.services.chat_service.build_prompt_messages', new=AsyncMock(return_value=[{'role': 'user', 'content': '流式回答'}])),
                patch('src.app.services.chat_service.get_model_config', return_value=fake_model()),
                patch('src.app.services.chat_service.get_model_adapter', return_value=stream_adapter),
                patch('src.app.services.chat_service.rag_service.query', new=AsyncMock(return_value=_retrieval_decision())),
            ):
                with client.stream(
                    'POST',
                    '/internal/ai/chat/completions/stream',
                    headers=request_headers('stream-1'),
                    json={'message': '请以流式方式回答', 'context': {'projectId': 88, 'attachments': []}},
                ) as response:
                    self.assertEqual(200, response.status_code)
                    payload = ''.join(response.iter_text())

        self.assertIn('event: stream.start', payload)
        self.assertIn('event: stream.delta', payload)
        self.assertIn('第一段', payload)
        self.assertIn('第二段', payload)
        self.assertIn('event: stream.end', payload)

        with SessionLocal() as db:
            logs = db.query(AiRequestLog).order_by(AiRequestLog.id.asc()).all()
            self.assertEqual(1, len(logs))
            self.assertTrue(logs[0].stream)
            self.assertEqual('success', logs[0].message_status)

    def test_rag_query_file_extract_and_pdf_ocr_endpoints(self) -> None:
        with TestClient(app) as client:
            with patch('src.app.api.v1.rag.rag_service.debug_query', new=AsyncMock(return_value=_rag_debug_response())):
                rag = client.post(
                    '/internal/ai/chat/rag/query',
                    headers=request_headers('rag-1'),
                    json={
                        'userId': 1,
                        'query': '测试 RAG 查询',
                        'projectScopeIds': [55],
                        'attachments': [],
                        'limit': 3,
                    },
                )
            self.assertEqual(200, rag.status_code, rag.text)
            rag_payload = rag.json()['data']
            self.assertEqual('PRIVATE_KNOWLEDGE', rag_payload['answerSource'])
            self.assertEqual(1, len(rag_payload['hits']))

            extract = client.post(
                '/internal/ai/chat/files/extract',
                headers=request_headers('extract-1'),
                files={'file': ('notes.txt', '第一行\n第二行'.encode('utf-8'), 'text/plain')},
                data={'fileName': 'notes.txt', 'fileExt': 'txt'},
            )
            self.assertEqual(200, extract.status_code, extract.text)
            extract_payload = extract.json()['data']
            self.assertEqual('text-decoder', extract_payload['parser'])
            self.assertIn('第一行', extract_payload['plainText'])
            self.assertGreaterEqual(len(extract_payload['chunks']), 1)

            import fitz

            document = fitz.open()
            page = document.new_page()
            page.insert_text((72, 72), 'Hello PDF OCR')
            pdf_bytes = document.tobytes()
            document.close()

            ocr = client.post(
                '/internal/ai/chat/ocr/pdf-text',
                headers=request_headers('ocr-1'),
                files={'file': ('sample.pdf', pdf_bytes, 'application/pdf')},
            )
            self.assertEqual(200, ocr.status_code, ocr.text)
            ocr_payload = ocr.json()['data']
            self.assertEqual(1, ocr_payload['pageCount'])
            self.assertIn('Hello PDF OCR', ocr_payload['text'])


class AiStreamCancellationTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self) -> None:
        reset_database()

    async def test_cancel_generation_marks_stream_and_request_log(self) -> None:
        original_store = chat_service.cancellation_store
        chat_service.cancellation_store = InMemoryCancellationStore()
        try:
            stream_adapter = FakeAdapter(
                stream_events=[
                    AdapterStreamEvent(delta='第一段'),
                    AdapterStreamEvent(delta='第二段'),
                    AdapterStreamEvent(usage=AdapterUsage(prompt_tokens=5, completion_tokens=3, total_tokens=8)),
                ]
            )
            request = ChatCompletionRequest(
                message='请开始流式回答',
                context=ChatContext(project_id=66, attachments=[]),
            )
            context = RequestContext(user_id=1, org_id=7, request_id='cancel-1')

            with SessionLocal() as db:
                with (
                    patch('src.app.services.chat_service.action_service.execute', new=AsyncMock(return_value=None)),
                    patch('src.app.services.chat_service.load_attachment_contexts', new=AsyncMock(return_value=[])),
                    patch('src.app.services.chat_service.build_prompt_messages', new=AsyncMock(return_value=[{'role': 'user', 'content': '开始'}])),
                    patch('src.app.services.chat_service.get_model_config', return_value=fake_model()),
                    patch('src.app.services.chat_service.get_model_adapter', return_value=stream_adapter),
                    patch('src.app.services.chat_service.rag_service.query', new=AsyncMock(return_value=_retrieval_decision())),
                ):
                    stream = await chat_service.stream(db, context, request)
                    start_event = await anext(stream)
                    first_delta = await anext(stream)
                    await chat_service.cancel('cancel-1')
                    error_event = await anext(stream)
                    await stream.aclose()

            self.assertIn('stream.start', start_event)
            self.assertIn('stream.delta', first_delta)
            self.assertIn('stream.error', error_event)
            self.assertIn('AI_CANCELLED', error_event)

            with SessionLocal() as db:
                logs = db.query(AiRequestLog).order_by(AiRequestLog.id.asc()).all()
                self.assertEqual(1, len(logs))
                self.assertFalse(logs[0].success_flag)
                self.assertEqual('AI_CANCELLED', logs[0].error_code)
                self.assertEqual('cancelled', logs[0].message_status)
        finally:
            chat_service.cancellation_store = original_store
