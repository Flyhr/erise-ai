from __future__ import annotations

import os
import sys
from pathlib import Path
from types import SimpleNamespace

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

RUNTIME_DIR = ROOT / '.test-runtime'
RUNTIME_DIR.mkdir(exist_ok=True)
DB_PATH = RUNTIME_DIR / 'ai_chat_test.sqlite3'

os.environ.setdefault('MYSQL_DSN', f"sqlite:///{DB_PATH.as_posix()}")
os.environ.setdefault('REDIS_URL', 'redis://127.0.0.1:6399/15')
os.environ.setdefault('INTERNAL_SERVICE_TOKEN', 'test-token')
os.environ.setdefault('XDG_CACHE_HOME', str(RUNTIME_DIR / 'cache'))
os.environ.setdefault('OPENAI_API_KEY', 'test-openai-key')
os.environ.setdefault('DEFAULT_MODEL_CODE', 'gpt-4.1-mini')
os.environ.setdefault('SQLITE_ECHO', 'false')

from src.app.adapters.llm.base import AdapterResult, AdapterStreamEvent, AdapterUsage  # noqa: E402
from src.app.core.config import get_settings  # noqa: E402

get_settings.cache_clear()

from src.app.main import app  # noqa: E402
from src.app.models.ai_request_log import AiRequestLog  # noqa: E402
from src.app.models.base import Base  # noqa: E402
from src.app.db.session import SessionLocal, engine  # noqa: E402
from src.app.services.chat_service import chat_service  # noqa: E402
from src.app.services.model_registry import bootstrap_defaults  # noqa: E402


HEADERS = {
    'X-Internal-Service-Token': 'test-token',
    'X-User-Id': '1',
    'X-Org-Id': '7',
}


def request_headers(request_id: str) -> dict[str, str]:
    return {**HEADERS, 'X-Request-Id': request_id}


def reset_database() -> None:
    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)
    bootstrap_defaults()


def list_request_logs() -> list[AiRequestLog]:
    with SessionLocal() as db:
        return db.query(AiRequestLog).order_by(AiRequestLog.id.asc()).all()


class FakeRedisClient:
    def __init__(self, ping_value: bool = True) -> None:
        self._ping_value = ping_value

    async def ping(self) -> bool:
        return self._ping_value

    async def aclose(self) -> None:
        return None


class InMemoryCancellationStore:
    def __init__(self) -> None:
        self._cancelled: set[str] = set()

    async def mark_cancelled(self, request_id: str) -> None:
        self._cancelled.add(request_id)

    async def is_cancelled(self, request_id: str) -> bool:
        return request_id in self._cancelled

    async def clear(self, request_id: str) -> None:
        self._cancelled.discard(request_id)


class FakeAdapter:
    def __init__(
        self,
        answer: str = '测试回答',
        provider_code: str = 'OPENAI',
        model_code: str = 'gpt-4.1-mini',
        usage: AdapterUsage | None = None,
        stream_events: list[AdapterStreamEvent] | None = None,
    ) -> None:
        self.answer = answer
        self.provider_code = provider_code
        self.model_code = model_code
        self.usage = usage or AdapterUsage(prompt_tokens=12, completion_tokens=8, total_tokens=20)
        self.stream_events = stream_events or [
            AdapterStreamEvent(delta='第一段'),
            AdapterStreamEvent(delta='第二段'),
            AdapterStreamEvent(usage=self.usage),
        ]

    async def chat(self, model_code: str, messages: list[dict[str, str]], temperature: float | None, max_tokens: int | None) -> AdapterResult:
        del model_code, messages, temperature, max_tokens
        return AdapterResult(
            text=self.answer,
            usage=self.usage,
            provider_code=self.provider_code,
            model_code=self.model_code,
            raw_response={'answer': self.answer},
        )

    async def stream_chat(self, model_code: str, messages: list[dict[str, str]], temperature: float | None, max_tokens: int | None):
        del model_code, messages, temperature, max_tokens
        for event in self.stream_events:
            yield event


def fake_model(model_code: str = 'gpt-4.1-mini', provider_code: str = 'OPENAI') -> SimpleNamespace:
    return SimpleNamespace(model_code=model_code, provider_code=provider_code)
