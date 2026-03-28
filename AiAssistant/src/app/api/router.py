from __future__ import annotations

from fastapi import APIRouter

from src.app.api.v1 import chat, health, messages, models, sessions
from src.app.core.config import get_settings


api_router = APIRouter()
internal_router = APIRouter(prefix=get_settings().api_prefix)
internal_router.include_router(health.router)
internal_router.include_router(models.router)
internal_router.include_router(sessions.router)
internal_router.include_router(messages.router)
internal_router.include_router(chat.router)
api_router.include_router(internal_router)
