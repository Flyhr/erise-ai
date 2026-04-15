from __future__ import annotations

from sqlalchemy import Boolean, Float, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from src.app.models.base import Base, IdType, TimestampMixin


class AiRequestLog(Base, TimestampMixin):
    __tablename__ = 'ai_request_log'

    id: Mapped[int] = mapped_column(IdType, primary_key=True, autoincrement=True)
    request_id: Mapped[str] = mapped_column(String(128), index=True, nullable=False)
    session_id: Mapped[int] = mapped_column(IdType, index=True, nullable=False)
    user_id: Mapped[int] = mapped_column(IdType, index=True, nullable=False, default=0)
    org_id: Mapped[int] = mapped_column(IdType, nullable=False, default=0)
    project_id: Mapped[int | None] = mapped_column(IdType, index=True, nullable=True)
    user_message_id: Mapped[int | None] = mapped_column(IdType, nullable=True)
    assistant_message_id: Mapped[int | None] = mapped_column(IdType, nullable=True)
    provider_code: Mapped[str] = mapped_column(String(64), nullable=False)
    model_code: Mapped[str] = mapped_column(String(128), nullable=False)
    scene: Mapped[str] = mapped_column(String(32), nullable=False)
    temperature: Mapped[float | None] = mapped_column(Float, nullable=True)
    max_tokens: Mapped[int | None] = mapped_column(Integer, nullable=True)
    stream: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    request_payload_json: Mapped[str | None] = mapped_column(Text, nullable=True)
    response_payload_json: Mapped[str | None] = mapped_column(Text, nullable=True)
    answer_source: Mapped[str | None] = mapped_column(String(64), nullable=True)
    message_status: Mapped[str | None] = mapped_column(String(32), nullable=True)
    input_token_count: Mapped[int | None] = mapped_column(Integer, nullable=True)
    output_token_count: Mapped[int | None] = mapped_column(Integer, nullable=True)
    total_token_count: Mapped[int | None] = mapped_column(Integer, nullable=True)
    latency_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
    duration_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
    success_flag: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    error_code: Mapped[str | None] = mapped_column(String(64), nullable=True)
    error_message: Mapped[str | None] = mapped_column(String(500), nullable=True)
