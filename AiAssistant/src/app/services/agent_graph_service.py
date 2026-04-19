from __future__ import annotations

import logging
from dataclasses import dataclass, field
from time import perf_counter
from typing import Any

from sqlalchemy.orm import Session

from src.app.adapters.java.project_client import fetch_project_context
from src.app.adapters.llm.base import AdapterResult, AdapterUsage
from src.app.api.deps import RequestContext
from src.app.core.exceptions import AiServiceError
from src.app.schemas.agent import AgentRunRequest, AgentRunView
from src.app.schemas.chat import ChatCompletionRequest
from src.app.schemas.message import CitationView
from src.app.services.context_service import LoadedAttachmentContext, load_attachment_contexts
from src.app.services.model_registry import get_model_adapter, get_model_config
from src.app.services.rag_service import RetrievalDecision, rag_service


logger = logging.getLogger(__name__)

LANGGRAPH_IMPORT_ERROR: str | None = None

try:
    from langgraph.graph import END, StateGraph

    LANGGRAPH_AVAILABLE = True
except Exception as exc:  # pragma: no cover - optional dependency
    END = '__end__'
    StateGraph = None
    LANGGRAPH_AVAILABLE = False
    LANGGRAPH_IMPORT_ERROR = f'{exc.__class__.__name__}: {exc}'


@dataclass
class AgentState:
    request: AgentRunRequest
    db: Session
    context: RequestContext
    request_id: str
    trace: list[str] = field(default_factory=list)
    project_context: dict[str, object] | None = None
    attachment_contexts: list[LoadedAttachmentContext] = field(default_factory=list)
    retrieval_decision: RetrievalDecision | None = None
    citations: list[CitationView] = field(default_factory=list)
    used_tools: list[str] = field(default_factory=list)
    answer: str = ''
    provider_code: str = 'SYSTEM'
    model_code: str = 'agent-graph'
    confidence: float | None = None
    usage: AdapterUsage = field(default_factory=AdapterUsage)
    fallback_used: bool = False
    fallback_reason: str | None = None
    orchestration_mode: str = 'linear'
    orchestration_fallback_detail: str | None = None


class AgentGraphService:
    def _trace(self, state: AgentState, step: str) -> AgentState:
        state.trace.append(step)
        return state

    async def _run_with_orchestration_fallback(self, state: AgentState) -> AgentState:
        if not LANGGRAPH_AVAILABLE:
            detail = LANGGRAPH_IMPORT_ERROR or 'LangGraph import failed for an unknown reason.'
            state.orchestration_mode = 'linear'
            state.orchestration_fallback_detail = f'dependency_missing: {detail}'
            self._trace(state, 'agent.orchestration.linear.dependency_missing')
            logger.info(
                'agent_graph_linear_fallback request_id=%s agent_type=%s reason=dependency_missing detail=%s',
                state.request_id,
                state.request.agent_type,
                detail,
            )
            return await self._run_linear(state)

        try:
            state.orchestration_mode = 'langgraph'
            self._trace(state, 'agent.orchestration.langgraph')
            return await self._run_langgraph(state)
        except Exception as exc:
            detail = f'{exc.__class__.__name__}: {exc}'
            state.orchestration_mode = 'linear'
            state.orchestration_fallback_detail = f'runtime_failure: {detail}'
            self._trace(state, 'agent.orchestration.linear.runtime_failure')
            logger.warning(
                'agent_graph_linear_fallback request_id=%s agent_type=%s reason=runtime_failure detail=%s',
                state.request_id,
                state.request.agent_type,
                detail,
                exc_info=True,
            )
            return await self._run_linear(state)

    async def run(self, db: Session, context: RequestContext, request: AgentRunRequest) -> AgentRunView:
        request_id = context.request_id
        started_at = perf_counter()
        state = AgentState(request=request, db=db, context=context, request_id=request_id)
        try:
            state = await self._run_with_orchestration_fallback(state)
        except Exception as exc:
            reason = str(exc)
            if state.orchestration_fallback_detail:
                reason = f'{state.orchestration_fallback_detail}; linear_execution_failed: {exc}'
            logger.warning(
                'agent_graph_chat_fallback request_id=%s agent_type=%s orchestration_mode=%s reason=%s',
                request_id,
                request.agent_type,
                state.orchestration_mode,
                reason,
                exc_info=True,
            )
            fallback = await self._fallback_to_chat(db, context, request, reason)
            return AgentRunView(
                request_id=fallback.request_id,
                agent_type=request.agent_type,
                answer=fallback.answer,
                citations=fallback.citations,
                used_tools=fallback.used_tools + ['agent_graph_fallback'],
                provider_code=fallback.provider_code,
                model_code=fallback.model_code,
                confidence=fallback.confidence,
                latency_ms=fallback.latency_ms,
                execution_trace=['agent.start', 'agent.fallback.chat_service'],
                fallback_used=True,
                fallback_reason=reason,
                usage=fallback.usage,
            )

        latency_ms = max(1, int((perf_counter() - started_at) * 1000))
        return AgentRunView(
            request_id=request_id,
            agent_type=request.agent_type,
            answer=state.answer,
            citations=state.citations,
            used_tools=state.used_tools,
            provider_code=state.provider_code,
            model_code=state.model_code,
            confidence=state.confidence,
            latency_ms=latency_ms,
            execution_trace=state.trace,
            fallback_used=state.fallback_used,
            fallback_reason=state.fallback_reason,
            usage=AdapterUsage(
                prompt_tokens=state.usage.prompt_tokens,
                completion_tokens=state.usage.completion_tokens,
                total_tokens=state.usage.total_tokens,
            ),
        )

    async def _run_langgraph(self, state: AgentState) -> AgentState:
        graph = StateGraph(dict)
        graph.add_node('bootstrap', self._bootstrap_node)
        graph.add_node('project_qa', self._project_qa_node)
        graph.add_node('document_summary_compare', self._document_summary_compare_node)
        graph.add_node('project_weekly_report', self._project_weekly_report_node)
        graph.set_entry_point('bootstrap')
        graph.add_conditional_edges(
            'bootstrap',
            self._route_node,
            {
                'project_qa': 'project_qa',
                'document_summary_compare': 'document_summary_compare',
                'project_weekly_report': 'project_weekly_report',
            },
        )
        graph.add_edge('project_qa', END)
        graph.add_edge('document_summary_compare', END)
        graph.add_edge('project_weekly_report', END)
        compiled = graph.compile()
        payload = await compiled.ainvoke({'state': state})
        return payload['state']

    async def _bootstrap_node(self, payload: dict[str, Any]) -> dict[str, Any]:
        state: AgentState = payload['state']
        self._trace(state, 'agent.bootstrap')
        return {'state': state}

    def _route_node(self, payload: dict[str, Any]) -> str:
        state: AgentState = payload['state']
        return state.request.agent_type

    async def _run_linear(self, state: AgentState) -> AgentState:
        self._trace(state, 'agent.bootstrap')
        if state.request.agent_type == 'project_qa':
            return await self._project_qa_state(state)
        if state.request.agent_type == 'document_summary_compare':
            return await self._document_summary_compare_state(state)
        if state.request.agent_type == 'project_weekly_report':
            return await self._project_weekly_report_state(state)
        raise AiServiceError('AGENT_NOT_FOUND', f'Unsupported agent `{state.request.agent_type}`', status_code=404)

    async def _project_qa_node(self, payload: dict[str, Any]) -> dict[str, Any]:
        state: AgentState = await self._project_qa_state(payload['state'])
        return {'state': state}

    async def _document_summary_compare_node(self, payload: dict[str, Any]) -> dict[str, Any]:
        state: AgentState = await self._document_summary_compare_state(payload['state'])
        return {'state': state}

    async def _project_weekly_report_node(self, payload: dict[str, Any]) -> dict[str, Any]:
        state: AgentState = await self._project_weekly_report_state(payload['state'])
        return {'state': state}

    async def _project_qa_state(self, state: AgentState) -> AgentState:
        self._trace(state, 'project_qa.load_project')
        project_id = state.request.context.project_id
        if project_id is None:
            raise AiServiceError('AGENT_CONTEXT_MISSING', 'Project QA agent requires project context', status_code=400)
        state.project_context = await fetch_project_context(project_id, state.request_id)
        self._trace(state, 'project_qa.retrieve')
        state.retrieval_decision = await rag_service.query(self._chat_request(state.request), state.context)
        self._trace(state, 'project_qa.answer')
        result = await self._model_answer(
            db=state.db,
            request=state.request,
            system_prompt=(
                f'You are the Erise project QA agent. '
                f'Project: {state.project_context.get("name") if state.project_context else project_id}. '
                'Answer using the provided project-private evidence first. If evidence is weak, say so clearly.'
            ),
            user_prompt=state.request.message,
            context_messages=state.retrieval_decision.context_messages if state.retrieval_decision else [],
        )
        state.answer = result.text
        state.provider_code = result.provider_code
        state.model_code = result.model_code
        state.usage = result.usage
        state.citations = state.retrieval_decision.citations if state.retrieval_decision else []
        state.used_tools = ['agent.project_qa'] + (state.retrieval_decision.used_tools if state.retrieval_decision else [])
        state.confidence = state.retrieval_decision.confidence if state.retrieval_decision else None
        return state

    async def _document_summary_compare_state(self, state: AgentState) -> AgentState:
        self._trace(state, 'document_summary_compare.load_attachments')
        state.attachment_contexts = await load_attachment_contexts(self._chat_request(state.request), state.request_id)
        ready = [item for item in state.attachment_contexts if item.is_ready]
        if not ready:
            raise AiServiceError('AGENT_CONTEXT_MISSING', 'Document summary/compare agent requires ready document or file attachments', status_code=400)
        self._trace(state, 'document_summary_compare.compose')
        if len(ready) == 1:
            title = ready[0].title or 'Untitled attachment'
            body = ready[0].plain_text or ready[0].summary or ''
            prompt = f'Summarize this document in Chinese with key findings and action points.\nTitle: {title}\n\nContent:\n{body[:9000]}'
            used_tools = ['agent.document_summary']
        else:
            first, second = ready[0], ready[1]
            prompt = (
                'Compare the following two documents in Chinese. Explain overlap, differences, risks, and recommended next steps.\n\n'
                f'Document A: {first.title}\n{(first.plain_text or first.summary or "")[:6000]}\n\n'
                f'Document B: {second.title}\n{(second.plain_text or second.summary or "")[:6000]}'
            )
            used_tools = ['agent.document_compare']
        result = await self._model_answer(
            db=state.db,
            request=state.request,
            system_prompt='You are the Erise document summary and comparison agent. Use only the supplied attachment content.',
            user_prompt=prompt,
            context_messages=[],
        )
        state.answer = result.text
        state.provider_code = result.provider_code
        state.model_code = result.model_code
        state.usage = result.usage
        state.used_tools = used_tools
        state.citations = [self._attachment_citation(item) for item in ready[:2]]
        state.confidence = 0.86 if len(ready) == 1 else 0.81
        return state

    async def _project_weekly_report_state(self, state: AgentState) -> AgentState:
        self._trace(state, 'project_weekly_report.load_project')
        project_id = state.request.context.project_id
        if project_id is None:
            raise AiServiceError('AGENT_CONTEXT_MISSING', 'Project weekly report agent requires project context', status_code=400)
        state.project_context = await fetch_project_context(project_id, state.request_id)
        self._trace(state, 'project_weekly_report.retrieve')
        state.retrieval_decision = await rag_service.query(self._chat_request(state.request), state.context)
        self._trace(state, 'project_weekly_report.draft')
        result = await self._model_answer(
            db=state.db,
            request=state.request,
            system_prompt='You are the Erise weekly report draft agent. Draft a concise Chinese weekly report with sections for progress, risks, next week, and coordination needs.',
            user_prompt=(
                f'Project: {state.project_context.get("name") if state.project_context else project_id}\n'
                f'Description: {state.project_context.get("description") if state.project_context else ""}\n'
                f'User intent: {state.request.message}'
            ),
            context_messages=state.retrieval_decision.context_messages if state.retrieval_decision else [],
        )
        state.answer = result.text
        state.provider_code = result.provider_code
        state.model_code = result.model_code
        state.usage = result.usage
        state.citations = state.retrieval_decision.citations if state.retrieval_decision else []
        state.used_tools = ['agent.project_weekly_report'] + (state.retrieval_decision.used_tools if state.retrieval_decision else [])
        state.confidence = state.retrieval_decision.confidence if state.retrieval_decision else 0.78
        return state

    async def _model_answer(
        self,
        *,
        db: Session,
        request: AgentRunRequest,
        system_prompt: str,
        user_prompt: str,
        context_messages: list[dict[str, str]],
    ) -> AdapterResult:
        model = get_model_config(db, request.model_code)
        adapter = get_model_adapter(model)
        messages = [{'role': 'system', 'content': system_prompt}]
        messages.extend(context_messages)
        messages.append({'role': 'user', 'content': user_prompt})
        return await adapter.chat(model.model_code, messages, request.temperature, request.max_tokens)

    def _chat_request(self, request: AgentRunRequest) -> ChatCompletionRequest:
        return ChatCompletionRequest.model_validate(request.model_dump(by_alias=True))

    def _attachment_citation(self, attachment: LoadedAttachmentContext) -> CitationView:
        source_type = attachment.attachment_type
        source_id = attachment.source_id
        if source_type == 'DOCUMENT':
            url = f'/documents/{source_id}/edit?mode=preview'
        elif source_type == 'FILE':
            url = f'/files/{source_id}'
        else:
            url = None
        return CitationView(
            source_type=source_type,
            source_id=source_id,
            source_title=attachment.title or f'{source_type}-{source_id}',
            snippet=(attachment.summary or attachment.plain_text or attachment.snippet or '')[:240] or None,
            page_no=None,
            section_path=None,
            score=None,
            url=url,
        )

    async def _fallback_to_chat(
        self,
        db: Session,
        context: RequestContext,
        request: AgentRunRequest,
        reason: str,
    ):
        from src.app.services.chat_service import chat_service

        fallback_request = ChatCompletionRequest.model_validate(request.model_dump(by_alias=True))
        logger.warning(
            'agent_graph_fallback request_id=%s agent_type=%s reason=%s',
            context.request_id,
            request.agent_type,
            reason,
        )
        return await chat_service.complete(db, context, fallback_request)


agent_graph_service = AgentGraphService()
