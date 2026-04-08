import http from './http'
import type {
  AiAttachmentPayload,
  AiChatResponse,
  AiModelView,
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

export const chat = (payload: AiChatPayload) =>
  http.post<never, AiChatResponse>('/v1/ai/chat', payload)

export const cancelChat = (requestId: string) =>
  http.post<never, void>(`/v1/ai/chat/${requestId}/cancel`)

export const getSessions = () => http.get<never, AiSessionSummaryView[]>('/v1/ai/sessions')

export const getSession = (id: number) => http.get<never, AiSessionDetailView>(`/v1/ai/sessions/${id}`)

export const deleteSession = (id: number) => http.delete(`/v1/ai/sessions/${id}`)

export const getModels = () => http.get<never, AiModelView[]>('/v1/ai/models')

export const uploadTempFile = (payload: FormData) =>
  http.post<never, AiTempFileView>('/v1/ai/temp-files', payload, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })

export const getTempFiles = (sessionId: number) =>
  http.get<never, AiTempFileView[]>('/v1/ai/temp-files', {
    params: { sessionId },
  })

export const deleteTempFile = (id: number) =>
  http.delete<never, void>(`/v1/ai/temp-files/${id}`)

export const getRetrievalSettings = () =>
  http.get<never, AiRetrievalSettingView>('/v1/ai/settings/retrieval')
