from __future__ import annotations

from sqlalchemy import Float, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from src.app.models.base import Base, IdType, TimestampMixin


class AiMessageCitation(Base, TimestampMixin):
    __tablename__ = 'ai_message_citation'

    id: Mapped[int] = mapped_column(IdType, primary_key=True, autoincrement=True)
    message_id: Mapped[int] = mapped_column(IdType, index=True, nullable=False)
    session_id: Mapped[int] = mapped_column(IdType, index=True, nullable=False)
    user_id: Mapped[int] = mapped_column(IdType, index=True, nullable=False)
    position_no: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    source_type: Mapped[str] = mapped_column(String(32), nullable=False)
    source_id: Mapped[int] = mapped_column(IdType, nullable=False)
    source_title: Mapped[str] = mapped_column(String(255), nullable=False)
    snippet: Mapped[str | None] = mapped_column(Text, nullable=True)
    page_no: Mapped[int | None] = mapped_column(Integer, nullable=True)
    score: Mapped[float | None] = mapped_column(Float, nullable=True)
    url: Mapped[str | None] = mapped_column(String(1000), nullable=True)
