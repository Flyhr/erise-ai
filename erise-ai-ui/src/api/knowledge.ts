import http from './http'
import type { KnowledgeAssetView, PageResponse } from '@/types/models'

export const getKnowledgeAssets = (params: {
  type?: 'FILE' | 'DOCUMENT'
  projectId?: number
  q?: string
  pageNum?: number
  pageSize?: number
}) => http.get<never, PageResponse<KnowledgeAssetView>>('/v1/knowledge/assets', { params })
