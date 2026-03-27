"""OpenAI client wrapper."""
from __future__ import annotations

from typing import List, Dict, Any

from openai import OpenAI

from src.config import get_settings


class LLMClient:
    def __init__(self):
        settings = get_settings()
        if not settings.openai_api_key:
            raise RuntimeError("OPENAI_API_KEY is required for AiAssistant")
        self.client = OpenAI(api_key=settings.openai_api_key)
        self.model = settings.model
        self.temperature = settings.temperature

    def chat(self, messages: List[Dict[str, str]], **kwargs: Any) -> str:
        settings = get_settings()
        completion = self.client.chat.completions.create(
            model=self.model,
            messages=messages,
            temperature=self.temperature,
            **kwargs,
        )
        return completion.choices[0].message.content.strip()
