from __future__ import annotations

from datetime import datetime

from sqlalchemy import DateTime, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from src.app.models.base import Base, IdType, TimestampMixin


class ApprovalRequest(Base, TimestampMixin):
    __tablename__ = 'approval_request'

    id: Mapped[int] = mapped_column(IdType, primary_key=True, autoincrement=True)
    request_id: Mapped[str] = mapped_column(String(128), index=True, nullable=False)
    session_id: Mapped[int | None] = mapped_column(IdType, index=True, nullable=True)
    initiated_user_id: Mapped[int] = mapped_column(IdType, index=True, nullable=False)
    confirmed_user_id: Mapped[int | None] = mapped_column(IdType, nullable=True)
    executed_user_id: Mapped[int | None] = mapped_column(IdType, nullable=True)
    org_id: Mapped[int] = mapped_column(IdType, nullable=False, default=0)
    project_id: Mapped[int | None] = mapped_column(IdType, index=True, nullable=True)
    action_code: Mapped[str] = mapped_column(String(64), index=True, nullable=False)
    target_type: Mapped[str | None] = mapped_column(String(32), nullable=True)
    target_id: Mapped[int | None] = mapped_column(IdType, nullable=True)
    status: Mapped[str] = mapped_column(String(32), index=True, nullable=False, default='PENDING')
    risk_level: Mapped[str] = mapped_column(String(32), nullable=False, default='HIGH')
    plan_summary: Mapped[str] = mapped_column(String(1000), nullable=False)
    request_payload_json: Mapped[str | None] = mapped_column(Text, nullable=True)
    params_json: Mapped[str | None] = mapped_column(Text, nullable=True)
    resource_snapshot_json: Mapped[str | None] = mapped_column(Text, nullable=True)
    result_payload_json: Mapped[str | None] = mapped_column(Text, nullable=True)
    error_code: Mapped[str | None] = mapped_column(String(64), nullable=True)
    error_message: Mapped[str | None] = mapped_column(String(500), nullable=True)
    confirmed_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    executed_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    expires_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    latency_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
