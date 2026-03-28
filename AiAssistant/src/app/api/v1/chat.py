from __future__ import annotations

from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse
from sqlalchemy.orm import Session

from src.app.api.deps import RequestContext, get_database_session, get_request_context
from src.app.schemas.chat import ChatCompletionRequest
from src.app.services.chat_service import chat_service


router = APIRouter()


@router.post('/completions')
async def complete(
    request: ChatCompletionRequest,
    context: RequestContext = Depends(get_request_context),
    db: Session = Depends(get_database_session),
) -> dict[str, object]:
    payload = await chat_service.complete(db, context, request)
    return {'code': 0, 'msg': 'ok', 'data': payload.model_dump(by_alias=True)}


@router.post('/completions/stream')
async def stream_complete(
    request: ChatCompletionRequest,
    context: RequestContext = Depends(get_request_context),
    db: Session = Depends(get_database_session),
) -> StreamingResponse:
    stream = await chat_service.stream(db, context, request)
    return StreamingResponse(stream, media_type='text/event-stream')


@router.post('/completions/{request_id}/cancel')
async def cancel_stream(
    request_id: str,
    _: RequestContext = Depends(get_request_context),
) -> dict[str, object]:
    payload = await chat_service.cancel(request_id)
    return {'code': 0, 'msg': 'ok', 'data': payload.model_dump(by_alias=True)}
