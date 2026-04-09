import http from './http'
import type { ContentItemDetailView, ContentItemSummaryView, PageResponse } from '@/types/models'

export const getContentItems = (params: {
  projectId: number
  itemType?: 'SHEET' | 'BOARD' | 'DATA_TABLE'
  q?: string
  pageNum?: number
  pageSize?: number
}) => http.get<never, PageResponse<ContentItemSummaryView>>('/v1/contents', { params })

export const createContentItem = (payload: {
  projectId: number
  itemType: 'SHEET' | 'BOARD' | 'DATA_TABLE'
  title: string
  summary?: string
  contentJson?: string
  plainText?: string
  coverMetaJson?: string
}) => http.post<never, ContentItemDetailView>('/v1/contents', payload)

export const getContentItem = (id: number) => http.get<never, ContentItemDetailView>(`/v1/contents/${id}`)

export const updateContentItem = (
  id: number,
  payload: { title: string; summary?: string; contentJson: string; plainText: string; coverMetaJson?: string },
) => http.put<never, ContentItemDetailView>(`/v1/contents/${id}`, payload)

export const deleteContentItem = (id: number) => http.delete(`/v1/contents/${id}`)