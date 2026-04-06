import http from './http'
import type { PageResponse, WorkspaceRecentItemView } from '@/types/models'

export const getWorkspaceRecent = (params: {
  mode: 'viewed' | 'edited'
  assetType?: 'FILE' | 'DOCUMENT'
  pageNum?: number
  pageSize?: number
}) => http.get<never, PageResponse<WorkspaceRecentItemView>>('/v1/workspace/recent', { params })

export const trackWorkspaceActivity = (payload: {
  assetType: 'FILE' | 'DOCUMENT'
  assetId: number
  actionCode: string
}) => http.post('/v1/workspace/activities/view', payload)
