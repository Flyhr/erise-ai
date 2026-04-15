from __future__ import annotations

import unittest
from unittest.mock import AsyncMock, patch

from fastapi.testclient import TestClient

from src.app.models.ai_action_log import AiActionLog
from src.app.models.ai_request_log import AiRequestLog

from tests.support import SessionLocal, app, fake_model, request_headers, reset_database, FakeAdapter


class AgentActionFrameworkTest(unittest.TestCase):
    def setUp(self) -> None:
        reset_database()

    def test_document_title_action_is_migrated_to_registry(self) -> None:
        with TestClient(app) as client:
            with (
                patch(
                    'src.app.actions.builtin_actions.fetch_document_context',
                    new=AsyncMock(return_value={'id': 9, 'projectId': 55, 'title': '旧标题', 'summary': '', 'plainText': '正文'}),
                ),
                patch(
                    'src.app.actions.builtin_actions.update_document_title',
                    new=AsyncMock(return_value={'id': 9, 'projectId': 55, 'title': '新的文档标题', 'summary': '', 'plainText': '正文'}),
                ),
            ):
                response = client.post(
                    '/internal/ai/chat/completions',
                    headers=request_headers('action-title-1'),
                    json={
                        'message': '把这份文档标题改为 新的文档标题',
                        'context': {
                            'projectId': 55,
                            'attachments': [{'attachmentType': 'DOCUMENT', 'sourceId': 9, 'projectId': 55}],
                        },
                    },
                )

        self.assertEqual(200, response.status_code, response.text)
        payload = response.json()['data']
        self.assertIn('新的文档标题', payload['answer'])

        with SessionLocal() as db:
            action_logs = db.query(AiActionLog).all()
            self.assertEqual(1, len(action_logs))
            self.assertEqual('document.update_title', action_logs[0].action_code)
            self.assertEqual('success', action_logs[0].action_status)
            self.assertTrue(action_logs[0].success_flag)

            request_logs = db.query(AiRequestLog).all()
            self.assertEqual(1, len(request_logs))
            self.assertEqual('document-title-update', request_logs[0].model_code)

    def test_document_summary_action_updates_summary_and_logs_usage(self) -> None:
        with TestClient(app) as client:
            with (
                patch(
                    'src.app.actions.builtin_actions.fetch_document_context',
                    new=AsyncMock(return_value={'id': 9, 'projectId': 55, 'title': '需求说明', 'summary': '', 'plainText': '正文内容 ' * 400}),
                ),
                patch(
                    'src.app.actions.builtin_actions.update_document_summary',
                    new=AsyncMock(return_value={'id': 9, 'projectId': 55, 'title': '需求说明', 'summary': '本周完成需求梳理与知识库接入准备。', 'plainText': '正文内容'}),
                ),
                patch('src.app.actions.builtin_actions.get_model_config', return_value=fake_model()),
                patch(
                    'src.app.actions.builtin_actions.get_model_adapter',
                    return_value=FakeAdapter(
                        answer='本周完成需求梳理与知识库接入准备。',
                        provider_code='OPENAI',
                        model_code='gpt-4.1-mini',
                    ),
                ),
            ):
                response = client.post(
                    '/internal/ai/chat/completions',
                    headers=request_headers('action-summary-1'),
                    json={
                        'message': '请为这份文档生成摘要',
                        'context': {
                            'projectId': 55,
                            'attachments': [{'attachmentType': 'DOCUMENT', 'sourceId': 9, 'projectId': 55}],
                        },
                    },
                )

        self.assertEqual(200, response.status_code, response.text)
        payload = response.json()['data']
        self.assertIn('生成并更新摘要', payload['answer'])

        with SessionLocal() as db:
            action_log = db.query(AiActionLog).one()
            self.assertEqual('document.generate_summary', action_log.action_code)
            self.assertEqual('OPENAI', action_log.provider_code)
            self.assertEqual('gpt-4.1-mini', action_log.model_code)

            request_log = db.query(AiRequestLog).one()
            self.assertEqual(20, request_log.total_token_count)

    def test_document_tags_action_uses_unified_protocol(self) -> None:
        with TestClient(app) as client:
            with (
                patch(
                    'src.app.actions.builtin_actions.fetch_document_context',
                    new=AsyncMock(return_value={'id': 11, 'projectId': 55, 'title': '测试文档', 'summary': '', 'plainText': '正文'}),
                ),
                patch(
                    'src.app.actions.builtin_actions.update_document_tags',
                    new=AsyncMock(return_value=[
                        {'id': 1, 'name': '研发', 'color': None},
                        {'id': 2, 'name': '风险', 'color': None},
                        {'id': 3, 'name': '本周', 'color': None},
                    ]),
                ),
            ):
                response = client.post(
                    '/internal/ai/chat/completions',
                    headers=request_headers('action-tags-1'),
                    json={
                        'message': '把这份文档标签改为 研发, 风险, 本周',
                        'context': {
                            'projectId': 55,
                            'attachments': [{'attachmentType': 'DOCUMENT', 'sourceId': 11, 'projectId': 55}],
                        },
                    },
                )

        self.assertEqual(200, response.status_code, response.text)
        self.assertIn('研发、风险、本周', response.json()['data']['answer'])

        with SessionLocal() as db:
            action_log = db.query(AiActionLog).one()
            self.assertEqual('document.update_tags', action_log.action_code)
            self.assertEqual('success', action_log.action_status)

    def test_archive_file_action_returns_permission_fallback_and_logs_denied(self) -> None:
        with TestClient(app) as client:
            response = client.post(
                '/internal/ai/chat/completions',
                headers=request_headers('action-archive-1'),
                json={
                    'message': '请归档这个文件',
                    'context': {'projectId': 55, 'attachments': []},
                },
            )

        self.assertEqual(200, response.status_code, response.text)
        self.assertIn('没有唯一可操作的文件', response.json()['data']['answer'])

        with SessionLocal() as db:
            action_log = db.query(AiActionLog).one()
            self.assertEqual('file.archive', action_log.action_code)
            self.assertEqual('denied', action_log.action_status)
            self.assertFalse(action_log.success_flag)

    def test_project_weekly_report_action_creates_draft(self) -> None:
        with TestClient(app) as client:
            with (
                patch(
                    'src.app.actions.builtin_actions.fetch_project_context',
                    new=AsyncMock(return_value={
                        'id': 77,
                        'name': 'Apollo',
                        'description': '推进知识库改造',
                        'projectStatus': 'ACTIVE',
                        'fileCount': 3,
                        'documentCount': 2,
                    }),
                ),
                patch(
                    'src.app.actions.builtin_actions.create_project_weekly_report_draft',
                    new=AsyncMock(return_value={
                        'id': 301,
                        'projectId': 77,
                        'title': 'Apollo 周报草稿 2026-04-16',
                        'summary': '本周推进知识库接入。',
                        'plainText': '正文内容',
                    }),
                ),
                patch('src.app.actions.builtin_actions.get_model_config', return_value=fake_model()),
                patch(
                    'src.app.actions.builtin_actions.get_model_adapter',
                    return_value=FakeAdapter(
                        answer='标题：Apollo 周报草稿 2026-04-16\n摘要：本周推进知识库接入。\n正文：\n1. 完成 AI Action 框架梳理\n2. 明确下周联调计划',
                        provider_code='OPENAI',
                        model_code='gpt-4.1-mini',
                    ),
                ),
            ):
                response = client.post(
                    '/internal/ai/chat/completions',
                    headers=request_headers('action-weekly-1'),
                    json={
                        'message': '请生成这个项目的周报草稿',
                        'context': {'projectId': 77, 'attachments': []},
                    },
                )

        self.assertEqual(200, response.status_code, response.text)
        self.assertIn('Apollo', response.json()['data']['answer'])

        with SessionLocal() as db:
            action_log = db.query(AiActionLog).one()
            self.assertEqual('project.create_weekly_report_draft', action_log.action_code)
            self.assertEqual('success', action_log.action_status)
            self.assertTrue(action_log.success_flag)


if __name__ == '__main__':
    unittest.main()
