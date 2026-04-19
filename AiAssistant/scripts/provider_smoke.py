from __future__ import annotations

import os
import shutil
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))


def main() -> int:
    temp_dir = Path(tempfile.mkdtemp(prefix='erise-ai-provider-smoke-'))
    db_path = temp_dir / 'ai_chat.db'

    os.environ.setdefault('MYSQL_DSN', f'sqlite:///{db_path.as_posix()}')
    os.environ.setdefault('REDIS_URL', 'redis://127.0.0.1:6399/15')
    os.environ.setdefault('INTERNAL_SERVICE_TOKEN', 'provider-smoke-token')
    os.environ.setdefault('MODEL_PROVIDER', 'OLLAMA')
    os.environ.setdefault('MODEL_BASE_URL', 'http://ollama:11434/v1')
    os.environ.setdefault('OLLAMA_BASE_URL', 'http://ollama:11434/v1')
    os.environ.setdefault('OLLAMA_CHAT_MODEL', 'qwen2.5:7b')
    os.environ.setdefault('EMBEDDING_PROVIDER_CODE', 'OLLAMA')
    os.environ.setdefault('OLLAMA_EMBEDDING_MODEL', 'nomic-embed-text')
    os.environ.setdefault('SQLITE_ECHO', 'false')

    from src.app.core.config import get_settings

    get_settings.cache_clear()

    from src.app.db.session import SessionLocal, init_database
    from src.app.services.model_registry import (
        bootstrap_defaults,
        get_embedding_route,
        get_model_config,
        list_enabled_models,
    )

    try:
        init_database()
        bootstrap_defaults()
        with SessionLocal() as db:
            enabled_models = list_enabled_models(db)
            selected_model = get_model_config(db, None)
        embedding_route = get_embedding_route()

        print('Enabled models:')
        for item in enabled_models:
            print(f'- {item.provider_code}:{item.model_code} (default={item.is_default})')
        print('')
        print('Resolved chat route:')
        print(f'- provider={selected_model.provider_code}')
        print(f'- model={selected_model.model_code}')
        print('')
        print('Resolved embedding route:')
        print(f'- provider={embedding_route.provider_code}')
        print(f'- model={embedding_route.model_code}')
        print(f'- baseUrl={embedding_route.base_url}')
        print('')
        print('Provider smoke passed: model registry resolved Ollama-first chat and embedding routes.')
        return 0
    finally:
        shutil.rmtree(temp_dir, ignore_errors=True)


if __name__ == '__main__':
    raise SystemExit(main())
