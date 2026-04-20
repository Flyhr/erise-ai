from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from src.app.api.deps import get_database_session, get_request_context
from src.app.services.model_health_service import model_health_service


router = APIRouter(prefix='/providers')


@router.get('/health')
async def provider_health(
    _: object = Depends(get_request_context),
    db: Session = Depends(get_database_session),
) -> dict[str, object]:
    payload = await model_health_service.provider_health(db)
    return {'code': 0, 'msg': 'ok', 'data': payload.model_dump(by_alias=True)}
