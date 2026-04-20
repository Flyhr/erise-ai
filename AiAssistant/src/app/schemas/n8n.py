from __future__ import annotations

from datetime import datetime

from pydantic import Field

from src.app.schemas.common import CamelModel


class N8nErrorMetricView(CamelModel):
    error_code: str
    count: int


class N8nEventView(CamelModel):
    id: int
    request_id: str
    event_type: str
    workflow_hint: str | None = None
    approval_id: int | None = None
    session_id: int | None = None
    user_id: int | None = None
    project_id: int | None = None
    target_url: str | None = None
    delivery_status: str
    workflow_status: str | None = None
    workflow_name: str | None = None
    workflow_version: str | None = None
    workflow_domain: str | None = None
    workflow_owner: str | None = None
    external_execution_id: str | None = None
    workflow_error_summary: str | None = None
    workflow_duration_ms: int | None = None
    delivery_retryable: bool = False
    manual_status: str | None = None
    manual_reason: str | None = None
    manual_replay_count: int = 0
    replayed_from_event_id: int | None = None
    last_callback_at: datetime | None = None
    status_code: int | None = None
    success_flag: bool
    error_code: str | None = None
    error_message: str | None = None
    attempt_count: int
    max_attempts: int
    idempotency_key: str | None = None
    callback_payload_json: str | None = None
    created_at: datetime
    updated_at: datetime


class N8nEventSummaryView(CamelModel):
    window_hours: int
    total_events: int
    delivered_count: int
    failed_count: int
    pending_count: int
    skipped_count: int
    workflow_failed_count: int
    workflow_running_count: int
    manual_pending_count: int
    retryable_failed_count: int
    success_rate: float
    latest_failure: N8nEventView | None = None
    top_error_codes: list[N8nErrorMetricView] = Field(default_factory=list)


class N8nEventDetailView(CamelModel):
    event: N8nEventView
    source_event: N8nEventView | None = None
    replay_events: list[N8nEventView] = Field(default_factory=list)


class N8nRetryResultView(CamelModel):
    retried: bool
    source_event_id: int
    event: N8nEventView


class N8nManualHandoffRequest(CamelModel):
    reason: str | None = None
