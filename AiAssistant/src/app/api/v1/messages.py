from __future__ import annotations

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from src.app.api.deps import RequestContext, get_database_session, get_request_context
from src.app.services.chat_service import chat_service


router = APIRouter()


@router.get('/sessions/{session_id}/messages')
def list_messages(
    session_id: int,
    page_num: int = Query(default=1, ge=1),
    page_size: int = Query(default=50, ge=1, le=200),
    context: RequestContext = Depends(get_request_context),
    db: Session = Depends(get_database_session),
) -> dict[str, object]:
    page = chat_service.list_messages(db, context, session_id, page_num, page_size)
    return {'code': 0, 'msg': 'ok', 'data': page.model_dump(by_alias=True)}
