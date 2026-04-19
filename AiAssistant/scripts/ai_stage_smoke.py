from __future__ import annotations

import json
import sys
from pathlib import Path
from unittest.mock import AsyncMock, patch

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from tests.support import (  # noqa: E402
    FakeAdapter,
    FakeRedisClient,
    app,
    fake_model,
    request_headers,
    reset_database,
)
from fastapi.testclient import TestClient  # noqa: E402
from src.app.adapters.llm.base import AdapterStreamEvent, AdapterUsage  # noqa: E402
from src.app.schemas.message import CitationView  # noqa: E402
from src.app.schemas.rag import RagQueryHit, RagQueryResponse  # noqa: E402
from src.app.services.rag_service import RetrievalDecision  # noqa: E402


def retrieval_decision() -> RetrievalDecision:
    citation = CitationView(
        source_type='DOCUMENT',
        source_id=101,
        source_title='阶段 smoke 文档',
        snippet='阶段 smoke 的 RAG 引用片段',
        page_no=1,
        section_path='第一章',
        score=0.95,
        url=None,
    )
    return RetrievalDecision(
        answer_source='PRIVATE_KNOWLEDGE',
        citations=[citation],
        used_tools=['private_knowledge'],
        confidence=0.95,
        context_messages=[{'role': 'system', 'content': '请优先使用检索到的知识。'}],
        rewritten_queries=['测试 RAG', '测试 RAG 摘要'],
        rewrite_hints=['生成了聚焦检索意图的重写查询'],
    )


def rag_debug_response() -> RagQueryResponse:
    return RagQueryResponse(
        hits=[
            RagQueryHit(
                score=0.95,
                source_type='DOCUMENT',
                source_id=101,
                source_title='阶段 smoke 文档',
                snippet='阶段 smoke 的 RAG 引用片段',
                page_no=1,
                section_path='第一章',
                url=None,
            )
        ],
        citations=retrieval_decision().citations,
        confidence=0.95,
        answer_source='PRIVATE_KNOWLEDGE',
        used_tools=['private_knowledge'],
    )


def main() -> int:
    reset_database()
    results: dict[str, dict[str, object]] = {}

    with TestClient(app) as client:
        with patch('src.app.api.v1.health.Redis.from_url', return_value=FakeRedisClient()):
            health = client.get('/internal/ai/chat/health')
        results['health'] = {'status_code': health.status_code, 'body': health.json()}

        models = client.get('/internal/ai/chat/models', headers=request_headers('smoke-models'))
        results['models'] = {'status_code': models.status_code, 'count': len(models.json().get('data', []))}

        create = client.post(
            '/internal/ai/chat/sessions',
            headers=request_headers('smoke-session-create'),
            json={'projectId': 77, 'scene': 'project_chat', 'title': '阶段 Smoke 会话'},
        )
        create_payload = create.json()['data']
        session_id = create_payload['id']
        results['session_create'] = {'status_code': create.status_code, 'session_id': session_id}

        with patch('src.app.services.chat_service.action_service.execute', new=AsyncMock(return_value=None)), patch(
            'src.app.services.chat_service.load_attachment_contexts',
            new=AsyncMock(return_value=[]),
        ), patch(
            'src.app.services.chat_service.build_prompt_messages',
            new=AsyncMock(return_value=[{'role': 'user', 'content': '请总结'}]),
        ), patch(
            'src.app.services.chat_service.get_model_config',
            return_value=fake_model(),
        ), patch(
            'src.app.services.chat_service.rag_service.query',
            new=AsyncMock(return_value=retrieval_decision()),
        ), patch(
            'src.app.services.chat_service.get_model_adapter',
            return_value=FakeAdapter(answer='普通对话 smoke 回答'),
        ):
            completion = client.post(
                '/internal/ai/chat/completions',
                headers=request_headers('smoke-complete'),
                json={
                    'sessionId': session_id,
                    'scene': 'project_chat',
                    'message': '请给我一段普通对话回答',
                    'context': {'projectId': 77, 'attachments': []},
                },
            )
        results['chat_completion'] = {'status_code': completion.status_code, 'body': completion.json()}

        messages = client.get(
            f'/internal/ai/chat/sessions/{session_id}/messages?pageNum=1&pageSize=20',
            headers=request_headers('smoke-messages'),
        )
        results['message_query'] = {'status_code': messages.status_code, 'count': messages.json()['data']['total']}

        with patch('src.app.services.chat_service.action_service.execute', new=AsyncMock(return_value=None)), patch(
            'src.app.services.chat_service.load_attachment_contexts',
            new=AsyncMock(return_value=[]),
        ), patch(
            'src.app.services.chat_service.build_prompt_messages',
            new=AsyncMock(return_value=[{'role': 'user', 'content': '请总结'}]),
        ), patch(
            'src.app.services.chat_service.get_model_config',
            return_value=fake_model(),
        ), patch(
            'src.app.services.chat_service.rag_service.query',
            new=AsyncMock(return_value=retrieval_decision()),
        ), patch(
            'src.app.services.chat_service.get_model_adapter',
            return_value=FakeAdapter(
                stream_events=[
                    AdapterStreamEvent(delta='流式第一段'),
                    AdapterStreamEvent(delta='流式第二段'),
                    AdapterStreamEvent(usage=AdapterUsage(prompt_tokens=9, completion_tokens=6, total_tokens=15)),
                ]
            ),
        ):
            with client.stream(
                'POST',
                '/internal/ai/chat/completions/stream',
                headers=request_headers('smoke-stream'),
                json={'message': '请开始流式回答', 'context': {'projectId': 77, 'attachments': []}},
            ) as response:
                stream_text = ''.join(response.iter_text())
                results['chat_stream'] = {'status_code': response.status_code, 'contains_end': 'event: stream.end' in stream_text}

        cancel = client.post('/internal/ai/chat/completions/smoke-cancel/cancel', headers=request_headers('smoke-cancel'))
        results['cancel'] = {'status_code': cancel.status_code, 'body': cancel.json()}

        with patch('src.app.api.v1.rag.rag_service.debug_query', new=AsyncMock(return_value=rag_debug_response())):
            rag = client.post(
                '/internal/ai/chat/rag/query',
                headers=request_headers('smoke-rag'),
                json={'userId': 1, 'query': '测试 RAG', 'projectScopeIds': [77], 'attachments': [], 'limit': 3},
            )
        results['rag_query'] = {'status_code': rag.status_code, 'body': rag.json()}

        extract = client.post(
            '/internal/ai/chat/files/extract',
            headers=request_headers('smoke-extract'),
            files={'file': ('notes.txt', '阶段 smoke 文本抽取'.encode('utf-8'), 'text/plain')},
            data={'fileName': 'notes.txt', 'fileExt': 'txt'},
        )
        results['file_extract'] = {'status_code': extract.status_code, 'body': extract.json()}

        import fitz

        document = fitz.open()
        page = document.new_page()
        page.insert_text((72, 72), 'Stage Smoke PDF OCR')
        pdf_bytes = document.tobytes()
        document.close()

        ocr = client.post(
            '/internal/ai/chat/ocr/pdf-text',
            headers=request_headers('smoke-ocr'),
            files={'file': ('sample.pdf', pdf_bytes, 'application/pdf')},
        )
        results['pdf_ocr'] = {'status_code': ocr.status_code, 'body': ocr.json()}

    print(json.dumps(results, ensure_ascii=False, indent=2))
    passed = all(item.get('status_code') == 200 for item in results.values())
    return 0 if passed else 1


if __name__ == '__main__':
    raise SystemExit(main())
