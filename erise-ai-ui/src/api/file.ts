import type { AxiosRequestConfig } from 'axios'
import http, { FILE_UPLOAD_TIMEOUT_MS, resolveApiUrl } from './http'
import type { EditableOfficeFileView, FileStatusWatchView, FileView, PageResponse } from '@/types/models'

export interface InitUploadResponse {
  fileId: number
  storageKey: string
  uploadUrl: string
}

interface RequestBehaviorOptions {
  background?: boolean
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
    timeout: FILE_UPLOAD_TIMEOUT_MS,
  })
}

export const completeUpload = (fileId: number) =>
  http.post<never, FileView>('/v1/files/complete-upload', { fileId })

export const getFile = (id: number, options?: RequestBehaviorOptions) =>
  http.get<never, FileView>(
    `/v1/files/${id}`,
    { skipRouteLoading: options?.background } as AxiosRequestConfig & { skipRouteLoading?: boolean },
  )

export const watchFileStatuses = (params: { fileIds: number[]; cursor?: string; timeoutMs?: number }, options?: RequestBehaviorOptions) =>
  http.get<never, FileStatusWatchView>('/v1/files/status-watch', {
    params: {
      fileIds: params.fileIds.join(','),
      cursor: params.cursor,
      timeoutMs: params.timeoutMs,
    },
    skipRouteLoading: options?.background,
  } as AxiosRequestConfig & { skipRouteLoading?: boolean })

export const retryFileParse = (id: number) =>
  http.post<never, FileView>(`/v1/files/${id}/retry-parse`)

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

const openPreviewWindow = () => {
  const previewWindow = window.open('', '_blank')
  if (!previewWindow) {
    throw new Error('预览窗口被浏览器拦截，请允许弹窗后重试')
  }
  return previewWindow
}

const writePreviewWindowLoading = (previewWindow: Window) => {
  try {
    previewWindow.document.open()
    previewWindow.document.write(`<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>正在加载预览</title>
  <style>
    body {
      margin: 0;
      min-height: 100vh;
      display: grid;
      place-items: center;
      background: #f4f7fb;
      font-family: "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
      color: #1f2937;
    }
    .preview-loading {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 14px;
      padding: 24px 28px;
      border-radius: 20px;
      background: rgba(255, 255, 255, 0.96);
      border: 1px solid rgba(192, 199, 212, 0.28);
      box-shadow: 0 18px 45px rgba(15, 23, 42, 0.08);
    }
    .preview-loading__spinner {
      width: 28px;
      height: 28px;
      border-radius: 999px;
      border: 3px solid rgba(0, 96, 169, 0.18);
      border-top-color: #0060a9;
      animation: preview-spin 0.9s linear infinite;
    }
    .preview-loading__text {
      font-size: 14px;
      font-weight: 700;
      letter-spacing: 0.01em;
    }
    @keyframes preview-spin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }
  </style>
</head>
<body>
  <div class="preview-loading">
    <div class="preview-loading__spinner"></div>
    <div class="preview-loading__text">正在加载文件预览，请稍候...</div>
  </div>
</body>
</html>`)
    previewWindow.document.close()
  } catch {
    // Ignore failures in the temporary loading shell.
  }
}

const normalizePreviewContentType = (response: Response) => {
  const contentType = response.headers.get('content-type') || 'application/octet-stream'
  if (/^text\/(html|plain|markdown)\b/i.test(contentType) && !/charset=/i.test(contentType)) {
    return `${contentType};charset=UTF-8`
  }
  return contentType
}

const isTextPreviewContentType = (contentType: string) => /^text\/(html|plain|markdown)\b/i.test(contentType)

const waitForPreviewWindowLoad = async (previewWindow: Window, timeoutMs = 1200) =>
  new Promise<void>((resolve) => {
    let settled = false
    const finalize = () => {
      if (settled) {
        return
      }
      settled = true
      resolve()
    }

    try {
      previewWindow.addEventListener('load', finalize, { once: true })
    } catch {
      finalize()
      return
    }

    window.setTimeout(finalize, timeoutMs)
  })

const openBlobWindow = async (path: string) => {
  const previewWindow = openPreviewWindow()
  writePreviewWindowLoading(previewWindow)
  try {
    const response = await fetchBinary(path)
    const contentType = normalizePreviewContentType(response)

    if (isTextPreviewContentType(contentType)) {
      const html = await response.text()
      previewWindow.document.open()
      previewWindow.document.write(html)
      previewWindow.document.close()
      await waitForPreviewWindowLoad(previewWindow, 500)
      return
    }

    const blob = await response.blob()
    const url = URL.createObjectURL(blob)
    previewWindow.location.replace(url)
    await waitForPreviewWindowLoad(previewWindow)
    window.setTimeout(() => URL.revokeObjectURL(url), 5000)
  } catch (error) {
    previewWindow.close()
    throw error
  }
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
