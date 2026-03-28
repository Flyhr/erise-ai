from __future__ import annotations

from fastapi import APIRouter, Query
from pydantic import BaseModel

from src.services.summarizer import Summarizer

router = APIRouter(prefix='/analyze', tags=['analyze'])


class AnalyzeResponse(BaseModel):
    summary: str
    focus: str | None = None


@router.get('', response_model=AnalyzeResponse)
def analyze(focus: str | None = Query(default=None, description='可选关注点')):
    summarizer = Summarizer()
    result = summarizer.analyze_project(focus)
    return AnalyzeResponse(summary=result.summary, focus=result.focus)
