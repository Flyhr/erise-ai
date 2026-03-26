import http from './http'
import type { FileView, PageResponse } from '@/types/models'

export interface InitUploadResponse {
  fileId: number
  storageKey: string
  uploadUrl: string
}

const apiBase = import.meta.env.VITE_API_BASE_URL

export const getFiles = (params: { projectId: number; pageNum?: number; pageSize?: number }) =>
  http.get<never, PageResponse<FileView>>('/v1/files', { params })

export const initUpload = (payload: {
  projectId: number
  fileName: string
  fileSize: number
  mimeType: string
}) => http.post<never, InitUploadResponse>('/v1/files/init-upload', payload)

export const uploadFileBinary = async (fileId: number, file: File) => {
  const formData = new FormData()
  formData.append('fileId', String(fileId))
  formData.append('file', file)
  return http.post<never, FileView>('/v1/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export const completeUpload = (fileId: number) =>
  http.post<never, FileView>('/v1/files/complete-upload', { fileId })

export const getFile = (id: number) => http.get<never, FileView>(`/v1/files/${id}`)

export const bindFileTags = (id: number, tags: string[]) =>
  http.post<never, { id: number; name: string; color?: string }[]>(`/v1/files/${id}/tags`, { tags })

export const deleteFile = (id: number) => http.delete(`/v1/files/${id}`)

const fetchBinary = async (path: string) => {
  const token = localStorage.getItem('erise-access-token')
  const headers = token ? { Authorization: `Bearer ${token}` } : undefined
  const response = await fetch(`${apiBase}${path}`, { headers })
  if (!response.ok) {
    throw new Error('文件获取失败')
  }
  return response
}

export const previewFileBinary = async (id: number) => {
  const response = await fetchBinary(`/v1/files/${id}/preview`)
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  window.open(url, '_blank', 'noopener,noreferrer')
  window.setTimeout(() => URL.revokeObjectURL(url), 5000)
}

export const downloadFileContent = async (id: number, fileName: string) => {
  const response = await fetchBinary(`/v1/files/${id}/download`)
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  anchor.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 5000)
}
