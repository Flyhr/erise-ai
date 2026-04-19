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


async def create_project_weekly_report_draft(
    project_id: int,
    actor_user_id: int,
    title: str,
    summary: str,
    plain_text: str,
    request_id: str,
) -> dict[str, object] | None:
    settings = get_settings()
    async with httpx.AsyncClient(timeout=settings.connect_timeout_seconds) as client:
        response = await client.post(
            f'{settings.java_internal_base_url}/projects/{project_id}/weekly-report-draft',
            headers={
                'X-Internal-Key': settings.java_api_key,
                'X-Request-Id': request_id,
            },
            json={
                'actorUserId': actor_user_id,
                'title': title,
                'summary': summary,
                'plainText': plain_text,
            },
        )
        response.raise_for_status()
        payload = response.json()
        return payload.get('data')
