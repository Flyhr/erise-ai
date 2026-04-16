from __future__ import annotations

from collections.abc import Generator

from sqlalchemy import create_engine, inspect
from sqlalchemy.orm import Session, sessionmaker

from src.app import models  # noqa: F401
from src.app.models.base import Base
from src.app.core.config import get_settings
settings = get_settings()
engine_kwargs: dict[str, object] = {'pool_pre_ping': True}
if settings.mysql_dsn.startswith('sqlite'):
    engine_kwargs['echo'] = settings.sqlite_echo

engine = create_engine(settings.mysql_dsn, **engine_kwargs)
SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False, expire_on_commit=False, class_=Session)

REQUIRED_TABLES = {
    'ai_action_log',
    'ai_chat_session',
    'ai_chat_message',
    'ai_request_log',
    'ai_prompt_template',
    'ai_model_config',
    'ai_message_citation',
}

REQUIRED_COLUMNS = {
    'ai_chat_message': {
        'confidence',
        'refused_reason',
        'citations_json',
        'used_tools_json',
        'answer_source',
    },
    'ai_request_log': {
        'user_id',
        'org_id',
        'project_id',
        'answer_source',
        'message_status',
        'total_token_count',
        'latency_ms',
    },
    'ai_message_citation': {
        'section_path',
    },
    'ai_model_config': {
        'is_default',
        'input_price_per_million',
        'output_price_per_million',
        'currency_code',
    },
}


def get_db() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def init_database() -> None:
    inspector = inspect(engine)
    existing_tables = set(inspector.get_table_names())
    missing_tables = sorted(REQUIRED_TABLES - existing_tables)
    if missing_tables and settings.mysql_dsn.startswith('sqlite') and settings.auto_init_sqlite_schema:
        Base.metadata.create_all(bind=engine)
        inspector = inspect(engine)
        existing_tables = set(inspector.get_table_names())
        missing_tables = sorted(REQUIRED_TABLES - existing_tables)
    if missing_tables:
        raise RuntimeError(
            'Missing required AI chat tables: '
            + ', '.join(missing_tables)
            + '. Run backend Flyway migrations before starting AiAssistant.'
        )
    missing_columns: list[str] = []
    for table_name, required_columns in REQUIRED_COLUMNS.items():
        if table_name not in existing_tables:
            continue
        existing_columns = {column['name'] for column in inspector.get_columns(table_name)}
        for column_name in sorted(required_columns - existing_columns):
            missing_columns.append(f'{table_name}.{column_name}')
    if missing_columns:
        raise RuntimeError(
            'Missing required AI chat columns: '
            + ', '.join(missing_columns)
            + '. Run backend Flyway migrations before starting AiAssistant.'
        )
