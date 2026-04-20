from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Body, Depends, Query
from sqlalchemy.orm import Session

from src.app.api.deps import get_database_session, get_request_context
from src.app.schemas.n8n import N8nManualHandoffRequest
from src.app.services.n8n_event_service import n8n_event_service


router = APIRouter(prefix='/n8n')


@router.get('/events')
def list_n8n_events(
    _: object = Depends(get_request_context),
    db: Session = Depends(get_database_session),
    page_num: int = Query(default=1, alias='pageNum', ge=1),
    page_size: int = Query(default=20, alias='pageSize', ge=1, le=100),
    q: str | None = Query(default=None),
    delivery_status: str | None = Query(default=None, alias='deliveryStatus'),
    workflow_status: str | None = Query(default=None, alias='workflowStatus'),
    manual_status: str | None = Query(default=None, alias='manualStatus'),
    event_type: str | None = Query(default=None, alias='eventType'),
    created_date: date | None = Query(default=None, alias='createdDate'),
) -> dict[str, object]:
    payload = n8n_event_service.list_events(
        db,
        page_num=page_num,
        page_size=page_size,
        q=q,
        delivery_status=delivery_status,
        workflow_status=workflow_status,
        manual_status=manual_status,
        event_type=event_type,
        created_date=created_date,
    )
    return {'code': 0, 'msg': 'ok', 'data': payload.model_dump(by_alias=True)}


@router.get('/events/summary')
def n8n_event_summary(
    _: object = Depends(get_request_context),
    db: Session = Depends(get_database_session),
    hours: int = Query(default=24, ge=1, le=168),
) -> dict[str, object]:
    payload = n8n_event_service.summary(db, window_hours=hours)
    return {'code': 0, 'msg': 'ok', 'data': payload.model_dump(by_alias=True)}


@router.get('/events/{event_id}')
def get_n8n_event_detail(
    event_id: int,
    _: object = Depends(get_request_context),
    db: Session = Depends(get_database_session),
) -> dict[str, object]:
    payload = n8n_event_service.get_event_detail(db, event_id)
    return {'code': 0, 'msg': 'ok', 'data': payload.model_dump(by_alias=True)}


@router.post('/events/{event_id}/retry')
async def retry_n8n_event(
    event_id: int,
    _: object = Depends(get_request_context),
    db: Session = Depends(get_database_session),
) -> dict[str, object]:
    payload = await n8n_event_service.retry_event(db, event_id)
    db.commit()
    return {'code': 0, 'msg': 'ok', 'data': payload.model_dump(by_alias=True)}


@router.post('/events/{event_id}/manual-handoff')
def manual_handoff_n8n_event(
    event_id: int,
    request: N8nManualHandoffRequest = Body(default_factory=N8nManualHandoffRequest),
    _: object = Depends(get_request_context),
    db: Session = Depends(get_database_session),
) -> dict[str, object]:
    payload = n8n_event_service.manual_handoff_event(db, event_id, reason=request.reason)
    db.commit()
    return {'code': 0, 'msg': 'ok', 'data': payload.model_dump(by_alias=True)}
