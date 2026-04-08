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


def _trim_text(text: str, limit: int = MAX_ATTACHMENT_CONTEXT_CHARS) -> tuple[str, bool]:
    normalized = ' '.join((text or '').split())
    if len(normalized) <= limit:
        return normalized, False
    return normalized[:limit].rstrip(), True


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
        if isinstance(payload, Exception) or payload is None:
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
        if not summary and not trimmed_text:
            continue
        loaded.append(LoadedAttachmentContext(
            attachment_type=attachment.attachment_type,
            source_id=attachment.source_id,
            project_id=project_id,
            title=title,
            summary=summary,
            plain_text=trimmed_text,
            snippet=(trimmed_text or summary)[:220],
            truncated=truncated,
        ))
    return loaded


def _build_attachment_messages(attachments: list[LoadedAttachmentContext]) -> list[dict[str, str]]:
    if not attachments:
        return []

    messages: list[dict[str, str]] = [{
        'role': 'system',
        'content': '本轮对话附带了用户主动指定的文档、文件或聊天临时附件。只要用户提到“发给你的文件”“聊天附件”或“这份资料”，优先以这些内容为准。',
    }]
    for attachment in attachments:
        attachment_label = '临时附件' if attachment.attachment_type == 'TEMP_FILE' else ('文档' if attachment.attachment_type == 'DOCUMENT' else '文件')
        lines = [f'用户附带了{attachment_label}《{attachment.title}》。']
        if attachment.project_id:
            lines.append(f'所属项目 ID: {attachment.project_id}')
        if attachment.summary:
            lines.append(f'摘要: {attachment.summary}')
        if attachment.plain_text:
            lines.append('正文内容:')
            lines.append(attachment.plain_text)
        else:
            lines.append('当前未读取到可用正文，请仅基于标题和摘要回答。')
        if attachment.truncated:
            lines.append('[正文已按上下文限制截断，回答时优先基于已提供部分。]')
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
    messages: list[dict[str, str]] = [{'role': 'system', 'content': get_system_prompt(db, session.scene)}]

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
            'content': '本轮请求是指定范围模式。请只基于当前项目上下文和用户附带资料回答；如果依据不足，请直接说明，不要越权扩展。',
        })

    if request.web_search_enabled is False:
        messages.append({
            'role': 'system',
            'content': '本轮请求未开启联网搜索。请不要伪造联网来源，如果上下文不足，需要明确说明。',
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
