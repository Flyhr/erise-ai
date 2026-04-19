from __future__ import annotations

from time import perf_counter

from sqlalchemy.orm import Session

from src.app.actions.approval_service import approval_service
from src.app.actions.action_log_service import AiActionLogRecord, action_log_service
from src.app.actions.protocol import ActionExecutionResult, ActionRuntimeContext
from src.app.actions.registry import build_action_registry
from src.app.adapters.llm.base import AdapterUsage
from src.app.api.deps import RequestContext
from src.app.core.exceptions import AiServiceError
from src.app.schemas.chat import ChatCompletionRequest


class ActionService:
    def __init__(self) -> None:
        self.registry = build_action_registry()
        from src.app.core.config import get_settings
        self.settings = get_settings()

    async def execute(
        self,
        db: Session,
        context: RequestContext,
        request: ChatCompletionRequest,
        request_id: str,
        session_id: int | None = None,
    ) -> ActionExecutionResult | None:
        runtime = ActionRuntimeContext(
            db=db,
            user_context=context,
            request=request,
            request_id=request_id,
            session_id=session_id,
        )

        for definition in self.registry.definitions:
            params = definition.match_rule(request)
            if params is None:
                continue

            started_at = perf_counter()
            try:
                permission = await definition.permission_rule(runtime, params)
                latency_ms = max(1, int((perf_counter() - started_at) * 1000))
                if not permission.allowed:
                    result = ActionExecutionResult(
                        action_code=definition.action_code,
                        answer=permission.fallback_message or definition.fallback_message,
                        answer_source='ACTION_FALLBACK',
                        used_tools=[definition.action_code, 'action.permission_denied'],
                        provider_code='SYSTEM',
                        model_code='agent-action-fallback',
                        usage=AdapterUsage(),
                        success_flag=False,
                        fallback_message=permission.fallback_message or definition.fallback_message,
                        latency_ms=latency_ms,
                        target_type=permission.target_type,
                        target_id=permission.target_id,
                    )
                    self._save_log(
                        db,
                        context,
                        runtime,
                        definition,
                        params,
                        result,
                        action_status='denied',
                        result_payload=permission.resource,
                    )
                    return result

                if definition.requires_confirmation and self.settings.action_confirmation_required and not runtime.approval_confirmed:
                    approval = await approval_service.create_pending(db, runtime, definition, params, permission)
                    latency_ms = max(1, int((perf_counter() - started_at) * 1000))
                    result = ActionExecutionResult(
                        action_code=definition.action_code,
                        answer=(
                            f'已生成高风险写操作执行计划，尚未执行。审批编号：{approval.id}。\n'
                            f'{approval.plan_summary}\n'
                            '请调用确认接口后再执行；未确认前不会写入业务数据。'
                        ),
                        answer_source='ACTION_PLAN',
                        used_tools=[definition.action_code, 'action.plan_created', 'action.confirm_required'],
                        provider_code='SYSTEM',
                        model_code='action-approval-plan',
                        usage=AdapterUsage(),
                        success_flag=True,
                        raw_payload={'approvalId': approval.id, 'status': approval.status, 'planSummary': approval.plan_summary},
                        latency_ms=latency_ms,
                        target_type=permission.target_type,
                        target_id=permission.target_id,
                        approval_id=approval.id,
                        approval_status=approval.status,
                    )
                    self._save_log(
                        db,
                        context,
                        runtime,
                        definition,
                        params,
                        result,
                        action_status='pending_confirmation',
                        result_payload=result.raw_payload,
                    )
                    return result

                result = await definition.executor(runtime, params, permission)
                if result.latency_ms is None:
                    result.latency_ms = latency_ms
                self._save_log(
                    db,
                    context,
                    runtime,
                    definition,
                    params,
                    result,
                    action_status='success' if result.success_flag else 'fallback',
                    result_payload=result.raw_payload,
                )
                return result
            except AiServiceError as exc:
                result = ActionExecutionResult(
                    action_code=definition.action_code,
                    answer=definition.fallback_message,
                    answer_source='ACTION_FALLBACK',
                    used_tools=[definition.action_code, 'action.failed'],
                    provider_code='SYSTEM',
                    model_code='agent-action-fallback',
                    usage=AdapterUsage(),
                    success_flag=False,
                    fallback_message=definition.fallback_message,
                    error_code=exc.error_code,
                    error_message=exc.message,
                    latency_ms=max(1, int((perf_counter() - started_at) * 1000)),
                )
                self._save_log(
                    db,
                    context,
                    runtime,
                    definition,
                    params,
                    result,
                    action_status='failed',
                    result_payload=None,
                )
                return result
            except Exception as exc:
                result = ActionExecutionResult(
                    action_code=definition.action_code,
                    answer=definition.fallback_message,
                    answer_source='ACTION_FALLBACK',
                    used_tools=[definition.action_code, 'action.failed'],
                    provider_code='SYSTEM',
                    model_code='agent-action-fallback',
                    usage=AdapterUsage(),
                    success_flag=False,
                    fallback_message=definition.fallback_message,
                    error_code='ACTION_EXECUTION_FAILED',
                    error_message=str(exc),
                    latency_ms=max(1, int((perf_counter() - started_at) * 1000)),
                )
                self._save_log(
                    db,
                    context,
                    runtime,
                    definition,
                    params,
                    result,
                    action_status='failed',
                    result_payload=None,
                )
                return result

        return None

    def _save_log(
        self,
        db: Session,
        context: RequestContext,
        runtime: ActionRuntimeContext,
        definition,
        params,
        result: ActionExecutionResult,
        action_status: str,
        result_payload: object | None,
    ) -> None:
        action_log_service.save(
            db,
            context,
            AiActionLogRecord(
                request_id=runtime.request_id,
                session_id=runtime.session_id,
                project_id=runtime.request.context.project_id,
                action_code=definition.action_code,
                match_rule=definition.match_rule_name,
                permission_rule=definition.permission_rule_name,
                action_status=action_status,
                target_type=result.target_type,
                target_id=result.target_id,
                model_code=result.model_code,
                provider_code=result.provider_code,
                params=params,
                result_payload=result_payload,
                fallback_message=result.fallback_message,
                error_code=result.error_code,
                error_message=result.error_message,
                latency_ms=result.latency_ms,
                success_flag=result.success_flag,
            ),
        )


action_service = ActionService()
