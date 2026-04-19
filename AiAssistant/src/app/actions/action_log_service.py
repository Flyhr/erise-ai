from __future__ import annotations

import json
from dataclasses import dataclass

from sqlalchemy.orm import Session

from src.app.api.deps import RequestContext
from src.app.models.ai_action_log import AiActionLog


def _normalize_payload(value: object | None) -> str | None:
    if value is None:
        return None
    if isinstance(value, str):
        return value
    if hasattr(value, 'model_dump'):
        return json.dumps(value.model_dump(by_alias=True), ensure_ascii=False)
    if isinstance(value, list):
        normalized = [item.model_dump(by_alias=True) if hasattr(item, 'model_dump') else item for item in value]
        return json.dumps(normalized, ensure_ascii=False)
    return json.dumps(value, ensure_ascii=False)


@dataclass(slots=True)
class AiActionLogRecord:
    request_id: str
    session_id: int | None
    project_id: int | None
    action_code: str
    match_rule: str
    permission_rule: str
    action_status: str
    target_type: str | None
    target_id: int | None
    model_code: str | None
    provider_code: str | None
    params: object | None
    result_payload: object | None
    fallback_message: str | None
    error_code: str | None
    error_message: str | None
    latency_ms: int | None
    success_flag: bool


class ActionLogService:
    def save(self, db: Session, context: RequestContext, record: AiActionLogRecord) -> None:
        db.add(
            AiActionLog(
                request_id=record.request_id,
                session_id=record.session_id,
                user_id=context.user_id,
                org_id=context.org_id,
                project_id=record.project_id,
                action_code=record.action_code,
                match_rule=record.match_rule,
                permission_rule=record.permission_rule,
                action_status=record.action_status,
                target_type=record.target_type,
                target_id=record.target_id,
                model_code=record.model_code,
                provider_code=record.provider_code,
                params_json=_normalize_payload(record.params),
                result_payload_json=_normalize_payload(record.result_payload),
                fallback_message=record.fallback_message,
                error_code=record.error_code,
                error_message=record.error_message,
                latency_ms=record.latency_ms,
                success_flag=record.success_flag,
            )
        )


action_log_service = ActionLogService()
