"""Lightweight project indexer for summarization."""
from __future__ import annotations

import os
from pathlib import Path
from typing import List, Tuple

from src.config import get_settings

IGNORED_DIRS = {".git", "node_modules", "dist", "build", "venv", "__pycache__", ".idea"}
IGNORED_SUFFIXES = {".lock", ".png", ".jpg", ".jpeg", ".gif", ".zip", ".tar", ".exe", ".dll"}


def collect_files(limit: int | None = None) -> List[Path]:
    settings = get_settings()
    max_files = limit or settings.max_files
    root = Path(settings.project_root)
    collected: List[Path] = []

    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in IGNORED_DIRS]
        for name in filenames:
            suffix = Path(name).suffix.lower()
            if suffix in IGNORED_SUFFIXES:
                continue
            full_path = Path(dirpath) / name
            collected.append(full_path)
            if len(collected) >= max_files:
                return collected
    return collected


def sample_file_snippet(path: Path, max_chars: int = 1200) -> str:
    try:
        with path.open("r", encoding="utf-8", errors="ignore") as f:
            content = f.read(max_chars)
            return content
    except Exception as exc:  # noqa: BLE001
        return f"<unable to read {path.name}: {exc}>"


def build_corpus() -> List[Tuple[str, str]]:
    corpus: List[Tuple[str, str]] = []
    for file_path in collect_files():
        snippet = sample_file_snippet(file_path)
        corpus.append((str(file_path), snippet))
    return corpus
