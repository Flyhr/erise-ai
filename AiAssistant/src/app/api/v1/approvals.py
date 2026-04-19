from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from src.app.actions.approval_service import approval_service
from src.app.api.deps import RequestContext, get_database_session, get_request_context
from src.app.schemas.approval import ApprovalConfirmRequest, ApprovalRejectRequest


router = APIRouter(prefix='/actions')


@router.get('/approvals/{approval_id}')
def get_approval(
    approval_id: int,
    context: RequestContext = Depends(get_request_context),
    db: Session = Depends(get_database_session),
) -> dict[str, object]:
    approval = approval_service.get_owned(db, context, approval_id)
    return {'code': 0, 'msg': 'ok', 'data': approval_service.to_view(approval).model_dump(by_alias=True)}


@router.post('/approvals/{approval_id}/confirm')
async def confirm_approval(
    approval_id: int,
    request: ApprovalConfirmRequest,
    context: RequestContext = Depends(get_request_context),
    db: Session = Depends(get_database_session),
) -> dict[str, object]:
    result = await approval_service.confirm_and_apply(db, context, approval_id, request.comment)
    return {
        'code': 0,
        'msg': 'ok',
        'data': {
            'applied': True,
            'approvalId': approval_id,
            'actionCode': result.action_code,
            'answer': result.answer,
            'targetType': result.target_type,
            'targetId': result.target_id,
            'providerCode': result.provider_code,
            'modelCode': result.model_code,
        },
    }


@router.post('/approvals/{approval_id}/reject')
async def reject_approval(
    approval_id: int,
    request: ApprovalRejectRequest,
    context: RequestContext = Depends(get_request_context),
    db: Session = Depends(get_database_session),
) -> dict[str, object]:
    approval = await approval_service.reject(db, context, approval_id, request.reason)
    return {'code': 0, 'msg': 'ok', 'data': approval_service.to_view(approval).model_dump(by_alias=True)}
