from __future__ import annotations

from fastapi import APIRouter, Query
from redis.asyncio import Redis
from sqlalchemy import text

from src.app.core.config import get_settings
from src.app.db.session import SessionLocal
from src.app.services.model_health_service import model_health_service


router = APIRouter()


@router.get('/health')
async def health(include_providers: bool = Query(default=True, alias='includeProviders')) -> dict[str, object]:
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

    providers: dict[str, object] = {'status': 'UNKNOWN', 'routes': []}
    if include_providers:
        try:
            with SessionLocal() as db:
                provider_summary = await model_health_service.check(db)
                providers = provider_summary.model_dump(by_alias=True)
        except Exception:
            providers = {'status': 'DOWN', 'routes': []}

    provider_ready = providers['status'] in {'UP', 'UNKNOWN'}
    overall_status = 'UP' if database_status == 'UP' and redis_status == 'UP' and provider_ready else 'DEGRADED'
    return {
        'code': 0,
        'msg': 'ok',
        'data': {
            'service': settings.app_name,
            'status': overall_status,
            'database': database_status,
            'redis': redis_status,
            'providers': providers,
        },
    }
