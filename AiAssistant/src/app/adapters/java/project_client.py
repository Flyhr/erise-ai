from __future__ import annotations

import httpx

from src.app.core.config import get_settings


async def fetch_project_context(project_id: int, request_id: str) -> dict[str, object] | None:
    settings = get_settings()
    async with httpx.AsyncClient(timeout=settings.connect_timeout_seconds) as client:
        response = await client.get(
            f'{settings.java_internal_base_url}/projects/{project_id}/context',
            headers={
                'X-Internal-Key': settings.java_api_key,
                'X-Request-Id': request_id,
            },
        )
        response.raise_for_status()
        payload = response.json()
        return payload.get('data')
