from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy import select
from sqlalchemy.orm import Session

from src.app.core.config import get_settings
from src.app.core.constants import DEFAULT_SYSTEM_PROMPTS, build_default_model_rows
from src.app.core.exceptions import AiServiceError
from src.app.models.ai_model_config import AiModelConfig
from src.app.models.ai_prompt_template import AiPromptTemplate
from src.app.providers import (
    DeepSeekProvider,
    LiteLlmProvider,
    ModelProvider,
    OllamaProvider,
    OpenAiCompatibleProvider,
    VllmProvider,
)
from src.app.schemas.model import ModelView


BASE_PROVIDER_ORDER = ('DEEPSEEK', 'OPENAI', 'OLLAMA', 'VLLM', 'LITELLM')
DEV_PROVIDER_ORDER = ('OLLAMA', 'DEEPSEEK', 'OPENAI', 'VLLM', 'LITELLM')
PROVIDER_API_KEY_REQUIRED = {
    'OPENAI': True,
    'DEEPSEEK': True,
    'OLLAMA': False,
    'VLLM': False,
    'LITELLM': False,
}

ProviderAdapter = ModelProvider


@dataclass(frozen=True, slots=True)
class ProviderRoute:
    provider_code: str
    model_code: str
    base_url: str
    api_key: str
    timeout_seconds: int
    configured: bool
    source: str


class ProviderRegistry:
    def __init__(self, settings=None) -> None:
        self.settings = settings or get_settings()

    def normalize_provider_code(self, provider_code: str | None) -> str:
        return (provider_code or '').strip().upper()

    def infer_provider_code_from_base_url(self, base_url: str | None) -> str:
        normalized = (base_url or '').strip().lower()
        if 'deepseek' in normalized:
            return 'DEEPSEEK'
        if 'ollama' in normalized or ':11434' in normalized:
            return 'OLLAMA'
        if 'litellm' in normalized or ':4000' in normalized:
            return 'LITELLM'
        if 'vllm' in normalized or ':8000' in normalized:
            return 'VLLM'
        return 'OPENAI'

    def gateway_override_enabled(self) -> bool:
        return bool(self.settings.model_provider or self.settings.model_base_url or self.settings.model_api_key)

    def gateway_provider_code(self) -> str:
        provider_code = self.normalize_provider_code(self.settings.model_provider)
        if provider_code:
            return provider_code
        if self.settings.model_base_url:
            return self.infer_provider_code_from_base_url(self.settings.model_base_url)
        return ''

    def provider_priority_order(self) -> tuple[str, ...]:
        base_order = DEV_PROVIDER_ORDER if self.settings.is_dev_env else BASE_PROVIDER_ORDER
        explicit_provider = self.gateway_provider_code()
        if not explicit_provider:
            return base_order
        return (explicit_provider,) + tuple(item for item in base_order if item != explicit_provider)

    def provider_priority_index(self, provider_code: str | None) -> int:
        normalized = self.normalize_provider_code(provider_code)
        try:
            return self.provider_priority_order().index(normalized)
        except ValueError:
            return 99

    def provider_api_key(self, provider_code: str) -> str:
        return {
            'OPENAI': self.settings.openai_api_key,
            'DEEPSEEK': self.settings.deepseek_api_key,
            'OLLAMA': self.settings.ollama_api_key,
            'VLLM': self.settings.vllm_api_key,
            'LITELLM': self.settings.litellm_api_key,
        }.get(provider_code, '')

    def provider_base_url(self, provider_code: str, configured_base_url: str | None = None) -> str:
        if configured_base_url:
            return configured_base_url
        return {
            'OPENAI': self.settings.openai_base_url,
            'DEEPSEEK': self.settings.deepseek_base_url,
            'OLLAMA': self.settings.ollama_base_url,
            'VLLM': self.settings.vllm_base_url,
            'LITELLM': self.settings.litellm_base_url,
        }.get(provider_code, '')

    def route_configured(self, provider_code: str, base_url: str, api_key: str) -> bool:
        if not base_url:
            return False
        if PROVIDER_API_KEY_REQUIRED.get(provider_code, True) and not api_key:
            return False
        return True

    def build_provider(self, route: ProviderRoute) -> ProviderAdapter:
        provider_code = route.provider_code
        if provider_code == 'OPENAI':
            return OpenAiCompatibleProvider(
                provider_code='OPENAI',
                api_key=route.api_key,
                base_url=route.base_url,
                timeout_seconds=route.timeout_seconds,
                require_api_key=True,
            )
        if provider_code == 'DEEPSEEK':
            return DeepSeekProvider(
                api_key=route.api_key,
                base_url=route.base_url,
                timeout_seconds=route.timeout_seconds,
            )
        if provider_code == 'OLLAMA':
            return OllamaProvider(
                api_key=route.api_key,
                base_url=route.base_url,
                timeout_seconds=route.timeout_seconds,
            )
        if provider_code == 'VLLM':
            return VllmProvider(
                api_key=route.api_key,
                base_url=route.base_url,
                timeout_seconds=route.timeout_seconds,
            )
        if provider_code == 'LITELLM':
            return LiteLlmProvider(
                api_key=route.api_key,
                base_url=route.base_url,
                timeout_seconds=route.timeout_seconds,
            )
        raise AiServiceError('AI_MODEL_NOT_FOUND', f'Unsupported provider `{provider_code}`', status_code=404)

    def resolve_model_route(self, model: AiModelConfig) -> ProviderRoute:
        model_provider_code = self.normalize_provider_code(model.provider_code)
        explicit_provider_code = self.gateway_provider_code()
        provider_code = explicit_provider_code or model_provider_code
        if self.settings.model_base_url:
            base_url = self.settings.model_base_url
        elif self.gateway_override_enabled() and explicit_provider_code and explicit_provider_code != model_provider_code:
            base_url = self.provider_base_url(provider_code)
        else:
            base_url = self.provider_base_url(provider_code, model.base_url)
        api_key = self.settings.model_api_key or self.provider_api_key(provider_code)
        return ProviderRoute(
            provider_code=provider_code,
            model_code=model.model_code,
            base_url=base_url,
            api_key=api_key,
            timeout_seconds=self.settings.provider_timeout_seconds,
            configured=self.route_configured(provider_code, base_url, api_key),
            source='gateway-override' if self.gateway_override_enabled() else 'model-config',
        )

    def resolve_embedding_route(self) -> ProviderRoute:
        provider_code = self.normalize_provider_code(self.settings.embedding_provider_code)
        if not provider_code:
            provider_code = self.gateway_provider_code()
        if not provider_code:
            provider_code = self.infer_provider_code_from_base_url(self.settings.resolved_embedding_base_url)
        if self.settings.embed_base_url:
            base_url = self.settings.embed_base_url
        elif self.settings.embedding_base_url:
            base_url = self.settings.embedding_base_url
        elif self.settings.model_base_url and provider_code == self.gateway_provider_code():
            base_url = self.settings.model_base_url
        else:
            base_url = self.provider_base_url(provider_code)
        api_key = (
            self.settings.embedding_api_key
            or self.settings.model_api_key
            or self.provider_api_key(provider_code)
            or self.settings.resolved_embedding_api_key
        )
        return ProviderRoute(
            provider_code=provider_code,
            model_code=self.settings.resolve_embedding_model_code(provider_code),
            base_url=base_url,
            api_key=api_key,
            timeout_seconds=self.settings.provider_timeout_seconds,
            configured=self.route_configured(provider_code, base_url, api_key),
            source='embedding-config' if self.settings.embedding_provider_code or self.settings.embed_base_url else 'model-default',
        )


def _model_sort_key(model: AiModelConfig, registry: ProviderRegistry | None = None) -> tuple[int, int, int, str]:
    registry = registry or ProviderRegistry()
    return (
        0 if model.is_default else 1,
        registry.provider_priority_index(model.provider_code),
        int(model.priority_no or 999),
        model.model_code,
    )


def bootstrap_defaults() -> None:
    settings = get_settings()
    from src.app.db.session import SessionLocal

    with SessionLocal() as db:
        gateway_override_enabled = bool(settings.model_provider or settings.model_base_url or settings.model_api_key)
        for row in build_default_model_rows(settings.ollama_chat_model):
            existing = db.execute(select(AiModelConfig).where(AiModelConfig.model_code == row['model_code'])).scalar_one_or_none()
            if existing:
                continue
            enabled = row['enabled']
            if row['provider_code'] == 'DEEPSEEK' and not gateway_override_enabled and not settings.deepseek_api_key:
                enabled = False
            if row['provider_code'] == 'OPENAI' and not gateway_override_enabled and not settings.openai_api_key:
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
    registry = ProviderRegistry()
    models = db.execute(select(AiModelConfig).where(AiModelConfig.enabled.is_(True))).scalars().all()
    models = sorted(models, key=lambda item: _model_sort_key(item, registry))
    effective_default = _select_effective_default_model(models, registry)
    effective_default_code = effective_default.model_code if effective_default is not None else None
    return [
        ModelView(
            provider_code=item.provider_code,
            model_code=item.model_code,
            model_name=item.model_name,
            is_default=item.model_code == effective_default_code,
            support_stream=item.support_stream,
            max_context_tokens=item.max_context_tokens,
        )
        for item in models
    ]


def _enabled_models(db: Session) -> list[AiModelConfig]:
    return db.execute(select(AiModelConfig).where(AiModelConfig.enabled.is_(True))).scalars().all()


def _find_model_by_code(models: list[AiModelConfig], model_code: str | None) -> AiModelConfig | None:
    if not model_code:
        return None
    for model in models:
        if model.model_code == model_code:
            return model
    return None


def _preferred_provider_model(models: list[AiModelConfig], provider_code: str, registry: ProviderRegistry) -> AiModelConfig | None:
    normalized = registry.normalize_provider_code(provider_code)
    candidates = [item for item in models if registry.normalize_provider_code(item.provider_code) == normalized]
    if not candidates:
        return None
    configured = [item for item in candidates if registry.resolve_model_route(item).configured]
    target_models = configured or candidates
    target_models = sorted(target_models, key=lambda item: _model_sort_key(item, registry))
    return target_models[0] if target_models else None


def _select_effective_default_model(models: list[AiModelConfig], registry: ProviderRegistry) -> AiModelConfig | None:
    settings = registry.settings
    preferred_provider = registry.gateway_provider_code()
    if preferred_provider:
        preferred_model = _preferred_provider_model(models, preferred_provider, registry)
        if preferred_model is not None:
            return preferred_model
        configured_default = _find_model_by_code(models, settings.default_model_code)
        if configured_default is not None:
            return configured_default

    default_models = [item for item in models if item.is_default]
    if default_models:
        return sorted(default_models, key=lambda item: _model_sort_key(item, registry))[0]

    configured_default = _find_model_by_code(models, settings.default_model_code)
    if configured_default is not None:
        return configured_default

    for provider_code in registry.provider_priority_order():
        candidate = _preferred_provider_model(models, provider_code, registry)
        if candidate is not None:
            return candidate

    if models:
        return sorted(models, key=lambda item: _model_sort_key(item, registry))[0]
    return None


def get_model_config(db: Session, requested_model_code: str | None) -> AiModelConfig:
    settings = get_settings()
    registry = ProviderRegistry(settings)
    models = _enabled_models(db)

    if requested_model_code:
        model = _find_model_by_code(models, requested_model_code)
        if model is not None:
            return model
        raise AiServiceError('AI_MODEL_NOT_FOUND', f'Model `{requested_model_code}` is not available', status_code=404)

    selected = _select_effective_default_model(models, registry)
    if selected is not None:
        return selected

    raise AiServiceError(
        'AI_MODEL_NOT_FOUND',
        'No enabled model is available. Please configure a provider-backed model or choose an explicit fallback model.',
        status_code=404,
    )


def _normalize_provider_code(provider_code: str | None) -> str:
    return ProviderRegistry().normalize_provider_code(provider_code)


def _gateway_provider_code(settings) -> str:
    return ProviderRegistry(settings).gateway_provider_code()


def _provider_api_key(settings, provider_code: str) -> str:
    return ProviderRegistry(settings).provider_api_key(provider_code)


def _provider_base_url(settings, provider_code: str, configured_base_url: str | None = None) -> str:
    return ProviderRegistry(settings).provider_base_url(provider_code, configured_base_url)


def _build_provider(
    *,
    provider_code: str,
    api_key: str,
    base_url: str,
    timeout_seconds: int,
) -> ModelProvider:
    route = ProviderRoute(
        provider_code=provider_code,
        model_code='',
        base_url=base_url,
        api_key=api_key,
        timeout_seconds=timeout_seconds,
        configured=True,
        source='compat',
    )
    return ProviderRegistry().build_provider(route)


def infer_provider_code_from_base_url(base_url: str | None) -> str:
    return ProviderRegistry().infer_provider_code_from_base_url(base_url)


def get_model_provider(model: AiModelConfig) -> ModelProvider:
    registry = ProviderRegistry()
    return registry.build_provider(registry.resolve_model_route(model))


def get_model_adapter(model: AiModelConfig) -> ModelProvider:
    return get_model_provider(model)


def get_embedding_route() -> ProviderRoute:
    return ProviderRegistry().resolve_embedding_route()


def get_embedding_model_code() -> str:
    return get_embedding_route().model_code


def get_embedding_provider() -> ModelProvider:
    registry = ProviderRegistry()
    return registry.build_provider(registry.resolve_embedding_route())


def get_embedding_adapter() -> tuple[ProviderRoute, ProviderAdapter]:
    registry = ProviderRegistry()
    route = registry.resolve_embedding_route()
    return route, registry.build_provider(route)
