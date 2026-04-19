from __future__ import annotations

import os
import unittest
from unittest.mock import patch

from src.app.models.ai_model_config import AiModelConfig
from src.app.core.config import get_settings
from src.app.services.model_registry import get_embedding_route, get_model_config, list_enabled_models

from tests.support import SessionLocal, reset_database


class ModelRegistryTest(unittest.TestCase):
    def setUp(self) -> None:
        reset_database()

    def test_list_enabled_models_puts_database_default_first(self) -> None:
        with SessionLocal() as db:
            self._promote_openai_as_default(db)
            views = list_enabled_models(db)

        self.assertGreaterEqual(len(views), 1)
        self.assertEqual('gpt-4.1-mini', views[0].model_code)
        self.assertTrue(views[0].is_default)

    def test_get_model_config_prefers_database_default_over_provider_preference(self) -> None:
        with SessionLocal() as db:
            self._promote_openai_as_default(db)
            selected = get_model_config(db, None)

        self.assertEqual('gpt-4.1-mini', selected.model_code)
        self.assertTrue(selected.is_default)

    def test_get_model_config_prefers_gateway_provider_specific_model(self) -> None:
        with patch.dict(
            os.environ,
            {
                'MODEL_PROVIDER': 'OLLAMA',
                'MODEL_BASE_URL': 'http://ollama:11434/v1',
                'OLLAMA_CHAT_MODEL': 'qwen2.5:7b',
            },
            clear=False,
        ):
            get_settings.cache_clear()
            reset_database()
            with SessionLocal() as db:
                selected = get_model_config(db, None)

        self.assertEqual('qwen2.5:7b', selected.model_code)
        self.assertEqual('OLLAMA', selected.provider_code)

    def test_list_enabled_models_marks_gateway_model_as_effective_default(self) -> None:
        with patch.dict(
            os.environ,
            {
                'MODEL_PROVIDER': 'OLLAMA',
                'MODEL_BASE_URL': 'http://ollama:11434/v1',
                'OLLAMA_CHAT_MODEL': 'qwen2.5:7b',
            },
            clear=False,
        ):
            get_settings.cache_clear()
            reset_database()
            with SessionLocal() as db:
                views = list_enabled_models(db)

        defaults = [item for item in views if item.is_default]
        self.assertEqual(1, len(defaults))
        self.assertEqual('qwen2.5:7b', defaults[0].model_code)
        self.assertEqual('OLLAMA', defaults[0].provider_code)

    def test_gateway_provider_without_model_row_prefers_default_model_alias(self) -> None:
        with patch.dict(
            os.environ,
            {
                'MODEL_PROVIDER': 'LITELLM',
                'MODEL_BASE_URL': 'http://litellm:4000/v1',
                'DEFAULT_MODEL_CODE': 'qwen2.5:7b',
                'OLLAMA_CHAT_MODEL': 'qwen2.5:7b',
            },
            clear=False,
        ):
            get_settings.cache_clear()
            reset_database()
            with SessionLocal() as db:
                selected = get_model_config(db, None)
                views = list_enabled_models(db)

        self.assertEqual('qwen2.5:7b', selected.model_code)
        self.assertEqual('OLLAMA', selected.provider_code)
        defaults = [item for item in views if item.is_default]
        self.assertEqual(1, len(defaults))
        self.assertEqual('qwen2.5:7b', defaults[0].model_code)

    def test_get_embedding_route_prefers_ollama_model_override(self) -> None:
        with patch.dict(
            os.environ,
            {
                'MODEL_PROVIDER': 'OLLAMA',
                'MODEL_BASE_URL': 'http://ollama:11434/v1',
                'EMBEDDING_PROVIDER_CODE': 'OLLAMA',
                'OLLAMA_EMBEDDING_MODEL': 'nomic-embed-text',
                'EMBEDDING_MODEL': 'text-embedding-3-small',
            },
            clear=False,
        ):
            get_settings.cache_clear()
            route = get_embedding_route()

        self.assertEqual('OLLAMA', route.provider_code)
        self.assertEqual('nomic-embed-text', route.model_code)
        self.assertEqual('http://ollama:11434/v1', route.base_url)

    @staticmethod
    def _promote_openai_as_default(db) -> None:
        models = db.query(AiModelConfig).all()
        for model in models:
            model.enabled = True
            model.is_default = model.model_code == 'gpt-4.1-mini'
        db.commit()

    def tearDown(self) -> None:
        for key in (
            'MODEL_PROVIDER',
            'MODEL_BASE_URL',
            'OLLAMA_CHAT_MODEL',
            'DEFAULT_MODEL_CODE',
            'EMBEDDING_PROVIDER_CODE',
            'OLLAMA_EMBEDDING_MODEL',
            'EMBEDDING_MODEL',
        ):
            os.environ.pop(key, None)
        get_settings.cache_clear()


if __name__ == '__main__':
    unittest.main()
