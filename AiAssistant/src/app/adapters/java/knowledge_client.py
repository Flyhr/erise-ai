from __future__ import annotations

import httpx

from src.app.core.config import get_settings


async def query_knowledge(
    *,
    user_id: int,
    keyword: str,
    request_id: str,
    project_scope_ids: list[int] | None = None,
    attachments: list[dict[str, int | str | None]] | None = None,
    limit: int = 6,
) -> list[dict[str, object]]:
    settings = get_settings()
    async with httpx.AsyncClient(timeout=settings.connect_timeout_seconds) as client:
        response = await client.post(
            f'{settings.java_internal_base_url}/knowledge/retrieve',
            headers={
                'X-Internal-Key': settings.java_api_key,
                'X-Request-Id': request_id,
            },
            json={
                'userId': user_id,
                'projectScopeIds': project_scope_ids or [],
                'attachments': attachments or [],
                'keyword': keyword,
                'limit': limit,
            },
        )
        response.raise_for_status()
        payload = response.json()
        return payload.get('data') or []
