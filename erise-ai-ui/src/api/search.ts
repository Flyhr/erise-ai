import http from './http'
import type { PageResponse, SearchHistoryView, SearchResultView } from '@/types/models'

export const search = (params: { q: string; projectId?: number; pageNum?: number; pageSize?: number }) =>
  http.get<never, PageResponse<SearchResultView>>('/v1/search', { params })

export const suggest = (params: { q: string; projectId?: number }) =>
  http.get<never, string[]>('/v1/search/suggest', { params })

export const getSearchHistory = () => http.get<never, SearchHistoryView[]>('/v1/search/history')
