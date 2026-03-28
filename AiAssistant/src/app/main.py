from __future__ import annotations

from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from src.app.api.router import api_router
from src.app.core.config import get_settings
from src.app.core.exceptions import AiServiceError
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


@app.exception_handler(AiServiceError)
async def handle_ai_service_error(_: Request, exc: AiServiceError) -> JSONResponse:
    return JSONResponse(
        status_code=exc.status_code,
        content={'code': exc.error_code, 'msg': exc.message, 'data': None},
    )


@app.exception_handler(Exception)
async def handle_unexpected_error(_: Request, exc: Exception) -> JSONResponse:
    return JSONResponse(
        status_code=500,
        content={'code': 'AI_PROVIDER_ERROR', 'msg': str(exc) or 'Internal server error', 'data': None},
    )
