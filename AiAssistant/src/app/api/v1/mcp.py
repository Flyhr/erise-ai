from __future__ import annotations

from dataclasses import dataclass
import json
from typing import Any
from urllib.parse import parse_qs, urlparse

from fastapi import APIRouter, Depends, Header
from fastapi.responses import JSONResponse
from sqlalchemy.orm import Session

from src.app.adapters.java.erise_user_api import EriseUserContext, erise_user_api
from src.app.api.deps import get_database_session
from src.app.core.exceptions import AiServiceError
from src.app.core.request_context import get_current_request_id, set_current_request_id
from src.app.schemas.mcp import McpRequest
from src.app.services.mcp_audit_service import McpAuditRecord, mcp_audit_service


router = APIRouter(prefix='/mcp')


@dataclass(slots=True)
class McpAuthContext:
    token: str
    request_id: str
    user: EriseUserContext


async def get_mcp_auth_context(
    authorization: str | None = Header(default=None, alias='Authorization'),
    x_request_id: str | None = Header(default=None, alias='X-Request-Id'),
) -> McpAuthContext:
    if not authorization or not authorization.startswith('Bearer '):
        raise AiServiceError('MCP_UNAUTHORIZED', 'Authorization Bearer token is required', status_code=401)
    token = authorization[7:].strip()
    if not token:
        raise AiServiceError('MCP_UNAUTHORIZED', 'Authorization Bearer token is required', status_code=401)
    request_id = x_request_id or get_current_request_id() or 'mcp-request'
    set_current_request_id(request_id)
    user = await erise_user_api.current_user(token, request_id)
    return McpAuthContext(token=token, request_id=request_id, user=user)


def _tool_definitions() -> list[dict[str, Any]]:
    return [
        {'name': 'user.me', 'description': 'Get the current Erise user profile.', 'inputSchema': {'type': 'object', 'properties': {}, 'additionalProperties': False}},
        {'name': 'projects.list', 'description': 'List accessible projects.', 'inputSchema': {'type': 'object', 'properties': {'pageNum': {'type': 'integer'}, 'pageSize': {'type': 'integer'}, 'q': {'type': 'string'}, 'status': {'type': 'string'}}}},
        {'name': 'projects.get', 'description': 'Get a project detail by id.', 'inputSchema': {'type': 'object', 'properties': {'projectId': {'type': 'integer'}}, 'required': ['projectId']}},
        {'name': 'documents.list', 'description': 'List accessible documents.', 'inputSchema': {'type': 'object', 'properties': {'pageNum': {'type': 'integer'}, 'pageSize': {'type': 'integer'}, 'projectId': {'type': 'integer'}, 'q': {'type': 'string'}}}},
        {'name': 'documents.get', 'description': 'Get a document detail by id.', 'inputSchema': {'type': 'object', 'properties': {'documentId': {'type': 'integer'}}, 'required': ['documentId']}},
        {'name': 'files.list', 'description': 'List accessible files.', 'inputSchema': {'type': 'object', 'properties': {'pageNum': {'type': 'integer'}, 'pageSize': {'type': 'integer'}, 'projectId': {'type': 'integer'}, 'q': {'type': 'string'}}}},
        {'name': 'files.get', 'description': 'Get a file detail by id.', 'inputSchema': {'type': 'object', 'properties': {'fileId': {'type': 'integer'}}, 'required': ['fileId']}},
        {'name': 'search.query', 'description': 'Run Erise unified search within the user-visible scope.', 'inputSchema': {'type': 'object', 'properties': {'q': {'type': 'string'}, 'projectId': {'type': 'integer'}, 'pageNum': {'type': 'integer'}, 'pageSize': {'type': 'integer'}}, 'required': ['q']}},
    ]


def _resource_definitions() -> list[dict[str, Any]]:
    return [
        {'uri': 'erise://me', 'name': 'Current User', 'description': 'Current Erise user profile'},
        {'uri': 'erise://projects', 'name': 'Projects', 'description': 'Accessible project list'},
        {'uri': 'erise://documents', 'name': 'Documents', 'description': 'Accessible document list'},
        {'uri': 'erise://files', 'name': 'Files', 'description': 'Accessible file list'},
        {'uri': 'erise://search', 'name': 'Search', 'description': 'Unified Erise search'},
    ]


def _ok(request_id: str | int | None, result: Any) -> dict[str, Any]:
    return {'jsonrpc': '2.0', 'id': request_id, 'result': result}


def _error(request_id: str | int | None, code: int, message: str, data: dict[str, Any] | None = None) -> dict[str, Any]:
    return {'jsonrpc': '2.0', 'id': request_id, 'error': {'code': code, 'message': message, 'data': data or {}}}


def _uri_query(uri: str) -> tuple[str, dict[str, list[str]]]:
    parsed = urlparse(uri)
    base = f'{parsed.scheme}://{parsed.netloc}{parsed.path}'
    return base, parse_qs(parsed.query)


def _payload_content(value: Any) -> dict[str, Any]:
    return {'contents': [{'mimeType': 'application/json', 'text': value}]}


async def _dispatch_tool(name: str, arguments: dict[str, Any], auth: McpAuthContext) -> Any:
    if name == 'user.me':
        return auth.user.__dict__
    if name == 'projects.list':
        return await erise_user_api.list_projects(auth.token, auth.request_id, page_num=int(arguments.get('pageNum') or 1), page_size=int(arguments.get('pageSize') or 10), q=arguments.get('q'), status=arguments.get('status'))
    if name == 'projects.get':
        return await erise_user_api.get_project(auth.token, auth.request_id, int(arguments['projectId']))
    if name == 'documents.list':
        return await erise_user_api.list_documents(auth.token, auth.request_id, page_num=int(arguments.get('pageNum') or 1), page_size=int(arguments.get('pageSize') or 10), project_id=int(arguments['projectId']) if arguments.get('projectId') is not None else None, q=arguments.get('q'))
    if name == 'documents.get':
        return await erise_user_api.get_document(auth.token, auth.request_id, int(arguments['documentId']))
    if name == 'files.list':
        return await erise_user_api.list_files(auth.token, auth.request_id, page_num=int(arguments.get('pageNum') or 1), page_size=int(arguments.get('pageSize') or 10), project_id=int(arguments['projectId']) if arguments.get('projectId') is not None else None, q=arguments.get('q'))
    if name == 'files.get':
        return await erise_user_api.get_file(auth.token, auth.request_id, int(arguments['fileId']))
    if name == 'search.query':
        return await erise_user_api.search(auth.token, auth.request_id, q=str(arguments['q']), project_id=int(arguments['projectId']) if arguments.get('projectId') is not None else None, page_num=int(arguments.get('pageNum') or 1), page_size=int(arguments.get('pageSize') or 10))
    raise AiServiceError('MCP_METHOD_NOT_FOUND', f'Tool `{name}` is not available', status_code=404)


async def _dispatch_resource(uri: str, auth: McpAuthContext) -> Any:
    base, query = _uri_query(uri)
    if base == 'erise://me':
        return _payload_content(auth.user.__dict__)
    if base == 'erise://projects':
        if 'id' in query:
            return _payload_content(await erise_user_api.get_project(auth.token, auth.request_id, int(query['id'][0])))
        return _payload_content(await erise_user_api.list_projects(auth.token, auth.request_id, page_num=int((query.get('pageNum') or ['1'])[0]), page_size=int((query.get('pageSize') or ['10'])[0]), q=(query.get('q') or [None])[0], status=(query.get('status') or [None])[0]))
    if base == 'erise://documents':
        if 'id' in query:
            return _payload_content(await erise_user_api.get_document(auth.token, auth.request_id, int(query['id'][0])))
        project_id = int(query['projectId'][0]) if 'projectId' in query else None
        return _payload_content(await erise_user_api.list_documents(auth.token, auth.request_id, page_num=int((query.get('pageNum') or ['1'])[0]), page_size=int((query.get('pageSize') or ['10'])[0]), project_id=project_id, q=(query.get('q') or [None])[0]))
    if base == 'erise://files':
        if 'id' in query:
            return _payload_content(await erise_user_api.get_file(auth.token, auth.request_id, int(query['id'][0])))
        project_id = int(query['projectId'][0]) if 'projectId' in query else None
        return _payload_content(await erise_user_api.list_files(auth.token, auth.request_id, page_num=int((query.get('pageNum') or ['1'])[0]), page_size=int((query.get('pageSize') or ['10'])[0]), project_id=project_id, q=(query.get('q') or [None])[0]))
    if base == 'erise://search':
        if 'q' not in query:
            raise AiServiceError('MCP_INVALID_PARAMS', 'Search resource requires q', status_code=400)
        project_id = int(query['projectId'][0]) if 'projectId' in query else None
        return _payload_content(await erise_user_api.search(auth.token, auth.request_id, q=query['q'][0], project_id=project_id, page_num=int((query.get('pageNum') or ['1'])[0]), page_size=int((query.get('pageSize') or ['10'])[0])))
    raise AiServiceError('MCP_RESOURCE_NOT_FOUND', f'Resource `{uri}` is not available', status_code=404)


def _audit(db: Session, auth: McpAuthContext, *, method: str, tool_name: str | None, resource_uri: str | None, status_code: int, success_flag: bool, error_code: str | None, error_message: str | None, request_payload: dict[str, Any] | None, response_payload: Any) -> None:
    mcp_audit_service.save(
        db,
        McpAuditRecord(
            request_id=auth.request_id,
            user_id=auth.user.user_id,
            org_id=0,
            username=auth.user.username,
            role_code=auth.user.role_code,
            method=method,
            tool_name=tool_name,
            resource_uri=resource_uri,
            status_code=status_code,
            success_flag=success_flag,
            error_code=error_code,
            error_message=error_message,
            request_payload=request_payload,
            response_payload=response_payload,
        ),
    )


@router.post('')
async def mcp_endpoint(
    request: McpRequest,
    auth: McpAuthContext = Depends(get_mcp_auth_context),
    db: Session = Depends(get_database_session),
) -> Any:
    method = request.method
    params = request.params or {}
    tool_name = None
    resource_uri = None
    try:
        if method == 'initialize':
            result = {
                'protocolVersion': '2025-03-26',
                'serverInfo': {'name': 'erise-readonly-mcp', 'version': '0.1.0'},
                'capabilities': {'tools': {}, 'resources': {}},
            }
        elif method == 'ping':
            result = {}
        elif method == 'tools/list':
            result = {'tools': _tool_definitions()}
        elif method == 'tools/call':
            tool_name = str(params.get('name') or '')
            arguments = params.get('arguments') or {}
            payload = await _dispatch_tool(tool_name, arguments, auth)
            result = {'content': [{'type': 'text', 'text': json.dumps(payload, ensure_ascii=False)}], 'structuredContent': payload}
        elif method == 'resources/list':
            result = {'resources': _resource_definitions()}
        elif method == 'resources/read':
            resource_uri = str(params.get('uri') or '')
            result = await _dispatch_resource(resource_uri, auth)
        elif method == 'initialized':
            result = {}
        else:
            raise AiServiceError('MCP_METHOD_NOT_FOUND', f'Method `{method}` is not supported', status_code=404)
        response = _ok(request.id, result)
        _audit(db, auth, method=method, tool_name=tool_name, resource_uri=resource_uri, status_code=200, success_flag=True, error_code=None, error_message=None, request_payload=request.model_dump(by_alias=True), response_payload=result)
        db.commit()
        return response
    except AiServiceError as exc:
        status_code = exc.status_code if exc.status_code in {400, 401, 403, 404} else 500
        response = _error(request.id, -32601 if exc.error_code in {'MCP_METHOD_NOT_FOUND', 'MCP_RESOURCE_NOT_FOUND'} else -32000, exc.message, {'errorCode': exc.error_code, 'statusCode': exc.status_code})
        _audit(db, auth, method=method, tool_name=tool_name, resource_uri=resource_uri, status_code=status_code, success_flag=False, error_code=exc.error_code, error_message=exc.message, request_payload=request.model_dump(by_alias=True), response_payload=response['error'])
        db.commit()
        if status_code in {401, 403}:
            return JSONResponse(status_code=status_code, content=response)
        return response
