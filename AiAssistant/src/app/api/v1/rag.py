from __future__ import annotations

from fastapi import APIRouter, Depends

from src.app.api.deps import RequestContext, get_request_context
from src.app.schemas.rag import RagIndexDeleteRequest, RagIndexUpsertRequest, RagQueryRequest
from src.app.services.rag_service import rag_service


router = APIRouter()


@router.post('/rag/index/upsert')
async def upsert_index(
    request: RagIndexUpsertRequest,
    _: RequestContext = Depends(get_request_context),
) -> dict[str, object]:
    payload = await rag_service.upsert(request)
    return {'code': 0, 'msg': 'ok', 'data': payload}


@router.post('/rag/index/delete')
async def delete_index(
    request: RagIndexDeleteRequest,
    _: RequestContext = Depends(get_request_context),
) -> dict[str, object]:
    payload = await rag_service.delete(request)
    return {'code': 0, 'msg': 'ok', 'data': payload}


@router.post('/rag/query')
async def query_index(
    request: RagQueryRequest,
    _: RequestContext = Depends(get_request_context),
) -> dict[str, object]:
    payload = await rag_service.debug_query(
        user_id=request.user_id,
        query=request.query,
        project_scope_ids=request.project_scope_ids,
        attachments=request.attachments,
        limit=request.limit,
    )
    return {'code': 0, 'msg': 'ok', 'data': payload.model_dump(by_alias=True)}
