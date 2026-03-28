from __future__ import annotations

import asyncio

from sqlalchemy import select
from sqlalchemy.orm import Session

from src.app.adapters.java.attachment_client import fetch_document_context, fetch_file_context
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


def _trim_text(text: str, limit: int = MAX_ATTACHMENT_CONTEXT_CHARS) -> tuple[str, bool]:
    normalized = ' '.join((text or '').split())
    if len(normalized) <= limit:
        return normalized, False
    return normalized[:limit].rstrip(), True


async def _fetch_attachment_payload(attachment: AttachmentContext, request_id: str) -> dict[str, object] | None:
    if attachment.attachment_type == 'DOCUMENT':
        return await fetch_document_context(attachment.source_id, request_id)
    return await fetch_file_context(attachment.source_id, request_id)


async def _build_attachment_messages(request: ChatCompletionRequest, request_id: str) -> list[dict[str, str]]:
    attachments = request.context.attachments[:MAX_ATTACHMENTS_PER_REQUEST]
    if not attachments:
        return []

    results = await asyncio.gather(
        *[_fetch_attachment_payload(item, request_id) for item in attachments],
        return_exceptions=True,
    )

    messages: list[dict[str, str]] = [
        {
            'role': 'system',
            'content': '本轮对话附带了用户主动指定的文件或文档。只要用户提到“发送给你的文档”或“这份文件”，优先以这些附件内容为准。',
        }
    ]
    for attachment, payload in zip(attachments, results, strict=False):
        if isinstance(payload, Exception) or payload is None:
            messages.append(
                {
                    'role': 'system',
                    'content': f'附件读取失败: {attachment.attachment_type} #{attachment.source_id}。如果用户要求基于该附件精确回答，请提醒其稍后重试。',
                }
            )
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
        attachment_label = '文档' if attachment.attachment_type == 'DOCUMENT' else '文件'
        file_ext = str(payload.get('fileExt') or '').strip()

        context_lines = [f'用户附带了{attachment_label}《{title}》。']
        if project_id:
            context_lines.append(f'所属项目 ID: {project_id}')
        if summary:
            context_lines.append(f'摘要: {summary}')
        if file_ext:
            context_lines.append(f'文件类型: {file_ext}')
        if trimmed_text:
            context_lines.append('正文内容:')
            context_lines.append(trimmed_text)
        else:
            context_lines.append('当前未读取到可用正文，请仅基于标题和摘要回答。')
        if truncated:
            context_lines.append('[正文已按上下文限制截断，回答时优先基于已提供部分。]')
        messages.append({'role': 'system', 'content': '\n'.join(context_lines)})
    return messages


async def build_prompt_messages(
    db: Session,
    context: RequestContext,
    session: AiChatSession,
    request: ChatCompletionRequest,
    exclude_message_id: int | None = None,
) -> list[dict[str, str]]:
    settings = get_settings()
    messages: list[dict[str, str]] = [{'role': 'system', 'content': get_system_prompt(db, session.scene)}]

    if session.project_id:
        project = await fetch_project_context(session.project_id, context.request_id)
        if project:
            summary = f"项目名称: {project.get('name')}\n项目描述: {project.get('description') or '暂无'}"
            messages.append({'role': 'system', 'content': summary})

    messages.extend(await _build_attachment_messages(request, context.request_id))

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