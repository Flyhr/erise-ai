from __future__ import annotations

import asyncio
from dataclasses import dataclass

from sqlalchemy import select
from sqlalchemy.orm import Session

from src.app.adapters.java.attachment_client import fetch_document_context, fetch_file_context, fetch_temp_file_context
from src.app.adapters.java.project_client import fetch_project_context
from src.app.api.deps import RequestContext
from src.app.core.config import get_settings
from src.app.core.constants import ROLE_ASSISTANT, ROLE_USER
from src.app.models.ai_message import AiChatMessage
from src.app.models.ai_session import AiChatSession
from src.app.schemas.chat import AttachmentContext, ChatCompletionRequest
from src.app.services.prompt_service import get_system_prompt

MAX_ATTACHMENT_CONTEXT_CHARS = 4000
MAX_ATTACHMENTS_PER_REQUEST = 8
READY_STATUSES = {'READY', 'SUCCESS', 'INDEXED', 'COMPLETED'}
PROCESSING_STATUSES = {'PROCESSING'}
PENDING_STATUSES = {'INIT', 'PENDING', 'UPLOADING'}
FAILED_STATUSES = {'FAILED', 'DELETED'}
FORMAT_GUIDANCE_MESSAGE = (
    '回答格式要求：默认使用结构化 Markdown。先给出简洁结论，再按需要分段或分小节；'
    '只有在确实需要列点时才使用列表，并统一使用 "-" 或 "1."，不要使用 "*" 作为常规项目符号；'
    '段落之间保留空行；如果资料不足，要明确说明，不要伪造引用来源。'
)


@dataclass(slots=True)
class LoadedAttachmentContext:
    attachment_type: str
    source_id: int
    project_id: int | None
    title: str
    summary: str
    plain_text: str
    snippet: str
    truncated: bool
    parse_status: str | None
    index_status: str | None
    parse_error_message: str | None
    readiness: str

    @property
    def has_material(self) -> bool:
        return bool(self.summary or self.plain_text)

    @property
    def is_ready(self) -> bool:
        return self.readiness == 'ready'

    @property
    def display_status(self) -> str:
        return {
            'ready': '可引用',
            'processing': '解析中',
            'pending': '待解析',
            'failed': '解析失败',
            'empty': '暂无可用正文',
        }.get(self.readiness, '状态未知')


def _trim_text(text: str, limit: int = MAX_ATTACHMENT_CONTEXT_CHARS) -> tuple[str, bool]:
    normalized = ' '.join((text or '').split())
    if len(normalized) <= limit:
        return normalized, False
    return normalized[:limit].rstrip(), True


def _normalize_status(value: object) -> str:
    return str(value or '').strip().upper()


def _resolve_attachment_readiness(parse_status: str, index_status: str, has_material: bool) -> str:
    if has_material:
        return 'ready'

    statuses = [status for status in (parse_status, index_status) if status]
    if not statuses:
        return 'empty'
    if any(status in FAILED_STATUSES for status in statuses):
        return 'failed'
    if any(status in PROCESSING_STATUSES for status in statuses):
        return 'processing'
    if any(status in READY_STATUSES for status in statuses):
        return 'empty'
    if any(status in PENDING_STATUSES for status in statuses):
        return 'pending'
    return 'empty'


async def _fetch_attachment_payload(attachment: AttachmentContext, request_id: str) -> dict[str, object] | None:
    if attachment.attachment_type == 'DOCUMENT':
        return await fetch_document_context(attachment.source_id, request_id)
    if attachment.attachment_type == 'TEMP_FILE':
        return await fetch_temp_file_context(attachment.source_id, request_id)
    return await fetch_file_context(attachment.source_id, request_id)


async def load_attachment_contexts(request: ChatCompletionRequest, request_id: str) -> list[LoadedAttachmentContext]:
    attachments = request.context.attachments[:MAX_ATTACHMENTS_PER_REQUEST]
    if not attachments:
        return []

    results = await asyncio.gather(
        *[_fetch_attachment_payload(item, request_id) for item in attachments],
        return_exceptions=True,
    )

    loaded: list[LoadedAttachmentContext] = []
    for attachment, payload in zip(attachments, results, strict=False):
        if isinstance(payload, Exception) or payload is None or not isinstance(payload, dict):
            continue

        title = (
            str(payload.get('title') or '')
            or str(payload.get('fileName') or '')
            or attachment.title
            or f'{attachment.attachment_type} #{attachment.source_id}'
        )
        project_id = payload.get('projectId') or attachment.project_id or request.context.project_id
        summary = str(payload.get('summary') or '').strip()
        plain_text = str(payload.get('plainText') or '').strip()
        trimmed_text, truncated = _trim_text(plain_text)
        parse_status = _normalize_status(payload.get('parseStatus'))
        index_status = _normalize_status(payload.get('indexStatus'))
        parse_error_message = str(payload.get('parseErrorMessage') or '').strip() or None
        readiness = _resolve_attachment_readiness(parse_status, index_status, bool(summary or trimmed_text))

        loaded.append(LoadedAttachmentContext(
            attachment_type=attachment.attachment_type,
            source_id=attachment.source_id,
            project_id=project_id,
            title=title,
            summary=summary,
            plain_text=trimmed_text,
            snippet=(trimmed_text or summary or parse_error_message or '')[:220],
            truncated=truncated,
            parse_status=parse_status or None,
            index_status=index_status or None,
            parse_error_message=parse_error_message,
            readiness=readiness,
        ))
    return loaded


def _build_attachment_messages(attachments: list[LoadedAttachmentContext]) -> list[dict[str, str]]:
    if not attachments:
        return []

    scope_instruction = (
        '本轮对话附带了用户主动指定的文档、文件或聊天临时附件。'
        '只要用户提到“发给你的文件”“聊天附件”“这份资料”“这个文档”或“这个 PDF”，'
        '都要优先以这些附件内容为主，再结合检索结果补充引用。'
    )
    if len(attachments) == 1:
        scope_instruction += '当前只有 1 份附件，用户使用单数表达时默认指向这份附件。'
    else:
        scope_instruction += f'当前共有 {len(attachments)} 份附件，用户未点名具体文件时默认汇总全部已附加资料回答。'

    messages: list[dict[str, str]] = [
        {'role': 'system', 'content': scope_instruction},
        {'role': 'system', 'content': FORMAT_GUIDANCE_MESSAGE},
    ]

    for attachment in attachments:
        attachment_label = '临时附件' if attachment.attachment_type == 'TEMP_FILE' else ('文档' if attachment.attachment_type == 'DOCUMENT' else '文件')
        lines = [f'用户附带了{attachment_label}《{attachment.title}》。']
        if attachment.project_id:
            lines.append(f'所属项目 ID: {attachment.project_id}')
        lines.append(f'当前状态: {attachment.display_status}')
        if attachment.summary:
            lines.append(f'摘要: {attachment.summary}')
        if attachment.plain_text:
            lines.append('正文内容:')
            lines.append(attachment.plain_text)
        elif attachment.parse_error_message:
            lines.append(f'失败原因: {attachment.parse_error_message}')
            lines.append('当前还没有可引用的正文，不要假装已经读到全文。')
        else:
            lines.append('当前还没有可引用的正文，不要假装已经读到全文。')
        if attachment.truncated:
            lines.append('[正文已按上下文上限截断，回答时优先依据已提供内容。]')
        messages.append({'role': 'system', 'content': '\n'.join(lines)})
    return messages


async def build_prompt_messages(
    db: Session,
    context: RequestContext,
    session: AiChatSession,
    request: ChatCompletionRequest,
    exclude_message_id: int | None = None,
    attachment_contexts: list[LoadedAttachmentContext] | None = None,
) -> list[dict[str, str]]:
    settings = get_settings()
    messages: list[dict[str, str]] = [
        {'role': 'system', 'content': get_system_prompt(db, session.scene)},
        {'role': 'system', 'content': FORMAT_GUIDANCE_MESSAGE},
    ]

    if session.project_id:
        project = await fetch_project_context(session.project_id, context.request_id)
        if project:
            summary = f"项目名称: {project.get('name')}\n项目描述: {project.get('description') or '暂无'}"
            messages.append({'role': 'system', 'content': summary})

    resolved_attachment_contexts = attachment_contexts
    if resolved_attachment_contexts is None:
        resolved_attachment_contexts = await load_attachment_contexts(request, context.request_id)
    messages.extend(_build_attachment_messages(resolved_attachment_contexts))

    if request.mode and request.mode.upper() == 'SCOPED':
        messages.append({
            'role': 'system',
            'content': '本轮请求处于指定范围模式。请先使用当前项目上下文、显式附加的知识库文件/文档和临时文件回答；如果范围内资料不足，再根据联网开关决定是否参考后续提供的联网结果；仍不足时可以使用通用知识补充，但不要伪造范围内引用。',
        })

    if request.web_search_enabled is False:
        messages.append({
            'role': 'system',
            'content': '本轮请求未开启联网搜索。请不要伪造联网来源；如果上下文不足，可以明确说明，并使用通用知识补充回答。',
        })

    if session.summary_text:
        messages.append({'role': 'system', 'content': f'历史摘要:\n{session.summary_text}'})

    history = db.execute(
        select(AiChatMessage)
        .where(AiChatMessage.session_id == session.id)
        .order_by(AiChatMessage.sequence_no.desc())
        .limit(settings.context_history_limit * 2)
    ).scalars().all()

    for item in reversed(history):
        if exclude_message_id is not None and item.id == exclude_message_id:
            continue
        if not item.content:
            continue
        if item.role not in {ROLE_USER, ROLE_ASSISTANT}:
            continue
        messages.append({'role': item.role, 'content': item.content})

    messages.append({'role': ROLE_USER, 'content': request.message})
    return messages
