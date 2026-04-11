import http from './http'
import type { PageResponse } from '@/types/models'

export interface AdminOverviewView {
  userCount: number
  projectCount: number
  fileCount: number
  documentCount: number
}

export interface AdminOperationalMetricsView {
  aiSessionCount: number
  searchCount: number
  activeUsersToday: number
  failedLogins24h: number
  downloads24h: number
  aiChats24h: number
}

export interface AdminTrendPointView {
  label: string
  value: number
}

export interface AdminSecurityLogView {
  username?: string
  loginIp?: string
  userAgent?: string
  createdAt: string
}

export interface AdminDownloadLogView {
  operatorUsername?: string
  resourceId?: number
  detailJson?: string
  createdAt: string
}

export interface AdminActionMetricView {
  actionCode: string
  total: number
}

export interface AdminDashboardView {
  overview: AdminOverviewView
  metrics: AdminOperationalMetricsView
  visitTrend: AdminTrendPointView[]
  downloadTrend: AdminTrendPointView[]
  securityLogs: AdminSecurityLogView[]
  downloadLogs: AdminDownloadLogView[]
  topActions: AdminActionMetricView[]
}

export interface AdminUserView {
  id: number
  username: string
  displayName: string
  email: string
  roleCode: string
  status: string
  enabled: number
  createdAt: string
}

export interface AdminTaskView {
  id: number
  taskOrigin: 'FILE_PARSE' | 'RAG' | 'TEMP_FILE_PARSE'
  taskType: string
  taskStatus: string
  resourceType?: string
  resourceId?: number
  resourceTitle?: string
  retryCount: number
  lastError?: string
  retryable: boolean
  createdAt: string
}

export interface AdminAuditLogView {
  id: number
  operatorUsername?: string
  actionCode: string
  resourceType?: string
  resourceId?: number
  detailJson?: string
  createdAt: string
}

export interface ModelConfigView {
  id: number
  modelCode: string
  modelName: string
  providerCode: string
  enabled: boolean
  isDefault: boolean
  supportStream: boolean
  maxContextTokens?: number
  priorityNo?: number
  baseUrl?: string
  apiKeyRef?: string
}

export const getAdminOverview = () => http.get<never, AdminOverviewView>('/v1/admin/overview')

export const getAdminDashboard = () => http.get<never, AdminDashboardView>('/v1/admin/dashboard')

export const getAdminUsers = (params: { pageNum?: number; pageSize?: number }) =>
  http.get<never, PageResponse<AdminUserView>>('/v1/admin/users', { params })

export const updateAdminUserStatus = (id: number, status: string) =>
  http.post(`/v1/admin/users/${id}/status`, { status })

export const getAdminTasks = (params: { pageNum?: number; pageSize?: number }) =>
  http.get<never, PageResponse<AdminTaskView>>('/v1/admin/tasks', { params })

export const retryAdminTask = (taskOrigin: string, id: number) =>
  http.post(`/v1/admin/tasks/${taskOrigin}/${id}/retry`)

export const getAdminAuditLogs = (params: { pageNum?: number; pageSize?: number }) =>
  http.get<never, PageResponse<AdminAuditLogView>>('/v1/admin/audit-logs', { params })

export const getAiModels = () => http.get<never, ModelConfigView[]>('/v1/admin/ai/models')
