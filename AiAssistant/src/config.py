"""Configuration helper for AiAssistant."""
from __future__ import annotations

import os
from functools import lru_cache
from typing import Optional

from pydantic import BaseModel, Field


class Settings(BaseModel):
    openai_api_key: str = Field(default_factory=lambda: os.getenv("OPENAI_API_KEY", ""))
    project_root: str = Field(default_factory=lambda: os.getenv("ASSISTANT_PROJECT_ROOT", "D:/EriseAi"))
    max_files: int = Field(default_factory=lambda: int(os.getenv("ASSISTANT_MAX_FILES", "40")))
    model: str = Field(default_factory=lambda: os.getenv("ASSISTANT_MODEL", "gpt-4.1"))
    temperature: float = Field(default_factory=lambda: float(os.getenv("ASSISTANT_TEMPERATURE", "0.2")))
    search_enabled: bool = Field(default_factory=lambda: os.getenv("ASSISTANT_SEARCH", "true").lower() == "true")
    search_source: str = Field(default_factory=lambda: os.getenv("ASSISTANT_SEARCH_SOURCE", "duckduckgo"))

    class Config:
        extra = "ignore"


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """Return cached settings."""
    return Settings()
