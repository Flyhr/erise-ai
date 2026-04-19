from __future__ import annotations

import re
from datetime import date
from time import perf_counter

from src.app.actions.protocol import (
    ActionDefinition,
    ActionExecutionResult,
    ActionPermissionDecision,
    ActionRuntimeContext,
)
from src.app.adapters.java.attachment_client import (
    archive_file,
    fetch_document_context,
    fetch_file_context,
    update_document_content,
    update_document_summary,
    update_document_tags,
    update_document_title,
    update_file_content,
    update_file_title,
)
from src.app.adapters.java.project_client import create_project_weekly_report_draft, fetch_project_context
from src.app.adapters.llm.base import AdapterUsage
from src.app.core.exceptions import AiServiceError
from src.app.models.ai_message_citation import AiMessageCitation
from src.app.schemas.chat import ChatCompletionRequest
from src.app.schemas.common import CamelModel
from src.app.services.model_registry import get_model_adapter, get_model_config
from src.app.services.n8n_event_service import n8n_event_service

SYSTEM_PROVIDER_CODE = 'SYSTEM'
SYSTEM_ACTION_SOURCE = 'SYSTEM_ACTION'
ACTION_FALLBACK_SOURCE = 'ACTION_FALLBACK'

TITLE_UPDATE_PATTERNS = (
    r'(?:把|将)?(?:发送给你(?:的)?|附件里(?:的)?|这个|这份|该|当前)?(?:文档|文件|附件)(?:的)?标题'
    r'(?:修改为|改为|改成|设置为|变更为)\s*[:：]?\s*[“"\']?(?P<title>[^”"\'\n]{1,120})',
    r'(?:把|将)?(?:文档标题|文件标题|标题)'
    r'(?:修改为|改为|改成|设置为|变更为)\s*[:：]?\s*[“"\']?(?P<title>[^”"\'\n]{1,120})',
)

SUMMARY_PATTERNS = (
    r'(?:生成|写|补充|更新).{0,6}(?:文档|这份文档|该文档).{0,6}(?:摘要|简介|概述)',
    r'(?:为|给).{0,6}(?:文档|这份文档|该文档).{0,6}(?:生成|写).{0,6}(?:摘要|简介|概述)',
)

TAG_PATTERNS = (
    r'(?:把|将)?(?:文档|这份文档|该文档).{0,6}(?:标签|tag)'
    r'(?:修改为|改为|改成|设置为|更新为|设为)\s*[:：]?\s*(?P<tags>.+)$',
    r'(?:给|为)?(?:文档|这份文档|该文档).{0,6}(?:添加|打上|设置).{0,6}(?:标签|tag)\s*[:：]?\s*(?P<tags>.+)$',
)

ARCHIVE_FILE_PATTERNS = (
    r'(?:把|将)?(?:这个|这份|该|当前)?(?:文件|附件)(?:进行)?归档',
    r'(?:归档)(?:这个|这份|该|当前)?(?:文件|附件)',
)

WEEKLY_REPORT_PATTERNS = (
    r'(?:创建|生成|写|产出).{0,8}(?:项目)?周报(?:草稿)?',
    r'(?:项目)?周报(?:草稿)?(?:创建|生成|写一份|来一份)',
)


CONTENT_UPDATE_PATTERNS = (
    r'(?:修改|改写|重写|更新|润色|补充|追加|增加|添加|插入|删除|删掉|替换).{0,24}(?:正文|内容|文本|文案)',
    r'(?:把|将|在).{0,12}(?:这个|这份|当前)?(?:文档|文件).{0,24}(?:修改|改写|重写|更新|润色|补充|追加|增加|添加|插入|删除|删掉|替换)',
    r'(?:edit|rewrite|update|revise|append|add|insert|delete|replace).{0,20}(?:content|body|text)',
)

EXPLICIT_APPEND_PATTERNS = (
    r'(?:增加|添加|补充|追加|插入)\s*[:：]\s*(?P<content>.+)$',
    r'(?:增加|添加|补充|追加|插入).{0,8}(?:以下|这句|这段|如下)?(?:内容|文本)?\s*[:：]\s*(?P<content>.+)$',
    r'(?:add|append|insert)\s*[:：]\s*(?P<content>.+)$',
)


class DocumentTitleActionParams(CamelModel):
    title: str


class DocumentSummaryActionParams(CamelModel):
    summary_style: str = 'concise'


class DocumentTagsActionParams(CamelModel):
    tags: list[str]


class ArchiveFileActionParams(CamelModel):
    archive_reason: str = 'user_request'


class WeeklyReportDraftActionParams(CamelModel):
    instruction: str


class ContentUpdateActionParams(CamelModel):
    instruction: str
    append_text: str | None = None


def _normalize_message(message: str) -> str:
    return ' '.join(message.strip().split())


def _resolve_document_id(request: ChatCompletionRequest) -> int | None:
    if request.context.document_id:
        return request.context.document_id
    attachments = [item for item in request.context.attachments if item.attachment_type == 'DOCUMENT']
    if len(attachments) == 1:
        return attachments[0].source_id
    return None


def _resolve_file_id(request: ChatCompletionRequest) -> int | None:
    attachments = [item for item in request.context.attachments if item.attachment_type == 'FILE']
    if len(attachments) == 1:
        return attachments[0].source_id
    return None


def _has_document_target_context(request: ChatCompletionRequest) -> bool:
    return bool(request.context.document_id) or any(item.attachment_type == 'DOCUMENT' for item in request.context.attachments)


def _has_file_target_context(request: ChatCompletionRequest) -> bool:
    return any(item.attachment_type == 'FILE' for item in request.context.attachments)


def _title_request_mentions_file(message: str) -> bool:
    normalized = _normalize_message(message)
    return bool(re.search(r'(?:文件|附件)(?:的)?标题|(?:这个|这份|该|当前)?(?:文件|附件)', normalized, flags=re.IGNORECASE))


def _resolve_project_id(request: ChatCompletionRequest) -> int | None:
    if request.context.project_id:
        return request.context.project_id
    project_ids = {item.project_id for item in request.context.attachments if item.project_id}
    if len(project_ids) == 1:
        return next(iter(project_ids))
    return None


def _split_tags(raw_tags: str) -> list[str]:
    value = raw_tags.strip().strip('。；; ')
    parts = re.split(r'[，,、/|；;]+', value)
    return [item.strip(' "\'“”') for item in parts if item.strip(' "\'“”')]


def _extract_requested_title(message: str) -> str | None:
    normalized = _normalize_message(message)
    for pattern in TITLE_UPDATE_PATTERNS:
        matched = re.search(pattern, normalized, flags=re.IGNORECASE)
        if not matched:
            continue
        title = matched.group('title').strip('“”"\' ,，。;；：:')
        if title:
            return title[:120]
    return None


def _has_content_update_intent(message: str) -> bool:
    normalized = _normalize_message(message)
    return any(re.search(pattern, normalized, flags=re.IGNORECASE) for pattern in CONTENT_UPDATE_PATTERNS)


def _extract_append_text(message: str) -> str | None:
    normalized = _normalize_message(message)
    for pattern in EXPLICIT_APPEND_PATTERNS:
        matched = re.search(pattern, normalized, flags=re.IGNORECASE)
        if not matched:
            continue
        content = matched.group('content').strip(' “"\'，。；;')
        if content:
            return content[:1000]
    return None


def _content_update_params(request: ChatCompletionRequest) -> ContentUpdateActionParams | None:
    if not _has_content_update_intent(request.message):
        return None
    normalized = _normalize_message(request.message)
    return ContentUpdateActionParams(instruction=normalized, append_text=_extract_append_text(normalized))


def match_document_title_action(request: ChatCompletionRequest) -> DocumentTitleActionParams | None:
    if not _has_document_target_context(request) and _has_file_target_context(request):
        return None
    title = _extract_requested_title(request.message)
    if not title:
        return None
    return DocumentTitleActionParams(title=title)


def match_file_title_action(request: ChatCompletionRequest) -> DocumentTitleActionParams | None:
    title = _extract_requested_title(request.message)
    if not title:
        return None
    if _has_document_target_context(request):
        return None
    if _has_file_target_context(request) or _title_request_mentions_file(request.message):
        return DocumentTitleActionParams(title=title)
    return None


def match_document_content_action(request: ChatCompletionRequest) -> ContentUpdateActionParams | None:
    if not _has_document_target_context(request) or _has_file_target_context(request):
        return None
    return _content_update_params(request)


def match_file_content_action(request: ChatCompletionRequest) -> ContentUpdateActionParams | None:
    if _has_document_target_context(request) or not _has_file_target_context(request):
        return None
    return _content_update_params(request)


def match_document_summary_action(request: ChatCompletionRequest) -> DocumentSummaryActionParams | None:
    normalized = _normalize_message(request.message)
    if any(re.search(pattern, normalized, flags=re.IGNORECASE) for pattern in SUMMARY_PATTERNS):
        return DocumentSummaryActionParams()
    return None


def match_document_tags_action(request: ChatCompletionRequest) -> DocumentTagsActionParams | None:
    normalized = _normalize_message(request.message)
    for pattern in TAG_PATTERNS:
        matched = re.search(pattern, normalized, flags=re.IGNORECASE)
        if not matched:
            continue
        tags = _split_tags(matched.group('tags'))
        if tags:
            return DocumentTagsActionParams(tags=tags[:10])
    return None


def match_archive_file_action(request: ChatCompletionRequest) -> ArchiveFileActionParams | None:
    normalized = _normalize_message(request.message)
    if any(re.search(pattern, normalized, flags=re.IGNORECASE) for pattern in ARCHIVE_FILE_PATTERNS):
        return ArchiveFileActionParams()
    return None


def match_weekly_report_draft_action(request: ChatCompletionRequest) -> WeeklyReportDraftActionParams | None:
    normalized = _normalize_message(request.message)
    if any(re.search(pattern, normalized, flags=re.IGNORECASE) for pattern in WEEKLY_REPORT_PATTERNS):
        return WeeklyReportDraftActionParams(instruction=normalized)
    return None


async def permission_document_target(runtime: ActionRuntimeContext, params: CamelModel) -> ActionPermissionDecision:
    del params
    document_id = _resolve_document_id(runtime.request)
    if not document_id:
        return ActionPermissionDecision(
            allowed=False,
            fallback_message='我识别到你想操作文档，但当前上下文里没有唯一可操作的文档。请先附上单个文档，或明确指定当前文档。',
        )
    resource = await fetch_document_context(document_id, runtime.request_id)
    if not resource:
        return ActionPermissionDecision(
            allowed=False,
            fallback_message='没有找到可操作的文档上下文，请确认文档仍然存在并且你拥有访问权限。',
        )
    return ActionPermissionDecision(allowed=True, target_type='DOCUMENT', target_id=document_id, resource=resource)


async def permission_file_target(runtime: ActionRuntimeContext, params: CamelModel) -> ActionPermissionDecision:
    del params
    file_id = _resolve_file_id(runtime.request)
    if not file_id:
        return ActionPermissionDecision(
            allowed=False,
            fallback_message='我识别到你想归档文件，但当前上下文里没有唯一可操作的文件。请先附上单个文件后再试。',
        )
    resource = await fetch_file_context(file_id, runtime.request_id)
    if not resource:
        return ActionPermissionDecision(
            allowed=False,
            fallback_message='没有找到可操作的文件上下文，请确认文件仍然存在并且你拥有访问权限。',
        )
    if int(resource.get('archived') or 0) == 1:
        return ActionPermissionDecision(
            allowed=False,
            target_type='FILE',
            target_id=file_id,
            resource=resource,
            fallback_message='这份文件已经归档了，不需要重复归档。',
        )
    return ActionPermissionDecision(allowed=True, target_type='FILE', target_id=file_id, resource=resource)


async def permission_file_title_target(runtime: ActionRuntimeContext, params: CamelModel) -> ActionPermissionDecision:
    del params
    file_id = _resolve_file_id(runtime.request)
    if not file_id:
        return ActionPermissionDecision(
            allowed=False,
            fallback_message='我识别到你想修改文件标题，但当前上下文里没有唯一可操作的文件。请先附上单个文件，或明确指定当前文件。',
        )
    resource = await fetch_file_context(file_id, runtime.request_id)
    if not resource:
        return ActionPermissionDecision(
            allowed=False,
            fallback_message='没有找到可操作的文件上下文，请确认文件仍然存在并且你拥有访问权限。',
        )
    if int(resource.get('archived') or 0) == 1:
        return ActionPermissionDecision(
            allowed=False,
            target_type='FILE',
            target_id=file_id,
            resource=resource,
            fallback_message='这份文件已经归档，不能继续修改标题。请先恢复文件后再操作。',
        )
    return ActionPermissionDecision(allowed=True, target_type='FILE', target_id=file_id, resource=resource)


async def permission_file_content_target(runtime: ActionRuntimeContext, params: CamelModel) -> ActionPermissionDecision:
    del params
    file_id = _resolve_file_id(runtime.request)
    if not file_id:
        return ActionPermissionDecision(
            allowed=False,
            fallback_message='我识别到你想修改文件正文，但当前上下文里没有唯一可操作的文件。请先附上单个文件后再试。',
        )
    resource = await fetch_file_context(file_id, runtime.request_id)
    if not resource:
        return ActionPermissionDecision(
            allowed=False,
            fallback_message='没有找到可操作的文件上下文，请确认文件仍然存在并且你拥有访问权限。',
        )
    if int(resource.get('archived') or 0) == 1:
        return ActionPermissionDecision(
            allowed=False,
            target_type='FILE',
            target_id=file_id,
            resource=resource,
            fallback_message='这份文件已经归档，不能继续修改正文。请先恢复文件后再操作。',
        )
    extension = str(resource.get('fileExt') or '').lower()
    if extension not in {'doc', 'docx', 'txt'}:
        return ActionPermissionDecision(
            allowed=False,
            target_type='FILE',
            target_id=file_id,
            resource=resource,
            fallback_message='当前仅支持通过 AI 修改 doc、docx、txt 文件的在线正文内容。',
        )
    return ActionPermissionDecision(allowed=True, target_type='FILE', target_id=file_id, resource=resource)


async def permission_project_target(runtime: ActionRuntimeContext, params: CamelModel) -> ActionPermissionDecision:
    del params
    project_id = _resolve_project_id(runtime.request)
    if not project_id:
        return ActionPermissionDecision(
            allowed=False,
            fallback_message='我识别到你想创建项目周报，但当前请求里没有明确项目。请先进入项目上下文，或在请求中带上项目 ID。',
        )
    resource = await fetch_project_context(project_id, runtime.request_id)
    if not resource:
        return ActionPermissionDecision(
            allowed=False,
            fallback_message='没有找到可操作的项目上下文，请确认项目仍然存在并且你拥有访问权限。',
        )
    return ActionPermissionDecision(allowed=True, target_type='PROJECT', target_id=project_id, resource=resource)


async def execute_document_title_action(
    runtime: ActionRuntimeContext,
    params: DocumentTitleActionParams,
    permission: ActionPermissionDecision,
) -> ActionExecutionResult:
    started_at = perf_counter()
    detail = await update_document_title(permission.target_id, runtime.user_id, params.title, runtime.request_id)
    latency_ms = max(1, int((perf_counter() - started_at) * 1000))
    title = str((detail or {}).get('title') or params.title)
    answer = f'已将文档标题更新为《{title}》。如果你愿意，我还可以继续帮你总结这份文档，或根据这份文档继续修改内容。'
    return ActionExecutionResult(
        action_code='document.update_title',
        answer=answer,
        answer_source=SYSTEM_ACTION_SOURCE,
        used_tools=['document.update_title'],
        provider_code=SYSTEM_PROVIDER_CODE,
        model_code='document-title-update',
        raw_payload={'document': detail},
        latency_ms=latency_ms,
        target_type=permission.target_type,
        target_id=permission.target_id,
    )


async def execute_file_title_action(
    runtime: ActionRuntimeContext,
    params: DocumentTitleActionParams,
    permission: ActionPermissionDecision,
) -> ActionExecutionResult:
    started_at = perf_counter()
    detail = await update_file_title(permission.target_id, runtime.user_id, params.title, runtime.request_id)
    latency_ms = max(1, int((perf_counter() - started_at) * 1000))
    file_name = str((detail or {}).get('fileName') or params.title)
    compact_detail = None
    if isinstance(detail, dict):
        compact_detail = {
            'id': detail.get('id'),
            'projectId': detail.get('projectId'),
            'fileName': detail.get('fileName'),
            'fileExt': detail.get('fileExt'),
            'mimeType': detail.get('mimeType'),
            'archived': detail.get('archived'),
            'updatedAt': detail.get('updatedAt'),
        }
    runtime.db.query(AiMessageCitation).filter(
        AiMessageCitation.user_id == runtime.user_id,
        AiMessageCitation.source_type == 'FILE',
        AiMessageCitation.source_id == permission.target_id,
    ).update(
        {AiMessageCitation.source_title: file_name},
        synchronize_session=False,
    )
    answer = f'已将文件标题更新为《{file_name}》。如果你愿意，我还可以继续帮你总结这份文件，或基于它继续整理项目知识。'
    return ActionExecutionResult(
        action_code='file.update_title',
        answer=answer,
        answer_source=SYSTEM_ACTION_SOURCE,
        used_tools=['file.update_title'],
        provider_code=SYSTEM_PROVIDER_CODE,
        model_code='file-title-update',
        raw_payload={'file': compact_detail},
        latency_ms=latency_ms,
        target_type=permission.target_type,
        target_id=permission.target_id,
    )


def _trim_text(text: str, limit: int = 6000) -> str:
    normalized = text.strip()
    if len(normalized) <= limit:
        return normalized
    return normalized[:limit].rstrip()


async def _run_model_text(
    runtime: ActionRuntimeContext,
    system_prompt: str,
    user_prompt: str,
) -> tuple[str, AdapterUsage, str, str]:
    model = get_model_config(runtime.db, runtime.request.model_code)
    adapter = get_model_adapter(model)
    result = await adapter.chat(
        model.model_code,
        [
            {'role': 'system', 'content': system_prompt},
            {'role': 'user', 'content': user_prompt},
        ],
        runtime.request.temperature,
        runtime.request.max_tokens,
    )
    return result.text.strip(), result.usage, result.provider_code, result.model_code


def _append_plain_text(current_text: str, append_text: str) -> str:
    base = current_text.strip()
    addition = append_text.strip()
    if not base:
        return addition
    if not addition:
        return base
    return f'{base}\n\n{addition}'


async def _build_revised_plain_text(
    runtime: ActionRuntimeContext,
    params: ContentUpdateActionParams,
    resource_title: str,
    current_plain_text: str,
    target_label: str,
) -> tuple[str, AdapterUsage, str, str]:
    if params.append_text:
        return _append_plain_text(current_plain_text, params.append_text), AdapterUsage(), SYSTEM_PROVIDER_CODE, 'content-append-action'

    revised, usage, provider_code, model_code = await _run_model_text(
        runtime,
        '你是 Erise-AI 的知识文稿编辑助手。请根据用户要求修改正文。只输出修改后的完整正文，不要解释，不要使用 Markdown 代码块。'
        '如果用户只要求增加或补充内容，请保留原文结构，并将新增内容放在最合适的位置；未指定位置时追加到正文末尾。',
        f'{target_label}标题：{resource_title or "未命名"}\n'
        f'用户要求：{params.instruction}\n\n'
        f'当前正文：\n{_trim_text(current_plain_text, 9000)}\n\n'
        '请输出修改后的完整正文：',
    )
    revised = revised.strip()
    if not revised:
        raise AiServiceError('ACTION_CONTENT_EMPTY', 'Revised content is empty', status_code=400)
    return revised, usage, provider_code, model_code


async def execute_document_content_action(
    runtime: ActionRuntimeContext,
    params: ContentUpdateActionParams,
    permission: ActionPermissionDecision,
) -> ActionExecutionResult:
    resource = permission.resource or {}
    title = str(resource.get('title') or '文档')
    current_plain_text = str(resource.get('plainText') or '')
    started_at = perf_counter()
    revised, usage, provider_code, model_code = await _build_revised_plain_text(
        runtime,
        params,
        title,
        current_plain_text,
        '文档',
    )
    detail = await update_document_content(permission.target_id, runtime.user_id, revised, runtime.request_id)
    latency_ms = max(1, int((perf_counter() - started_at) * 1000))
    compact_detail = None
    if isinstance(detail, dict):
        compact_detail = {
            'id': detail.get('id'),
            'projectId': detail.get('projectId'),
            'title': detail.get('title'),
            'updatedAt': detail.get('updatedAt'),
        }
    return ActionExecutionResult(
        action_code='document.update_content',
        answer=f'已根据你的要求更新文档《{str((detail or {}).get("title") or title)}》的正文内容。',
        answer_source=SYSTEM_ACTION_SOURCE,
        used_tools=['document.update_content'],
        provider_code=provider_code,
        model_code=model_code,
        usage=usage,
        raw_payload={'document': compact_detail, 'plainTextLength': len(revised)},
        latency_ms=latency_ms,
        target_type=permission.target_type,
        target_id=permission.target_id,
    )


async def execute_file_content_action(
    runtime: ActionRuntimeContext,
    params: ContentUpdateActionParams,
    permission: ActionPermissionDecision,
) -> ActionExecutionResult:
    resource = permission.resource or {}
    file_name = str(resource.get('fileName') or '文件')
    current_plain_text = str(resource.get('plainText') or '')
    started_at = perf_counter()
    revised, usage, provider_code, model_code = await _build_revised_plain_text(
        runtime,
        params,
        file_name,
        current_plain_text,
        '文件',
    )
    detail = await update_file_content(permission.target_id, runtime.user_id, revised, runtime.request_id)
    latency_ms = max(1, int((perf_counter() - started_at) * 1000))
    compact_detail = None
    if isinstance(detail, dict):
        compact_detail = {
            'id': detail.get('id'),
            'projectId': detail.get('projectId'),
            'fileName': detail.get('fileName'),
            'fileExt': detail.get('fileExt'),
            'archived': detail.get('archived'),
            'updatedAt': detail.get('updatedAt'),
        }
    return ActionExecutionResult(
        action_code='file.update_content',
        answer=f'已根据你的要求更新文件《{str((detail or {}).get("fileName") or file_name)}》的正文内容。',
        answer_source=SYSTEM_ACTION_SOURCE,
        used_tools=['file.update_content'],
        provider_code=provider_code,
        model_code=model_code,
        usage=usage,
        raw_payload={'file': compact_detail, 'plainTextLength': len(revised)},
        latency_ms=latency_ms,
        target_type=permission.target_type,
        target_id=permission.target_id,
    )


async def execute_document_summary_action(
    runtime: ActionRuntimeContext,
    params: DocumentSummaryActionParams,
    permission: ActionPermissionDecision,
) -> ActionExecutionResult:
    del params
    resource = permission.resource or {}
    plain_text = str(resource.get('plainText') or '').strip()
    if not plain_text:
        raise AiServiceError('ACTION_CONTEXT_EMPTY', 'Document plain text is empty', status_code=400)
    started_at = perf_counter()
    summary, usage, provider_code, model_code = await _run_model_text(
        runtime,
        '你是 Erise-AI 的文档整理助手。请输出适合写入文档摘要字段的中文摘要。只输出 1 段纯文本，不要使用列表、标题、引号或解释语。',
        f'文档标题：{resource.get("title") or "未命名文档"}\n'
        f'文档正文：\n{_trim_text(plain_text)}\n\n'
        '请生成 80 到 160 字之间的摘要。',
    )
    detail = await update_document_summary(permission.target_id, runtime.user_id, summary, runtime.request_id)
    latency_ms = max(1, int((perf_counter() - started_at) * 1000))
    return ActionExecutionResult(
        action_code='document.generate_summary',
        answer=f'已为《{detail.get("title") or resource.get("title") or "文档"}》生成并更新摘要：{summary}',
        answer_source=SYSTEM_ACTION_SOURCE,
        used_tools=['document.generate_summary'],
        provider_code=provider_code,
        model_code=model_code,
        usage=usage,
        raw_payload={'document': detail, 'summary': summary},
        latency_ms=latency_ms,
        target_type=permission.target_type,
        target_id=permission.target_id,
    )


async def execute_document_tags_action(
    runtime: ActionRuntimeContext,
    params: DocumentTagsActionParams,
    permission: ActionPermissionDecision,
) -> ActionExecutionResult:
    started_at = perf_counter()
    tags = await update_document_tags(permission.target_id, runtime.user_id, params.tags, runtime.request_id)
    latency_ms = max(1, int((perf_counter() - started_at) * 1000))
    tag_names = '、'.join(str(item.get('name') or '') for item in tags if item.get('name'))
    title = str((permission.resource or {}).get('title') or '文档')
    return ActionExecutionResult(
        action_code='document.update_tags',
        answer=f'已更新《{title}》的标签：{tag_names or "未设置有效标签"}。',
        answer_source=SYSTEM_ACTION_SOURCE,
        used_tools=['document.update_tags'],
        provider_code=SYSTEM_PROVIDER_CODE,
        model_code='document-tags-update',
        raw_payload={'tags': tags},
        latency_ms=latency_ms,
        target_type=permission.target_type,
        target_id=permission.target_id,
    )


async def execute_archive_file_action(
    runtime: ActionRuntimeContext,
    params: ArchiveFileActionParams,
    permission: ActionPermissionDecision,
) -> ActionExecutionResult:
    del params
    started_at = perf_counter()
    detail = await archive_file(permission.target_id, runtime.user_id, runtime.request_id)
    latency_ms = max(1, int((perf_counter() - started_at) * 1000))
    file_name = str((detail or {}).get('fileName') or (permission.resource or {}).get('fileName') or '文件')
    return ActionExecutionResult(
        action_code='file.archive',
        answer=f'已归档文件《{file_name}》。后续如果需要，我还可以继续帮你整理与这份文件相关的摘要或周报草稿。',
        answer_source=SYSTEM_ACTION_SOURCE,
        used_tools=['file.archive'],
        provider_code=SYSTEM_PROVIDER_CODE,
        model_code='file-archive-action',
        raw_payload={'file': detail},
        latency_ms=latency_ms,
        target_type=permission.target_type,
        target_id=permission.target_id,
    )


def _parse_weekly_report_output(raw_text: str, fallback_title: str) -> tuple[str, str, str]:
    normalized = raw_text.strip()
    title_match = re.search(r'标题[:：]\s*(?P<title>.+)', normalized)
    summary_match = re.search(r'摘要[:：]\s*(?P<summary>.+)', normalized)
    body_match = re.search(r'正文[:：]\s*(?P<body>[\s\S]+)', normalized)
    title = (title_match.group('title').strip() if title_match else fallback_title)[:120]
    body = body_match.group('body').strip() if body_match else normalized
    summary = summary_match.group('summary').strip() if summary_match else body.replace('\n', ' ')[:160]
    return title, summary[:200], body


async def execute_weekly_report_draft_action(
    runtime: ActionRuntimeContext,
    params: WeeklyReportDraftActionParams,
    permission: ActionPermissionDecision,
) -> ActionExecutionResult:
    resource = permission.resource or {}
    project_name = str(resource.get('name') or f'项目 {permission.target_id}')
    default_title = f'{project_name} 周报草稿 {date.today().isoformat()}'
    started_at = perf_counter()
    report_text, usage, provider_code, model_code = await _run_model_text(
        runtime,
        '你是 Erise-AI 的项目周报助手。请根据提供的项目上下文生成一份可直接保存为文档草稿的中文周报。'
        '严格使用以下格式输出：\n标题：...\n摘要：...\n正文：...\n不要输出额外说明。',
        f'项目名称：{project_name}\n'
        f'项目描述：{resource.get("description") or "暂无项目描述"}\n'
        f'项目状态：{resource.get("projectStatus") or "ACTIVE"}\n'
        f'文件数：{resource.get("fileCount") or 0}\n'
        f'文档数：{resource.get("documentCount") or 0}\n'
        f'用户意图：{params.instruction}\n\n'
        '请产出一份重点清晰、适合企业项目同步的周报草稿。',
    )
    title, summary, body = _parse_weekly_report_output(report_text, default_title)
    detail = await create_project_weekly_report_draft(
        permission.target_id,
        runtime.user_id,
        title,
        summary,
        body,
        runtime.request_id,
    )
    await n8n_event_service.emit(
        runtime.db,
        request_id=runtime.request_id,
        event_type='weekly_report.created',
        workflow_hint='weekly-report-created',
        payload={
            'projectId': permission.target_id,
            'userId': runtime.user_id,
            'documentId': detail.get('id') if isinstance(detail, dict) else None,
            'title': detail.get('title') if isinstance(detail, dict) else title,
            'summary': summary,
        },
        session_id=runtime.session_id,
        user_id=runtime.user_id,
        project_id=permission.target_id,
    )
    latency_ms = max(1, int((perf_counter() - started_at) * 1000))
    return ActionExecutionResult(
        action_code='project.create_weekly_report_draft',
        answer=f'已在项目《{project_name}》下创建周报草稿《{detail.get("title") or title}》。你可以继续让我帮你补充风险、计划或里程碑。',
        answer_source=SYSTEM_ACTION_SOURCE,
        used_tools=['project.create_weekly_report_draft'],
        provider_code=provider_code,
        model_code=model_code,
        usage=usage,
        raw_payload={'document': detail, 'summary': summary},
        latency_ms=latency_ms,
        target_type='PROJECT',
        target_id=permission.target_id,
    )


def build_builtin_action_definitions() -> list[ActionDefinition]:
    return [
        ActionDefinition(
            action_code='file.update_title',
            match_rule=match_file_title_action,
            param_schema=DocumentTitleActionParams,
            permission_rule=permission_file_title_target,
            executor=execute_file_title_action,
            fallback_message='我识别到你想修改文件标题，但当前没有唯一可操作的文件。请先附上单个文件后再试。',
        ),
        ActionDefinition(
            action_code='document.update_title',
            match_rule=match_document_title_action,
            param_schema=DocumentTitleActionParams,
            permission_rule=permission_document_target,
            executor=execute_document_title_action,
            fallback_message='我识别到你想修改文档标题，但当前没有唯一可操作的文档。请先附上单个文档后再试。',
        ),
        ActionDefinition(
            action_code='file.update_content',
            match_rule=match_file_content_action,
            param_schema=ContentUpdateActionParams,
            permission_rule=permission_file_content_target,
            executor=execute_file_content_action,
            fallback_message='我识别到你想修改文件正文，但当前没有唯一且可编辑的文件可供操作。',
        ),
        ActionDefinition(
            action_code='document.update_content',
            match_rule=match_document_content_action,
            param_schema=ContentUpdateActionParams,
            permission_rule=permission_document_target,
            executor=execute_document_content_action,
            fallback_message='我识别到你想修改文档正文，但当前没有唯一可操作的文档。',
        ),
        ActionDefinition(
            action_code='document.generate_summary',
            match_rule=match_document_summary_action,
            param_schema=DocumentSummaryActionParams,
            permission_rule=permission_document_target,
            executor=execute_document_summary_action,
            fallback_message='我识别到你想生成文档摘要，但当前没有足够的文档正文可供整理。请先确认文档已完成解析。',
        ),
        ActionDefinition(
            action_code='document.update_tags',
            match_rule=match_document_tags_action,
            param_schema=DocumentTagsActionParams,
            permission_rule=permission_document_target,
            executor=execute_document_tags_action,
            fallback_message='我识别到你想更新文档标签，但当前没有唯一可操作的文档，或者标签内容无法解析。',
        ),
        ActionDefinition(
            action_code='file.archive',
            match_rule=match_archive_file_action,
            param_schema=ArchiveFileActionParams,
            permission_rule=permission_file_target,
            executor=execute_archive_file_action,
            fallback_message='我识别到你想归档文件，但当前没有唯一可操作的文件可供归档。',
        ),
        ActionDefinition(
            action_code='project.create_weekly_report_draft',
            match_rule=match_weekly_report_draft_action,
            param_schema=WeeklyReportDraftActionParams,
            permission_rule=permission_project_target,
            executor=execute_weekly_report_draft_action,
            fallback_message='我识别到你想创建项目周报草稿，但当前没有明确项目上下文，暂时还不能直接创建。',
        ),
    ]
