from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from src.app.api.deps import get_database_session, get_request_context
from src.app.services.model_health_service import model_health_service
from src.app.services.model_registry import list_enabled_models


router = APIRouter()


@router.get('/models')
def models(
    _: object = Depends(get_request_context),
    db: Session = Depends(get_database_session),
) -> dict[str, object]:
    return {'code': 0, 'msg': 'ok', 'data': [item.model_dump(by_alias=True) for item in list_enabled_models(db)]}


@router.get('/models/health')
async def model_health(
    _: object = Depends(get_request_context),
    db: Session = Depends(get_database_session),
) -> dict[str, object]:
    payload = await model_health_service.check(db)
    return {'code': 0, 'msg': 'ok', 'data': payload.model_dump(by_alias=True)}
