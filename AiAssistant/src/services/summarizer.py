from __future__ import annotations

from pydantic import BaseModel


class ProjectAnalysis(BaseModel):
    summary: str
    focus: str | None = None


class Summarizer:
    def analyze_project(self, focus: str | None = None) -> ProjectAnalysis:
        return ProjectAnalysis(
            summary='Legacy analyze endpoint has been retired. Use the new internal AI chat service under /internal/ai/chat/*.',
            focus=focus,
        )

    def answer(self, message: str, history: list[dict[str, str]] | None = None, use_search: bool = False) -> str:
        del history, use_search
        return f'Legacy chat endpoint has been retired. Use the new internal AI chat service under /internal/ai/chat/*. Received: {message}'
