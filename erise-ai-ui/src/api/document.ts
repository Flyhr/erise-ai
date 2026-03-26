import http from './http'
import type { DocumentDetailView, DocumentSummaryView, PageResponse } from '@/types/models'

export interface DocumentVersionView {
  versionNo: number
  title: string
  contentJson: string
  contentHtmlSnapshot: string
  plainText: string
  createdAt: string
}

export const getDocuments = (params: { projectId: number; pageNum?: number; pageSize?: number }) =>
  http.get<never, PageResponse<DocumentSummaryView>>('/v1/documents', { params })

export const createDocument = (payload: { projectId: number; title: string; summary?: string }) =>
  http.post<never, DocumentDetailView>('/v1/documents', payload)

export const getDocument = (id: number) => http.get<never, DocumentDetailView>(`/v1/documents/${id}`)

export const updateDocument = (
  id: number,
  payload: { title: string; summary?: string; contentJson: string; contentHtmlSnapshot: string; plainText: string },
) => http.put<never, DocumentDetailView>(`/v1/documents/${id}`, payload)

export const publishDocument = (id: number) =>
  http.post<never, DocumentDetailView>(`/v1/documents/${id}/publish`)

export const getDocumentVersions = (id: number, params?: { pageNum?: number; pageSize?: number }) =>
  http.get<never, PageResponse<DocumentVersionView>>(`/v1/documents/${id}/versions`, { params })

export const getDocumentVersion = (id: number, versionNo: number) =>
  http.get<never, DocumentVersionView>(`/v1/documents/${id}/versions/${versionNo}`)

export const deleteDocument = (id: number) => http.delete(`/v1/documents/${id}`)
