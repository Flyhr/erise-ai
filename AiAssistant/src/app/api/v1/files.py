from __future__ import annotations

from fastapi import APIRouter, Depends, File, Form, UploadFile

from src.app.api.deps import RequestContext, get_request_context
from src.app.schemas.file_extract import FileExtractView, TextChunkRequest, TextChunkView
from src.app.services.file_extract_service import file_extract_service


router = APIRouter(prefix='/files')


@router.post('/extract')
async def extract_file(
    file: UploadFile = File(...),
    file_name: str | None = Form(default=None, alias='fileName'),
    file_ext: str | None = Form(default=None, alias='fileExt'),
    context: RequestContext = Depends(get_request_context),
) -> dict[str, object]:
    del context
    payload = await file.read()
    result = file_extract_service.extract(payload, file_name or file.filename, file_ext)
    view = FileExtractView(
        plain_text=result.plain_text,
        chunks=result.chunks,
        parser=result.parser,
        used_ocr=result.used_ocr,
        page_count=result.page_count,
    )
    return {'code': 0, 'msg': 'ok', 'data': view.model_dump(by_alias=True)}


@router.post('/chunk-text')
async def chunk_text(
    request: TextChunkRequest,
    context: RequestContext = Depends(get_request_context),
) -> dict[str, object]:
    del context
    view = TextChunkView(
        chunks=file_extract_service.chunk_text(request.plain_text, request.page_no),
    )
    return {'code': 0, 'msg': 'ok', 'data': view.model_dump(by_alias=True)}
