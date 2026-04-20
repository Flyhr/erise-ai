from __future__ import annotations

from typing import Any

ACTIVE_SESSION_STATUS = 'active'
DELETED_SESSION_STATUS = 'deleted'

ROLE_SYSTEM = 'system'
ROLE_USER = 'user'
ROLE_ASSISTANT = 'assistant'

STATUS_PENDING = 'pending'
STATUS_STREAMING = 'streaming'
STATUS_SUCCESS = 'success'
STATUS_FAILED = 'failed'
STATUS_CANCELLED = 'cancelled'

SCENE_GENERAL = 'general_chat'
SCENE_PROJECT = 'project_chat'
SCENE_DOCUMENT = 'document_chat'

DEFAULT_SYSTEM_PROMPTS = {
    SCENE_GENERAL: '你是 Erise-AI 的聊天助手。请优先用中文回答，结论直接，推测要明确说明；当引用证据不足时，不得伪造“依据某文档”或“根据附件”。',
    SCENE_PROJECT: '你是 Erise-AI 的项目协作助手。请结合项目上下文回答，优先给出可执行建议；当私有资料证据不足时，必须明确降级说明，不能伪造文档依据。',
    SCENE_DOCUMENT: '你是 Erise-AI 的文档助手。请围绕文档内容进行解释、总结和整理；只有在引用充分时，才能给出“依据文档”的确定性结论。',
}


def build_default_model_rows(
    *,
    deepseek_model: str = 'deepseek-chat',
    openai_model: str = 'gpt-4.1-mini',
    ollama_chat_model: str = 'qwen2.5:7b',
    vllm_model: str = 'Qwen/Qwen2.5-7B-Instruct',
    litellm_model: str = 'deepseek/deepseek-chat',
) -> tuple[dict[str, Any], ...]:
    normalized_deepseek_model = (deepseek_model or 'deepseek-chat').strip() or 'deepseek-chat'
    normalized_openai_model = (openai_model or 'gpt-4.1-mini').strip() or 'gpt-4.1-mini'
    normalized_ollama_chat_model = (ollama_chat_model or 'qwen2.5:7b').strip() or 'qwen2.5:7b'
    normalized_vllm_model = (vllm_model or 'Qwen/Qwen2.5-7B-Instruct').strip() or 'Qwen/Qwen2.5-7B-Instruct'
    normalized_litellm_model = (litellm_model or 'deepseek/deepseek-chat').strip() or 'deepseek/deepseek-chat'
    rows = (
        {
            'provider_code': 'DEEPSEEK',
            'model_code': normalized_deepseek_model,
            'model_name': f'DeepSeek {normalized_deepseek_model}',
            'base_url': 'https://api.deepseek.com/v1',
            'api_key_ref': 'DEEPSEEK_API_KEY',
            'enabled': True,
            'is_default': True,
            'support_stream': True,
            'support_system_prompt': True,
            'max_context_tokens': 64000,
            'priority_no': 1,
        },
        {
            'provider_code': 'OPENAI',
            'model_code': normalized_openai_model,
            'model_name': normalized_openai_model,
            'base_url': 'https://api.openai.com/v1',
            'api_key_ref': 'OPENAI_API_KEY',
            'enabled': True,
            'is_default': False,
            'support_stream': True,
            'support_system_prompt': True,
            'max_context_tokens': 128000,
            'priority_no': 2,
        },
        {
            'provider_code': 'OLLAMA',
            'model_code': normalized_ollama_chat_model,
            'model_name': f'Ollama {normalized_ollama_chat_model}',
            'base_url': 'http://localhost:11434/v1',
            'api_key_ref': None,
            'enabled': True,
            'is_default': False,
            'support_stream': True,
            'support_system_prompt': True,
            'max_context_tokens': 32768,
            'priority_no': 3,
        },
        {
            'provider_code': 'VLLM',
            'model_code': normalized_vllm_model,
            'model_name': f'vLLM {normalized_vllm_model}',
            'base_url': 'http://localhost:8000/v1',
            'api_key_ref': 'VLLM_API_KEY',
            'enabled': True,
            'is_default': False,
            'support_stream': True,
            'support_system_prompt': True,
            'max_context_tokens': 32768,
            'priority_no': 4,
        },
        {
            'provider_code': 'LITELLM',
            'model_code': normalized_litellm_model,
            'model_name': f'LiteLLM {normalized_litellm_model}',
            'base_url': 'http://localhost:4000/v1',
            'api_key_ref': 'LITELLM_API_KEY',
            'enabled': True,
            'is_default': False,
            'support_stream': True,
            'support_system_prompt': True,
            'max_context_tokens': 64000,
            'priority_no': 5,
        },
    )
    deduped_rows: list[dict[str, Any]] = []
    seen_model_codes: set[str] = set()
    for row in rows:
        model_code = (row.get('model_code') or '').strip()
        if not model_code or model_code in seen_model_codes:
            continue
        deduped_rows.append(row)
        seen_model_codes.add(model_code)
    return tuple(deduped_rows)


DEFAULT_MODEL_ROWS = build_default_model_rows()
