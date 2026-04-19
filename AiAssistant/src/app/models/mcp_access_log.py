from __future__ import annotations

from sqlalchemy import Boolean, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from src.app.models.base import Base, IdType, TimestampMixin


class McpAccessLog(Base, TimestampMixin):
    __tablename__ = 'mcp_access_log'

    id: Mapped[int] = mapped_column(IdType, primary_key=True, autoincrement=True)
    request_id: Mapped[str] = mapped_column(String(128), index=True, nullable=False)
    user_id: Mapped[int] = mapped_column(IdType, index=True, nullable=False)
    org_id: Mapped[int] = mapped_column(IdType, nullable=False, default=0)
    username: Mapped[str | None] = mapped_column(String(128), nullable=True)
    role_code: Mapped[str | None] = mapped_column(String(32), nullable=True)
    method: Mapped[str] = mapped_column(String(64), index=True, nullable=False)
    tool_name: Mapped[str | None] = mapped_column(String(128), nullable=True)
    resource_uri: Mapped[str | None] = mapped_column(String(1000), nullable=True)
    status_code: Mapped[int | None] = mapped_column(Integer, nullable=True)
    success_flag: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    error_code: Mapped[str | None] = mapped_column(String(64), nullable=True)
    error_message: Mapped[str | None] = mapped_column(String(500), nullable=True)
    request_payload_json: Mapped[str | None] = mapped_column(Text, nullable=True)
    response_payload_json: Mapped[str | None] = mapped_column(Text, nullable=True)
