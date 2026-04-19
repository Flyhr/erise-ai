from __future__ import annotations

from sqlalchemy import Boolean, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from src.app.models.base import Base, IdType, TimestampMixin


class AiActionLog(Base, TimestampMixin):
    __tablename__ = 'ai_action_log'

    id: Mapped[int] = mapped_column(IdType, primary_key=True, autoincrement=True)
    request_id: Mapped[str] = mapped_column(String(128), index=True, nullable=False)
    session_id: Mapped[int | None] = mapped_column(IdType, index=True, nullable=True)
    user_id: Mapped[int] = mapped_column(IdType, index=True, nullable=False, default=0)
    org_id: Mapped[int] = mapped_column(IdType, nullable=False, default=0)
    project_id: Mapped[int | None] = mapped_column(IdType, index=True, nullable=True)
    action_code: Mapped[str] = mapped_column(String(64), index=True, nullable=False)
    match_rule: Mapped[str] = mapped_column(String(128), nullable=False)
    permission_rule: Mapped[str] = mapped_column(String(128), nullable=False)
    action_status: Mapped[str] = mapped_column(String(32), index=True, nullable=False)
    target_type: Mapped[str | None] = mapped_column(String(32), nullable=True)
    target_id: Mapped[int | None] = mapped_column(IdType, nullable=True)
    model_code: Mapped[str | None] = mapped_column(String(128), nullable=True)
    provider_code: Mapped[str | None] = mapped_column(String(64), nullable=True)
    params_json: Mapped[str | None] = mapped_column(Text, nullable=True)
    result_payload_json: Mapped[str | None] = mapped_column(Text, nullable=True)
    fallback_message: Mapped[str | None] = mapped_column(String(500), nullable=True)
    error_code: Mapped[str | None] = mapped_column(String(64), nullable=True)
    error_message: Mapped[str | None] = mapped_column(String(500), nullable=True)
    latency_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
    success_flag: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
