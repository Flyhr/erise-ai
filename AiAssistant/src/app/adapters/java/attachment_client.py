from __future__ import annotations

import httpx

from src.app.core.config import get_settings


async def _request(method: str, path: str, request_id: str, payload: dict[str, object] | None = None) -> dict[str, object] | None:
    settings = get_settings()
    async with httpx.AsyncClient(timeout=settings.connect_timeout_seconds) as client:
        response = await client.request(
            method,
            f'{settings.java_internal_base_url}{path}',
            headers={
                'X-Internal-Key': settings.java_api_key,
                'X-Request-Id': request_id,
            },
            json=payload,
        )
        response.raise_for_status()
        body = response.json()
        return body.get('data')


async def fetch_document_context(document_id: int, request_id: str) -> dict[str, object] | None:
    return await _request('GET', f'/documents/{document_id}/context', request_id)


async def fetch_file_context(file_id: int, request_id: str) -> dict[str, object] | None:
    return await _request('GET', f'/files/{file_id}/context', request_id)


async def fetch_temp_file_context(temp_file_id: int, request_id: str) -> dict[str, object] | None:
    return await _request('GET', f'/ai/temp-files/{temp_file_id}/context', request_id)


async def update_document_title(document_id: int, actor_user_id: int, title: str, request_id: str) -> dict[str, object] | None:
    return await _request('POST', f'/documents/{document_id}/title', request_id, {'actorUserId': actor_user_id, 'title': title})


async def update_file_title(file_id: int, actor_user_id: int, title: str, request_id: str) -> dict[str, object] | None:
    return await _request('POST', f'/files/{file_id}/title', request_id, {'actorUserId': actor_user_id, 'title': title})


async def update_document_summary(document_id: int, actor_user_id: int, summary: str, request_id: str) -> dict[str, object] | None:
    return await _request(
        'POST',
        f'/documents/{document_id}/summary',
        request_id,
        {'actorUserId': actor_user_id, 'summary': summary},
    )


async def update_document_content(document_id: int, actor_user_id: int, plain_text: str, request_id: str) -> dict[str, object] | None:
    return await _request(
        'POST',
        f'/documents/{document_id}/content',
        request_id,
        {'actorUserId': actor_user_id, 'plainText': plain_text},
    )


async def update_document_tags(document_id: int, actor_user_id: int, tags: list[str], request_id: str) -> list[dict[str, object]]:
    data = await _request(
        'POST',
        f'/documents/{document_id}/tags',
        request_id,
        {'actorUserId': actor_user_id, 'tags': tags},
    )
    if not isinstance(data, list):
        return []
    return [item for item in data if isinstance(item, dict)]


async def archive_file(file_id: int, actor_user_id: int, request_id: str) -> dict[str, object] | None:
    return await _request('POST', f'/files/{file_id}/archive', request_id, {'actorUserId': actor_user_id})


async def update_file_content(file_id: int, actor_user_id: int, plain_text: str, request_id: str) -> dict[str, object] | None:
    return await _request(
        'POST',
        f'/files/{file_id}/content',
        request_id,
        {'actorUserId': actor_user_id, 'plainText': plain_text},
    )
