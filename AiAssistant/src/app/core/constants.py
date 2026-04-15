from __future__ import annotations

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
    SCENE_GENERAL: '你是 Erise-AI 的企业级聊天助手。请优先用中文回答，结论直接，推测要明确说明；当引用证据不足时，不得伪造“依据某文档”或“根据附件”。',
    SCENE_PROJECT: '你是 Erise-AI 的项目协作助手。请结合项目上下文回答，优先给出可执行建议；当私有资料证据不足时，必须明确降级说明，不能伪造文档依据。',
    SCENE_DOCUMENT: '你是 Erise-AI 的文档助手。请围绕文档内容进行解释、总结和整理；只有在引用充分时，才能给出“依据文档”的确定性结论。',
}

DEFAULT_MODEL_ROWS = (
    {
        'provider_code': 'DEEPSEEK',
        'model_code': 'deepseek-chat',
        'model_name': 'DeepSeek Chat',
        'base_url': 'https://api.deepseek.com/v1',
        'api_key_ref': 'DEEPSEEK_API_KEY',
        'enabled': True,
        'support_stream': True,
        'support_system_prompt': True,
        'max_context_tokens': 64000,
        'priority_no': 1,
    },
    {
        'provider_code': 'OPENAI',
        'model_code': 'gpt-4.1-mini',
        'model_name': 'GPT-4.1 Mini',
        'base_url': 'https://api.openai.com/v1',
        'api_key_ref': 'OPENAI_API_KEY',
        'enabled': True,
        'support_stream': True,
        'support_system_prompt': True,
        'max_context_tokens': 128000,
        'priority_no': 2,
    },
)
