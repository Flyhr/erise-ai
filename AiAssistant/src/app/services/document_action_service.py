from __future__ import annotations

import re

from src.app.adapters.java.attachment_client import update_document_title
from src.app.schemas.chat import ChatCompletionRequest

TITLE_UPDATE_PATTERNS = (
    r'(?:把|将)?(?:发送给你(?:的)?|附件里(?:的)?|这份|该)?(?:文档|文件)(?:的)?标题'
    r'(?:修改为|改为|改成|设置为|变更为)\s*[:：]?\s*[“"\']?(?P<title>[^”"\'\n]{1,120})',
    r'(?:把|将)?(?:文档标题|文件标题|标题)'
    r'(?:修改为|改为|改成|设置为|变更为)\s*[:：]?\s*[“"\']?(?P<title>[^”"\'\n]{1,120})',
)


def extract_requested_title(message: str) -> str | None:
    normalized = ' '.join(message.strip().split())
    for pattern in TITLE_UPDATE_PATTERNS:
        matched = re.search(pattern, normalized, flags=re.IGNORECASE)
        if not matched:
            continue
        title = matched.group('title').strip('“”"\' ,，。;；：:')
        if title:
            return title[:120]
    return None


async def apply_document_title_action(request: ChatCompletionRequest, request_id: str) -> dict[str, object] | None:
    title = extract_requested_title(request.message)
    if not title:
        return None

    document_attachments = [item for item in request.context.attachments if item.attachment_type == 'DOCUMENT']
    if len(document_attachments) != 1:
        return None

    return await update_document_title(document_attachments[0].source_id, title, request_id)
