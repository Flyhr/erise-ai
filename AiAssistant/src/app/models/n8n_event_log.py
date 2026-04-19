from __future__ import annotations

from sqlalchemy import Boolean, Integer, String, Text
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
    status_code: Mapped[int | None] = mapped_column(Integer, nullable=True)
    success_flag: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    error_message: Mapped[str | None] = mapped_column(String(500), nullable=True)
    payload_json: Mapped[str | None] = mapped_column(Text, nullable=True)
