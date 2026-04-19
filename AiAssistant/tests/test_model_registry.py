from __future__ import annotations

import unittest

from src.app.models.ai_model_config import AiModelConfig
from src.app.services.model_registry import get_model_config, list_enabled_models

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

    @staticmethod
    def _promote_openai_as_default(db) -> None:
        models = db.query(AiModelConfig).all()
        for model in models:
            model.enabled = True
            model.is_default = model.model_code == 'gpt-4.1-mini'
        db.commit()


if __name__ == '__main__':
    unittest.main()
