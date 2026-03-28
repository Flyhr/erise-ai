from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Any


@dataclass(slots=True)
class AdapterUsage:
    prompt_tokens: int = 0
    completion_tokens: int = 0
    total_tokens: int = 0


@dataclass(slots=True)
class AdapterResult:
    text: str
    usage: AdapterUsage
    provider_code: str
    model_code: str
    raw_response: dict[str, Any] | None = None


@dataclass(slots=True)
class AdapterStreamEvent:
    delta: str = ''
    usage: AdapterUsage | None = None


class LlmAdapter(ABC):
    @abstractmethod
    async def chat(
        self,
        model_code: str,
        messages: list[dict[str, str]],
        temperature: float | None,
        max_tokens: int | None,
    ) -> AdapterResult:
        raise NotImplementedError

    @abstractmethod
    async def stream_chat(
        self,
        model_code: str,
        messages: list[dict[str, str]],
        temperature: float | None,
        max_tokens: int | None,
    ):
        raise NotImplementedError
