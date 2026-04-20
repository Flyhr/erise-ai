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

export interface AdminSeriesView {
  key: string
  label: string
  points: AdminTrendPointView[]
}

export interface AdminTokenUsageView {
  promptTokens7d: number
  completionTokens7d: number
  totalTokens7d: number
  totalTokens24h: number
  apiCalls24h: number
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
  visitSeries: AdminSeriesView[]
  apiCallSeries: AdminSeriesView[]
  tokenSeries: AdminSeriesView[]
  tokenUsage: AdminTokenUsageView
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

export interface AdminUserUpdatePayload {
  username: string
  displayName: string
  email: string
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

export interface AdminAuditLogQuery {
  pageNum?: number
  pageSize?: number
  q?: string
  operatorUsername?: string
  actionCode?: string
  createdDate?: string
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
  inputPricePerMillion?: number
  outputPricePerMillion?: number
  currencyCode?: string
  priorityNo?: number
  baseUrl?: string
  apiKeyRef?: string
}

export interface ModelConfigUpdatePayload {
  modelName?: string
  providerCode?: string
  enabled?: boolean
  isDefault?: boolean
  supportStream?: boolean
  maxContextTokens?: number | null
  inputPricePerMillion?: number | null
  outputPricePerMillion?: number | null
  currencyCode?: string
  priorityNo?: number | null
  baseUrl?: string
  apiKeyRef?: string
}

export const getAdminOverview = () => http.get<never, AdminOverviewView>('/v1/admin/overview')

export const getAdminDashboard = () => http.get<never, AdminDashboardView>('/v1/admin/dashboard')

export const getAdminUsers = (params: { pageNum?: number; pageSize?: number; q?: string; roleCode?: string }) =>
  http.get<never, PageResponse<AdminUserView>>('/v1/admin/users', { params })

export const updateAdminUserStatus = (id: number, status: string) =>
  http.post(`/v1/admin/users/${id}/status`, { status })

export const updateAdminUser = (id: number, payload: AdminUserUpdatePayload) =>
  http.put<never, AdminUserView>(`/v1/admin/users/${id}`, payload)

export const deleteAdminUser = (id: number) =>
  http.delete(`/v1/admin/users/${id}`)

export const getAdminTasks = (params: { pageNum?: number; pageSize?: number }) =>
  http.get<never, PageResponse<AdminTaskView>>('/v1/admin/tasks', { params })

export const retryAdminTask = (taskOrigin: string, id: number) =>
  http.post(`/v1/admin/tasks/${taskOrigin}/${id}/retry`)

export const getAdminAuditLogs = (params: AdminAuditLogQuery) =>
  http.get<never, PageResponse<AdminAuditLogView>>('/v1/admin/audit-logs', { params })

export interface ModelConfigCreatePayload {
  modelCode: string
  modelName: string
  providerCode: string
  enabled?: boolean
  isDefault?: boolean
  supportStream?: boolean
  maxContextTokens?: number | null
  inputPricePerMillion?: number | null
  outputPricePerMillion?: number | null
  currencyCode?: string
  priorityNo?: number | null
  baseUrl?: string
  apiKeyRef?: string
}

export interface AiPromptTemplateSummaryView {
  templateCode: string
  templateName: string
  scene: string
  latestVersionId: number
  latestVersionNo: number
  enabledVersionId?: number
  enabledVersionNo?: number
  enabled: boolean
  updatedAt: string
}

export interface AiPromptTemplateVersionView {
  id: number
  templateCode: string
  templateName: string
  scene: string
  systemPrompt: string
  userPromptWrapper?: string
  enabled: boolean
  versionNo: number
  createdBy?: string
  createdAt: string
  updatedAt: string
}

export interface AiPromptTemplateQuery {
  pageNum?: number
  pageSize?: number
  q?: string
  scene?: string
  enabledOnly?: boolean
}

export interface AiPromptTemplateCreatePayload {
  templateCode: string
  templateName: string
  scene: string
  systemPrompt: string
  userPromptWrapper?: string
  enabled?: boolean
}

export interface AiPromptTemplateVersionCreatePayload {
  templateName?: string
  scene?: string
  systemPrompt: string
  userPromptWrapper?: string
  enabled?: boolean
}

export interface AiPromptTemplateUpdatePayload {
  templateName: string
  scene: string
  systemPrompt: string
  userPromptWrapper?: string
}

export interface AiRequestLogAdminView {
  id: number
  requestId: string
  sessionId: number
  userId: number
  username?: string
  projectId?: number
  projectName?: string
  providerCode: string
  modelCode: string
  scene: string
  stream: boolean
  inputTokenCount?: number
  outputTokenCount?: number
  totalTokenCount?: number
  latencyMs?: number
  successFlag: boolean
  answerSource?: string
  messageStatus?: string
  errorCode?: string
  errorMessage?: string
  createdAt: string
}

export interface AiRequestLogQuery {
  pageNum?: number
  pageSize?: number
  q?: string
  modelCode?: string
  scene?: string
  successFlag?: boolean
  errorOnly?: boolean
  createdDate?: string
}

export interface AiCostModelBreakdownView {
  modelCode: string
  modelName: string
  currencyCode: string
  requestCount: number
  totalTokens: number
  estimatedCost: number
}

export interface AiCostStatsView {
  windowDays: number
  totalRequests: number
  successRequests: number
  failedRequests: number
  promptTokens: number
  completionTokens: number
  totalTokens: number
  averageLatencyMs: number
  currencyCode: string
  estimatedCost: number
  modelBreakdown: AiCostModelBreakdownView[]
}

export interface AiFeedbackAdminView {
  id: number
  messageId: number
  sessionId: number
  userId: number
  username?: string
  feedbackType: string
  feedbackNote?: string
  answerExcerpt?: string
  projectId?: number
  projectName?: string
  modelCode?: string
  createdAt: string
}

export interface AiFeedbackQuery {
  pageNum?: number
  pageSize?: number
  q?: string
  feedbackType?: string
  createdDate?: string
}

export interface AiIndexTaskAdminView {
  id: number
  taskOrigin: string
  taskType: string
  taskStatus: string
  resourceType?: string
  resourceId?: number
  resourceTitle?: string
  retryCount?: number
  lastError?: string
  retryable: boolean
  createdAt: string
}

export interface AiIndexTaskQuery {
  pageNum?: number
  pageSize?: number
  q?: string
  taskOrigin?: string
  taskStatus?: string
  errorOnly?: boolean
}

export interface AdminAiProviderHealthView {
  role: string
  providerCode: string
  modelCode: string
  baseUrl: string
  configured: boolean
  status: string
  latencyMs?: number
  errorCode?: string
  message?: string
}

export interface AdminAiServiceProviderSummaryView {
  status: string
  routes: AdminAiProviderHealthView[]
}

export interface AdminAiServiceHealthView {
  service: string
  status: string
  database: string
  redis: string
  providers: AdminAiServiceProviderSummaryView
}

export interface AdminAiProviderRecentErrorView {
  errorCode: string
  count: number
  lastSeenAt?: string
}

export interface AdminAiProviderRouteView {
  role: string
  providerCode: string
  modelCode: string
  modelName?: string
  baseUrl: string
  endpointUrl: string
  probeUrl: string
  configured: boolean
  status: string
  timeoutSeconds: number
  source: string
  latencyMs?: number
  errorCode?: string
  message?: string
  isDefault: boolean
  isEffective: boolean
  recentRequestCount24h: number
  recentErrorCount24h: number
  recentErrorCodes: AdminAiProviderRecentErrorView[]
}

export interface AdminAiProviderHealthInventoryView {
  status: string
  generatedAt: string
  defaultProviderCode?: string
  defaultModelCode?: string
  effectiveRoutes: AdminAiProviderRouteView[]
  enabledRoutes: AdminAiProviderRouteView[]
}

export interface AdminAiParserRuntimeView {
  parserCode: string
  label: string
  status: string
  errorCode?: string
  message?: string
  supportedExtensions: string[]
}

export interface AdminAiFileTypeCapabilityView {
  extension: string
  label: string
  primaryParser: string
  fallbackParser: string
  supportsOcr: boolean
  supportsPages: boolean
  parseStatuses: string[]
  retryPolicy: string
  notes: string
}

export interface AdminAiFileCapabilityMatrixView {
  parserOrder: string[]
  parseStatuses: string[]
  parserRuntimes: AdminAiParserRuntimeView[]
  fileTypes: AdminAiFileTypeCapabilityView[]
}

export interface AdminAiN8nErrorMetricView {
  errorCode: string
  count: number
}

export interface AdminAiN8nEventView {
  id: number
  requestId: string
  eventType: string
  workflowHint?: string
  approvalId?: number
  sessionId?: number
  userId?: number
  projectId?: number
  targetUrl?: string
  deliveryStatus: string
  workflowStatus?: string
   workflowName?: string
   workflowVersion?: string
   workflowDomain?: string
   workflowOwner?: string
   externalExecutionId?: string
   workflowErrorSummary?: string
   workflowDurationMs?: number
   deliveryRetryable: boolean
   manualStatus?: string
   manualReason?: string
   manualReplayCount: number
   replayedFromEventId?: number
   lastCallbackAt?: string
  statusCode?: number
  successFlag: boolean
  errorCode?: string
  errorMessage?: string
  attemptCount: number
  maxAttempts: number
  idempotencyKey?: string
   callbackPayloadJson?: string
  createdAt: string
  updatedAt: string
}

export interface AdminAiN8nEventSummaryView {
  windowHours: number
  totalEvents: number
  deliveredCount: number
  failedCount: number
  pendingCount: number
  skippedCount: number
   workflowFailedCount: number
   workflowRunningCount: number
   manualPendingCount: number
   retryableFailedCount: number
  successRate: number
  latestFailure?: AdminAiN8nEventView
  topErrorCodes: AdminAiN8nErrorMetricView[]
}

export interface AdminAiInfrastructureWarningView {
  section: string
  message: string
}

export interface AdminAiInfrastructureOverviewView {
  serviceHealth: AdminAiServiceHealthView
  providers: AdminAiProviderHealthInventoryView
  n8nSummary: AdminAiN8nEventSummaryView
  fileCapabilities: AdminAiFileCapabilityMatrixView
  warnings: AdminAiInfrastructureWarningView[]
}

export interface AdminAiN8nRetryView {
  retried: boolean
  sourceEventId: number
  event: AdminAiN8nEventView
}

export interface AdminAiN8nEventDetailView {
  event: AdminAiN8nEventView
  sourceEvent?: AdminAiN8nEventView
  replayEvents: AdminAiN8nEventView[]
}

export interface AdminAiN8nEventQuery {
  pageNum?: number
  pageSize?: number
  q?: string
  deliveryStatus?: string
  workflowStatus?: string
   manualStatus?: string
  eventType?: string
  createdDate?: string
}

export const getAiModels = () => http.get<never, ModelConfigView[]>('/v1/admin/ai/models')

export const createAiModel = (payload: ModelConfigCreatePayload) =>
  http.post<never, ModelConfigView>('/v1/admin/ai/models', payload)

export const updateAiModel = (id: number, payload: ModelConfigUpdatePayload) =>
  http.put(`/v1/admin/ai/models/${id}`, payload)

export const switchDefaultAiModel = (id: number) =>
  http.post(`/v1/admin/ai/models/${id}/default`)

export const getAiPromptTemplates = (params: AiPromptTemplateQuery) =>
  http.get<never, PageResponse<AiPromptTemplateSummaryView>>('/v1/admin/ai/prompts', { params })

export const getAiPromptTemplateVersions = (templateCode: string) =>
  http.get<never, AiPromptTemplateVersionView[]>(`/v1/admin/ai/prompts/${templateCode}/versions`)

export const createAiPromptTemplate = (payload: AiPromptTemplateCreatePayload) =>
  http.post<never, AiPromptTemplateVersionView>('/v1/admin/ai/prompts', payload)

export const createAiPromptTemplateVersion = (templateCode: string, payload: AiPromptTemplateVersionCreatePayload) =>
  http.post<never, AiPromptTemplateVersionView>(`/v1/admin/ai/prompts/${templateCode}/versions`, payload)

export const updateAiPromptTemplate = (id: number, payload: AiPromptTemplateUpdatePayload) =>
  http.put(`/v1/admin/ai/prompts/${id}`, payload)

export const updateAiPromptTemplateStatus = (id: number, enabled: boolean) =>
  http.post(`/v1/admin/ai/prompts/${id}/status`, { enabled })

export const getAiRequestLogs = (params: AiRequestLogQuery) =>
  http.get<never, PageResponse<AiRequestLogAdminView>>('/v1/admin/ai/request-logs', { params })

export const getAiCostStats = (days = 7) =>
  http.get<never, AiCostStatsView>('/v1/admin/ai/cost-stats', { params: { days } })

export const getAiFeedback = (params: AiFeedbackQuery) =>
  http.get<never, PageResponse<AiFeedbackAdminView>>('/v1/admin/ai/feedback', { params })

export const getAiIndexTasks = (params: AiIndexTaskQuery) =>
  http.get<never, PageResponse<AiIndexTaskAdminView>>('/v1/admin/ai/index-tasks', { params })

export const getAiInfrastructureOverview = (hours = 24) =>
  http.get<never, AdminAiInfrastructureOverviewView>('/v1/admin/ai/infrastructure/overview', {
    params: { hours },
  })

export const getAiInfrastructureN8nEvents = (params: AdminAiN8nEventQuery) =>
  http.get<never, PageResponse<AdminAiN8nEventView>>('/v1/admin/ai/infrastructure/n8n/events', { params })

export const getAiInfrastructureN8nEventDetail = (eventId: number) =>
  http.get<never, AdminAiN8nEventDetailView>(`/v1/admin/ai/infrastructure/n8n/events/${eventId}`)

export const retryAiInfrastructureN8nEvent = (eventId: number) =>
  http.post<never, AdminAiN8nRetryView>(`/v1/admin/ai/infrastructure/n8n/events/${eventId}/retry`)

export const manualHandoffAiInfrastructureN8nEvent = (eventId: number, reason?: string) =>
  http.post<never, AdminAiN8nEventView>(`/v1/admin/ai/infrastructure/n8n/events/${eventId}/manual-handoff`, {
    reason,
  })
