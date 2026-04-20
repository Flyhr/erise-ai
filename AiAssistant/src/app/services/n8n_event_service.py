from __future__ import annotations

import asyncio
from dataclasses import dataclass
from datetime import date, datetime, timedelta
import hashlib
import hmac
import json
from time import time
from typing import Any
from uuid import uuid4

import httpx
from sqlalchemy import func, or_, select
from sqlalchemy.orm import Session

from src.app.core.config import get_settings
from src.app.core.exceptions import AiServiceError
from src.app.models.n8n_event_log import N8nEventLog
from src.app.schemas.common import PageData
from src.app.schemas.n8n import (
    N8nErrorMetricView,
    N8nEventDetailView,
    N8nEventSummaryView,
    N8nEventView,
    N8nRetryResultView,
)
from src.app.services.n8n_workflow_registry import N8nWorkflowDefinition, resolve_workflow_definition


@dataclass(slots=True)
class N8nEmitResult:
    event_log_id: int | None
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
    workflow_name: str | None
    workflow_version: str | None
    workflow_domain: str | None
    workflow_owner: str | None

    def as_audit_payload(self) -> dict[str, object]:
        return {
            'eventLogId': self.event_log_id,
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
            'workflowName': self.workflow_name,
            'workflowVersion': self.workflow_version,
            'workflowDomain': self.workflow_domain,
            'workflowOwner': self.workflow_owner,
        }


class N8nEventService:
    DELIVERY_PENDING = 'PENDING'
    DELIVERY_SKIPPED = 'SKIPPED'
    DELIVERY_DELIVERED = 'DELIVERED'
    DELIVERY_FAILED = 'FAILED'

    WORKFLOW_NOT_STARTED = 'NOT_STARTED'
    WORKFLOW_PENDING = 'PENDING'
    WORKFLOW_RUNNING = 'RUNNING'
    WORKFLOW_COMPLETED = 'COMPLETED'
    WORKFLOW_FAILED = 'FAILED'
    WORKFLOW_CANCELLED = 'CANCELLED'

    MANUAL_PENDING = 'PENDING'
    MANUAL_RESOLVED = 'RESOLVED'

    def __init__(self) -> None:
        self.settings = get_settings()

    def _target_url(self, workflow_path: str) -> str | None:
        base = (self.settings.n8n_webhook_base_url or '').strip().rstrip('/')
        if not base:
            return None
        return f'{base}/{workflow_path.lstrip("/")}'

    def _callback_url(self) -> str:
        return f'{self.settings.resolved_java_public_base_url}/automation/webhooks/workflow-status'

    def _max_attempts(self) -> int:
        return max(1, int(self.settings.n8n_event_max_retries or 0) + 1)

    def _truncate(self, message: str | None) -> str | None:
        if not message:
            return None
        return str(message)[:500]

    def _normalize_workflow_status(self, value: str | None, *, fallback: str | None = None) -> str | None:
        normalized = (value or '').strip().upper()
        if normalized:
            return normalized
        return fallback

    def _resolve_definition(self, workflow_hint: str | None) -> N8nWorkflowDefinition | None:
        return resolve_workflow_definition(workflow_hint)

    def _resolved_workflow_name(self, workflow_hint: str | None, definition: N8nWorkflowDefinition | None) -> str | None:
        if definition is not None:
            return definition.workflow_name
        normalized = (workflow_hint or '').strip()
        return normalized or None

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

    def _headers(
        self,
        *,
        request_id: str,
        idempotency_key: str,
        timestamp: str,
        signature: str | None,
        record: N8nEventLog,
    ) -> dict[str, str]:
        headers = {
            'Content-Type': 'application/json',
            'X-N8N-Webhook-Secret': self.settings.n8n_webhook_secret,
            'X-Request-Id': request_id,
            'X-Idempotency-Key': idempotency_key,
            'X-N8N-Signature-Timestamp': timestamp,
            'X-N8N-Event-Log-Id': str(record.id),
        }
        if record.workflow_name:
            headers['X-N8N-Workflow-Name'] = record.workflow_name
        if record.workflow_version:
            headers['X-N8N-Workflow-Version'] = record.workflow_version
        if record.workflow_domain:
            headers['X-N8N-Workflow-Domain'] = record.workflow_domain
        if record.workflow_owner:
            headers['X-N8N-Workflow-Owner'] = record.workflow_owner
        if signature:
            headers['X-N8N-Signature'] = signature
        return headers

    def _outbound_payload(
        self,
        *,
        record: N8nEventLog,
        request_id: str,
        event_type: str,
        workflow_hint: str,
        idempotency_key: str,
        payload: dict[str, Any],
        definition: N8nWorkflowDefinition | None,
    ) -> dict[str, Any]:
        outbound = dict(payload)
        outbound['_n8n'] = {
            'eventLogId': record.id,
            'requestId': request_id,
            'eventType': event_type,
            'workflowHint': workflow_hint,
            'workflowName': self._resolved_workflow_name(workflow_hint, definition),
            'workflowVersion': definition.version if definition is not None else record.workflow_version,
            'workflowDomain': definition.domain if definition is not None else record.workflow_domain,
            'workflowOwner': definition.owner if definition is not None else record.workflow_owner,
            'assetFile': definition.asset_file if definition is not None else None,
            'deliveryStatus': record.delivery_status,
            'workflowStatus': record.workflow_status,
            'idempotencyKey': idempotency_key,
            'callbackUrl': self._callback_url(),
        }
        return outbound

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

    def _emit_result(self, record: N8nEventLog) -> N8nEmitResult:
        return N8nEmitResult(
            event_log_id=record.id,
            success=record.success_flag,
            delivery_status=record.delivery_status,
            workflow_status=record.workflow_status,
            error_code=record.error_code,
            error_message=record.error_message,
            attempts=record.attempt_count,
            max_attempts=record.max_attempts,
            idempotency_key=record.idempotency_key or '',
            target_url=record.target_url,
            status_code=record.status_code,
            workflow_name=record.workflow_name,
            workflow_version=record.workflow_version,
            workflow_domain=record.workflow_domain,
            workflow_owner=record.workflow_owner,
        )

    def _record_or_404(self, db: Session, event_id: int) -> N8nEventLog:
        record = db.execute(select(N8nEventLog).where(N8nEventLog.id == event_id)).scalar_one_or_none()
        if record is None:
            raise AiServiceError('N8N_EVENT_NOT_FOUND', f'n8n event `{event_id}` was not found', status_code=404)
        return record

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
        replayed_from_event_id: int | None = None,
    ) -> N8nEmitResult:
        target_url = self._target_url(workflow_hint)
        definition = self._resolve_definition(workflow_hint)
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
        initial_workflow_status = self._normalize_workflow_status(workflow_status, fallback=self.WORKFLOW_PENDING)
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
            workflow_status=initial_workflow_status,
            workflow_name=self._resolved_workflow_name(workflow_hint, definition),
            workflow_version=definition.version if definition is not None else None,
            workflow_domain=definition.domain if definition is not None else None,
            workflow_owner=definition.owner if definition is not None else None,
            payload_json=payload_json,
            success_flag=False,
            max_attempts=max_attempts,
            idempotency_key=resolved_idempotency_key,
            delivery_retryable=False,
            manual_replay_count=0,
            replayed_from_event_id=replayed_from_event_id,
        )
        db.add(record)
        db.flush()

        outbound_payload = self._outbound_payload(
            record=record,
            request_id=request_id,
            event_type=event_type,
            workflow_hint=workflow_hint,
            idempotency_key=resolved_idempotency_key,
            payload=payload,
            definition=definition,
        )
        outbound_payload_json = json.dumps(outbound_payload, ensure_ascii=False, sort_keys=True)

        if not self.settings.n8n_enabled:
            record.delivery_status = self.DELIVERY_SKIPPED
            record.workflow_status = self.WORKFLOW_NOT_STARTED
            record.status_code = 503
            record.error_code = 'N8N_DISABLED'
            record.error_message = 'n8n disabled'
            db.flush()
            return self._emit_result(record)

        if not target_url:
            record.delivery_status = self.DELIVERY_SKIPPED
            record.workflow_status = self.WORKFLOW_NOT_STARTED
            record.status_code = 503
            record.error_code = 'N8N_WEBHOOK_NOT_CONFIGURED'
            record.error_message = 'n8n webhook base url not configured'
            db.flush()
            return self._emit_result(record)

        timeout_seconds = max(1, int(self.settings.n8n_event_timeout_seconds or 15))
        timeout = httpx.Timeout(timeout_seconds, connect=min(timeout_seconds, 5.0))
        last_error_code: str | None = None
        last_error_message: str | None = None
        last_status_code: int | None = None
        last_signature: str | None = None
        last_retryable = False

        for attempt in range(1, max_attempts + 1):
            timestamp = str(int(time()))
            signature = self._signature(
                request_id=request_id,
                event_type=event_type,
                workflow_hint=workflow_hint,
                payload_json=outbound_payload_json,
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
                            record=record,
                        ),
                        json=outbound_payload,
                    )
                last_status_code = response.status_code
                if response.status_code < 400:
                    record.status_code = response.status_code
                    record.success_flag = True
                    record.delivery_status = self.DELIVERY_DELIVERED
                    record.error_code = None
                    record.error_message = None
                    record.delivery_retryable = False
                    db.flush()
                    return self._emit_result(record)
                last_error_code, last_retryable = self._failure_from_response(response.status_code)
                last_error_message = self._truncate(response.text)
                if last_retryable and attempt < max_attempts:
                    await self._retry_delay(attempt)
                    continue
                break
            except Exception as exc:
                last_error_code, last_retryable = self._failure_from_exception(exc)
                last_status_code = 503
                last_error_message = self._truncate(str(exc))
                if last_retryable and attempt < max_attempts:
                    await self._retry_delay(attempt)
                    continue
                break

        record.status_code = last_status_code
        record.success_flag = False
        record.delivery_status = self.DELIVERY_FAILED
        record.workflow_status = self.WORKFLOW_NOT_STARTED
        record.error_code = last_error_code
        record.error_message = last_error_message
        record.signature = last_signature
        record.delivery_retryable = last_retryable
        db.flush()
        return self._emit_result(record)

    def to_view(self, record: N8nEventLog) -> N8nEventView:
        return N8nEventView.model_validate(record)

    def list_events(
        self,
        db: Session,
        *,
        page_num: int = 1,
        page_size: int = 20,
        q: str | None = None,
        delivery_status: str | None = None,
        workflow_status: str | None = None,
        manual_status: str | None = None,
        event_type: str | None = None,
        created_date: date | None = None,
    ) -> PageData:
        safe_page_num = max(page_num, 1)
        safe_page_size = max(1, min(page_size, 100))
        filters = []
        keyword = (q or '').strip()
        if keyword:
            fuzzy = f'%{keyword}%'
            filters.append(
                or_(
                    N8nEventLog.request_id.like(fuzzy),
                    N8nEventLog.event_type.like(fuzzy),
                    N8nEventLog.workflow_hint.like(fuzzy),
                    N8nEventLog.workflow_name.like(fuzzy),
                    N8nEventLog.workflow_domain.like(fuzzy),
                    N8nEventLog.workflow_owner.like(fuzzy),
                    N8nEventLog.error_code.like(fuzzy),
                    N8nEventLog.error_message.like(fuzzy),
                    N8nEventLog.idempotency_key.like(fuzzy),
                    N8nEventLog.external_execution_id.like(fuzzy),
                )
            )
        if delivery_status:
            filters.append(N8nEventLog.delivery_status == delivery_status.strip().upper())
        if workflow_status:
            filters.append(N8nEventLog.workflow_status == workflow_status.strip().upper())
        if manual_status:
            filters.append(N8nEventLog.manual_status == manual_status.strip().upper())
        if event_type:
            filters.append(N8nEventLog.event_type == event_type.strip())
        if created_date is not None:
            day_start = datetime.combine(created_date, datetime.min.time())
            filters.append(N8nEventLog.created_at >= day_start)
            filters.append(N8nEventLog.created_at < day_start + timedelta(days=1))

        query = select(N8nEventLog)
        count_query = select(func.count(N8nEventLog.id))
        if filters:
            query = query.where(*filters)
            count_query = count_query.where(*filters)

        total = int(db.execute(count_query).scalar_one() or 0)
        rows = db.execute(
            query.order_by(N8nEventLog.created_at.desc(), N8nEventLog.id.desc())
            .offset((safe_page_num - 1) * safe_page_size)
            .limit(safe_page_size)
        ).scalars().all()
        total_pages = (total + safe_page_size - 1) // safe_page_size if total else 0
        return PageData(
            records=[self.to_view(item) for item in rows],
            page_num=safe_page_num,
            page_size=safe_page_size,
            total=total,
            total_pages=total_pages,
        )

    def get_event_detail(self, db: Session, event_id: int) -> N8nEventDetailView:
        record = self._record_or_404(db, event_id)
        source_event = None
        if record.replayed_from_event_id:
            source = db.execute(select(N8nEventLog).where(N8nEventLog.id == record.replayed_from_event_id)).scalar_one_or_none()
            if source is not None:
                source_event = self.to_view(source)
        replay_events = db.execute(
            select(N8nEventLog)
            .where(N8nEventLog.replayed_from_event_id == event_id)
            .order_by(N8nEventLog.created_at.asc(), N8nEventLog.id.asc())
        ).scalars().all()
        return N8nEventDetailView(
            event=self.to_view(record),
            source_event=source_event,
            replay_events=[self.to_view(item) for item in replay_events],
        )

    def summary(self, db: Session, *, window_hours: int = 24) -> N8nEventSummaryView:
        hours = max(1, min(window_hours, 168))
        since = datetime.utcnow() - timedelta(hours=hours)
        total = int(db.execute(select(func.count(N8nEventLog.id)).where(N8nEventLog.created_at >= since)).scalar_one() or 0)
        delivered = int(
            db.execute(
                select(func.count(N8nEventLog.id)).where(
                    N8nEventLog.created_at >= since,
                    N8nEventLog.delivery_status == self.DELIVERY_DELIVERED,
                )
            ).scalar_one()
            or 0
        )
        failed = int(
            db.execute(
                select(func.count(N8nEventLog.id)).where(
                    N8nEventLog.created_at >= since,
                    N8nEventLog.delivery_status == self.DELIVERY_FAILED,
                )
            ).scalar_one()
            or 0
        )
        pending = int(
            db.execute(
                select(func.count(N8nEventLog.id)).where(
                    N8nEventLog.created_at >= since,
                    N8nEventLog.delivery_status == self.DELIVERY_PENDING,
                )
            ).scalar_one()
            or 0
        )
        skipped = int(
            db.execute(
                select(func.count(N8nEventLog.id)).where(
                    N8nEventLog.created_at >= since,
                    N8nEventLog.delivery_status == self.DELIVERY_SKIPPED,
                )
            ).scalar_one()
            or 0
        )
        workflow_failed = int(
            db.execute(
                select(func.count(N8nEventLog.id)).where(
                    N8nEventLog.created_at >= since,
                    N8nEventLog.workflow_status == self.WORKFLOW_FAILED,
                )
            ).scalar_one()
            or 0
        )
        workflow_running = int(
            db.execute(
                select(func.count(N8nEventLog.id)).where(
                    N8nEventLog.created_at >= since,
                    N8nEventLog.workflow_status == self.WORKFLOW_RUNNING,
                )
            ).scalar_one()
            or 0
        )
        manual_pending = int(
            db.execute(
                select(func.count(N8nEventLog.id)).where(
                    N8nEventLog.created_at >= since,
                    N8nEventLog.manual_status == self.MANUAL_PENDING,
                )
            ).scalar_one()
            or 0
        )
        retryable_failed = int(
            db.execute(
                select(func.count(N8nEventLog.id)).where(
                    N8nEventLog.created_at >= since,
                    N8nEventLog.delivery_status == self.DELIVERY_FAILED,
                    N8nEventLog.delivery_retryable.is_(True),
                )
            ).scalar_one()
            or 0
        )
        latest_failure = db.execute(
            select(N8nEventLog)
            .where(
                N8nEventLog.created_at >= since,
                or_(
                    N8nEventLog.delivery_status == self.DELIVERY_FAILED,
                    N8nEventLog.workflow_status == self.WORKFLOW_FAILED,
                ),
            )
            .order_by(N8nEventLog.updated_at.desc(), N8nEventLog.id.desc())
            .limit(1)
        ).scalars().first()
        top_error_rows = db.execute(
            select(N8nEventLog.error_code, func.count(N8nEventLog.id))
            .where(
                N8nEventLog.created_at >= since,
                N8nEventLog.error_code.is_not(None),
            )
            .group_by(N8nEventLog.error_code)
            .order_by(func.count(N8nEventLog.id).desc(), N8nEventLog.error_code.asc())
            .limit(5)
        ).all()
        success_rate = round((delivered / total) * 100, 1) if total else 0.0
        return N8nEventSummaryView(
            window_hours=hours,
            total_events=total,
            delivered_count=delivered,
            failed_count=failed,
            pending_count=pending,
            skipped_count=skipped,
            workflow_failed_count=workflow_failed,
            workflow_running_count=workflow_running,
            manual_pending_count=manual_pending,
            retryable_failed_count=retryable_failed,
            success_rate=success_rate,
            latest_failure=self.to_view(latest_failure) if latest_failure is not None else None,
            top_error_codes=[
                N8nErrorMetricView(error_code=error_code, count=int(count or 0))
                for error_code, count in top_error_rows
                if error_code
            ],
        )

    async def retry_event(self, db: Session, event_id: int) -> N8nRetryResultView:
        record = self._record_or_404(db, event_id)
        if not record.workflow_hint:
            raise AiServiceError('N8N_EVENT_NOT_RETRYABLE', 'Event does not contain a workflow hint and cannot be retried', status_code=400)
        try:
            payload = json.loads(record.payload_json or '{}')
        except json.JSONDecodeError as exc:
            raise AiServiceError('N8N_EVENT_INVALID_PAYLOAD', 'Stored n8n payload is invalid and cannot be retried', status_code=422) from exc
        if not isinstance(payload, dict):
            raise AiServiceError('N8N_EVENT_INVALID_PAYLOAD', 'Stored n8n payload must be an object to retry delivery', status_code=422)

        record.manual_replay_count = (record.manual_replay_count or 0) + 1
        if record.manual_status == self.MANUAL_PENDING:
            record.manual_status = self.MANUAL_RESOLVED
        retry_request_id = f'n8n-retry-{event_id}-{uuid4().hex[:12]}'
        result = await self.emit(
            db,
            request_id=retry_request_id,
            event_type=record.event_type,
            workflow_hint=record.workflow_hint,
            payload=payload,
            approval_id=record.approval_id,
            session_id=record.session_id,
            user_id=record.user_id,
            project_id=record.project_id,
            workflow_status=self.WORKFLOW_PENDING,
            idempotency_key=record.idempotency_key,
            replayed_from_event_id=record.id,
        )
        retried_record = self._record_or_404(db, result.event_log_id or 0)
        db.flush()
        return N8nRetryResultView(
            retried=True,
            source_event_id=event_id,
            event=self.to_view(retried_record),
        )

    def manual_handoff_event(self, db: Session, event_id: int, *, reason: str | None = None) -> N8nEventView:
        record = self._record_or_404(db, event_id)
        can_handoff = (
            record.delivery_status in {self.DELIVERY_FAILED, self.DELIVERY_SKIPPED}
            or record.workflow_status in {self.WORKFLOW_FAILED, self.WORKFLOW_CANCELLED, self.WORKFLOW_NOT_STARTED}
        )
        if not can_handoff:
            raise AiServiceError('N8N_EVENT_NOT_HANDOFFABLE', 'Only failed, skipped, or cancelled workflow events can be transferred to manual handling', status_code=409)
        record.manual_status = self.MANUAL_PENDING
        record.manual_reason = self._truncate(reason or 'Sent to manual handling from admin console')
        db.flush()
        return self.to_view(record)


n8n_event_service = N8nEventService()
