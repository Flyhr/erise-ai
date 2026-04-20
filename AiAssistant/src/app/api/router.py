from __future__ import annotations

from fastapi import APIRouter

from src.app.api.v1 import agents, approvals, chat, files, health, mcp, messages, models, n8n, ocr, providers, rag, sessions
from src.app.core.config import get_settings


api_router = APIRouter()
api_router.include_router(mcp.router)
internal_router = APIRouter(prefix=get_settings().api_prefix)
internal_router.include_router(health.router)
internal_router.include_router(models.router)
internal_router.include_router(providers.router)
internal_router.include_router(sessions.router)
internal_router.include_router(messages.router)
internal_router.include_router(chat.router)
internal_router.include_router(agents.router)
internal_router.include_router(approvals.router)
internal_router.include_router(rag.router)
internal_router.include_router(ocr.router)
internal_router.include_router(files.router)
internal_router.include_router(n8n.router)
api_router.include_router(internal_router)
