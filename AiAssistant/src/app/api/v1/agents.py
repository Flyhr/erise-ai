from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from src.app.api.deps import RequestContext, get_database_session, get_request_context
from src.app.schemas.agent import AgentRunRequest
from src.app.services.agent_graph_service import agent_graph_service


router = APIRouter(prefix='/agents')


@router.post('/run')
async def run_agent(
    request: AgentRunRequest,
    context: RequestContext = Depends(get_request_context),
    db: Session = Depends(get_database_session),
) -> dict[str, object]:
    payload = await agent_graph_service.run(db, context, request)
    return {'code': 0, 'msg': 'ok', 'data': payload.model_dump(by_alias=True)}
