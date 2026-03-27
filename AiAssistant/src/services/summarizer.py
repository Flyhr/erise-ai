"""Summarization and QA logic."""
from __future__ import annotations

from typing import List, Dict, Any

from src.services.llm_client import LLMClient
from src.services.project_index import build_corpus
from src.services.search import web_search
from src.config import get_settings

SYSTEM_PROMPT = (
    "你是 EriseAi 的 AI 助手，擅长阅读代码并做简明总结，"
    "同时可以回答通用问答或搜索问题。"
)


class Summarizer:
    def __init__(self):
        self.client = LLMClient()
        self.settings = get_settings()

    def summarize_project(self, focus: str | None = None) -> str:
        corpus = build_corpus()
        focus_hint = focus or "概览"
        docs_text = "\n\n".join(
            f"[FILE] {path}\n{snippet}" for path, snippet in corpus
        )
        messages = [
            {"role": "system", "content": SYSTEM_PROMPT},
            {
                "role": "user",
                "content": (
                    f"根据以下项目片段，生成按模块分组的摘要，突出职责、栈、接口。"
                    f"关注点：{focus_hint}\n\n{docs_text}"
                ),
            },
        ]
        return self.client.chat(messages)

    def answer(self, message: str, history: List[Dict[str, str]] | None = None, use_search: bool = False) -> str:
        history = history or []
        messages = [{"role": "system", "content": SYSTEM_PROMPT}]
        messages.extend(history)
        context: str = ""
        search_block: str = ""

        if use_search and self.settings.search_enabled:
            results = web_search(message, max_results=5)
            formatted = "\n".join(f"- {r['title']} ({r['link']}) {r['snippet']}" for r in results)
            search_block = f"\n\n[SEARCH]\n{formatted}"
        else:
            corpus = build_corpus()
            context = "\n\n".join(f"[FILE] {p}\n{t}" for p, t in corpus[:8])

        user_content = (
            f"问题：{message}\n"
            f"如果有搜索结果，请结合；如果没有，则使用项目上下文。"
            f"{search_block or context}"
        )
        messages.append({"role": "user", "content": user_content})
        return self.client.chat(messages)
