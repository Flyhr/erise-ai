"""Deprecated: legacy LLM adapter implementation.

This module is no longer used by the active AiAssistant model chain.
The runtime now routes through `src.app.providers.*` and keeps
`src.app.adapters.llm.base` only as shared DTO/contracts.

The old implementation is intentionally kept below as comments for
historical reference and rollback comparison. Do not wire new code to
this module.
"""

# from __future__ import annotations
#
# from collections.abc import AsyncGenerator
#
# from openai import AsyncOpenAI
#
# from src.app.adapters.llm.base import AdapterResult, AdapterStreamEvent, AdapterUsage, LlmAdapter
# from src.app.core.exceptions import AiServiceError
#
#
# class OpenAiCompatibleAdapter(LlmAdapter):
#     def __init__(self, provider_code: str, api_key: str, base_url: str, timeout_seconds: int):
#         self.provider_code = provider_code
#         self.api_key = api_key
#         self.client = AsyncOpenAI(api_key=api_key, base_url=base_url, timeout=timeout_seconds)
#
#     def _ensure_available(self) -> None:
#         if not self.api_key:
#             raise AiServiceError('AI_MODEL_NOT_FOUND', f'{self.provider_code} api key is not configured', status_code=503)
#
#     async def chat(
#         self,
#         model_code: str,
#         messages: list[dict[str, str]],
#         temperature: float | None,
#         max_tokens: int | None,
#     ) -> AdapterResult:
#         self._ensure_available()
#         response = await self.client.chat.completions.create(
#             model=model_code,
#             messages=messages,
#             temperature=temperature,
#             max_tokens=max_tokens,
#         )
#         usage = AdapterUsage(
#             prompt_tokens=response.usage.prompt_tokens if response.usage else 0,
#             completion_tokens=response.usage.completion_tokens if response.usage else 0,
#             total_tokens=response.usage.total_tokens if response.usage else 0,
#         )
#         content = response.choices[0].message.content or ''
#         return AdapterResult(
#             text=content.strip(),
#             usage=usage,
#             provider_code=self.provider_code,
#             model_code=model_code,
#             raw_response=response.model_dump(),
#         )
#
#     async def stream_chat(
#         self,
#         model_code: str,
#         messages: list[dict[str, str]],
#         temperature: float | None,
#         max_tokens: int | None,
#     ) -> AsyncGenerator[AdapterStreamEvent, None]:
#         self._ensure_available()
#         stream = await self.client.chat.completions.create(
#             model=model_code,
#             messages=messages,
#             temperature=temperature,
#             max_tokens=max_tokens,
#             stream=True,
#             stream_options={'include_usage': True},
#         )
#         async for chunk in stream:
#             delta = ''
#             if chunk.choices:
#                 delta = chunk.choices[0].delta.content or ''
#             usage = None
#             if chunk.usage:
#                 usage = AdapterUsage(
#                     prompt_tokens=chunk.usage.prompt_tokens or 0,
#                     completion_tokens=chunk.usage.completion_tokens or 0,
#                     total_tokens=chunk.usage.total_tokens or 0,
#                 )
#             if delta or usage:
#                 yield AdapterStreamEvent(delta=delta, usage=usage)
