from __future__ import annotations

import unittest
from unittest.mock import patch

from src.app.models.ai_model_config import AiModelConfig
from src.app.schemas.model import ProviderHealthInventoryView, ProviderHealthView
from src.app.services.model_health_service import model_health_service

from tests.support import SessionLocal, reset_database


class ProviderHealthServiceTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self) -> None:
        reset_database()
        with SessionLocal() as db:
            models = db.query(AiModelConfig).all()
            for model in models:
                model.enabled = model.provider_code == 'OPENAI'
                model.is_default = model.provider_code == 'OPENAI'
            db.commit()

    async def test_provider_health_exposes_effective_and_enabled_routes(self) -> None:
        with (
            SessionLocal() as db,
            patch.object(
                model_health_service,
                '_probe_route',
                side_effect=[
                    ProviderHealthView(
                        role='chat',
                        provider_code='OPENAI',
                        model_code='gpt-4.1-mini',
                        base_url='https://api.openai.com/v1',
                        configured=True,
                        status='UP',
                        latency_ms=18,
                    ),
                    ProviderHealthView(
                        role='embedding',
                        provider_code='OPENAI',
                        model_code='text-embedding-3-small',
                        base_url='https://api.openai.com/v1',
                        configured=True,
                        status='UP',
                        latency_ms=11,
                    ),
                ],
            ),
        ):
            payload = await model_health_service.provider_health(db)

        self.assertIsInstance(payload, ProviderHealthInventoryView)
        self.assertEqual('UP', payload.status)
        self.assertEqual('OPENAI', payload.default_provider_code)
        self.assertEqual('gpt-4.1-mini', payload.default_model_code)
        self.assertGreaterEqual(len(payload.enabled_routes), 1)
        self.assertEqual(2, len(payload.effective_routes))
        self.assertEqual('chat', payload.effective_routes[0].role)
        self.assertTrue(payload.effective_routes[0].is_effective)
        self.assertEqual('embedding', payload.effective_routes[1].role)
        self.assertTrue(payload.effective_routes[1].configured)
