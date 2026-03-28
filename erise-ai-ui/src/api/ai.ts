import http from './http'
import type { AiAttachmentPayload, AiChatResponse, AiModelView, AiSessionDetailView, AiSessionSummaryView } from '@/types/models'

export interface AiChatPayload {
  projectId?: number
  sessionId?: number
  question: string
  modelCode?: string
  attachments?: AiAttachmentPayload[]
}

export const chat = (payload: AiChatPayload) =>
  http.post<never, AiChatResponse>('/v1/ai/chat', payload)

export const cancelChat = (requestId: string) =>
  http.post<never, void>(`/v1/ai/chat/${requestId}/cancel`)

export const getSessions = () => http.get<never, AiSessionSummaryView[]>('/v1/ai/sessions')

export const getSession = (id: number) => http.get<never, AiSessionDetailView>(`/v1/ai/sessions/${id}`)

export const deleteSession = (id: number) => http.delete(`/v1/ai/sessions/${id}`)

export const getModels = () => http.get<never, AiModelView[]>('/v1/ai/models')