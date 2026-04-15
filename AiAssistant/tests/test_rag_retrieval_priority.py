from __future__ import annotations

import unittest
from unittest.mock import AsyncMock, patch

from src.app.api.deps import RequestContext
from src.app.schemas.chat import AttachmentContext, ChatCompletionRequest, ChatContext
from src.app.schemas.rag import RagQueryHit
from src.app.services.rag_service import rag_service
from src.app.services.web_search_service import WebSearchHit


def _hit(source_type: str, source_id: int, score: float, title: str = '测试资料') -> RagQueryHit:
    return RagQueryHit(
        score=score,
        source_type=source_type,
        source_id=source_id,
        source_title=title,
        snippet='这是一段足够支撑回答的检索片段，用于验证多级检索优先级。',
        page_no=1,
        section_path='测试章节',
    )


class RagRetrievalPriorityTest(unittest.IsolatedAsyncioTestCase):
    def _context(self) -> RequestContext:
        return RequestContext(user_id=1, org_id=7, request_id='priority-test')

    async def test_attachment_source_stops_before_project_and_web(self) -> None:
        calls: list[ChatCompletionRequest] = []

        async def private_hits(request, *_args):
            calls.append(request)
            return [_hit('DOCUMENT', 101, 0.92, '引用文档')]

        request = ChatCompletionRequest(
            message='请总结这份引用文档',
            context=ChatContext(
                project_id=88,
                attachments=[AttachmentContext(attachment_type='DOCUMENT', source_id=101, project_id=88)],
            ),
            web_search_enabled=True,
            similarity_threshold=0.7,
            query_rewrite_enabled=False,
        )

        with (
            patch.object(rag_service, '_private_hits', new=AsyncMock(side_effect=private_hits)),
            patch.object(rag_service, '_safe_web_search', new=AsyncMock(return_value=[])) as web_search,
        ):
            decision = await rag_service.query(request, self._context())

        self.assertEqual('PRIVATE_KNOWLEDGE', decision.answer_source)
        self.assertIn('priority_source', decision.used_tools)
        self.assertNotIn('project_knowledge', decision.used_tools)
        self.assertNotIn('web_search', decision.used_tools)
        self.assertEqual(1, len(calls))
        self.assertIsNone(calls[0].context.project_id)
        self.assertEqual(1, len(calls[0].context.attachments))
        web_search.assert_not_awaited()

    async def test_project_source_runs_after_weak_attachment_source(self) -> None:
        calls: list[ChatCompletionRequest] = []

        async def private_hits(request, *_args):
            calls.append(request)
            if request.context.attachments:
                return [_hit('DOCUMENT', 101, 0.42, '弱引用文档')]
            return [_hit('FILE', 202, 0.9, '项目文件')]

        request = ChatCompletionRequest(
            message='项目方案里如何定义交付流程？',
            context=ChatContext(
                project_id=88,
                attachments=[AttachmentContext(attachment_type='DOCUMENT', source_id=101, project_id=88)],
            ),
            web_search_enabled=True,
            similarity_threshold=0.7,
            query_rewrite_enabled=False,
        )

        with (
            patch.object(rag_service, '_private_hits', new=AsyncMock(side_effect=private_hits)),
            patch.object(rag_service, '_safe_web_search', new=AsyncMock(return_value=[])) as web_search,
        ):
            decision = await rag_service.query(request, self._context())

        self.assertEqual('PRIVATE_KNOWLEDGE', decision.answer_source)
        self.assertIn('priority_source', decision.used_tools)
        self.assertIn('project_knowledge', decision.used_tools)
        self.assertNotIn('web_search', decision.used_tools)
        self.assertEqual(2, len(calls))
        self.assertEqual([], calls[1].context.attachments)
        self.assertEqual(88, calls[1].context.project_id)
        web_search.assert_not_awaited()

    async def test_web_search_runs_after_weak_private_sources(self) -> None:
        async def private_hits(*_args):
            return [_hit('FILE', 202, 0.35, '弱项目文件')]

        request = ChatCompletionRequest(
            message='补充最新行业背景',
            context=ChatContext(project_id=88),
            web_search_enabled=True,
            similarity_threshold=0.7,
            query_rewrite_enabled=False,
        )
        web_hits = [
            WebSearchHit(
                title='行业背景资料',
                url='https://example.com/report',
                snippet='这是一段超过四十个字符的联网搜索摘要，用于判断联网结果足够支撑回答。',
            )
        ]

        with (
            patch.object(rag_service, '_private_hits', new=AsyncMock(side_effect=private_hits)),
            patch.object(rag_service, '_safe_web_search', new=AsyncMock(return_value=web_hits)) as web_search,
        ):
            decision = await rag_service.query(request, self._context())

        self.assertEqual('WEB_SEARCH', decision.answer_source)
        self.assertIn('project_knowledge', decision.used_tools)
        self.assertIn('web_search', decision.used_tools)
        web_search.assert_awaited_once()

    async def test_general_knowledge_is_final_fallback(self) -> None:
        request = ChatCompletionRequest(
            message='给我一个通用解释',
            context=ChatContext(project_id=88),
            web_search_enabled=False,
            similarity_threshold=0.7,
            query_rewrite_enabled=False,
        )

        with patch.object(rag_service, '_private_hits', new=AsyncMock(return_value=[])):
            decision = await rag_service.query(request, self._context())

        self.assertEqual('GENERAL_KNOWLEDGE', decision.answer_source)
        self.assertEqual([], decision.citations)
        self.assertIn('project_knowledge', decision.used_tools)
        self.assertIn('general_knowledge', decision.used_tools)
        self.assertNotIn('web_search', decision.used_tools)
