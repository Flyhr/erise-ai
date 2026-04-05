import http, { resolveApiUrl } from './http'
import type { EditableOfficeFileView, FileView, PageResponse } from '@/types/models'

export interface InitUploadResponse {
  fileId: number
  storageKey: string
  uploadUrl: string
}

export const getFiles = (params: { projectId?: number; q?: string; pageNum?: number; pageSize?: number }) =>
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

export const getEditableOfficeFile = (id: number) => http.get<never, EditableOfficeFileView>(`/v1/files/${id}/office`)

export const updateEditableOfficeFile = (id: number, payload: { contentHtmlSnapshot: string; plainText: string }) =>
  http.put<never, EditableOfficeFileView>(`/v1/files/${id}/office`, payload)

const fetchBinary = async (path: string) => {
  const token = localStorage.getItem('erise-access-token')
  const headers = token ? { Authorization: `Bearer ${token}` } : undefined
  const response = await fetch(resolveApiUrl(path), { headers })
  if (!response.ok) {
    throw new Error('Unable to fetch file content')
  }
  return response
}

const openBlobWindow = async (path: string) => {
  const response = await fetchBinary(path)
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  window.open(url, '_blank', 'noopener,noreferrer')
  window.setTimeout(() => URL.revokeObjectURL(url), 5000)
}

export const previewFileBinary = async (id: number) => openBlobWindow(`/v1/files/${id}/preview`)

export const previewOfficeFile = async (id: number) => openBlobWindow(`/v1/files/${id}/office/preview`)

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