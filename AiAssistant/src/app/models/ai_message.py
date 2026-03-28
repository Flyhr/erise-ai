from __future__ import annotations

from sqlalchemy import Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from src.app.models.base import Base, IdType, TimestampMixin


class AiChatMessage(Base, TimestampMixin):
    __tablename__ = 'ai_chat_message'

    id: Mapped[int] = mapped_column(IdType, primary_key=True, autoincrement=True)
    session_id: Mapped[int] = mapped_column(IdType, index=True, nullable=False)
    user_id: Mapped[int] = mapped_column(IdType, index=True, nullable=False)
    role: Mapped[str] = mapped_column(String(32), nullable=False)
    content: Mapped[str] = mapped_column(Text, nullable=False, default='')
    content_format: Mapped[str] = mapped_column(String(32), nullable=False, default='text')
    message_status: Mapped[str] = mapped_column(String(32), nullable=False, default='success', index=True)
    sequence_no: Mapped[int] = mapped_column(Integer, nullable=False)
    model_code: Mapped[str | None] = mapped_column(String(128), nullable=True)
    provider_code: Mapped[str | None] = mapped_column(String(64), nullable=True)
    prompt_tokens: Mapped[int | None] = mapped_column(Integer, nullable=True)
    completion_tokens: Mapped[int | None] = mapped_column(Integer, nullable=True)
    total_tokens: Mapped[int | None] = mapped_column(Integer, nullable=True)
    latency_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
    error_code: Mapped[str | None] = mapped_column(String(64), nullable=True)
    error_message: Mapped[str | None] = mapped_column(String(500), nullable=True)
    request_id: Mapped[str | None] = mapped_column(String(128), nullable=True, index=True)
    parent_message_id: Mapped[int | None] = mapped_column(IdType, nullable=True)
