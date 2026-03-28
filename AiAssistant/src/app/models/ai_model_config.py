from __future__ import annotations

from sqlalchemy import Boolean, Integer, String
from sqlalchemy.orm import Mapped, mapped_column

from src.app.models.base import Base, IdType, TimestampMixin


class AiModelConfig(Base, TimestampMixin):
    __tablename__ = 'ai_model_config'

    id: Mapped[int] = mapped_column(IdType, primary_key=True, autoincrement=True)
    provider_code: Mapped[str] = mapped_column(String(64), nullable=False)
    model_code: Mapped[str] = mapped_column(String(128), unique=True, nullable=False)
    model_name: Mapped[str] = mapped_column(String(255), nullable=False)
    base_url: Mapped[str | None] = mapped_column(String(255), nullable=True)
    api_key_ref: Mapped[str | None] = mapped_column(String(64), nullable=True)
    enabled: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    support_stream: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    support_system_prompt: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    max_context_tokens: Mapped[int | None] = mapped_column(Integer, nullable=True)
    priority_no: Mapped[int] = mapped_column(Integer, nullable=False, default=1)
