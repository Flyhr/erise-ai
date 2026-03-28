from __future__ import annotations

from collections.abc import Generator

from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker

from src.app import models  # noqa: F401
from src.app.core.config import get_settings
from src.app.models.base import Base


settings = get_settings()
engine_kwargs: dict[str, object] = {'pool_pre_ping': True}
if settings.mysql_dsn.startswith('sqlite'):
    engine_kwargs['echo'] = settings.sqlite_echo

engine = create_engine(settings.mysql_dsn, **engine_kwargs)
SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False, expire_on_commit=False, class_=Session)


def get_db() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def init_database() -> None:
    Base.metadata.create_all(bind=engine)
