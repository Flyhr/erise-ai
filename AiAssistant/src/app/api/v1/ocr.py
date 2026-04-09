from __future__ import annotations

from fastapi import APIRouter, Depends, File, UploadFile

from src.app.api.deps import RequestContext, get_request_context
from src.app.schemas.ocr import PdfOcrView
from src.app.services.ocr_service import ocr_service


router = APIRouter(prefix='/ocr')


@router.post('/pdf-text')
async def extract_pdf_text(
    file: UploadFile = File(...),
    context: RequestContext = Depends(get_request_context),
) -> dict[str, object]:
    del context
    payload = await file.read()
    result = ocr_service.extract_pdf_text(payload)
    view = PdfOcrView(
        text=result.text,
        page_texts=result.page_texts,
        page_count=result.page_count,
        used_ocr=result.used_ocr,
        engine=result.engine,
    )
    return {'code': 0, 'msg': 'ok', 'data': view.model_dump(by_alias=True)}
