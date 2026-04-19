from __future__ import annotations

from typing import Any

from src.app.schemas.common import CamelModel


class McpRequest(CamelModel):
    jsonrpc: str = '2.0'
    id: str | int | None = None
    method: str
    params: dict[str, Any] | None = None
