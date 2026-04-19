from __future__ import annotations

import unittest
from unittest.mock import patch

from src.app.models.n8n_event_log import N8nEventLog
from src.app.services.n8n_event_service import n8n_event_service

from tests.support import SessionLocal, reset_database


class _FakeResponse:
    def __init__(self, status_code: int, text: str = '') -> None:
        self.status_code = status_code
        self.text = text


class _FakeAsyncClient:
    def __init__(self, *, response: _FakeResponse | None = None, exc: Exception | None = None, timeout: int | None = None) -> None:
        self.response = response
        self.exc = exc
        self.timeout = timeout

    async def __aenter__(self) -> _FakeAsyncClient:
        return self

    async def __aexit__(self, exc_type, exc, tb) -> None:
        return None

    async def post(self, url: str, *, headers: dict[str, str], json: dict[str, object]) -> _FakeResponse:
        del url, headers, json
        if self.exc is not None:
            raise self.exc
        assert self.response is not None
        return self.response


class N8nEventServiceTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self) -> None:
        reset_database()
        self.original_enabled = n8n_event_service.settings.n8n_enabled
        self.original_base_url = n8n_event_service.settings.n8n_webhook_base_url
        self.original_secret = n8n_event_service.settings.n8n_webhook_secret
        self.original_timeout = n8n_event_service.settings.n8n_event_timeout_seconds

    def tearDown(self) -> None:
        n8n_event_service.settings.n8n_enabled = self.original_enabled
        n8n_event_service.settings.n8n_webhook_base_url = self.original_base_url
        n8n_event_service.settings.n8n_webhook_secret = self.original_secret
        n8n_event_service.settings.n8n_event_timeout_seconds = self.original_timeout

    async def test_emit_records_disabled_delivery_as_traceable_failure(self) -> None:
        n8n_event_service.settings.n8n_enabled = False
        n8n_event_service.settings.n8n_webhook_base_url = None

        with SessionLocal() as db:
            await n8n_event_service.emit(
                db,
                request_id='n8n-disabled-1',
                event_type='approval.pending',
                workflow_hint='approval-pending',
                payload={'approvalId': 12},
                approval_id=12,
                user_id=1,
                project_id=55,
            )
            db.commit()

        with SessionLocal() as db:
            record = db.query(N8nEventLog).one()
            self.assertEqual('n8n-disabled-1', record.request_id)
            self.assertEqual('approval.pending', record.event_type)
            self.assertFalse(record.success_flag)
            self.assertEqual(503, record.status_code)
            self.assertIn('n8n disabled', record.error_message or '')

    async def test_emit_records_http_failure_status_and_body(self) -> None:
        n8n_event_service.settings.n8n_enabled = True
        n8n_event_service.settings.n8n_webhook_base_url = 'http://localhost:5678/webhook'
        n8n_event_service.settings.n8n_webhook_secret = 'secret'
        n8n_event_service.settings.n8n_event_timeout_seconds = 10

        with patch(
            'src.app.services.n8n_event_service.httpx.AsyncClient',
            return_value=_FakeAsyncClient(response=_FakeResponse(500, 'upstream failure')),
        ):
            with SessionLocal() as db:
                await n8n_event_service.emit(
                    db,
                    request_id='n8n-failure-1',
                    event_type='approval.failed',
                    workflow_hint='approval-failed',
                    payload={'approvalId': 21, 'errorCode': 'ACTION_PERMISSION_DENIED'},
                    approval_id=21,
                    user_id=1,
                    project_id=55,
                )
                db.commit()

        with SessionLocal() as db:
            record = db.query(N8nEventLog).one()
            self.assertEqual('http://localhost:5678/webhook/approval-failed', record.target_url)
            self.assertEqual(500, record.status_code)
            self.assertFalse(record.success_flag)
            self.assertEqual('upstream failure', record.error_message)


if __name__ == '__main__':
    unittest.main()
