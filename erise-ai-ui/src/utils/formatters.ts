import dayjs from 'dayjs'
import type { AiModelView } from '@/types/models'

export const formatDateTime = (value?: string, pattern = 'YYYY-MM-DD HH:mm') =>
  value ? dayjs(value).format(pattern) : '--'

const trimDecimalTail = (value: string) => value.replace(/\.?0+$/, '')

export const formatFileSize = (value?: number) => {
  if (!value || value <= 0) {
    return '--'
  }
  const mb = value / 1024 / 1024
  if (mb > 0.1) {
    return `${mb.toFixed(mb >= 10 ? 0 : 1)} MB`
  }
  const kb = value / 1024
  return `${Math.max(kb, 0.1).toFixed(kb >= 10 ? 0 : 1)} KB`
}

export const formatTokenCountInK = (value?: number) => {
  if (!value || value <= 0) {
    return '--'
  }
  const inK = value / 1000
  if (Number.isInteger(inK)) {
    return `${inK}K`
  }
  if (inK >= 10) {
    return `${trimDecimalTail(inK.toFixed(1))}K`
  }
  return `${trimDecimalTail(inK.toFixed(3))}K`
}

export const toEditableTokenCountK = (value?: number) => {
  if (!value || value <= 0) {
    return ''
  }
  const inK = value / 1000
  if (Number.isInteger(inK)) {
    return String(inK)
  }
  return trimDecimalTail(inK.toFixed(inK >= 10 ? 1 : 3))
}

export const normalizeFileTypeLabel = (fileExt?: string, mimeType?: string) => {
  if (fileExt) {
    return fileExt.trim().replace('.', '').toUpperCase()
  }
  if (mimeType?.includes('/')) {
    return mimeType.split('/').pop()?.toUpperCase() || 'FILE'
  }
  return mimeType?.toUpperCase() || 'FILE'
}

export const documentStatusLabel = (status?: string) => ({
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  ARCHIVED: '已归档',
}[status || ''] || status || '未知')

export const documentStatusTone = (status?: string) => ({
  DRAFT: 'warning',
  PUBLISHED: 'success',
  ARCHIVED: 'info',
}[status || ''] || 'info') as 'primary' | 'success' | 'warning' | 'danger' | 'info'

export const projectStatusLabel = (status?: string) => ({
  ACTIVE: '进行中',
  DRAFT: '草稿',
  ARCHIVED: '已归档',
}[status || ''] || status || '未知')

export const projectStatusTone = (status?: string) => ({
  ACTIVE: 'success',
  DRAFT: 'warning',
  ARCHIVED: 'info',
}[status || ''] || 'info') as 'primary' | 'success' | 'warning' | 'danger' | 'info'

export const uploadStatusLabel = (status?: string) => ({
  INIT: '待上传',
  UPLOADING: '上传中',
  READY: '已完成',
  FAILED: '失败',
}[status || ''] || status || '未知')

export type KnowledgeReadiness = 'ready' | 'pending' | 'processing' | 'failed' | 'unsupported'
export type KnowledgeProgressPhase =
  | 'pending'
  | 'parsing'
  | 'parsed'
  | 'indexing'
  | 'parse_retrying'
  | 'index_retrying'
  | 'parse_timeout_retrying'
  | 'index_timeout_retrying'
  | 'completed'
  | 'failed'
  | 'unsupported'

const READY_STATUSES = new Set(['READY', 'SUCCESS', 'INDEXED', 'COMPLETED'])
const PROCESSING_STATUSES = new Set(['PROCESSING'])
const PENDING_STATUSES = new Set(['INIT', 'PENDING', 'UPLOADING'])
const FAILED_STATUSES = new Set(['FAILED', 'DELETED', 'NEEDS_REPAIR'])
const UNSUPPORTED_STATUSES = new Set(['SKIPPED', 'UNSUPPORTED'])
const RETRYING_STATUS = 'RETRYING'
const TIMEOUT_RETRYING_STATUS = 'TIMEOUT_RETRYING'

const normalizeStatus = (status?: string) => (status || '').trim().toUpperCase()

const resolveKnowledgePhaseByStatus = (parseStatus?: string, indexStatus?: string): KnowledgeProgressPhase => {
  const parse = normalizeStatus(parseStatus)
  const index = normalizeStatus(indexStatus)

  if (!parse && !index) {
    return 'pending'
  }
  if (parse === TIMEOUT_RETRYING_STATUS) {
    return 'parse_timeout_retrying'
  }
  if (index === TIMEOUT_RETRYING_STATUS) {
    return 'index_timeout_retrying'
  }
  if (parse === RETRYING_STATUS) {
    return 'parse_retrying'
  }
  if (index === RETRYING_STATUS) {
    return 'index_retrying'
  }
  if ([parse, index].some((status) => FAILED_STATUSES.has(status))) {
    return 'failed'
  }
  if (PROCESSING_STATUSES.has(parse)) {
    return 'parsing'
  }
  if (READY_STATUSES.has(parse)) {
    if (PROCESSING_STATUSES.has(index)) {
      return 'indexing'
    }
    if (!index || PENDING_STATUSES.has(index)) {
      return 'parsed'
    }
    if (READY_STATUSES.has(index)) {
      return 'completed'
    }
  }
  if (UNSUPPORTED_STATUSES.has(parse)) {
    if (PROCESSING_STATUSES.has(index)) {
      return 'indexing'
    }
    if (READY_STATUSES.has(index)) {
      return 'completed'
    }
    if (!index || PENDING_STATUSES.has(index) || UNSUPPORTED_STATUSES.has(index)) {
      return 'unsupported'
    }
  }
  if (PROCESSING_STATUSES.has(index)) {
    return 'indexing'
  }
  if (READY_STATUSES.has(index) && !parse) {
    return 'completed'
  }
  if ([parse, index].filter(Boolean).every((status) => UNSUPPORTED_STATUSES.has(status))) {
    return 'unsupported'
  }
  return 'pending'
}

export const resolveKnowledgePhase = (parseStatus?: string, indexStatus?: string): KnowledgeProgressPhase =>
  resolveKnowledgePhaseByStatus(parseStatus, indexStatus)

export const resolveKnowledgeReadiness = (parseStatus?: string, indexStatus?: string): KnowledgeReadiness => {
  const phase = resolveKnowledgeProgressPhase(parseStatus, indexStatus)
  if (phase === 'completed') {
    return 'ready'
  }
  if (phase === 'failed') {
    return 'failed'
  }
  if (phase === 'unsupported') {
    return 'unsupported'
  }
  if (phase === 'pending') {
    return 'pending'
  }
  return 'processing'
}

export const knowledgeReadinessLabel = (parseStatus?: string, indexStatus?: string) => ({
  ready: '可引用',
  pending: '待解析',
  processing: '处理中',
  failed: '解析失败',
  unsupported: '不可引用',
}[resolveKnowledgeReadiness(parseStatus, indexStatus)])

export const knowledgeReadinessTone = (parseStatus?: string, indexStatus?: string) => ({
  ready: 'success',
  pending: 'warning',
  processing: 'primary',
  failed: 'danger',
  unsupported: 'info',
}[resolveKnowledgeReadiness(parseStatus, indexStatus)] || 'info') as 'primary' | 'success' | 'warning' | 'danger' | 'info'

export const isKnowledgeReady = (parseStatus?: string, indexStatus?: string) =>
  resolveKnowledgeReadiness(parseStatus, indexStatus) === 'ready'

export const isKnowledgeFailed = (parseStatus?: string, indexStatus?: string) =>
  resolveKnowledgeReadiness(parseStatus, indexStatus) === 'failed'

export const resolveKnowledgeProgressPhase = (parseStatus?: string, indexStatus?: string): KnowledgeProgressPhase =>
  resolveKnowledgePhaseByStatus(parseStatus, indexStatus)

export const knowledgeProgressLabel = (parseStatus?: string, indexStatus?: string) => ({
  pending: '待解析',
  parsing: '解析中',
  parsed: '已解析',
  indexing: '索引中',
  parse_retrying: '解析重试中',
  index_retrying: '索引重试中',
  parse_timeout_retrying: '解析超时，重试中',
  index_timeout_retrying: '索引超时，重试中',
  completed: '已完成',
  failed: '解析失败',
  unsupported: '不可引用',
}[resolveKnowledgeProgressPhase(parseStatus, indexStatus)])

export const knowledgeProgressTone = (parseStatus?: string, indexStatus?: string) => ({
  pending: 'warning',
  parsing: 'primary',
  parsed: 'info',
  indexing: 'primary',
  parse_retrying: 'warning',
  index_retrying: 'warning',
  parse_timeout_retrying: 'warning',
  index_timeout_retrying: 'warning',
  completed: 'success',
  failed: 'danger',
  unsupported: 'info',
}[resolveKnowledgeProgressPhase(parseStatus, indexStatus)] || 'info') as 'primary' | 'success' | 'warning' | 'danger' | 'info'

export const isKnowledgeCompleted = (parseStatus?: string, indexStatus?: string) =>
  resolveKnowledgeProgressPhase(parseStatus, indexStatus) === 'completed'

export const isKnowledgeInFlight = (parseStatus?: string, indexStatus?: string) =>
  [
    'pending',
    'parsing',
    'parsed',
    'indexing',
    'parse_retrying',
    'index_retrying',
    'parse_timeout_retrying',
    'index_timeout_retrying',
  ].includes(resolveKnowledgeProgressPhase(parseStatus, indexStatus))

const AI_PROVIDER_ORDER: Record<string, number> = {
  DEEPSEEK: 0,
  OPENAI: 1,
}

export const sortAiModelsByPreference = <T extends AiModelView>(models: T[]) =>
  [...models].sort((left, right) => {
    const defaultDiff = Number(Boolean(right.isDefault)) - Number(Boolean(left.isDefault))
    if (defaultDiff !== 0) {
      return defaultDiff
    }
    const providerDiff =
      (AI_PROVIDER_ORDER[(left.providerCode || '').toUpperCase()] ?? 9) -
      (AI_PROVIDER_ORDER[(right.providerCode || '').toUpperCase()] ?? 9)
    if (providerDiff !== 0) {
      return providerDiff
    }
    return (left.modelName || left.modelCode).localeCompare(right.modelName || right.modelCode)
  })

export const pickPreferredAiModel = <T extends AiModelView>(models: T[], selectedModelCode?: string | null) => {
  const sorted = sortAiModelsByPreference(models)
  if (!sorted.length) {
    return undefined
  }
  return sorted.find((item) => item.modelCode === selectedModelCode) || sorted[0]
}

export const isOfficeEditableFile = (ext?: string) => ['doc', 'docx', 'txt'].includes((ext || '').toLowerCase())

export const contentTypeLabel = (type?: string) => ({
  SHEET: '表格',
  BOARD: '画板',
  DATA_TABLE: '数据表',
}[type || ''] || type || '内容')

export const resolveErrorMessage = (error: unknown, fallback = '操作失败，请稍后重试') => {
  const candidate = error as {
    response?: { data?: { message?: string; msg?: string } }
    message?: string
  }
  return candidate?.response?.data?.message || candidate?.response?.data?.msg || candidate?.message || fallback
}

export const plainTextToHtml = (value: string) => {
  const escaped = value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
  return escaped
    .split(/\n{2,}/)
    .map((block) => `<p>${block.replace(/\n/g, '<br />')}</p>`)
    .join('') || '<p></p>'
}
