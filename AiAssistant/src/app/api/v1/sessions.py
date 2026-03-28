from __future__ import annotations

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from src.app.api.deps import RequestContext, get_database_session, get_request_context
from src.app.schemas.session import SessionCreateRequest
from src.app.services.chat_service import chat_service


router = APIRouter()


@router.post('/sessions')
def create_session(
    request: SessionCreateRequest,
    context: RequestContext = Depends(get_request_context),
    db: Session = Depends(get_database_session),
) -> dict[str, object]:
    session = chat_service.create_session(db, context, request)
    return {'code': 0, 'msg': 'ok', 'data': session.model_dump(by_alias=True)}


@router.get('/sessions')
def list_sessions(
    page_num: int = Query(default=1, ge=1),
    page_size: int = Query(default=20, ge=1, le=100),
    context: RequestContext = Depends(get_request_context),
    db: Session = Depends(get_database_session),
) -> dict[str, object]:
    return {'code': 0, 'msg': 'ok', 'data': chat_service.list_sessions(db, context, page_num, page_size).model_dump(by_alias=True)}


@router.get('/sessions/{session_id}')
def session_detail(
    session_id: int,
    context: RequestContext = Depends(get_request_context),
    db: Session = Depends(get_database_session),
) -> dict[str, object]:
    session = chat_service.get_session_detail(db, context, session_id)
    return {'code': 0, 'msg': 'ok', 'data': session.model_dump(by_alias=True)}


@router.delete('/sessions/{session_id}')
def delete_session(
    session_id: int,
    context: RequestContext = Depends(get_request_context),
    db: Session = Depends(get_database_session),
) -> dict[str, object]:
    chat_service.delete_session(db, context, session_id)
    return {'code': 0, 'msg': 'ok', 'data': {'deleted': True, 'sessionId': session_id}}
