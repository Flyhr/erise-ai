from __future__ import annotations

from fastapi import APIRouter
from pydantic import BaseModel, Field

from src.services.summarizer import Summarizer

router = APIRouter(prefix="/chat", tags=["chat"])


class ChatRequest(BaseModel):
    message: str
    history: list[dict[str, str]] = Field(default_factory=list)
    use_search: bool = False


class ChatResponse(BaseModel):
    reply: str


@router.post("", response_model=ChatResponse)
def chat(req: ChatRequest):
    summarizer = Summarizer()
    reply = summarizer.answer(req.message, req.history, req.use_search)
    return ChatResponse(reply=reply)
