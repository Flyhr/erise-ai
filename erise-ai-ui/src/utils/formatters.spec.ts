import { describe, expect, it } from 'vitest'
import {
  knowledgeProgressLabel,
  knowledgeReadinessLabel,
  pickPreferredAiModel,
  resolveKnowledgeProgressPhase,
  resolveKnowledgeReadiness,
  sortAiModelsByPreference,
} from './formatters'

describe('formatters', () => {
  it('resolves readiness from parse and index status pairs', () => {
    expect(resolveKnowledgeReadiness('SUCCESS', 'SUCCESS')).toBe('ready')
    expect(resolveKnowledgeReadiness('PROCESSING', 'PENDING')).toBe('processing')
    expect(resolveKnowledgeReadiness('FAILED', 'PENDING')).toBe('failed')
    expect(resolveKnowledgeReadiness('SKIPPED', 'SKIPPED')).toBe('unsupported')
    expect(knowledgeReadinessLabel('PENDING', 'PENDING')).toBe('待解析')
  })

  it('renders retrying knowledge states with explicit retry labels', () => {
    expect(resolveKnowledgeProgressPhase('RETRYING', 'PENDING')).toBe('parse_retrying')
    expect(resolveKnowledgeProgressPhase('SUCCESS', 'RETRYING')).toBe('index_retrying')
    expect(resolveKnowledgeProgressPhase('TIMEOUT_RETRYING', 'PENDING')).toBe('parse_timeout_retrying')
    expect(resolveKnowledgeProgressPhase('SUCCESS', 'TIMEOUT_RETRYING')).toBe('index_timeout_retrying')
    expect(knowledgeProgressLabel('SUCCESS', 'TIMEOUT_RETRYING')).toBe('索引超时，重试中')
  })

  it('sorts ai models with default first and provider priority second', () => {
    const sorted = sortAiModelsByPreference([
      { providerCode: 'OPENAI', modelCode: 'gpt-4.1-mini', modelName: 'GPT-4.1 Mini', supportStream: true },
      { providerCode: 'DEEPSEEK', modelCode: 'deepseek-chat', modelName: 'DeepSeek Chat', isDefault: true, supportStream: true },
      { providerCode: 'OPENAI', modelCode: 'gpt-4.1', modelName: 'GPT-4.1', supportStream: true },
    ])

    expect(sorted.map((item) => item.modelCode)).toEqual(['deepseek-chat', 'gpt-4.1', 'gpt-4.1-mini'])
  })

  it('prefers the currently selected model when still available, otherwise falls back to the default-first order', () => {
    const models = [
      { providerCode: 'OPENAI', modelCode: 'gpt-4.1-mini', modelName: 'GPT-4.1 Mini', supportStream: true },
      { providerCode: 'DEEPSEEK', modelCode: 'deepseek-chat', modelName: 'DeepSeek Chat', isDefault: true, supportStream: true },
      { providerCode: 'OPENAI', modelCode: 'gpt-4.1', modelName: 'GPT-4.1', supportStream: true },
    ]

    expect(pickPreferredAiModel(models, 'gpt-4.1-mini')?.modelCode).toBe('gpt-4.1-mini')
    expect(pickPreferredAiModel(models, 'missing-model')?.modelCode).toBe('deepseek-chat')
  })
})
