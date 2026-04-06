from __future__ import annotations

import asyncio
from dataclasses import dataclass

import httpx
from duckduckgo_search import DDGS

from src.app.core.config import get_settings


@dataclass(slots=True)
class WebSearchHit:
    title: str
    url: str
    snippet: str
    site_name: str | None = None


async def search_web(query: str) -> list[WebSearchHit]:
    settings = get_settings()
    provider = (settings.web_search_provider or '').strip().lower()
    if not provider:
        return []
    if provider == 'duckduckgo':
        return await _search_duckduckgo(query, settings.web_search_max_results)
    if provider == 'tavily':
        return await _search_tavily(query, settings)
    return []


async def _search_duckduckgo(query: str, max_results: int) -> list[WebSearchHit]:
    def _search() -> list[WebSearchHit]:
        hits: list[WebSearchHit] = []
        with DDGS() as ddgs:
            for item in ddgs.text(query, max_results=max_results):
                hits.append(
                    WebSearchHit(
                        title=item.get('title') or 'Web result',
                        url=item.get('href') or item.get('url') or '',
                        snippet=item.get('body') or '',
                        site_name=item.get('source'),
                    )
                )
        return hits

    return await asyncio.to_thread(_search)


async def _search_tavily(query: str, settings) -> list[WebSearchHit]:
    if not settings.tavily_api_key:
        return []

    async with httpx.AsyncClient(timeout=settings.provider_timeout_seconds) as client:
        response = await client.post(
            'https://api.tavily.com/search',
            json={
                'api_key': settings.tavily_api_key,
                'query': query,
                'search_depth': 'basic',
                'max_results': settings.web_search_max_results,
                'include_answer': False,
                'include_raw_content': False,
            },
        )
        response.raise_for_status()
        payload = response.json()

    results = payload.get('results') or []
    hits: list[WebSearchHit] = []
    for item in results:
        hits.append(
            WebSearchHit(
                title=item.get('title') or 'Web result',
                url=item.get('url') or '',
                snippet=item.get('content') or item.get('snippet') or '',
                site_name=item.get('site_name') or item.get('domain'),
            )
        )
    return hits
