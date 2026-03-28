from __future__ import annotations

from fastapi import APIRouter
from redis.asyncio import Redis
from sqlalchemy import text

from src.app.core.config import get_settings
from src.app.db.session import SessionLocal


router = APIRouter()


@router.get('/health')
async def health() -> dict[str, object]:
    settings = get_settings()
    database_status = 'DOWN'
    redis_status = 'DOWN'

    try:
        with SessionLocal() as db:
            db.execute(text('select 1'))
            database_status = 'UP'
    except Exception:
        database_status = 'DOWN'

    redis_client = Redis.from_url(settings.redis_url, decode_responses=True)
    try:
        if await redis_client.ping():
            redis_status = 'UP'
    except Exception:
        redis_status = 'DOWN'
    finally:
        try:
            await redis_client.aclose()
        except Exception:
            pass

    return {
        'code': 0,
        'msg': 'ok',
        'data': {
            'service': settings.app_name,
            'status': 'UP' if database_status == 'UP' and redis_status == 'UP' else 'DEGRADED',
            'database': database_status,
            'redis': redis_status,
        },
    }