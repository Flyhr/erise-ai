import dayjs from 'dayjs'

export const formatDateTime = (value?: string, pattern = 'YYYY-MM-DD HH:mm') =>
  value ? dayjs(value).format(pattern) : '--'

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
