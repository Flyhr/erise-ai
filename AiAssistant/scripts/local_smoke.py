from __future__ import annotations

import json
import sys
from pathlib import Path

from fastapi.testclient import TestClient

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from src.app.db.session import init_database  # noqa: E402
from src.app.main import app  # noqa: E402


HEADERS = {
    'X-Internal-Service-Token': 'change-this-in-production',
    'X-User-Id': '1',
    'X-Org-Id': '0',
}


def main() -> int:
    init_database()
    client = TestClient(app)

    health = client.get('/internal/ai/chat/health', headers={**HEADERS, 'X-Request-Id': 'local-smoke-health'})
    rag = client.post(
        '/internal/ai/chat/rag/query',
        headers={**HEADERS, 'X-Request-Id': 'local-smoke-rag'},
        json={
            'userId': 1,
            'query': 'local smoke rag',
            'projectScopeIds': [],
            'attachments': [],
            'limit': 3,
        },
    )
    scoped = client.post(
        '/internal/ai/chat/completions',
        headers={**HEADERS, 'X-Request-Id': 'local-smoke-scoped'},
        json={
            'message': 'local smoke scoped fallback',
            'scene': 'general_chat',
            'mode': 'SCOPED',
            'webSearchEnabled': False,
            'context': {
                'projectId': 1001,
                'attachments': [],
            },
        },
    )

    payload = {
        'health': {'status_code': health.status_code, 'body': health.json()},
        'rag': {'status_code': rag.status_code, 'body': rag.json()},
        'scoped': {'status_code': scoped.status_code, 'body': scoped.json()},
    }
    print(json.dumps(payload, ensure_ascii=False, indent=2))

    success = all(item['status_code'] == 200 for item in payload.values())
    return 0 if success else 1


if __name__ == '__main__':
    raise SystemExit(main())
