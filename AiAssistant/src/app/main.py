from __future__ import annotations

from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from src.app.api.router import api_router
from src.app.core.config import get_settings
from src.app.core.exceptions import AiServiceError
from src.app.core.request_context import get_current_request_id, set_current_request_id
from src.app.db.session import init_database
from src.app.services.model_registry import bootstrap_defaults


@asynccontextmanager
async def lifespan(_: FastAPI):
    init_database()
    bootstrap_defaults()
    yield


settings = get_settings()
app = FastAPI(title=settings.app_name, version='1.0.0', lifespan=lifespan)
app.include_router(api_router)


@app.middleware('http')
async def request_id_middleware(request: Request, call_next):
    request_id = request.headers.get('X-Request-Id') or get_current_request_id()
    set_current_request_id(request_id)
    response = await call_next(request)
    if request_id:
        response.headers['X-Request-Id'] = request_id
    return response


@app.exception_handler(AiServiceError)
async def handle_ai_service_error(request: Request, exc: AiServiceError) -> JSONResponse:
    request_id = request.headers.get('X-Request-Id') or get_current_request_id()
    content = {
        'code': exc.error_code,
        'msg': exc.message,
        'data': None,
        'requestId': request_id,
    }
    if exc.provider_code or exc.model_code or exc.upstream_status_code is not None:
        content['error'] = {
            'providerCode': exc.provider_code,
            'modelCode': exc.model_code,
            'upstreamStatusCode': exc.upstream_status_code,
        }
    return JSONResponse(
        status_code=exc.status_code,
        content=content,
        headers={'X-Request-Id': request_id} if request_id else None,
    )


@app.exception_handler(Exception)
async def handle_unexpected_error(request: Request, exc: Exception) -> JSONResponse:
    request_id = request.headers.get('X-Request-Id') or get_current_request_id()
    return JSONResponse(
        status_code=500,
        content={'code': 'AI_PROVIDER_ERROR', 'msg': str(exc) or 'Internal server error', 'data': None, 'requestId': request_id},
        headers={'X-Request-Id': request_id} if request_id else None,
    )
