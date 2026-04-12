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

const READY_STATUSES = new Set(['READY', 'SUCCESS', 'INDEXED', 'COMPLETED'])
const PROCESSING_STATUSES = new Set(['PROCESSING'])
const PENDING_STATUSES = new Set(['INIT', 'PENDING', 'UPLOADING'])
const FAILED_STATUSES = new Set(['FAILED', 'DELETED', 'NEEDS_REPAIR'])
const UNSUPPORTED_STATUSES = new Set(['SKIPPED', 'UNSUPPORTED'])

const normalizeStatus = (status?: string) => (status || '').trim().toUpperCase()

export const resolveKnowledgeReadiness = (parseStatus?: string, indexStatus?: string): KnowledgeReadiness => {
  const parse = normalizeStatus(parseStatus)
  const index = normalizeStatus(indexStatus)
  const statuses = [parse, index].filter(Boolean)

  if (!statuses.length) {
    return 'pending'
  }
  if (statuses.some((status) => FAILED_STATUSES.has(status))) {
    return 'failed'
  }
  if (statuses.some((status) => PROCESSING_STATUSES.has(status))) {
    return 'processing'
  }
  if (statuses.some((status) => READY_STATUSES.has(status))) {
    return 'ready'
  }
  if (statuses.every((status) => UNSUPPORTED_STATUSES.has(status))) {
    return 'unsupported'
  }
  if (statuses.some((status) => PENDING_STATUSES.has(status) || !status)) {
    return 'pending'
  }
  return 'pending'
}

export const knowledgeReadinessLabel = (parseStatus?: string, indexStatus?: string) => ({
  ready: '可引用',
  pending: '待解析',
  processing: '解析中',
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

const AI_PROVIDER_ORDER: Record<string, number> = {
  DEEPSEEK: 0,
  OPENAI: 1,
}

export const sortAiModelsByPreference = <T extends AiModelView>(models: T[]) =>
  [...models].sort((left, right) => {
    const providerDiff =
      (AI_PROVIDER_ORDER[(left.providerCode || '').toUpperCase()] ?? 9) -
      (AI_PROVIDER_ORDER[(right.providerCode || '').toUpperCase()] ?? 9)
    if (providerDiff !== 0) {
      return providerDiff
    }
    return (left.modelName || left.modelCode).localeCompare(right.modelName || right.modelCode)
  })

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
