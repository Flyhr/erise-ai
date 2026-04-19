from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.orm import Session

from src.app.adapters.llm.base import LlmAdapter
from src.app.adapters.llm.deepseek_adapter import DeepSeekAdapter
from src.app.adapters.llm.openai_adapter import OpenAiCompatibleAdapter
from src.app.core.config import get_settings
from src.app.core.constants import DEFAULT_MODEL_ROWS, DEFAULT_SYSTEM_PROMPTS
from src.app.core.exceptions import AiServiceError
from src.app.models.ai_model_config import AiModelConfig
from src.app.models.ai_prompt_template import AiPromptTemplate
from src.app.schemas.model import ModelView


PREFERRED_PROVIDER_ORDER = {
    'DEEPSEEK': 0,
    'OPENAI': 1,
}


def _model_sort_key(model: AiModelConfig) -> tuple[int, int, str]:
    return (
        0 if model.is_default else 1,
        PREFERRED_PROVIDER_ORDER.get((model.provider_code or '').upper(), 9),
        int(model.priority_no or 999),
        model.model_code,
    )


def bootstrap_defaults() -> None:
    settings = get_settings()
    from src.app.db.session import SessionLocal

    with SessionLocal() as db:
        for row in DEFAULT_MODEL_ROWS:
            existing = db.execute(select(AiModelConfig).where(AiModelConfig.model_code == row['model_code'])).scalar_one_or_none()
            if existing:
                continue
            enabled = row['enabled']
            if row['provider_code'] == 'DEEPSEEK' and not settings.deepseek_api_key:
                enabled = False
            if row['provider_code'] == 'OPENAI' and not settings.openai_api_key:
                enabled = False
            payload = dict(row)
            payload['enabled'] = enabled
            db.add(AiModelConfig(**payload))

        for scene, prompt in DEFAULT_SYSTEM_PROMPTS.items():
            code = f'default_{scene}'
            existing_prompt = db.execute(select(AiPromptTemplate).where(AiPromptTemplate.template_code == code)).scalar_one_or_none()
            if existing_prompt:
                continue
            db.add(
                AiPromptTemplate(
                    template_code=code,
                    template_name=scene,
                    scene=scene,
                    system_prompt=prompt,
                    user_prompt_wrapper=None,
                    enabled=True,
                    version_no=1,
                    created_by='system',
                )
            )
        db.commit()


def list_enabled_models(db: Session) -> list[ModelView]:
    models = db.execute(
        select(AiModelConfig).where(AiModelConfig.enabled.is_(True))
    ).scalars().all()
    models = sorted(models, key=_model_sort_key)
    return [
        ModelView(
            provider_code=item.provider_code,
            model_code=item.model_code,
            model_name=item.model_name,
            is_default=item.is_default,
            support_stream=item.support_stream,
            max_context_tokens=item.max_context_tokens,
        )
        for item in models
    ]


def get_model_config(db: Session, requested_model_code: str | None) -> AiModelConfig:
    settings = get_settings()
    if requested_model_code:
        model = db.execute(select(AiModelConfig).where(AiModelConfig.model_code == requested_model_code)).scalar_one_or_none()
        if model is not None and model.enabled:
            return model
        raise AiServiceError('AI_MODEL_NOT_FOUND', f'Model `{requested_model_code}` is not available', status_code=404)
    default_model = db.execute(
        select(AiModelConfig).where(
            AiModelConfig.is_default.is_(True),
            AiModelConfig.enabled.is_(True),
        )
    ).scalar_one_or_none()
    if default_model is not None:
        return default_model
    default_model = db.execute(select(AiModelConfig).where(AiModelConfig.model_code == settings.default_model_code)).scalar_one_or_none()
    if default_model is not None and default_model.enabled:
        return default_model
    deepseek_models = db.execute(
        select(AiModelConfig).where(
            AiModelConfig.enabled.is_(True),
            AiModelConfig.provider_code == 'DEEPSEEK',
        )
    ).scalars().all()
    deepseek_models = sorted(deepseek_models, key=_model_sort_key)
    if deepseek_models:
        return deepseek_models[0]
    raise AiServiceError(
        'AI_MODEL_NOT_FOUND',
        'Default DeepSeek model is not available. Please configure a DeepSeek model or choose an explicit fallback model.',
        status_code=404,
    )


def get_model_adapter(model: AiModelConfig) -> LlmAdapter:
    settings = get_settings()
    if model.provider_code == 'OPENAI':
        return OpenAiCompatibleAdapter(
            provider_code='OPENAI',
            api_key=settings.openai_api_key,
            base_url=model.base_url or settings.openai_base_url,
            timeout_seconds=settings.provider_timeout_seconds,
        )
    if model.provider_code == 'DEEPSEEK':
        return DeepSeekAdapter(
            provider_code='DEEPSEEK',
            api_key=settings.deepseek_api_key,
            base_url=model.base_url or settings.deepseek_base_url,
            timeout_seconds=settings.provider_timeout_seconds,
        )
    raise AiServiceError('AI_MODEL_NOT_FOUND', f'Unsupported provider `{model.provider_code}`', status_code=404)
