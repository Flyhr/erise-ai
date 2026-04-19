from __future__ import annotations

import unittest
from unittest.mock import AsyncMock, patch

from fastapi.testclient import TestClient

from src.app.core.exceptions import AiServiceError
from src.app.models.mcp_access_log import McpAccessLog

from tests.support import SessionLocal, app, reset_database


def bearer_headers(request_id: str) -> dict[str, str]:
    return {
        'Authorization': 'Bearer test-jwt',
        'X-Request-Id': request_id,
    }


class McpApiTest(unittest.TestCase):
    def setUp(self) -> None:
        reset_database()

    def test_missing_bearer_returns_401(self) -> None:
        with TestClient(app) as client:
            response = client.post('/mcp', json={'jsonrpc': '2.0', 'id': '1', 'method': 'initialize', 'params': {}})

        self.assertEqual(401, response.status_code)
        self.assertEqual('MCP_UNAUTHORIZED', response.json()['code'])

    def test_tools_call_project_get_forbidden_returns_403_and_audit(self) -> None:
        with TestClient(app) as client:
            with (
                patch(
                    'src.app.api.v1.mcp.erise_user_api.current_user',
                    new=AsyncMock(return_value=type('User', (), {'user_id': 1, 'username': 'alice', 'role_code': 'USER', 'display_name': 'Alice'})()),
                ),
                patch(
                    'src.app.api.v1.mcp.erise_user_api.get_project',
                    new=AsyncMock(side_effect=AiServiceError('MCP_FORBIDDEN', 'No permission to access this Erise resource', status_code=403)),
                ),
            ):
                response = client.post(
                    '/mcp',
                    headers=bearer_headers('mcp-forbidden-1'),
                    json={'jsonrpc': '2.0', 'id': '2', 'method': 'tools/call', 'params': {'name': 'projects.get', 'arguments': {'projectId': 999}}},
                )

        self.assertEqual(403, response.status_code)
        self.assertIn('error', response.json())

        with SessionLocal() as db:
            logs = db.query(McpAccessLog).all()
            self.assertEqual(1, len(logs))
            self.assertEqual('tools/call', logs[0].method)
            self.assertEqual('projects.get', logs[0].tool_name)
            self.assertFalse(logs[0].success_flag)

    def test_tools_and_resources_are_readonly_and_audited(self) -> None:
        user = type('User', (), {'user_id': 1, 'username': 'alice', 'role_code': 'USER', 'display_name': 'Alice'})()
        with TestClient(app) as client:
            with (
                patch('src.app.api.v1.mcp.erise_user_api.current_user', new=AsyncMock(return_value=user)),
                patch('src.app.api.v1.mcp.erise_user_api.list_projects', new=AsyncMock(return_value={'records': [{'id': 88, 'name': 'Apollo'}], 'total': 1})),
                patch('src.app.api.v1.mcp.erise_user_api.search', new=AsyncMock(return_value={'records': [{'sourceType': 'DOCUMENT', 'sourceId': 101, 'title': 'Spec'}], 'total': 1})),
            ):
                tools = client.post('/mcp', headers=bearer_headers('mcp-tools-1'), json={'jsonrpc': '2.0', 'id': '1', 'method': 'tools/list', 'params': {}})
                projects = client.post('/mcp', headers=bearer_headers('mcp-tools-2'), json={'jsonrpc': '2.0', 'id': '2', 'method': 'tools/call', 'params': {'name': 'projects.list', 'arguments': {'pageNum': 1, 'pageSize': 10}}})
                resource = client.post('/mcp', headers=bearer_headers('mcp-resource-1'), json={'jsonrpc': '2.0', 'id': '3', 'method': 'resources/read', 'params': {'uri': 'erise://search?q=spec'}})

        self.assertEqual(200, tools.status_code)
        tool_names = [item['name'] for item in tools.json()['result']['tools']]
        self.assertIn('projects.list', tool_names)
        self.assertNotIn('admin.users.list', tool_names)
        self.assertEqual(200, projects.status_code)
        self.assertIn('structuredContent', projects.json()['result'])
        self.assertEqual(200, resource.status_code)
        self.assertIn('contents', resource.json()['result'])

        with SessionLocal() as db:
            logs = db.query(McpAccessLog).order_by(McpAccessLog.id.asc()).all()
            self.assertEqual(3, len(logs))
            self.assertEqual(['tools/list', 'tools/call', 'resources/read'], [item.method for item in logs])
            self.assertTrue(all(item.success_flag for item in logs))

    def test_non_admin_live_request_cannot_call_admin_tool_name(self) -> None:
        user = type('User', (), {'user_id': 1, 'username': 'alice', 'role_code': 'USER', 'display_name': 'Alice'})()
        with TestClient(app) as client:
            with patch('src.app.api.v1.mcp.erise_user_api.current_user', new=AsyncMock(return_value=user)):
                response = client.post(
                    '/mcp',
                    headers=bearer_headers('mcp-admin-tool-1'),
                    json={'jsonrpc': '2.0', 'id': '4', 'method': 'tools/call', 'params': {'name': 'admin.users.list', 'arguments': {}}},
                )

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertIn('error', payload)
        self.assertEqual('MCP_METHOD_NOT_FOUND', payload['error']['data']['errorCode'])

        with SessionLocal() as db:
            logs = db.query(McpAccessLog).all()
            self.assertEqual(1, len(logs))
            self.assertEqual('admin.users.list', logs[0].tool_name)
            self.assertFalse(logs[0].success_flag)
            self.assertEqual('MCP_METHOD_NOT_FOUND', logs[0].error_code)


if __name__ == '__main__':
    unittest.main()
