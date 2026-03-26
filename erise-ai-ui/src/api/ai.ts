import http from './http'
import type { AiChatResponse, AiSessionDetailView, AiSessionSummaryView } from '@/types/models'

export const chat = (payload: { projectId: number; sessionId?: number; question: string }) =>
  http.post<never, AiChatResponse>('/v1/ai/chat', payload)

export const getSessions = () => http.get<never, AiSessionSummaryView[]>('/v1/ai/sessions')

export const getSession = (id: number) => http.get<never, AiSessionDetailView>(`/v1/ai/sessions/${id}`)

export const deleteSession = (id: number) => http.delete(`/v1/ai/sessions/${id}`)
