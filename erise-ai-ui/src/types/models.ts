export interface ApiResponse<T> {
  code: number
  message?: string
  msg?: string
  data: T
}

export interface PageResponse<T> {
  records: T[]
  pageNum: number
  pageSize: number
  total: number
  totalPages: number
}

export interface UserView {
  id: number
  username: string
  displayName: string
  email: string
  roleCode: string
  avatarUrl?: string
  bio?: string
}

export interface AuthTokenResponse {
  accessToken: string
  refreshToken: string
  user: UserView
}

export interface ProjectDetailView {
  id: number
  ownerUserId: number
  name: string
  description?: string
  projectStatus: string
  archived: number
  fileCount: number
  documentCount: number
  createdAt: string
  updatedAt: string
}

export interface FileView {
  id: number
  projectId: number
  fileName: string
  fileExt: string
  mimeType: string
  fileSize: number
  uploadStatus: string
  parseStatus: string
  indexStatus: string
  parseErrorMessage?: string
  createdAt: string
  updatedAt: string
}

export interface EditableOfficeFileView {
  id: number
  projectId: number
  fileName: string
  fileExt: string
  editorType: string
  contentHtmlSnapshot: string
  plainText: string
  updatedAt: string
}

export interface DocumentSummaryView {
  id: number
  projectId: number
  title: string
  summary?: string
  docStatus: string
  latestVersionNo: number
  createdAt: string
  updatedAt: string
}

export interface DocumentDetailView {
  id: number
  projectId: number
  title: string
  summary?: string
  docStatus: string
  latestVersionNo: number
  contentJson: string
  contentHtmlSnapshot: string
  plainText: string
  parseStatus?: string
  indexStatus?: string
  parseErrorMessage?: string
  createdAt: string
  updatedAt: string
}

export interface ContentItemSummaryView {
  id: number
  projectId: number
  itemType: 'SHEET' | 'BOARD' | 'DATA_TABLE'
  title: string
  summary?: string
  updatedAt: string
}

export interface ContentItemDetailView {
  id: number
  projectId: number
  itemType: 'SHEET' | 'BOARD' | 'DATA_TABLE'
  title: string
  summary?: string
  contentJson: string
  plainText: string
  coverMetaJson?: string
  createdAt: string
  updatedAt: string
}

export interface SearchResultView {
  sourceType: string
  sourceId: number
  projectId: number
  title: string
  mimeType: string
  snippet?: string
  updatedAt?: string
  pageNo?: number
  sectionPath?: string
  fileExt?: string
  fileSize?: number
  uploadStatus?: string
  parseStatus?: string
  indexStatus?: string
  docStatus?: string
}

export interface KnowledgeAssetView {
  assetType: 'FILE' | 'DOCUMENT' | 'CONTENT'
  assetId: number
  projectId?: number
  title: string
  summary?: string
  fileExt?: string
  mimeType?: string
  fileSize?: number
  parseStatus?: string
  indexStatus?: string
  parseErrorMessage?: string
  docStatus?: string
  itemType?: 'SHEET' | 'BOARD' | 'DATA_TABLE'
  updatedAt: string
  createdAt?: string
}

export interface WorkspaceRecentItemView {
  assetType: 'FILE' | 'DOCUMENT'
  assetId: number
  projectId?: number
  title: string
  summary?: string
  actionCode: string
  lastActionAt: string
  fileExt?: string
  mimeType?: string
  fileSize?: number
  docStatus?: string
}

export interface AiCitationView {
  sourceType: string
  sourceId: number
  sourceTitle: string
  snippet?: string
  pageNo?: number
  sectionPath?: string
  score?: number
  url?: string
}

export interface AiChatResponse {
  sessionId: number
  messageId: number
  answer: string
  citations: AiCitationView[]
  usedTools: string[]
  confidence?: number
  refusedReason?: string
  requestId?: string
  messageStatus?: string
  modelCode?: string
  providerCode?: string
  latencyMs?: number
}

export interface AiTempFileView {
  id: number
  sessionId: number
  projectId?: number
  fileName: string
  mimeType: string
  sizeBytes: number
  parseStatus: string
  indexStatus: string
  parseErrorMessage?: string
  createdAt: string
}

export interface AiRetrievalSettingView {
  similarityThreshold: number
  topK: number
  webSearchEnabledDefault: boolean
}

export interface AiRetrievalSettingUpdateView {
  updated: boolean
}

export type AiAttachmentType = 'DOCUMENT' | 'FILE'

export interface AiAttachmentPayload {
  attachmentType: AiAttachmentType
  sourceId: number
  projectId?: number
  title?: string
}

export interface AiModelView {
  providerCode: string
  modelCode: string
  modelName: string
  supportStream: boolean
  maxContextTokens?: number
}

export interface AiSessionSummaryView {
  id: number
  projectId?: number
  title: string
  lastMessageAt?: string
  createdAt: string
  tempFileCount?: number
  recentSourceType?: string
}

export interface AiMessageView {
  id: number
  roleCode: string
  content: string
  confidence?: number
  refusedReason?: string
  citations: AiCitationView[]
  createdAt: string
  status?: string
  errorMessage?: string
  requestId?: string
  modelCode?: string
  providerCode?: string
  latencyMs?: number
}

export interface AiSessionDetailView {
  id: number
  projectId?: number
  title: string
  messages: AiMessageView[]
}

export interface SearchHistoryView {
  keyword: string
  projectId?: number
  createdAt: string
}
