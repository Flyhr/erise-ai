from __future__ import annotations

import unittest
from unittest.mock import patch

import httpx

from src.app.models.n8n_event_log import N8nEventLog
from src.app.services.n8n_event_service import n8n_event_service

from tests.support import SessionLocal, reset_database


class _FakeResponse:
    def __init__(self, status_code: int, text: str = '') -> None:
        self.status_code = status_code
        self.text = text


class _FakeAsyncClient:
    def __init__(self, *, responses: list[_FakeResponse] | None = None, excs: list[Exception] | None = None) -> None:
        self.responses = list(responses or [])
        self.excs = list(excs or [])
        self.calls: list[dict[str, object]] = []

    async def __aenter__(self) -> _FakeAsyncClient:
        return self

    async def __aexit__(self, exc_type, exc, tb) -> None:
        return None

    async def post(self, url: str, *, headers: dict[str, str], json: dict[str, object]) -> _FakeResponse:
        self.calls.append({'url': url, 'headers': headers, 'json': json})
        if self.excs:
            raise self.excs.pop(0)
        assert self.responses
        return self.responses.pop(0)


class N8nEventServiceTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self) -> None:
        reset_database()
        self.original_enabled = n8n_event_service.settings.n8n_enabled
        self.original_base_url = n8n_event_service.settings.n8n_webhook_base_url
        self.original_secret = n8n_event_service.settings.n8n_webhook_secret
        self.original_timeout = n8n_event_service.settings.n8n_event_timeout_seconds
        self.original_retries = n8n_event_service.settings.n8n_event_max_retries
        self.original_backoff = n8n_event_service.settings.n8n_event_retry_backoff_seconds

    def tearDown(self) -> None:
        n8n_event_service.settings.n8n_enabled = self.original_enabled
        n8n_event_service.settings.n8n_webhook_base_url = self.original_base_url
        n8n_event_service.settings.n8n_webhook_secret = self.original_secret
        n8n_event_service.settings.n8n_event_timeout_seconds = self.original_timeout
        n8n_event_service.settings.n8n_event_max_retries = self.original_retries
        n8n_event_service.settings.n8n_event_retry_backoff_seconds = self.original_backoff

    async def test_emit_records_disabled_delivery_as_not_started_workflow(self) -> None:
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
            self.assertEqual('SKIPPED', record.delivery_status)
            self.assertEqual('NOT_STARTED', record.workflow_status)
            self.assertEqual(503, record.status_code)
            self.assertEqual('N8N_DISABLED', record.error_code)
            self.assertEqual('approval-pending', record.workflow_name)
            self.assertEqual('approval', record.workflow_domain)

    async def test_emit_wraps_payload_with_governance_metadata(self) -> None:
        n8n_event_service.settings.n8n_enabled = True
        n8n_event_service.settings.n8n_webhook_base_url = 'http://localhost:5678/webhook'
        n8n_event_service.settings.n8n_webhook_secret = 'secret'
        n8n_event_service.settings.n8n_event_timeout_seconds = 10
        n8n_event_service.settings.n8n_event_max_retries = 0
        n8n_event_service.settings.n8n_event_retry_backoff_seconds = 0

        client = _FakeAsyncClient(responses=[_FakeResponse(202, 'accepted')])

        with patch('src.app.services.n8n_event_service.httpx.AsyncClient', return_value=client):
            with SessionLocal() as db:
                result = await n8n_event_service.emit(
                    db,
                    request_id='n8n-success-1',
                    event_type='approval.pending',
                    workflow_hint='approval-pending',
                    payload={'approvalId': 12, 'actionCode': 'document.update_title'},
                    approval_id=12,
                    user_id=1,
                    project_id=55,
                )
                db.commit()

        self.assertTrue(result.success)
        outbound_payload = client.calls[0]['json']
        self.assertEqual(12, outbound_payload['approvalId'])
        self.assertEqual('approval-pending', outbound_payload['_n8n']['workflowName'])
        self.assertEqual(result.event_log_id, outbound_payload['_n8n']['eventLogId'])
        self.assertIn('/api/v1/automation/webhooks/workflow-status', outbound_payload['_n8n']['callbackUrl'])
        self.assertEqual('approval-pending', client.calls[0]['headers']['X-N8N-Workflow-Name'])
        self.assertIn('X-N8N-Event-Log-Id', client.calls[0]['headers'])

    async def test_emit_records_http_failure_and_marks_retryable_delivery(self) -> None:
        n8n_event_service.settings.n8n_enabled = True
        n8n_event_service.settings.n8n_webhook_base_url = 'http://localhost:5678/webhook'
        n8n_event_service.settings.n8n_webhook_secret = 'secret'
        n8n_event_service.settings.n8n_event_timeout_seconds = 10
        n8n_event_service.settings.n8n_event_max_retries = 0
        n8n_event_service.settings.n8n_event_retry_backoff_seconds = 0

        client = _FakeAsyncClient(responses=[_FakeResponse(500, 'upstream failure')])

        with patch('src.app.services.n8n_event_service.httpx.AsyncClient', return_value=client):
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
            self.assertEqual('FAILED', record.delivery_status)
            self.assertEqual('NOT_STARTED', record.workflow_status)
            self.assertEqual(500, record.status_code)
            self.assertFalse(record.success_flag)
            self.assertEqual('N8N_UPSTREAM_UNAVAILABLE', record.error_code)
            self.assertEqual(1, record.attempt_count)
            self.assertEqual('upstream failure', record.error_message)
            self.assertTrue(record.delivery_retryable)
            self.assertTrue(record.idempotency_key)
            self.assertTrue(record.signature)

    async def test_emit_retries_retryable_failure_and_records_second_attempt_success(self) -> None:
        n8n_event_service.settings.n8n_enabled = True
        n8n_event_service.settings.n8n_webhook_base_url = 'http://localhost:5678/webhook'
        n8n_event_service.settings.n8n_webhook_secret = 'secret'
        n8n_event_service.settings.n8n_event_timeout_seconds = 10
        n8n_event_service.settings.n8n_event_max_retries = 2
        n8n_event_service.settings.n8n_event_retry_backoff_seconds = 0

        client = _FakeAsyncClient(responses=[_FakeResponse(500, 'upstream failure'), _FakeResponse(202, 'accepted')])

        with patch('src.app.services.n8n_event_service.httpx.AsyncClient', return_value=client):
            with SessionLocal() as db:
                result = await n8n_event_service.emit(
                    db,
                    request_id='n8n-retry-1',
                    event_type='approval.applied',
                    workflow_hint='approval-applied',
                    payload={'approvalId': 33},
                    approval_id=33,
                    user_id=1,
                    project_id=55,
                    workflow_status='PENDING',
                    idempotency_key='approval:33:applied',
                )
                db.commit()

        self.assertTrue(result.success)
        self.assertEqual(2, result.attempts)
        with SessionLocal() as db:
            record = db.query(N8nEventLog).one()
            self.assertTrue(record.success_flag)
            self.assertEqual('DELIVERED', record.delivery_status)
            self.assertEqual(2, record.attempt_count)
            self.assertEqual(3, record.max_attempts)
            self.assertEqual('PENDING', record.workflow_status)
            self.assertEqual('approval:33:applied', record.idempotency_key)
        self.assertEqual(2, len(client.calls))

    async def test_emit_classifies_network_timeout_as_retryable_failure(self) -> None:
        n8n_event_service.settings.n8n_enabled = True
        n8n_event_service.settings.n8n_webhook_base_url = 'http://localhost:5678/webhook'
        n8n_event_service.settings.n8n_webhook_secret = 'secret'
        n8n_event_service.settings.n8n_event_timeout_seconds = 10
        n8n_event_service.settings.n8n_event_max_retries = 0
        n8n_event_service.settings.n8n_event_retry_backoff_seconds = 0

        client = _FakeAsyncClient(excs=[httpx.ReadTimeout('timed out')])

        with patch('src.app.services.n8n_event_service.httpx.AsyncClient', return_value=client):
            with SessionLocal() as db:
                await n8n_event_service.emit(
                    db,
                    request_id='n8n-timeout-1',
                    event_type='approval.failed',
                    workflow_hint='approval-failed',
                    payload={'approvalId': 21},
                    approval_id=21,
                    user_id=1,
                    project_id=55,
                )
                db.commit()

        with SessionLocal() as db:
            record = db.query(N8nEventLog).one()
            self.assertEqual('N8N_TIMEOUT', record.error_code)
            self.assertEqual('FAILED', record.delivery_status)
            self.assertEqual('NOT_STARTED', record.workflow_status)
            self.assertEqual(503, record.status_code)
            self.assertTrue(record.delivery_retryable)

    async def test_retry_event_reuses_payload_tracks_source_and_resolves_manual_queue(self) -> None:
        n8n_event_service.settings.n8n_enabled = True
        n8n_event_service.settings.n8n_webhook_base_url = 'http://localhost:5678/webhook'
        n8n_event_service.settings.n8n_webhook_secret = 'secret'
        n8n_event_service.settings.n8n_event_timeout_seconds = 10
        n8n_event_service.settings.n8n_event_max_retries = 0
        n8n_event_service.settings.n8n_event_retry_backoff_seconds = 0

        original_client = _FakeAsyncClient(responses=[_FakeResponse(500, 'upstream failure')])
        retry_client = _FakeAsyncClient(responses=[_FakeResponse(202, 'accepted')])

        with patch('src.app.services.n8n_event_service.httpx.AsyncClient', side_effect=[original_client, retry_client]):
            with SessionLocal() as db:
                first = await n8n_event_service.emit(
                    db,
                    request_id='n8n-source-1',
                    event_type='approval.pending',
                    workflow_hint='approval-pending',
                    payload={'approvalId': 48, 'step': 'review'},
                    approval_id=48,
                    user_id=1,
                    project_id=55,
                    workflow_status='PENDING',
                    idempotency_key='approval:48:pending',
                )
                n8n_event_service.manual_handoff_event(db, first.event_log_id or 0, reason='Need manual replay')
                db.commit()

            with SessionLocal() as db:
                retried = await n8n_event_service.retry_event(db, first.event_log_id or 0)
                db.commit()

        self.assertTrue(retried.retried)
        self.assertEqual(first.event_log_id, retried.source_event_id)
        self.assertEqual('approval:48:pending', retried.event.idempotency_key)
        self.assertEqual('DELIVERED', retried.event.delivery_status)
        self.assertEqual('PENDING', retried.event.workflow_status)
        self.assertEqual(first.event_log_id, retried.event.replayed_from_event_id)
        self.assertEqual(48, retry_client.calls[0]['json']['approvalId'])
        self.assertEqual('review', retry_client.calls[0]['json']['step'])
        self.assertEqual('approval:48:pending', retry_client.calls[0]['headers']['X-Idempotency-Key'])

        with SessionLocal() as db:
            source = db.get(N8nEventLog, first.event_log_id)
            self.assertEqual(1, source.manual_replay_count)
            self.assertEqual('RESOLVED', source.manual_status)

    async def test_event_detail_includes_replay_lineage(self) -> None:
        n8n_event_service.settings.n8n_enabled = True
        n8n_event_service.settings.n8n_webhook_base_url = 'http://localhost:5678/webhook'
        n8n_event_service.settings.n8n_webhook_secret = 'secret'
        n8n_event_service.settings.n8n_event_timeout_seconds = 10
        n8n_event_service.settings.n8n_event_max_retries = 0
        n8n_event_service.settings.n8n_event_retry_backoff_seconds = 0

        clients = [
            _FakeAsyncClient(responses=[_FakeResponse(500, 'upstream failure')]),
            _FakeAsyncClient(responses=[_FakeResponse(202, 'accepted')]),
        ]

        with patch('src.app.services.n8n_event_service.httpx.AsyncClient', side_effect=clients):
            with SessionLocal() as db:
                source = await n8n_event_service.emit(
                    db,
                    request_id='n8n-lineage-source',
                    event_type='approval.pending',
                    workflow_hint='approval-pending',
                    payload={'approvalId': 52},
                )
                db.commit()

            with SessionLocal() as db:
                replay = await n8n_event_service.retry_event(db, source.event_log_id or 0)
                db.commit()

            with SessionLocal() as db:
                detail = n8n_event_service.get_event_detail(db, source.event_log_id or 0)

        self.assertIsNone(detail.source_event)
        self.assertEqual(1, len(detail.replay_events))
        self.assertEqual(replay.event.id, detail.replay_events[0].id)


if __name__ == '__main__':
    unittest.main()
