import http, { FILE_UPLOAD_TIMEOUT_MS } from './http'
import type {
  AiAttachmentPayload,
  AiChatResponse,
  AiModelView,
  AiRetrievalSettingUpdateView,
  AiRetrievalSettingView,
  AiSessionDetailView,
  AiSessionSummaryView,
  AiTempFileView,
} from '@/types/models'

export interface AiChatPayload {
  projectId?: number
  sessionId?: number
  question: string
  modelCode?: string
  mode?: 'GENERAL' | 'SCOPED'
  attachments?: AiAttachmentPayload[]
  tempFileIds?: number[]
  webSearchEnabled?: boolean
  similarityThreshold?: number
  topK?: number
}

export interface AiCreateSessionPayload {
  projectId?: number
  scene?: string
  title?: string
}

export interface AiUpdateRetrievalSettingsPayload {
  similarityThreshold: number
  topK: number
  webSearchEnabledDefault: boolean
}

export interface AiMessageFeedbackPayload {
  feedbackType: 'UP' | 'DOWN'
  feedbackNote?: string
}

const AI_METADATA_TIMEOUT_MS = 8000

export const chat = (payload: AiChatPayload) =>
  http.post<never, AiChatResponse>('/v1/ai/chat', payload)

export const cancelChat = (requestId: string) =>
  http.post<never, void>(`/v1/ai/chat/${requestId}/cancel`)

export const getSessions = () =>
  http.get<never, AiSessionSummaryView[]>('/v1/ai/sessions', { timeout: AI_METADATA_TIMEOUT_MS })

export const createSession = (payload: AiCreateSessionPayload) =>
  http.post<never, AiSessionSummaryView>('/v1/ai/sessions', payload)

export const getSession = (id: number) =>
  http.get<never, AiSessionDetailView>(`/v1/ai/sessions/${id}`, { timeout: AI_METADATA_TIMEOUT_MS })

export const deleteSession = (id: number) => http.delete(`/v1/ai/sessions/${id}`)

export const getModels = () =>
  http.get<never, AiModelView[]>('/v1/ai/models', { timeout: AI_METADATA_TIMEOUT_MS })

export const uploadTempFile = (payload: FormData) =>
  http.post<never, AiTempFileView>('/v1/ai/temp-files', payload, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
    timeout: FILE_UPLOAD_TIMEOUT_MS,
  })

export const getTempFiles = (sessionId: number) =>
  http.get<never, AiTempFileView[]>('/v1/ai/temp-files', {
    params: { sessionId },
    timeout: AI_METADATA_TIMEOUT_MS,
  })

export const deleteTempFile = (id: number) =>
  http.delete<never, void>(`/v1/ai/temp-files/${id}`)

export const retryTempFile = (id: number) =>
  http.post<never, AiTempFileView>(`/v1/ai/temp-files/${id}/retry`)

export const getRetrievalSettings = () =>
  http.get<never, AiRetrievalSettingView>('/v1/ai/settings/retrieval')

export const updateRetrievalSettings = (payload: AiUpdateRetrievalSettingsPayload) =>
  http.put<never, AiRetrievalSettingUpdateView>('/v1/ai/settings/retrieval', payload)

export const submitAiMessageFeedback = (id: number, payload: AiMessageFeedbackPayload) =>
  http.post<never, void>(`/v1/ai/messages/${id}/feedback`, payload)

export interface McpJsonRpcRequest {
  jsonrpc: '2.0'
  id?: string | number | null
  method: string
  params?: Record<string, unknown>
}

export interface McpJsonRpcResponse<T = unknown> {
  jsonrpc: '2.0'
  id?: string | number | null
  result?: T
  error?: {
    code: number
    message: string
    data?: Record<string, unknown>
  }
}

export const proxyMcp = <T = unknown>(payload: McpJsonRpcRequest) =>
  http.post<never, McpJsonRpcResponse<T>>('/v1/ai/mcp', payload)
