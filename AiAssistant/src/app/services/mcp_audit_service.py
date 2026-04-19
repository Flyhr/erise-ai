from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any

from sqlalchemy.orm import Session

from src.app.models.mcp_access_log import McpAccessLog


def _normalize(value: object | None) -> str | None:
    if value is None:
        return None
    return json.dumps(value, ensure_ascii=False)


@dataclass(slots=True)
class McpAuditRecord:
    request_id: str
    user_id: int
    org_id: int
    username: str | None
    role_code: str | None
    method: str
    tool_name: str | None
    resource_uri: str | None
    status_code: int | None
    success_flag: bool
    error_code: str | None
    error_message: str | None
    request_payload: dict[str, Any] | None
    response_payload: dict[str, Any] | list[Any] | None


class McpAuditService:
    def save(self, db: Session, record: McpAuditRecord) -> None:
        db.add(
            McpAccessLog(
                request_id=record.request_id,
                user_id=record.user_id,
                org_id=record.org_id,
                username=record.username,
                role_code=record.role_code,
                method=record.method,
                tool_name=record.tool_name,
                resource_uri=record.resource_uri,
                status_code=record.status_code,
                success_flag=record.success_flag,
                error_code=record.error_code,
                error_message=record.error_message,
                request_payload_json=_normalize(record.request_payload),
                response_payload_json=_normalize(record.response_payload),
            )
        )


mcp_audit_service = McpAuditService()
