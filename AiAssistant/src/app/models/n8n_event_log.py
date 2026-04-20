from __future__ import annotations

from datetime import datetime

from sqlalchemy import Boolean, DateTime, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from src.app.models.base import Base, IdType, TimestampMixin


class N8nEventLog(Base, TimestampMixin):
    __tablename__ = 'n8n_event_log'

    id: Mapped[int] = mapped_column(IdType, primary_key=True, autoincrement=True)
    request_id: Mapped[str] = mapped_column(String(128), index=True, nullable=False)
    event_type: Mapped[str] = mapped_column(String(64), index=True, nullable=False)
    workflow_hint: Mapped[str | None] = mapped_column(String(128), nullable=True)
    approval_id: Mapped[int | None] = mapped_column(IdType, nullable=True)
    session_id: Mapped[int | None] = mapped_column(IdType, nullable=True)
    user_id: Mapped[int | None] = mapped_column(IdType, nullable=True)
    project_id: Mapped[int | None] = mapped_column(IdType, nullable=True)
    target_url: Mapped[str | None] = mapped_column(String(1000), nullable=True)
    delivery_status: Mapped[str] = mapped_column(String(32), nullable=False, default='PENDING')
    workflow_status: Mapped[str | None] = mapped_column(String(64), nullable=True)
    workflow_name: Mapped[str | None] = mapped_column(String(128), nullable=True)
    workflow_version: Mapped[str | None] = mapped_column(String(32), nullable=True)
    workflow_domain: Mapped[str | None] = mapped_column(String(64), nullable=True)
    workflow_owner: Mapped[str | None] = mapped_column(String(128), nullable=True)
    external_execution_id: Mapped[str | None] = mapped_column(String(128), nullable=True)
    workflow_error_summary: Mapped[str | None] = mapped_column(String(500), nullable=True)
    workflow_duration_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
    delivery_retryable: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    manual_status: Mapped[str | None] = mapped_column(String(32), nullable=True)
    manual_reason: Mapped[str | None] = mapped_column(String(500), nullable=True)
    manual_replay_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    replayed_from_event_id: Mapped[int | None] = mapped_column(IdType, nullable=True)
    last_callback_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    status_code: Mapped[int | None] = mapped_column(Integer, nullable=True)
    success_flag: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    error_code: Mapped[str | None] = mapped_column(String(64), nullable=True)
    error_message: Mapped[str | None] = mapped_column(String(500), nullable=True)
    attempt_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    max_attempts: Mapped[int] = mapped_column(Integer, nullable=False, default=1)
    idempotency_key: Mapped[str | None] = mapped_column(String(255), index=True, nullable=True)
    signature: Mapped[str | None] = mapped_column(String(128), nullable=True)
    payload_json: Mapped[str | None] = mapped_column(Text, nullable=True)
    callback_payload_json: Mapped[str | None] = mapped_column(Text, nullable=True)
