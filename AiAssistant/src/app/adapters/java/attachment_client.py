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


async def update_document_title(document_id: int, title: str, request_id: str) -> dict[str, object] | None:
    return await _request('POST', f'/documents/{document_id}/title', request_id, {'title': title})
