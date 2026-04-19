from __future__ import annotations

import asyncio
from dataclasses import dataclass
import hashlib
import hmac
import json
from time import time
from typing import Any

import httpx
from sqlalchemy.orm import Session

from src.app.core.config import get_settings
from src.app.models.n8n_event_log import N8nEventLog


@dataclass(slots=True)
class N8nEmitResult:
    success: bool
    delivery_status: str
    workflow_status: str | None
    error_code: str | None
    error_message: str | None
    attempts: int
    max_attempts: int
    idempotency_key: str
    target_url: str | None
    status_code: int | None

    def as_audit_payload(self) -> dict[str, object]:
        return {
            'success': self.success,
            'deliveryStatus': self.delivery_status,
            'workflowStatus': self.workflow_status,
            'errorCode': self.error_code,
            'errorMessage': self.error_message,
            'attempts': self.attempts,
            'maxAttempts': self.max_attempts,
            'idempotencyKey': self.idempotency_key,
            'targetUrl': self.target_url,
            'statusCode': self.status_code,
        }


class N8nEventService:
    DELIVERY_PENDING = 'PENDING'
    DELIVERY_SKIPPED = 'SKIPPED'
    DELIVERY_DELIVERED = 'DELIVERED'
    DELIVERY_FAILED = 'FAILED'

    def __init__(self) -> None:
        self.settings = get_settings()

    def _target_url(self, workflow_path: str) -> str | None:
        base = (self.settings.n8n_webhook_base_url or '').strip().rstrip('/')
        if not base:
            return None
        return f'{base}/{workflow_path.lstrip("/")}'

    def _max_attempts(self) -> int:
        return max(1, int(self.settings.n8n_event_max_retries or 0) + 1)

    def _truncate(self, message: str | None) -> str | None:
        if not message:
            return None
        return str(message)[:500]

    def _idempotency_key(
        self,
        *,
        request_id: str,
        event_type: str,
        workflow_hint: str,
        approval_id: int | None,
        session_id: int | None,
        user_id: int | None,
        project_id: int | None,
        payload_json: str,
        explicit_key: str | None,
    ) -> str:
        if explicit_key:
            return explicit_key
        seed = '|'.join(
            [
                request_id,
                event_type,
                workflow_hint,
                str(approval_id or ''),
                str(session_id or ''),
                str(user_id or ''),
                str(project_id or ''),
                payload_json,
            ]
        )
        return hashlib.sha256(seed.encode('utf-8')).hexdigest()

    def _signature(self, *, request_id: str, event_type: str, workflow_hint: str, payload_json: str, timestamp: str) -> str | None:
        secret = (self.settings.n8n_webhook_secret or '').strip()
        if not secret:
            return None
        message = '\n'.join([request_id, event_type, workflow_hint, timestamp, payload_json]).encode('utf-8')
        return hmac.new(secret.encode('utf-8'), message, hashlib.sha256).hexdigest()

    def _headers(self, *, request_id: str, idempotency_key: str, timestamp: str, signature: str | None) -> dict[str, str]:
        headers = {
            'Content-Type': 'application/json',
            'X-N8N-Webhook-Secret': self.settings.n8n_webhook_secret,
            'X-Request-Id': request_id,
            'X-Idempotency-Key': idempotency_key,
            'X-N8N-Signature-Timestamp': timestamp,
        }
        if signature:
            headers['X-N8N-Signature'] = signature
        return headers

    def _failure_from_response(self, status_code: int) -> tuple[str, bool]:
        if status_code in {401, 403}:
            return 'N8N_AUTH_FAILED', False
        if status_code == 404:
            return 'N8N_WORKFLOW_NOT_FOUND', False
        if status_code == 408:
            return 'N8N_TIMEOUT', True
        if status_code == 429:
            return 'N8N_RATE_LIMITED', True
        if status_code >= 500:
            return 'N8N_UPSTREAM_UNAVAILABLE', True
        return 'N8N_WEBHOOK_REJECTED', False

    def _failure_from_exception(self, exc: Exception) -> tuple[str, bool]:
        if isinstance(exc, httpx.TimeoutException):
            return 'N8N_TIMEOUT', True
        if isinstance(exc, httpx.NetworkError):
            return 'N8N_NETWORK_ERROR', True
        return 'N8N_DELIVERY_FAILED', False

    async def _retry_delay(self, attempt: int) -> None:
        base_delay = max(0.0, float(self.settings.n8n_event_retry_backoff_seconds or 0.0))
        if base_delay <= 0:
            return
        await asyncio.sleep(base_delay * (2 ** max(0, attempt - 1)))

    async def emit(
        self,
        db: Session,
        *,
        request_id: str,
        event_type: str,
        workflow_hint: str,
        payload: dict[str, Any],
        approval_id: int | None = None,
        session_id: int | None = None,
        user_id: int | None = None,
        project_id: int | None = None,
        workflow_status: str | None = None,
        idempotency_key: str | None = None,
    ) -> N8nEmitResult:
        target_url = self._target_url(workflow_hint)
        payload_json = json.dumps(payload, ensure_ascii=False, sort_keys=True)
        max_attempts = self._max_attempts()
        resolved_idempotency_key = self._idempotency_key(
            request_id=request_id,
            event_type=event_type,
            workflow_hint=workflow_hint,
            approval_id=approval_id,
            session_id=session_id,
            user_id=user_id,
            project_id=project_id,
            payload_json=payload_json,
            explicit_key=idempotency_key,
        )
        record = N8nEventLog(
            request_id=request_id,
            event_type=event_type,
            workflow_hint=workflow_hint,
            approval_id=approval_id,
            session_id=session_id,
            user_id=user_id,
            project_id=project_id,
            target_url=target_url,
            delivery_status=self.DELIVERY_PENDING,
            workflow_status=workflow_status,
            payload_json=payload_json,
            success_flag=False,
            max_attempts=max_attempts,
            idempotency_key=resolved_idempotency_key,
        )

        if not self.settings.n8n_enabled:
            record.delivery_status = self.DELIVERY_SKIPPED
            record.status_code = 503
            record.error_code = 'N8N_DISABLED'
            record.error_message = 'n8n disabled'
            db.add(record)
            return N8nEmitResult(
                success=False,
                delivery_status=record.delivery_status,
                workflow_status=workflow_status,
                error_code=record.error_code,
                error_message=record.error_message,
                attempts=0,
                max_attempts=max_attempts,
                idempotency_key=resolved_idempotency_key,
                target_url=target_url,
                status_code=record.status_code,
            )

        if not target_url:
            record.delivery_status = self.DELIVERY_SKIPPED
            record.status_code = 503
            record.error_code = 'N8N_WEBHOOK_NOT_CONFIGURED'
            record.error_message = 'n8n webhook base url not configured'
            db.add(record)
            return N8nEmitResult(
                success=False,
                delivery_status=record.delivery_status,
                workflow_status=workflow_status,
                error_code=record.error_code,
                error_message=record.error_message,
                attempts=0,
                max_attempts=max_attempts,
                idempotency_key=resolved_idempotency_key,
                target_url=target_url,
                status_code=record.status_code,
            )

        timeout_seconds = max(1, int(self.settings.n8n_event_timeout_seconds or 15))
        timeout = httpx.Timeout(timeout_seconds, connect=min(timeout_seconds, 5.0))
        last_error_code: str | None = None
        last_error_message: str | None = None
        last_status_code: int | None = None
        last_signature: str | None = None

        for attempt in range(1, max_attempts + 1):
            timestamp = str(int(time()))
            signature = self._signature(
                request_id=request_id,
                event_type=event_type,
                workflow_hint=workflow_hint,
                payload_json=payload_json,
                timestamp=timestamp,
            )
            last_signature = signature
            record.attempt_count = attempt
            record.signature = signature
            try:
                async with httpx.AsyncClient(timeout=timeout) as client:
                    response = await client.post(
                        target_url,
                        headers=self._headers(
                            request_id=request_id,
                            idempotency_key=resolved_idempotency_key,
                            timestamp=timestamp,
                            signature=signature,
                        ),
                        json=payload,
                    )
                last_status_code = response.status_code
                if response.status_code < 400:
                    record.status_code = response.status_code
                    record.success_flag = True
                    record.delivery_status = self.DELIVERY_DELIVERED
                    record.error_code = None
                    record.error_message = None
                    db.add(record)
                    return N8nEmitResult(
                        success=True,
                        delivery_status=record.delivery_status,
                        workflow_status=workflow_status,
                        error_code=None,
                        error_message=None,
                        attempts=attempt,
                        max_attempts=max_attempts,
                        idempotency_key=resolved_idempotency_key,
                        target_url=target_url,
                        status_code=response.status_code,
                    )
                last_error_code, retryable = self._failure_from_response(response.status_code)
                last_error_message = self._truncate(response.text)
                if retryable and attempt < max_attempts:
                    await self._retry_delay(attempt)
                    continue
                break
            except Exception as exc:
                last_error_code, retryable = self._failure_from_exception(exc)
                last_status_code = 503
                last_error_message = self._truncate(str(exc))
                if retryable and attempt < max_attempts:
                    await self._retry_delay(attempt)
                    continue
                break

        record.status_code = last_status_code
        record.success_flag = False
        record.delivery_status = self.DELIVERY_FAILED
        record.error_code = last_error_code
        record.error_message = last_error_message
        record.signature = last_signature
        db.add(record)
        return N8nEmitResult(
            success=False,
            delivery_status=record.delivery_status,
            workflow_status=workflow_status,
            error_code=last_error_code,
            error_message=last_error_message,
            attempts=record.attempt_count,
            max_attempts=max_attempts,
            idempotency_key=resolved_idempotency_key,
            target_url=target_url,
            status_code=last_status_code,
        )


n8n_event_service = N8nEventService()
