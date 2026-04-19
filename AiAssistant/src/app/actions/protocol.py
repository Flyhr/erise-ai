from __future__ import annotations

from dataclasses import dataclass, field
from typing import Awaitable, Callable, Generic, TypeVar

from sqlalchemy.orm import Session

from src.app.adapters.llm.base import AdapterUsage
from src.app.api.deps import RequestContext
from src.app.schemas.chat import ChatCompletionRequest
from src.app.schemas.message import CitationView


@dataclass(slots=True)
class ActionRuntimeContext:
    db: Session
    user_context: RequestContext
    request: ChatCompletionRequest
    request_id: str
    session_id: int | None = None

    @property
    def user_id(self) -> int:
        return self.user_context.user_id

    @property
    def org_id(self) -> int:
        return self.user_context.org_id


@dataclass(slots=True)
class ActionPermissionDecision:
    allowed: bool
    fallback_message: str | None = None
    target_type: str | None = None
    target_id: int | None = None
    resource: dict[str, object] | None = None


@dataclass(slots=True)
class ActionExecutionResult:
    action_code: str
    answer: str
    answer_source: str
    used_tools: list[str]
    provider_code: str
    model_code: str
    usage: AdapterUsage = field(default_factory=AdapterUsage)
    citations: list[CitationView] = field(default_factory=list)
    raw_payload: dict[str, object] | None = None
    success_flag: bool = True
    fallback_message: str | None = None
    error_code: str | None = None
    error_message: str | None = None
    latency_ms: int | None = None
    target_type: str | None = None
    target_id: int | None = None


ParamsT = TypeVar('ParamsT')

MatchRule = Callable[[ChatCompletionRequest], ParamsT | None]
PermissionRule = Callable[[ActionRuntimeContext, ParamsT], Awaitable[ActionPermissionDecision]]
Executor = Callable[[ActionRuntimeContext, ParamsT, ActionPermissionDecision], Awaitable[ActionExecutionResult]]


@dataclass(slots=True)
class ActionDefinition(Generic[ParamsT]):
    action_code: str
    match_rule: MatchRule
    param_schema: type
    permission_rule: PermissionRule
    executor: Executor
    fallback_message: str

    @property
    def match_rule_name(self) -> str:
        return getattr(self.match_rule, '__name__', 'match_rule')

    @property
    def permission_rule_name(self) -> str:
        return getattr(self.permission_rule, '__name__', 'permission_rule')
