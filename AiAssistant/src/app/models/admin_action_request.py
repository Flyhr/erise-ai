from __future__ import annotations

from sqlalchemy import String, Text
from sqlalchemy.orm import Mapped, mapped_column

from src.app.models.base import Base, IdType, TimestampMixin


class AdminActionRequest(Base, TimestampMixin):
    __tablename__ = 'admin_action_request'

    id: Mapped[int] = mapped_column(IdType, primary_key=True, autoincrement=True)
    approval_request_id: Mapped[int] = mapped_column(IdType, index=True, nullable=False)
    request_id: Mapped[str] = mapped_column(String(128), index=True, nullable=False)
    initiated_user_id: Mapped[int] = mapped_column(IdType, index=True, nullable=False)
    confirmed_user_id: Mapped[int | None] = mapped_column(IdType, nullable=True)
    executed_user_id: Mapped[int | None] = mapped_column(IdType, nullable=True)
    action_code: Mapped[str] = mapped_column(String(64), index=True, nullable=False)
    action_status: Mapped[str] = mapped_column(String(32), index=True, nullable=False)
    target_type: Mapped[str | None] = mapped_column(String(32), nullable=True)
    target_id: Mapped[int | None] = mapped_column(IdType, nullable=True)
    audit_payload_json: Mapped[str | None] = mapped_column(Text, nullable=True)
