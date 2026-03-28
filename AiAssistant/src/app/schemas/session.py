from __future__ import annotations

from datetime import datetime

from pydantic import Field

from src.app.schemas.common import CamelModel
from src.app.schemas.message import MessageView


class SessionCreateRequest(CamelModel):
    project_id: int | None = None
    scene: str = 'general_chat'
    title: str | None = None


class SessionSummaryView(CamelModel):
    id: int
    user_id: int
    org_id: int
    project_id: int | None = None
    scene: str
    title: str
    summary_text: str | None = None
    last_message_at: datetime | None = None
    message_count: int
    status: str
    created_at: datetime
    updated_at: datetime


class SessionDetailView(SessionSummaryView):
    messages: list[MessageView] = Field(default_factory=list)
