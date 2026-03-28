from __future__ import annotations

from typing import Any

from pydantic import BaseModel, ConfigDict


def to_camel(value: str) -> str:
    parts = value.split('_')
    return parts[0] + ''.join(item.capitalize() for item in parts[1:])


class CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, from_attributes=True)


class PageData(CamelModel):
    records: list[Any]
    page_num: int
    page_size: int
    total: int
    total_pages: int
