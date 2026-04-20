from __future__ import annotations

import json
from datetime import datetime, timedelta
from time import perf_counter
from typing import Any

from sqlalchemy.orm import Session

from src.app.actions.action_log_service import AiActionLogRecord, action_log_service
from src.app.actions.protocol import ActionDefinition, ActionExecutionResult, ActionPermissionDecision, ActionRuntimeContext
from src.app.actions.registry import build_action_registry
from src.app.adapters.llm.base import AdapterUsage
from src.app.api.deps import RequestContext
from src.app.core.config import get_settings
from src.app.core.exceptions import AiServiceError
from src.app.models.admin_action_request import AdminActionRequest
from src.app.models.approval_request import ApprovalRequest
from src.app.schemas.approval import ApprovalView
from src.app.schemas.chat import ChatCompletionRequest
from src.app.services.n8n_event_service import n8n_event_service


APPROVAL_STATUS_PENDING = 'PENDING'
APPROVAL_STATUS_APPLIED = 'APPLIED'
APPROVAL_STATUS_REJECTED = 'REJECTED'
APPROVAL_STATUS_FAILED = 'FAILED'


def _json_dumps(value: object | None) -> str | None:
    if value is None:
        return None

    def normalize(item: object) -> object:
        if hasattr(item, 'model_dump'):
            return item.model_dump(by_alias=True)
        return item

    return json.dumps(normalize(value), ensure_ascii=False)


def _json_loads(value: str | None) -> Any:
    if not value:
        return None
    try:
        return json.loads(value)
    except json.JSONDecodeError:
        return None


class ApprovalService:
    def __init__(self) -> None:
        self.settings = get_settings()
        self.registry = build_action_registry()

    def _definition(self, action_code: str) -> ActionDefinition:
        for definition in self.registry.definitions:
            if definition.action_code == action_code:
                return definition
        raise AiServiceError('ACTION_NOT_FOUND', f'Action `{action_code}` is not registered', status_code=404)

    def _plan_summary(self, definition: ActionDefinition, params: Any, permission: ActionPermissionDecision) -> str:
        target = f'{permission.target_type or "UNKNOWN"}#{permission.target_id or "-"}'
        params_payload = params.model_dump(by_alias=True) if hasattr(params, 'model_dump') else params
        return f'Plan to execute `{definition.action_code}` on {target}. Params: {params_payload}'

    def _approval_event_payload(self, approval: ApprovalRequest, event_type: str, **extra: object) -> dict[str, object]:
        payload: dict[str, object] = {
            'approvalId': approval.id,
            'requestId': approval.request_id,
            'actionCode': approval.action_code,
            'userId': approval.initiated_user_id,
            'confirmedUserId': approval.confirmed_user_id,
            'executedUserId': approval.executed_user_id,
            'projectId': approval.project_id,
            'targetType': approval.target_type,
            'targetId': approval.target_id,
            'approvalStatus': approval.status,
            'workflowExecutionStatus': n8n_event_service.WORKFLOW_PENDING,
            'workflowMode': 'WEBHOOK',
            'workflowEngine': 'N8N',
            'eventType': event_type,
        }
        payload.update({key: value for key, value in extra.items() if value is not None})
        return payload

    async def create_pending(
        self,
        db: Session,
        runtime: ActionRuntimeContext,
        definition: ActionDefinition,
        params: Any,
        permission: ActionPermissionDecision,
    ) -> ApprovalRequest:
        approval = ApprovalRequest(
            request_id=runtime.request_id,
            session_id=runtime.session_id,
            initiated_user_id=runtime.user_id,
            org_id=runtime.org_id,
            project_id=runtime.request.context.project_id,
            action_code=definition.action_code,
            target_type=permission.target_type,
            target_id=permission.target_id,
            status=APPROVAL_STATUS_PENDING,
            risk_level='HIGH',
            plan_summary=self._plan_summary(definition, params, permission),
            request_payload_json=_json_dumps(runtime.request),
            params_json=_json_dumps(params),
            resource_snapshot_json=_json_dumps(permission.resource),
            expires_at=datetime.utcnow() + timedelta(hours=24),
        )
        db.add(approval)
        db.flush()
        delivery = await n8n_event_service.emit(
            db,
            request_id=runtime.request_id,
            event_type='approval.pending',
            workflow_hint='approval-pending',
            payload=self._approval_event_payload(
                approval,
                'approval.pending',
                planSummary=approval.plan_summary,
            ),
            approval_id=approval.id,
            session_id=approval.session_id,
            user_id=approval.initiated_user_id,
            project_id=approval.project_id,
            workflow_status=n8n_event_service.WORKFLOW_PENDING,
            idempotency_key=f'approval:{approval.id}:pending',
        )
        self._audit(
            db,
            approval,
            status=APPROVAL_STATUS_PENDING,
            payload={
                'event': 'plan_created',
                'params': _json_loads(approval.params_json),
                'workflow': delivery.as_audit_payload(),
            },
        )
        return approval

    def to_view(self, approval: ApprovalRequest) -> ApprovalView:
        return ApprovalView(
            id=approval.id,
            request_id=approval.request_id,
            session_id=approval.session_id,
            initiated_user_id=approval.initiated_user_id,
            confirmed_user_id=approval.confirmed_user_id,
            executed_user_id=approval.executed_user_id,
            org_id=approval.org_id,
            project_id=approval.project_id,
            action_code=approval.action_code,
            target_type=approval.target_type,
            target_id=approval.target_id,
            status=approval.status,
            risk_level=approval.risk_level,
            plan_summary=approval.plan_summary,
            params=_json_loads(approval.params_json),
            result_payload=_json_loads(approval.result_payload_json),
            error_code=approval.error_code,
            error_message=approval.error_message,
            confirmed_at=approval.confirmed_at,
            executed_at=approval.executed_at,
            expires_at=approval.expires_at,
        )

    def get_owned(self, db: Session, context: RequestContext, approval_id: int) -> ApprovalRequest:
        approval = db.get(ApprovalRequest, approval_id)
        if approval is None or approval.initiated_user_id != context.user_id:
            raise AiServiceError('APPROVAL_NOT_FOUND', 'Approval request was not found', status_code=404)
        return approval

    async def confirm_and_apply(
        self,
        db: Session,
        context: RequestContext,
        approval_id: int,
        comment: str | None = None,
    ) -> ActionExecutionResult:
        approval = self.get_owned(db, context, approval_id)
        if approval.status != APPROVAL_STATUS_PENDING:
            raise AiServiceError('APPROVAL_NOT_PENDING', f'Approval status is {approval.status}', status_code=409)
        if approval.expires_at and approval.expires_at < datetime.utcnow():
            approval.status = 'EXPIRED'
            db.commit()
            raise AiServiceError('APPROVAL_EXPIRED', 'Approval request has expired', status_code=409)

        definition = self._definition(approval.action_code)
        request = ChatCompletionRequest.model_validate(_json_loads(approval.request_payload_json) or {})
        params = definition.param_schema.model_validate(_json_loads(approval.params_json) or {})
        runtime = ActionRuntimeContext(
            db=db,
            user_context=context,
            request=request,
            request_id=context.request_id or approval.request_id,
            session_id=approval.session_id,
            approval_confirmed=True,
        )
        started_at = perf_counter()
        approval.confirmed_user_id = context.user_id
        approval.confirmed_at = datetime.utcnow()
        try:
            permission = await definition.permission_rule(runtime, params)
            if not permission.allowed:
                raise AiServiceError('ACTION_PERMISSION_DENIED', permission.fallback_message or definition.fallback_message, status_code=403)
            result = await definition.executor(runtime, params, permission)
            result.latency_ms = result.latency_ms or max(1, int((perf_counter() - started_at) * 1000))
            approval.status = APPROVAL_STATUS_APPLIED
            approval.executed_user_id = context.user_id
            approval.executed_at = datetime.utcnow()
            approval.result_payload_json = _json_dumps(result.raw_payload)
            approval.latency_ms = result.latency_ms
            action_log_service.save(
                db,
                context,
                AiActionLogRecord(
                    request_id=runtime.request_id,
                    session_id=runtime.session_id,
                    project_id=request.context.project_id,
                    action_code=definition.action_code,
                    match_rule=definition.match_rule_name,
                    permission_rule=definition.permission_rule_name,
                    action_status='applied',
                    target_type=result.target_type,
                    target_id=result.target_id,
                    model_code=result.model_code,
                    provider_code=result.provider_code,
                    params=params,
                    result_payload=result.raw_payload,
                    fallback_message=result.fallback_message,
                    error_code=result.error_code,
                    error_message=result.error_message,
                    latency_ms=result.latency_ms,
                    success_flag=result.success_flag,
                ),
            )
            delivery = await n8n_event_service.emit(
                db,
                request_id=runtime.request_id,
                event_type='approval.applied',
                workflow_hint='approval-applied',
                payload=self._approval_event_payload(
                    approval,
                    'approval.applied',
                ),
                approval_id=approval.id,
                session_id=approval.session_id,
                user_id=approval.initiated_user_id,
                project_id=approval.project_id,
                workflow_status=n8n_event_service.WORKFLOW_PENDING,
                idempotency_key=f'approval:{approval.id}:applied',
            )
            self._audit(
                db,
                approval,
                status=APPROVAL_STATUS_APPLIED,
                payload={'event': 'applied', 'comment': comment, 'result': result.raw_payload, 'workflow': delivery.as_audit_payload()},
            )
            db.commit()
            return result
        except AiServiceError as exc:
            approval.status = APPROVAL_STATUS_FAILED
            approval.error_code = exc.error_code
            approval.error_message = exc.message
            approval.executed_user_id = context.user_id
            approval.executed_at = datetime.utcnow()
            delivery = await n8n_event_service.emit(
                db,
                request_id=runtime.request_id,
                event_type='approval.failed',
                workflow_hint='approval-failed',
                payload=self._approval_event_payload(
                    approval,
                    'approval.failed',
                    errorCode=exc.error_code,
                    errorMessage=exc.message,
                ),
                approval_id=approval.id,
                session_id=approval.session_id,
                user_id=approval.initiated_user_id,
                project_id=approval.project_id,
                workflow_status=n8n_event_service.WORKFLOW_PENDING,
                idempotency_key=f'approval:{approval.id}:failed',
            )
            self._audit(
                db,
                approval,
                status=APPROVAL_STATUS_FAILED,
                payload={
                    'event': 'failed',
                    'comment': comment,
                    'errorCode': exc.error_code,
                    'message': exc.message,
                    'workflow': delivery.as_audit_payload(),
                },
            )
            db.commit()
            raise

    async def reject(self, db: Session, context: RequestContext, approval_id: int, reason: str | None = None) -> ApprovalRequest:
        approval = self.get_owned(db, context, approval_id)
        if approval.status != APPROVAL_STATUS_PENDING:
            raise AiServiceError('APPROVAL_NOT_PENDING', f'Approval status is {approval.status}', status_code=409)
        approval.status = APPROVAL_STATUS_REJECTED
        approval.confirmed_user_id = context.user_id
        approval.confirmed_at = datetime.utcnow()
        delivery = await n8n_event_service.emit(
            db,
            request_id=context.request_id or approval.request_id,
            event_type='approval.rejected',
            workflow_hint='approval-rejected',
            payload=self._approval_event_payload(
                approval,
                'approval.rejected',
                reason=reason,
            ),
            approval_id=approval.id,
            session_id=approval.session_id,
            user_id=approval.initiated_user_id,
            project_id=approval.project_id,
            workflow_status=n8n_event_service.WORKFLOW_PENDING,
            idempotency_key=f'approval:{approval.id}:rejected',
        )
        self._audit(
            db,
            approval,
            status=APPROVAL_STATUS_REJECTED,
            payload={'event': 'rejected', 'reason': reason, 'workflow': delivery.as_audit_payload()},
        )
        db.commit()
        return approval

    def _audit(self, db: Session, approval: ApprovalRequest, status: str, payload: dict[str, object]) -> None:
        db.add(
            AdminActionRequest(
                approval_request_id=approval.id,
                request_id=approval.request_id,
                initiated_user_id=approval.initiated_user_id,
                confirmed_user_id=approval.confirmed_user_id,
                executed_user_id=approval.executed_user_id,
                action_code=approval.action_code,
                action_status=status,
                target_type=approval.target_type,
                target_id=approval.target_id,
                audit_payload_json=_json_dumps(payload),
            )
        )


approval_service = ApprovalService()
