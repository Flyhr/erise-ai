from __future__ import annotations

import os
import shutil
import sys
import tempfile
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))


def main() -> int:
    temp_dir = Path(tempfile.mkdtemp(prefix='erise-ai-chat-smoke-'))
    db_path = temp_dir / 'ai_chat.db'

    os.environ['MYSQL_DSN'] = f"sqlite:///{db_path.as_posix()}"
    os.environ.setdefault('REDIS_URL', 'redis://localhost:6379/15')
    os.environ['INTERNAL_SERVICE_TOKEN'] = 'smoke-token'
    os.environ.setdefault('DEFAULT_MODEL_CODE', 'deepseek-chat')

    from fastapi.testclient import TestClient
    from src.app.main import app

    headers = {
        'X-Internal-Service-Token': 'smoke-token',
        'X-User-Id': '1',
        'X-Org-Id': '0',
        'X-Request-Id': str(uuid.uuid4()),
    }

    try:
        with TestClient(app) as client:
            health = client.get('/internal/ai/chat/health')
            assert health.status_code == 200, health.text
            health_payload = health.json()
            assert health_payload['code'] == 0, health_payload
            assert health_payload['data']['database'] == 'UP', health_payload

            models = client.get('/internal/ai/chat/models', headers=headers)
            assert models.status_code == 200, models.text
            assert models.json()['code'] == 0, models.json()

            create_session = client.post(
                '/internal/ai/chat/sessions',
                headers=headers,
                json={'scene': 'general_chat', 'title': 'Smoke Session'},
            )
            assert create_session.status_code == 200, create_session.text
            created = create_session.json()['data']
            session_id = created['id']
            assert session_id > 0, created

            list_sessions = client.get('/internal/ai/chat/sessions?pageNum=1&pageSize=20', headers=headers)
            assert list_sessions.status_code == 200, list_sessions.text
            listed = list_sessions.json()['data']
            assert listed['total'] >= 1, listed

            detail = client.get(f'/internal/ai/chat/sessions/{session_id}', headers=headers)
            assert detail.status_code == 200, detail.text
            assert detail.json()['data']['id'] == session_id, detail.json()

            delete_session = client.delete(f'/internal/ai/chat/sessions/{session_id}', headers=headers)
            assert delete_session.status_code == 200, delete_session.text
            assert delete_session.json()['data']['deleted'] is True, delete_session.json()
    finally:
        shutil.rmtree(temp_dir, ignore_errors=True)

    print('Smoke test passed: Python AI service initialized, served requests, and completed session lifecycle.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())