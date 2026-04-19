from __future__ import annotations

from typing import Literal

from pydantic import Field

from src.app.schemas.chat import ChatContext, UsageView
from src.app.schemas.common import CamelModel
from src.app.schemas.message import CitationView


AgentType = Literal['project_qa', 'document_summary_compare', 'project_weekly_report']


class AgentRunRequest(CamelModel):
    agent_type: AgentType
    session_id: int | None = None
    scene: str = 'general_chat'
    model_code: str | None = None
    message: str
    context: ChatContext = Field(default_factory=ChatContext)
    temperature: float | None = 0.3
    max_tokens: int | None = 2048
    web_search_enabled: bool | None = None
    similarity_threshold: float | None = None
    top_k: int | None = None
    query_rewrite_enabled: bool | None = None
    strict_citation_enabled: bool | None = None


class AgentRunView(CamelModel):
    request_id: str
    agent_type: AgentType
    answer: str
    citations: list[CitationView] = Field(default_factory=list)
    used_tools: list[str] = Field(default_factory=list)
    provider_code: str
    model_code: str
    confidence: float | None = None
    latency_ms: int
    execution_trace: list[str] = Field(default_factory=list)
    fallback_used: bool = False
    fallback_reason: str | None = None
    usage: UsageView = Field(default_factory=UsageView)
