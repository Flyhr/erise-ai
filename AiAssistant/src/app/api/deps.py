from __future__ import annotations

from dataclasses import dataclass
from uuid import uuid4

from fastapi import Depends, Header
from sqlalchemy.orm import Session

from src.app.core.config import Settings, get_settings
from src.app.core.exceptions import AiServiceError
from src.app.core.request_context import set_current_request_id
from src.app.db.session import get_db


@dataclass(slots=True)
class RequestContext:
    user_id: int
    org_id: int
    request_id: str


def get_request_context(
    settings: Settings = Depends(get_settings),
    x_internal_service_token: str | None = Header(default=None, alias='X-Internal-Service-Token'),
    x_internal_key: str | None = Header(default=None, alias='X-Internal-Key'),
    x_user_id: str | None = Header(default=None, alias='X-User-Id'),
    x_org_id: str | None = Header(default=None, alias='X-Org-Id'),
    x_request_id: str | None = Header(default=None, alias='X-Request-Id'),
) -> RequestContext:
    provided_token = x_internal_service_token or x_internal_key
    if provided_token != settings.internal_service_token:
        raise AiServiceError('AI_FORBIDDEN', 'Invalid internal service token', status_code=401)
    if not x_user_id:
        raise AiServiceError('AI_FORBIDDEN', 'Missing user identity', status_code=401)
    try:
        user_id = int(x_user_id)
    except ValueError as exc:
        raise AiServiceError('AI_FORBIDDEN', 'Invalid user identity', status_code=400) from exc
    try:
        org_id = int(x_org_id) if x_org_id is not None else settings.default_org_id
    except ValueError as exc:
        raise AiServiceError('AI_FORBIDDEN', 'Invalid organization identity', status_code=400) from exc
    request_id = x_request_id or str(uuid4())
    set_current_request_id(request_id)
    return RequestContext(user_id=user_id, org_id=org_id, request_id=request_id)


def get_database_session(db: Session = Depends(get_db)) -> Session:
    return db
