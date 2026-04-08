import http from './http'
import type { PageResponse, ProjectDetailView } from '@/types/models'

export const getProjects = (params: { pageNum?: number; pageSize?: number; q?: string; status?: string }) =>
  http.get<never, PageResponse<ProjectDetailView>>('/v1/projects', { params })

export const getProject = (id: number) => http.get<never, ProjectDetailView>(`/v1/projects/${id}`)

export const createProject = (payload: { name: string; description?: string }) =>
  http.post<never, ProjectDetailView>('/v1/projects', payload)

export const updateProject = (id: number, payload: { name: string; description?: string }) =>
  http.put<never, ProjectDetailView>(`/v1/projects/${id}`, payload)

export const archiveProject = (id: number) => http.post(`/v1/projects/${id}/archive`)

export const deleteProject = (id: number) => http.delete(`/v1/projects/${id}`)
