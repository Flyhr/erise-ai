from __future__ import annotations

import json
from typing import Any

import httpx
from sqlalchemy.orm import Session

from src.app.core.config import get_settings
from src.app.models.n8n_event_log import N8nEventLog


class N8nEventService:
    def __init__(self) -> None:
        self.settings = get_settings()

    def _target_url(self, workflow_path: str) -> str | None:
        base = (self.settings.n8n_webhook_base_url or '').strip().rstrip('/')
        if not base:
            return None
        return f'{base}/{workflow_path.lstrip("/")}'

    async def emit(
        self,
        db: Session,
        *,
        request_id: str,
        event_type: str,
        workflow_hint: str,
        payload: dict[str, Any],
        approval_id: int | None = None,
        session_id: int | None = None,
        user_id: int | None = None,
        project_id: int | None = None,
    ) -> None:
        target_url = self._target_url(workflow_hint)
        record = N8nEventLog(
            request_id=request_id,
            event_type=event_type,
            workflow_hint=workflow_hint,
            approval_id=approval_id,
            session_id=session_id,
            user_id=user_id,
            project_id=project_id,
            target_url=target_url,
            payload_json=json.dumps(payload, ensure_ascii=False),
            success_flag=False,
        )
        if not self.settings.n8n_enabled or not target_url:
            record.status_code = 503
            record.error_message = 'n8n disabled or webhook base url not configured'
            db.add(record)
            return
        try:
            async with httpx.AsyncClient(timeout=self.settings.n8n_event_timeout_seconds) as client:
                response = await client.post(
                    target_url,
                    headers={
                        'Content-Type': 'application/json',
                        'X-N8N-Webhook-Secret': self.settings.n8n_webhook_secret,
                        'X-Request-Id': request_id,
                    },
                    json=payload,
                )
            record.status_code = response.status_code
            record.success_flag = response.status_code < 400
            if response.status_code >= 400:
                record.error_message = response.text[:500]
        except Exception as exc:
            record.status_code = 503
            record.error_message = str(exc)[:500]
        db.add(record)


n8n_event_service = N8nEventService()
