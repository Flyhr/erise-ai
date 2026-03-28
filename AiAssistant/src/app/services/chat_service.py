from __future__ import annotations

import json
import math
from collections.abc import AsyncGenerator
from datetime import datetime
from time import perf_counter
from uuid import uuid4

from redis.asyncio import Redis
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from src.app.adapters.llm.base import AdapterUsage
from src.app.api.deps import RequestContext
from src.app.core.config import get_settings
from src.app.core.constants import (
    ACTIVE_SESSION_STATUS,
    DELETED_SESSION_STATUS,
    ROLE_ASSISTANT,
    ROLE_USER,
    SCENE_GENERAL,
    STATUS_CANCELLED,
    STATUS_FAILED,
    STATUS_STREAMING,
    STATUS_SUCCESS,
)
from src.app.core.exceptions import AiServiceError
from src.app.models.ai_message import AiChatMessage
from src.app.models.ai_request_log import AiRequestLog
from src.app.models.ai_session import AiChatSession
from src.app.schemas.chat import CancelResponse, ChatCompletionRequest, ChatCompletionView, UsageView
from src.app.schemas.common import PageData
from src.app.schemas.message import MessageView
from src.app.schemas.session import SessionCreateRequest, SessionDetailView, SessionSummaryView
from src.app.services.context_service import build_prompt_messages
from src.app.services.document_action_service import apply_document_title_action
from src.app.services.model_registry import get_model_adapter, get_model_config
from src.app.utils.sse import sse_event

SYSTEM_PROVIDER_CODE = 'SYSTEM'
DOCUMENT_TITLE_ACTION_MODEL = 'document-title-update'


class CancellationStore:
    def __init__(self) -> None:
        self.settings = get_settings()
        self._memory: set[str] = set()
        self._redis = Redis.from_url(self.settings.redis_url, decode_responses=True)

    def _key(self, request_id: str) -> str:
        return f'ai:cancel:{request_id}'

    async def mark_cancelled(self, request_id: str) -> None:
        try:
            await self._redis.set(self._key(request_id), '1', ex=self.settings.stream_cancel_ttl_seconds)
        except Exception:
            self._memory.add(request_id)

    async def is_cancelled(self, request_id: str) -> bool:
        try:
            value = await self._redis.get(self._key(request_id))
            if value == '1':
                return True
        except Exception:
            pass
        return request_id in self._memory

    async def clear(self, request_id: str) -> None:
        self._memory.discard(request_id)
        try:
            await self._redis.delete(self._key(request_id))
        except Exception:
            pass


class ChatService:
    def __init__(self) -> None:
        self.settings = get_settings()
        self.cancellation_store = CancellationStore()

    def _validate_message(self, message: str) -> None:
        if not message.strip():
            raise AiServiceError('AI_CONTEXT_TOO_LARGE', 'Message cannot be empty', status_code=400)
        if len(message) > self.settings.request_body_char_limit:
            raise AiServiceError('AI_CONTEXT_TOO_LARGE', 'Message is too large', status_code=400)
        lowered = message.lower()
        for term in self.settings.blocked_term_list:
            if term and term in lowered:
                raise AiServiceError('AI_FORBIDDEN', 'Message contains blocked content', status_code=403)

    def _query_session(self, db: Session, context: RequestContext, session_id: int) -> AiChatSession:
        session = db.execute(
            select(AiChatSession).where(
                AiChatSession.id == session_id,
                AiChatSession.user_id == context.user_id,
                AiChatSession.status != DELETED_SESSION_STATUS,
            )
        ).scalar_one_or_none()
        if session is None:
            raise AiServiceError('AI_SESSION_NOT_FOUND', 'Session not found', status_code=404)
        return session

    def _next_sequence_no(self, db: Session, session_id: int) -> int:
        current = db.execute(select(func.max(AiChatMessage.sequence_no)).where(AiChatMessage.session_id == session_id)).scalar_one_or_none()
        return (current or 0) + 1

    def _session_title(self, title: str | None, message: str | None) -> str:
        source = (title or message or '新会话').strip()
        return source[:50] if source else '新会话'

    def _touch_session(self, session: AiChatSession) -> None:
        session.last_message_at = datetime.utcnow()

    def _build_document_title_action_reply(self, detail: dict[str, object] | None) -> str:
        title = str((detail or {}).get('title') or '未命名文档')
        return f'已将文档标题更新为《{title}》。如果你愿意，我还可以继续帮你总结这份文档，或根据这份文档继续修改内容。'

    def create_session(self, db: Session, context: RequestContext, request: SessionCreateRequest) -> SessionSummaryView:
        session = AiChatSession(
            user_id=context.user_id,
            org_id=context.org_id,
            project_id=request.project_id,
            scene=request.scene or SCENE_GENERAL,
            title=self._session_title(request.title, None),
            summary_text=None,
            last_message_at=None,
            message_count=0,
            status=ACTIVE_SESSION_STATUS,
        )
        db.add(session)
        db.commit()
        db.refresh(session)
        return SessionSummaryView.model_validate(session)

    def list_sessions(self, db: Session, context: RequestContext, page_num: int, page_size: int) -> PageData:
        query = select(AiChatSession).where(
            AiChatSession.user_id == context.user_id,
            AiChatSession.status != DELETED_SESSION_STATUS,
        )
        total = db.execute(select(func.count()).select_from(query.subquery())).scalar_one()
        records = db.execute(
            query.order_by(AiChatSession.last_message_at.desc(), AiChatSession.updated_at.desc())
            .offset((page_num - 1) * page_size)
            .limit(page_size)
        ).scalars().all()
        return PageData(
            records=[SessionSummaryView.model_validate(item).model_dump(by_alias=True) for item in records],
            page_num=page_num,
            page_size=page_size,
            total=total,
            total_pages=math.ceil(total / page_size) if page_size else 1,
        )

    def list_messages(self, db: Session, context: RequestContext, session_id: int, page_num: int, page_size: int) -> PageData:
        self._query_session(db, context, session_id)
        query = select(AiChatMessage).where(AiChatMessage.session_id == session_id)
        total = db.execute(select(func.count()).select_from(query.subquery())).scalar_one()
        records = db.execute(
            query.order_by(AiChatMessage.sequence_no.asc())
            .offset((page_num - 1) * page_size)
            .limit(page_size)
        ).scalars().all()
        return PageData(
            records=[MessageView.model_validate(item).model_dump(by_alias=True) for item in records],
            page_num=page_num,
            page_size=page_size,
            total=total,
            total_pages=math.ceil(total / page_size) if page_size else 1,
        )

    def get_session_detail(self, db: Session, context: RequestContext, session_id: int) -> SessionDetailView:
        session = self._query_session(db, context, session_id)
        messages = db.execute(
            select(AiChatMessage).where(AiChatMessage.session_id == session_id).order_by(AiChatMessage.sequence_no.asc())
        ).scalars().all()
        return SessionDetailView(
            **SessionSummaryView.model_validate(session).model_dump(),
            messages=[MessageView.model_validate(item) for item in messages],
        )

    def delete_session(self, db: Session, context: RequestContext, session_id: int) -> None:
        session = self._query_session(db, context, session_id)
        session.status = DELETED_SESSION_STATUS
        db.commit()

    def _ensure_session(self, db: Session, context: RequestContext, request: ChatCompletionRequest) -> AiChatSession:
        if request.session_id:
            return self._query_session(db, context, request.session_id)
        session = AiChatSession(
            user_id=context.user_id,
            org_id=context.org_id,
            project_id=request.context.project_id,
            scene=request.scene or SCENE_GENERAL,
            title=self._session_title(None, request.message),
            summary_text=None,
            last_message_at=None,
            message_count=0,
            status=ACTIVE_SESSION_STATUS,
        )
        db.add(session)
        db.flush()
        return session

    def _create_message(
        self,
        db: Session,
        session_id: int,
        user_id: int,
        role: str,
        content: str,
        status: str,
        request_id: str | None = None,
        model_code: str | None = None,
        provider_code: str | None = None,
    ) -> AiChatMessage:
        message = AiChatMessage(
            session_id=session_id,
            user_id=user_id,
            role=role,
            content=content,
            content_format='text',
            message_status=status,
            sequence_no=self._next_sequence_no(db, session_id),
            request_id=request_id,
            model_code=model_code,
            provider_code=provider_code,
        )
        db.add(message)
        db.flush()
        return message

    def _save_request_log(
        self,
        db: Session,
        request_id: str,
        session_id: int,
        user_message_id: int,
        assistant_message_id: int,
        provider_code: str,
        model_code: str,
        scene: str,
        temperature: float | None,
        max_tokens: int | None,
        stream: bool,
        request_payload: dict[str, object],
        response_payload: dict[str, object],
        usage: AdapterUsage,
        duration_ms: int,
        success_flag: bool,
        error_code: str | None = None,
        error_message: str | None = None,
    ) -> None:
        db.add(
            AiRequestLog(
                request_id=request_id,
                session_id=session_id,
                user_message_id=user_message_id,
                assistant_message_id=assistant_message_id,
                provider_code=provider_code,
                model_code=model_code,
                scene=scene,
                temperature=temperature,
                max_tokens=max_tokens,
                stream=stream,
                request_payload_json=json.dumps(request_payload, ensure_ascii=False),
                response_payload_json=json.dumps(response_payload, ensure_ascii=False),
                input_token_count=usage.prompt_tokens,
                output_token_count=usage.completion_tokens,
                duration_ms=duration_ms,
                success_flag=success_flag,
                error_code=error_code,
                error_message=error_message,
            )
        )

    def _build_completion_view(
        self,
        request_id: str,
        session: AiChatSession,
        user_message: AiChatMessage,
        assistant_message: AiChatMessage,
        answer: str,
        model_code: str,
        provider_code: str,
        usage: AdapterUsage,
        latency_ms: int,
    ) -> ChatCompletionView:
        return ChatCompletionView(
            request_id=request_id,
            session_id=session.id,
            user_message_id=user_message.id,
            assistant_message_id=assistant_message.id,
            answer=answer,
            scene=session.scene,
            model_code=model_code,
            provider_code=provider_code,
            message_status=assistant_message.message_status,
            usage=UsageView(
                prompt_tokens=usage.prompt_tokens,
                completion_tokens=usage.completion_tokens,
                total_tokens=usage.total_tokens,
            ),
            latency_ms=latency_ms,
        )

    async def complete(self, db: Session, context: RequestContext, request: ChatCompletionRequest) -> ChatCompletionView:
        self._validate_message(request.message)
        session = self._ensure_session(db, context, request)
        request_id = context.request_id or str(uuid4())
        user_message = self._create_message(db, session.id, context.user_id, ROLE_USER, request.message, STATUS_SUCCESS)

        action_started_at = perf_counter()
        action_result = await apply_document_title_action(request, request_id)
        if action_result:
            latency_ms = max(1, int((perf_counter() - action_started_at) * 1000))
            answer = self._build_document_title_action_reply(action_result)
            usage = AdapterUsage()
            assistant_message = self._create_message(
                db,
                session.id,
                context.user_id,
                ROLE_ASSISTANT,
                answer,
                STATUS_SUCCESS,
                request_id=request_id,
                model_code=DOCUMENT_TITLE_ACTION_MODEL,
                provider_code=SYSTEM_PROVIDER_CODE,
            )
            assistant_message.latency_ms = latency_ms
            session.message_count = (session.message_count or 0) + 2
            self._touch_session(session)
            self._save_request_log(
                db,
                request_id=request_id,
                session_id=session.id,
                user_message_id=user_message.id,
                assistant_message_id=assistant_message.id,
                provider_code=SYSTEM_PROVIDER_CODE,
                model_code=DOCUMENT_TITLE_ACTION_MODEL,
                scene=session.scene,
                temperature=request.temperature,
                max_tokens=request.max_tokens,
                stream=False,
                request_payload=request.model_dump(by_alias=True),
                response_payload={'answer': answer, 'document': action_result},
                usage=usage,
                duration_ms=latency_ms,
                success_flag=True,
            )
            db.commit()
            db.refresh(session)
            db.refresh(assistant_message)
            return self._build_completion_view(
                request_id,
                session,
                user_message,
                assistant_message,
                answer,
                DOCUMENT_TITLE_ACTION_MODEL,
                SYSTEM_PROVIDER_CODE,
                usage,
                latency_ms,
            )

        model = get_model_config(db, request.model_code)
        adapter = get_model_adapter(model)
        prompt_messages = await build_prompt_messages(db, context, session, request, exclude_message_id=user_message.id)
        started_at = perf_counter()
        result = await adapter.chat(model.model_code, prompt_messages, request.temperature, request.max_tokens)
        latency_ms = max(1, int((perf_counter() - started_at) * 1000))
        assistant_message = self._create_message(
            db,
            session.id,
            context.user_id,
            ROLE_ASSISTANT,
            result.text,
            STATUS_SUCCESS,
            request_id=request_id,
            model_code=result.model_code,
            provider_code=result.provider_code,
        )
        assistant_message.prompt_tokens = result.usage.prompt_tokens
        assistant_message.completion_tokens = result.usage.completion_tokens
        assistant_message.total_tokens = result.usage.total_tokens
        assistant_message.latency_ms = latency_ms
        session.message_count = (session.message_count or 0) + 2
        self._touch_session(session)
        self._save_request_log(
            db,
            request_id=request_id,
            session_id=session.id,
            user_message_id=user_message.id,
            assistant_message_id=assistant_message.id,
            provider_code=result.provider_code,
            model_code=result.model_code,
            scene=session.scene,
            temperature=request.temperature,
            max_tokens=request.max_tokens,
            stream=False,
            request_payload=request.model_dump(by_alias=True),
            response_payload=result.raw_response or {'answer': result.text},
            usage=result.usage,
            duration_ms=latency_ms,
            success_flag=True,
        )
        db.commit()
        db.refresh(session)
        db.refresh(assistant_message)
        return self._build_completion_view(
            request_id,
            session,
            user_message,
            assistant_message,
            result.text,
            result.model_code,
            result.provider_code,
            result.usage,
            latency_ms,
        )

    async def stream(self, db: Session, context: RequestContext, request: ChatCompletionRequest) -> AsyncGenerator[str, None]:
        self._validate_message(request.message)
        session = self._ensure_session(db, context, request)
        request_id = context.request_id or str(uuid4())
        user_message = self._create_message(db, session.id, context.user_id, ROLE_USER, request.message, STATUS_SUCCESS)

        action_started_at = perf_counter()
        action_result = await apply_document_title_action(request, request_id)
        if action_result:
            answer = self._build_document_title_action_reply(action_result)
            latency_ms = max(1, int((perf_counter() - action_started_at) * 1000))
            usage = AdapterUsage()
            assistant_message = self._create_message(
                db,
                session.id,
                context.user_id,
                ROLE_ASSISTANT,
                answer,
                STATUS_SUCCESS,
                request_id=request_id,
                model_code=DOCUMENT_TITLE_ACTION_MODEL,
                provider_code=SYSTEM_PROVIDER_CODE,
            )
            assistant_message.latency_ms = latency_ms
            session.message_count = (session.message_count or 0) + 2
            self._touch_session(session)
            self._save_request_log(
                db,
                request_id=request_id,
                session_id=session.id,
                user_message_id=user_message.id,
                assistant_message_id=assistant_message.id,
                provider_code=SYSTEM_PROVIDER_CODE,
                model_code=DOCUMENT_TITLE_ACTION_MODEL,
                scene=session.scene,
                temperature=request.temperature,
                max_tokens=request.max_tokens,
                stream=True,
                request_payload=request.model_dump(by_alias=True),
                response_payload={'answer': answer, 'document': action_result},
                usage=usage,
                duration_ms=latency_ms,
                success_flag=True,
            )
            db.commit()

            async def action_stream() -> AsyncGenerator[str, None]:
                try:
                    yield sse_event(
                        'stream.start',
                        {
                            'requestId': request_id,
                            'sessionId': session.id,
                            'assistantMessageId': assistant_message.id,
                        },
                    )
                    yield sse_event('stream.delta', {'requestId': request_id, 'delta': answer})
                    yield sse_event(
                        'stream.end',
                        {
                            'requestId': request_id,
                            'sessionId': session.id,
                            'assistantMessageId': assistant_message.id,
                            'usage': UsageView().model_dump(by_alias=True),
                            'latencyMs': latency_ms,
                        },
                    )
                finally:
                    await self.cancellation_store.clear(request_id)

            return action_stream()

        model = get_model_config(db, request.model_code)
        adapter = get_model_adapter(model)
        assistant_message = self._create_message(
            db,
            session.id,
            context.user_id,
            ROLE_ASSISTANT,
            '',
            STATUS_STREAMING,
            request_id=request_id,
            model_code=model.model_code,
            provider_code=model.provider_code,
        )
        prompt_messages = await build_prompt_messages(db, context, session, request, exclude_message_id=user_message.id)
        db.commit()

        async def event_stream() -> AsyncGenerator[str, None]:
            started_at = perf_counter()
            aggregated = ''
            usage = AdapterUsage()
            try:
                yield sse_event(
                    'stream.start',
                    {
                        'requestId': request_id,
                        'sessionId': session.id,
                        'assistantMessageId': assistant_message.id,
                    },
                )
                async for event in adapter.stream_chat(model.model_code, prompt_messages, request.temperature, request.max_tokens):
                    if await self.cancellation_store.is_cancelled(request_id):
                        raise AiServiceError('AI_CANCELLED', 'generation cancelled', status_code=409)
                    if event.delta:
                        aggregated += event.delta
                        yield sse_event('stream.delta', {'requestId': request_id, 'delta': event.delta})
                    if event.usage:
                        usage = event.usage

                latency_ms = max(1, int((perf_counter() - started_at) * 1000))
                stored_message = db.execute(select(AiChatMessage).where(AiChatMessage.id == assistant_message.id)).scalar_one()
                stored_session = db.execute(select(AiChatSession).where(AiChatSession.id == session.id)).scalar_one()
                stored_message.content = aggregated
                stored_message.message_status = STATUS_SUCCESS
                stored_message.prompt_tokens = usage.prompt_tokens
                stored_message.completion_tokens = usage.completion_tokens
                stored_message.total_tokens = usage.total_tokens
                stored_message.latency_ms = latency_ms
                stored_session.message_count = (stored_session.message_count or 0) + 2
                self._touch_session(stored_session)
                self._save_request_log(
                    db,
                    request_id=request_id,
                    session_id=stored_session.id,
                    user_message_id=user_message.id,
                    assistant_message_id=stored_message.id,
                    provider_code=model.provider_code,
                    model_code=model.model_code,
                    scene=stored_session.scene,
                    temperature=request.temperature,
                    max_tokens=request.max_tokens,
                    stream=True,
                    request_payload=request.model_dump(by_alias=True),
                    response_payload={'answer': aggregated},
                    usage=usage,
                    duration_ms=latency_ms,
                    success_flag=True,
                )
                db.commit()
                yield sse_event(
                    'stream.end',
                    {
                        'requestId': request_id,
                        'sessionId': session.id,
                        'assistantMessageId': assistant_message.id,
                        'usage': UsageView(
                            prompt_tokens=usage.prompt_tokens,
                            completion_tokens=usage.completion_tokens,
                            total_tokens=usage.total_tokens,
                        ).model_dump(by_alias=True),
                        'latencyMs': latency_ms,
                    },
                )
            except AiServiceError as exc:
                final_status = STATUS_CANCELLED if exc.error_code == 'AI_CANCELLED' else STATUS_FAILED
                stored_message = db.execute(select(AiChatMessage).where(AiChatMessage.id == assistant_message.id)).scalar_one()
                stored_message.content = aggregated
                stored_message.message_status = final_status
                stored_message.error_code = exc.error_code
                stored_message.error_message = exc.message
                if usage.total_tokens:
                    stored_message.prompt_tokens = usage.prompt_tokens
                    stored_message.completion_tokens = usage.completion_tokens
                    stored_message.total_tokens = usage.total_tokens
                self._save_request_log(
                    db,
                    request_id=request_id,
                    session_id=session.id,
                    user_message_id=user_message.id,
                    assistant_message_id=assistant_message.id,
                    provider_code=model.provider_code,
                    model_code=model.model_code,
                    scene=session.scene,
                    temperature=request.temperature,
                    max_tokens=request.max_tokens,
                    stream=True,
                    request_payload=request.model_dump(by_alias=True),
                    response_payload={'answer': aggregated},
                    usage=usage,
                    duration_ms=max(1, int((perf_counter() - started_at) * 1000)),
                    success_flag=False,
                    error_code=exc.error_code,
                    error_message=exc.message,
                )
                db.commit()
                yield sse_event(
                    'stream.error',
                    {
                        'requestId': request_id,
                        'sessionId': session.id,
                        'assistantMessageId': assistant_message.id,
                        'errorCode': exc.error_code,
                        'message': exc.message,
                    },
                )
            except Exception as exc:
                stored_message = db.execute(select(AiChatMessage).where(AiChatMessage.id == assistant_message.id)).scalar_one()
                stored_message.content = aggregated
                stored_message.message_status = STATUS_FAILED
                stored_message.error_code = 'AI_PROVIDER_ERROR'
                stored_message.error_message = str(exc)
                self._save_request_log(
                    db,
                    request_id=request_id,
                    session_id=session.id,
                    user_message_id=user_message.id,
                    assistant_message_id=assistant_message.id,
                    provider_code=model.provider_code,
                    model_code=model.model_code,
                    scene=session.scene,
                    temperature=request.temperature,
                    max_tokens=request.max_tokens,
                    stream=True,
                    request_payload=request.model_dump(by_alias=True),
                    response_payload={'answer': aggregated},
                    usage=usage,
                    duration_ms=max(1, int((perf_counter() - started_at) * 1000)),
                    success_flag=False,
                    error_code='AI_PROVIDER_ERROR',
                    error_message=str(exc),
                )
                db.commit()
                yield sse_event(
                    'stream.error',
                    {
                        'requestId': request_id,
                        'sessionId': session.id,
                        'assistantMessageId': assistant_message.id,
                        'errorCode': 'AI_PROVIDER_ERROR',
                        'message': str(exc),
                    },
                )
            finally:
                await self.cancellation_store.clear(request_id)

        return event_stream()

    async def cancel(self, request_id: str) -> CancelResponse:
        await self.cancellation_store.mark_cancelled(request_id)
        return CancelResponse(request_id=request_id, cancelled=True)


chat_service = ChatService()