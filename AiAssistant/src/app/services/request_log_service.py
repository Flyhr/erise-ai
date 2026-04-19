from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any

from sqlalchemy.orm import Session

from src.app.adapters.llm.base import AdapterUsage
from src.app.api.deps import RequestContext
from src.app.models.ai_request_log import AiRequestLog
from src.app.models.ai_session import AiChatSession


def _normalize_payload(value: object | None) -> str | None:
    if value is None:
        return None
    if isinstance(value, str):
        return value

    def _normalize(item: object) -> object:
        if hasattr(item, 'model_dump'):
            return item.model_dump(by_alias=True)
        return item

    if isinstance(value, list):
        return json.dumps([_normalize(item) for item in value], ensure_ascii=False)
    return json.dumps(_normalize(value), ensure_ascii=False)


@dataclass(slots=True)
class AiRequestLogRecord:
    request_id: str
    session_id: int
    user_message_id: int | None
    assistant_message_id: int | None
    provider_code: str
    model_code: str
    scene: str
    temperature: float | None
    max_tokens: int | None
    stream: bool
    request_payload: dict[str, object] | list[object] | str | None
    response_payload: dict[str, object] | list[object] | str | None
    answer_source: str | None
    message_status: str | None
    usage: AdapterUsage
    latency_ms: int | None
    success_flag: bool
    error_code: str | None = None
    error_message: str | None = None


class RequestLogService:
    def save(
        self,
        db: Session,
        context: RequestContext,
        session: AiChatSession,
        record: AiRequestLogRecord,
    ) -> None:
        total_tokens = record.usage.total_tokens
        if total_tokens <= 0:
            total_tokens = (record.usage.prompt_tokens or 0) + (record.usage.completion_tokens or 0)

        latency_ms = record.latency_ms
        db.add(
            AiRequestLog(
                request_id=record.request_id,
                session_id=record.session_id,
                user_id=context.user_id,
                org_id=context.org_id,
                project_id=session.project_id,
                user_message_id=record.user_message_id,
                assistant_message_id=record.assistant_message_id,
                provider_code=record.provider_code,
                model_code=record.model_code,
                scene=record.scene,
                temperature=record.temperature,
                max_tokens=record.max_tokens,
                stream=record.stream,
                request_payload_json=_normalize_payload(record.request_payload),
                response_payload_json=_normalize_payload(record.response_payload),
                answer_source=record.answer_source,
                message_status=record.message_status,
                input_token_count=record.usage.prompt_tokens,
                output_token_count=record.usage.completion_tokens,
                total_token_count=total_tokens,
                latency_ms=latency_ms,
                duration_ms=latency_ms,
                success_flag=record.success_flag,
                error_code=record.error_code,
                error_message=record.error_message,
            )
        )


request_log_service = RequestLogService()
