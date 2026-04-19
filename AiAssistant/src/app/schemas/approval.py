from __future__ import annotations

from datetime import datetime
from typing import Any

from src.app.schemas.common import CamelModel


class ApprovalView(CamelModel):
    id: int
    request_id: str
    session_id: int | None = None
    initiated_user_id: int
    confirmed_user_id: int | None = None
    executed_user_id: int | None = None
    org_id: int
    project_id: int | None = None
    action_code: str
    target_type: str | None = None
    target_id: int | None = None
    status: str
    risk_level: str
    plan_summary: str
    params: dict[str, Any] | None = None
    result_payload: dict[str, Any] | None = None
    error_code: str | None = None
    error_message: str | None = None
    confirmed_at: datetime | None = None
    executed_at: datetime | None = None
    expires_at: datetime | None = None


class ApprovalConfirmRequest(CamelModel):
    comment: str | None = None


class ApprovalRejectRequest(CamelModel):
    reason: str | None = None
