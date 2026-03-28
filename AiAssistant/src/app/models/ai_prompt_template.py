from __future__ import annotations

from sqlalchemy import Boolean, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from src.app.models.base import Base, IdType, TimestampMixin


class AiPromptTemplate(Base, TimestampMixin):
    __tablename__ = 'ai_prompt_template'

    id: Mapped[int] = mapped_column(IdType, primary_key=True, autoincrement=True)
    template_code: Mapped[str] = mapped_column(String(128), unique=True, nullable=False)
    template_name: Mapped[str] = mapped_column(String(255), nullable=False)
    scene: Mapped[str] = mapped_column(String(32), nullable=False, index=True)
    system_prompt: Mapped[str] = mapped_column(Text, nullable=False)
    user_prompt_wrapper: Mapped[str | None] = mapped_column(Text, nullable=True)
    enabled: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    version_no: Mapped[int] = mapped_column(Integer, nullable=False, default=1)
    created_by: Mapped[str | None] = mapped_column(String(64), nullable=True)
