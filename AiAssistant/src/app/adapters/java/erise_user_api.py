from __future__ import annotations

from dataclasses import dataclass
from typing import Any

import httpx

from src.app.core.config import get_settings
from src.app.core.exceptions import AiServiceError


@dataclass(slots=True)
class EriseUserContext:
    user_id: int
    username: str
    role_code: str
    display_name: str | None = None


class EriseUserApiClient:
    def __init__(self) -> None:
        self.settings = get_settings()

    def _headers(self, token: str, request_id: str) -> dict[str, str]:
        return {
            'Authorization': f'Bearer {token}',
            'X-Request-Id': request_id,
        }

    def _url(self, path: str) -> str:
        return f'{self.settings.resolved_java_public_base_url}{path}'

    async def _get(self, path: str, token: str, request_id: str, params: dict[str, object] | None = None) -> Any:
        try:
            async with httpx.AsyncClient(timeout=self.settings.connect_timeout_seconds) as client:
                response = await client.get(self._url(path), headers=self._headers(token, request_id), params=params)
        except Exception as exc:
            raise AiServiceError('MCP_BACKEND_ERROR', f'Failed to reach Erise backend: {exc}', status_code=502) from exc
        return self._read(response)

    def _read(self, response: httpx.Response) -> Any:
        if response.status_code == 401:
            raise AiServiceError('MCP_UNAUTHORIZED', 'Erise token is invalid or expired', status_code=401)
        if response.status_code == 403:
            raise AiServiceError('MCP_FORBIDDEN', 'No permission to access this Erise resource', status_code=403)
        if response.status_code == 404:
            raise AiServiceError('MCP_RESOURCE_NOT_FOUND', 'Erise resource was not found or is not visible to the current user', status_code=404)
        if response.status_code >= 500:
            raise AiServiceError('MCP_BACKEND_ERROR', 'Erise backend is temporarily unavailable', status_code=502)
        response.raise_for_status()
        payload = response.json()
        if payload.get('code') not in {0, '0'}:
            raise AiServiceError('MCP_BACKEND_ERROR', str(payload.get('msg') or 'Erise backend request failed'), status_code=400)
        return payload.get('data')

    async def current_user(self, token: str, request_id: str) -> EriseUserContext:
        payload = await self._get('/users/me', token, request_id)
        return EriseUserContext(
            user_id=int(payload['id']),
            username=str(payload.get('username') or ''),
            role_code=str(payload.get('roleCode') or 'USER'),
            display_name=str(payload.get('displayName') or '') or None,
        )

    async def list_projects(self, token: str, request_id: str, *, page_num: int, page_size: int, q: str | None = None, status: str | None = None) -> Any:
        return await self._get('/projects', token, request_id, {'pageNum': page_num, 'pageSize': page_size, 'q': q, 'status': status})

    async def get_project(self, token: str, request_id: str, project_id: int) -> Any:
        return await self._get(f'/projects/{project_id}', token, request_id)

    async def list_documents(self, token: str, request_id: str, *, page_num: int, page_size: int, project_id: int | None = None, q: str | None = None) -> Any:
        return await self._get('/documents', token, request_id, {'pageNum': page_num, 'pageSize': page_size, 'projectId': project_id, 'q': q})

    async def get_document(self, token: str, request_id: str, document_id: int) -> Any:
        return await self._get(f'/documents/{document_id}', token, request_id)

    async def list_files(self, token: str, request_id: str, *, page_num: int, page_size: int, project_id: int | None = None, q: str | None = None) -> Any:
        return await self._get('/files', token, request_id, {'pageNum': page_num, 'pageSize': page_size, 'projectId': project_id, 'q': q})

    async def get_file(self, token: str, request_id: str, file_id: int) -> Any:
        return await self._get(f'/files/{file_id}', token, request_id)

    async def search(self, token: str, request_id: str, *, q: str, project_id: int | None = None, page_num: int = 1, page_size: int = 10) -> Any:
        return await self._get('/search', token, request_id, {'q': q, 'projectId': project_id, 'pageNum': page_num, 'pageSize': page_size})


erise_user_api = EriseUserApiClient()
