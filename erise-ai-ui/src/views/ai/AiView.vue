<template>
  <div class="page-shell ai-admin-page">
    <WorkspaceNavigationShell v-model="searchKeyword" active-nav="ai" brand-title="Erise Ai 知识库"
      brand-subtitle="The Digital Curator" create-text="新建对话"
      :footer-title="selectedProjectDisplay || 'Erise AI 知识库 V1.0'"
      :footer-copy="activeModel?.providerCode || 'Premium Account'"
      :footer-avatar="(selectedProjectDisplay || 'ER').slice(0, 2).toUpperCase()"
      :user-name="activeModel?.modelName || 'AI 助理'" :user-role="sessionStatusText"
      :user-avatar="(activeModel?.modelName || 'A').slice(0, 1)" search-placeholder="搜索项目、知识库、文件或 AI 会话..."
      @create="resetConversation" @navigate-dashboard="router.push('/workspace')"
      @navigate-projects="router.push('/projects')" @navigate-knowledge="router.push('/knowledge')"
      @navigate-ai="router.push('/ai')" @search="openSearch" @notify="showComingSoon('通知中心')"
      @settings="router.push('/settings/profile')" @profile="router.push('/settings/profile')">
      <div class="workspace-shell-card app-card">
        <div class="ai-workspace">
          <aside class="conversation-history">
            <div class="conversation-history__head">
              <div>
                <h3>会话列表</h3>
              </div>
              <button type="button" class="soft-chip" :disabled="sending" @click="resetConversation">新对话</button>
            </div>

            <div class="knowledge-card">
              <div class="knowledge-subtabs">
                <span class="section-eyebrow">知识库文件</span>
              </div>
              <div v-if="selectedAttachments.length" class="temp-file-list">
                <div v-for="attachment in selectedAttachments.filter((item) => item.attachmentType === 'FILE')"
                  :key="attachmentKeyOf(attachment)" class="temp-file-chip">
                  <div class="temp-file-chip__copy">
                    <strong>{{ attachment.title || `文件 #${attachment.sourceId}` }}</strong>
                  </div>
                  <button type="button" class="temp-file-chip__remove" :disabled="sending"
                    @click="removeAttachment(attachment)">×</button>
                </div>
                <button v-if="selectedAttachments.some((item) => item.attachmentType !== 'FILE')" type="button"
                  class="mini-link mini-link--block" @click="showUnavailable('当前只展示文件标签')">
                  还有其它类型资料已附加
                </button>
              </div>
              <div v-else class="knowledge-empty">还没有添加知识库文件。</div>

              <div class="knowledge-temp">
                <div class="knowledge-temp__head">
                  <span class="section-eyebrow">临时文件</span>
                </div>
                <div v-if="tempFiles.length" class="temp-file-list">
                  <div v-for="tempFile in tempFiles" :key="tempFile.id" class="temp-file-chip"
                    :class="tempFileSurfaceClass(tempFile)">
                    <div class="temp-file-chip__copy">
                      <strong>{{ tempFile.fileName }}</strong>
                      <small>{{ tempFileStatusLabel(tempFile) }}</small>
                      <small v-if="tempFile.parseErrorMessage" class="temp-file-chip__error">{{
                        tempFile.parseErrorMessage }}</small>
                      <button v-if="isTempFileFailed(tempFile)" type="button" class="temp-file-chip__retry"
                        :disabled="sending || uploadingTempFile" @click="retryFailedTempFile(tempFile)">
                        重新解析
                      </button>
                    </div>
                    <button type="button" class="temp-file-chip__remove" :disabled="sending || uploadingTempFile"
                      @click="removeTempFile(tempFile)">
                      ×
                    </button>
                  </div>
                </div>
                <div v-else class="knowledge-empty">
                  {{ activeSessionId ? '当前会话还没有临时文件。' : '当前会话还没有临时文件' }}
                </div>
              </div>
            </div>

            <div class="thread-list thread-list--history">
              <div v-for="session in visibleSessions" :key="session.id" class="thread-item"
                :class="{ 'is-active': session.id === activeSessionId, 'is-hovering-delete': hoveredDeleteId === session.id }">
                <button type="button" class="thread-item__main" @click="openSession(session.id)">
                  <div class="thread-item__title">{{ session.title }}</div>
                  <div class="thread-item__meta">
                    <span>{{ relativeTime(session.lastMessageAt || session.createdAt) }}</span>
                  </div>
                </button>
                <button type="button" class="thread-item__delete" :disabled="sending"
                  @mouseenter="onDeleteMouseEnter(session.id)" @mouseleave="onDeleteMouseLeave(session.id)"
                  @click="removeSession(session.id)">×</button>
              </div>
              <div v-if="!visibleSessions.length" class="thread-empty">这里还没有历史会话。发送第一条消息后，会自动生成会话记录。</div>
            </div>
          </aside>

          <section class="chat-stage">
            <div class="chat-stage__header">
              <div>
                <h3>{{ sessionTitleText }}</h3>
              </div>
              <div class="chat-stage__meta">
                <div class="header-model-chip">
                  <span class="material-symbols-outlined">data_object</span>
                  <div class="header-model-chip__copy">
                    <strong>{{ headerModelName }}</strong>
                  </div>
                </div>
                <div class="web-search-toggle">
                  <span class="web-search-toggle__label">联网搜索</span>
                  <el-switch v-model="retrievalSettings.webSearchEnabledDefault" size="small" inline-prompt
                    active-text="开" inactive-text="关" :loading="savingRetrievalSettings"
                    :disabled="sending || savingRetrievalSettings" @change="handleWebSearchToggle" />
                </div>
                <div class="run-chip" :class="{ 'is-live': sending }">
                  <span class="run-chip__dot"></span>
                  <span>{{ thinkingStatusText }}</span>
                </div>
              </div>
            </div>

            <section ref="messageListRef" class="message-board" :class="{ 'is-empty': !messages.length }">
              <div class="message-board__inner">
                <div v-if="networkError" class="status-banner status-banner--error">
                  <span>{{ networkError }}</span>
                  <button type="button" @click="networkError = ''">关闭</button>
                </div>

                <div v-if="messages.length" class="transcript-list transcript-list--modern">
                  <article v-for="message in messages" :key="message.id" class="transcript-item"
                    :class="message.roleCode === 'USER' ? 'is-user' : 'is-assistant'">
                    <div class="transcript-item__avatar">
                      <span v-if="message.roleCode === 'USER'" class="material-symbols-outlined">person</span>
                      <span v-else class="material-symbols-outlined"
                        style="font-variation-settings: 'FILL' 1">smart_toy</span>
                    </div>
                    <div class="transcript-item__panel">
                      <div class="transcript-item__head">
                        <span class="transcript-item__label">{{ message.roleCode === 'USER' ? '' : 'Erise AI' }}</span>
                        <span class="transcript-item__time">{{ formatTime(message.createdAt) }}</span>
                      </div>
                      <div class="transcript-item__body" :class="surfaceClasses(message)">
                        <div
                          v-if="message.roleCode === 'ASSISTANT' && message.status === 'streaming' && !message.content"
                          class="thinking-dots">
                          <span></span><span></span><span></span>
                        </div>
                        <div v-else-if="message.roleCode === 'ASSISTANT'"
                          class="transcript-item__content transcript-item__content--markdown"
                          :class="{ 'is-collapsed': isCollapsed(message) }" v-html="renderAssistantContent(message)">
                        </div>
                        <div v-else class="transcript-item__content" :class="{ 'is-collapsed': isCollapsed(message) }">
                          {{ message.content || '...' }}
                        </div>
                        <div v-if="message.refusedReason" class="transcript-item__notice">{{ message.refusedReason }}
                        </div>
                        <div v-if="message.errorMessage" class="transcript-item__notice">{{ message.errorMessage }}
                        </div>

                        <div v-if="privateCitationGroups(message).length" class="citation-panel citation-panel--modern">
                          <div class="citation-panel__title">引用来源</div>
                          <div class="citation-panel__section">
                            <button v-for="group in privateCitationGroups(message)" :key="group.key" type="button"
                              class="citation-card" @click="openCitation(group.representative)">
                              <div class="citation-card__content">
                                <strong class="citation-card__title">{{ group.title }}</strong>
                                <small class="citation-card__meta">{{ privateCitationMeta(group) }}</small>
                              </div>
                            </button>
                          </div>
                        </div>

                        <div v-if="visibleWebCitationGroups(message).length"
                          class="citation-panel citation-panel--modern">
                          <div class="citation-panel__title">联网搜索</div>
                          <div class="citation-panel__section">
                            <div v-for="group in visibleWebCitationGroups(message)" :key="group.key"
                              class="citation-card citation-card--web">
                              <div class="citation-card__content">
                                <strong class="citation-card__title citation-card__title--single">{{ group.title
                                  }}</strong>
                                <small class="citation-card__meta">{{ group.urlLabel || '网页引用' }}</small>
                              </div>
                              <button type="button" class="citation-card__action"
                                @click="openCitation(group.representative)">
                                网页
                              </button>
                            </div>
                          </div>
                        </div>
                        <button v-if="hiddenWebCitationCount(message)" type="button" class="transcript-item__toggle"
                          @click="toggleCitationExpansion(message)">
                          {{ message.citationsExpanded ? '收起网页引用' : `查看剩余 ${hiddenWebCitationCount(message)} 条网页引用`
                          }}
                        </button>


                        <button
                          v-if="message.roleCode === 'USER' && message.pendingQuestion && message.status === 'failed'"
                          type="button" class="retry-button" :disabled="sending" @click="retryMessage(message)">
                          重新发送
                        </button>
                      </div>
                    </div>
                  </article>
                </div>

                <div v-else class="empty-stage empty-stage--architect">
                  <div class="empty-stage__robot">
                    <span class="material-symbols-outlined" style="font-variation-settings: 'FILL' 1">smart_toy</span>
                  </div>
                  <div class="empty-stage__eyebrow">{{ activeModel?.modelName || 'Erise AI 助理' }}</div>
                  <h3 class="empty-stage__title">今天有什么问题吗?</h3>
                  <div class="empty-stage__prompts">
                    <button v-for="prompt in quickPrompts" :key="prompt" type="button" class="prompt-card"
                      :disabled="sending" @click="usePrompt(prompt)">
                      {{ prompt }}
                    </button>
                  </div>
                </div>
              </div>
            </section>

            <footer class="composer-wrap--architect">
              <div class="composer-box--architect">
                <div class="composer-box__toptools">
                  <button type="button" class="toolbar-ghost" :disabled="sending" @click="openAttachmentPicker">
                    <span class="material-symbols-outlined">attach_file</span>
                    <span>知识库文件</span>
                  </button>
                  <button type="button" class="toolbar-ghost" :disabled="sending || uploadingTempFile"
                    @click="triggerTempFileUpload">
                    <span class="material-symbols-outlined">note_add</span>
                    <span>{{ uploadingTempFile ? '上传中' : '临时文件' }}</span>
                  </button>
                  <button type="button" class="toolbar-ghost" @click="showUnavailable('图片上传')">
                    <span class="material-symbols-outlined">image</span>
                    <span>图片</span>
                  </button>
                  <div class="toolbar-model-picker">
                    <span class="material-symbols-outlined">tune</span>
                    <el-select v-model="selectedModelCode" size="small" class="toolbar-model-select"
                      :disabled="sending || loadingModels || !modelChoices.length" placeholder="选择模型">
                      <el-option v-for="model in modelChoices" :key="model.modelCode" :label="modelOptionLabel(model)"
                        :value="model.modelCode" />
                    </el-select>
                  </div>
                </div>

                <div class="composer-box__content">
                  <textarea ref="composerRef" v-model="question"
                    class="composer-box__input composer-box__input--architect" rows="1" :disabled="sending"
                    :placeholder="composerPlaceholder" @input="resizeComposer" @keydown="handleComposerKeydown" />

                  <div class="composer-box__toolbar">
                    <div class="composer-box__left-tools">
                      <!-- <button type="button" class="toolbar-chip" disabled>{{ modelProviderLabel }}</button> -->
                      <button v-if="selectedProjectDisplay" type="button" class="toolbar-chip" disabled>{{
                        selectedProjectDisplay
                        }}</button>
                      <button v-if="selectedAttachments.length" type="button" class="toolbar-chip" disabled>
                        已附加 {{ selectedAttachments.length }} 份资料
                      </button>
                      <button v-if="tempFiles.length" type="button" class="toolbar-chip" disabled>
                        临时文件 {{ indexedTempFiles.length }}/{{ tempFiles.length }}
                      </button>
                    </div>
                    <div class="composer-box__right-tools">
                      <span class="composer-box__hint">Enter 发送，Shift + Enter 换行</span>
                      <button v-if="sending" type="button" class="send-button is-danger" :disabled="!currentRequestId"
                        @click="stopGeneration">停止生成</button>
                      <button v-else type="button" class="send-button send-button--architect" :disabled="!canSend"
                        @click="send()">
                        <span class="material-symbols-outlined">send</span>
                      </button>
                    </div>
                  </div>
                </div>
              </div>
              <!-- <p class="composer-footnote">
                AI Assistant may provide generated content that still requires your review.
                <button type="button" class="mini-link" @click="showUnavailable('服务条款')">Terms of Service</button>
              </p> -->
            </footer>
          </section>
        </div>
      </div>
    </WorkspaceNavigationShell>

    <el-dialog v-model="attachmentDialogVisible" title="资料托盘" width="760px">
      <div class="attachment-dialog attachment-dialog--modern">
        <div class="attachment-dialog__project">
          <div class="attachment-dialog__label">项目</div>
          <el-select v-model="draftProjectId" class="attachment-dialog__project-select" filterable
            :clearable="!projectLocked" :disabled="projectLocked || loadingAttachmentOptions" placeholder="请选择项目">
            <el-option v-for="project in selectableProjects" :key="project.id" :label="project.name"
              :value="project.id" />
          </el-select>
          <div class="attachment-dialog__hint">先选项目，再勾选要带进本轮对话的资料。当前页优先强调文件，其他资料类型保留原功能。</div>
        </div>

        <div class="attachment-dialog__grid">
          <section class="attachment-panel">
            <div class="attachment-panel__title">文件</div>
            <div v-if="!draftProjectId" class="attachment-panel__empty">请先选择项目。</div>
            <div v-else-if="loadingAttachmentOptions" class="attachment-panel__empty">正在加载文件列表...</div>
            <div v-else-if="draftFiles.length" class="attachment-panel__list">
              <label v-for="file in draftFiles" :key="`file-${file.id}`" class="attachment-option"
                :class="{ 'is-disabled': !canAttachKnowledgeFile(file) }">
                <input type="checkbox" :checked="draftAttachmentSelected('FILE', file.id)"
                  :disabled="!canAttachKnowledgeFile(file)" @change="toggleDraftFileAttachment(file)" />
                <span class="attachment-option__copy">
                  <strong>{{ file.fileName }}</strong>
                  <small>{{ knowledgeFileStatusText(file) }}</small>
                  <small v-if="file.parseErrorMessage" class="attachment-option__error">{{ file.parseErrorMessage
                    }}</small>
                </span>
              </label>
            </div>
            <div v-else class="attachment-panel__empty">当前项目还没有文件。</div>
          </section>

          <section class="attachment-panel attachment-panel--secondary">
            <div class="attachment-panel__title">更多资料</div>
            <div v-if="!draftProjectId" class="attachment-panel__empty">请先选择项目。</div>
            <div v-else-if="loadingAttachmentOptions" class="attachment-panel__empty">正在加载资料...</div>
            <div v-else-if="draftDocuments.length" class="attachment-panel__list">
              <label v-for="document in draftDocuments" :key="`document-${document.id}`" class="attachment-option">
                <input type="checkbox" :checked="draftAttachmentSelected('DOCUMENT', document.id)"
                  @change="toggleDraftAttachment('DOCUMENT', document.id, document.title)" />
                <span class="attachment-option__copy">
                  <strong>{{ document.title }}</strong>
                  <small>{{ document.summary || '暂无摘要' }}</small>
                </span>
              </label>
            </div>
            <div v-else class="attachment-panel__empty">当前项目还没有文档。</div>
          </section>
        </div>
      </div>

      <template #footer>
        <div class="attachment-dialog__footer">
          <button type="button" class="soft-chip" @click="clearAttachmentSelection">清空选择</button>
          <div class="attachment-dialog__footer-actions">
            <button type="button" class="soft-chip" @click="attachmentDialogVisible = false">取消</button>
            <button type="button" class="send-button" @click="applyAttachmentSelection">应用到对话</button>
          </div>
        </div>
      </template>
    </el-dialog>

    <input ref="tempFileInputRef" hidden type="file" accept=".doc,.docx,.pdf,.md,.txt,.csv,.json"
      @change="handleTempFilePicked" />
  </div>
</template>

<script setup lang="ts">
import dayjs from 'dayjs'
import MarkdownIt from 'markdown-it'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import {
  cancelChat,
  chat,
  deleteSession,
  deleteTempFile,
  getModels,
  getRetrievalSettings,
  getSession,
  getSessions,
  getTempFiles,
  retryTempFile,
  updateRetrievalSettings,
  uploadTempFile,
} from '@/api/ai'
import type { AiChatPayload } from '@/api/ai'
import { getDocuments } from '@/api/document'
import { getFiles } from '@/api/file'
import { getProjects } from '@/api/project'
import { resolveApiUrl } from '@/api/http'
import WorkspaceNavigationShell from '@/components/common/WorkspaceNavigationShell.vue'
import { useKnowledgeStatusPolling } from '@/composables/useKnowledgeStatusPolling'
import type {
  AiAttachmentPayload,
  AiChatResponse,
  AiCitationView,
  AiMessageView,
  AiModelView,
  AiRetrievalSettingView,
  AiSessionSummaryView,
  AiTempFileView,
  DocumentSummaryView,
  FileView,
  ProjectDetailView,
} from '@/types/models'
import { knowledgeReadinessLabel, resolveKnowledgeReadiness, sortAiModelsByPreference } from '@/utils/formatters'

type MessageStatus = 'sent' | 'sending' | 'streaming' | 'failed'

interface UiMessage {
  id: string
  serverId?: number
  roleCode: 'USER' | 'ASSISTANT'
  content: string
  createdAt: string
  refusedReason?: string
  status: MessageStatus
  pendingQuestion?: string
  errorMessage?: string
  expanded?: boolean
  citationsExpanded?: boolean
  citations?: AiCitationView[]
  modelCode?: string
  providerCode?: string
  latencyMs?: number
}

interface StreamDonePayload {
  sessionId?: number
  messageId?: number
  latencyMs?: number
}

interface DraftAttachmentOption {
  attachmentType: 'DOCUMENT' | 'FILE'
  sourceId: number
  title: string
}

interface CitationGroup {
  key: string
  sourceType: string
  title: string
  snippet?: string
  representative: AiCitationView
  pageNumbers: number[]
  pageLabel?: string
  urlLabel?: string
}

const DEFAULT_RETRIEVAL_SETTINGS: AiRetrievalSettingView = {
  similarityThreshold: 0.75,
  topK: 5,
  webSearchEnabledDefault: false,
}
/**
 * 显示“敬请期待”弹窗提示。
 * @param feature 要提示的功能名称
 */
const showComingSoon = (feature: string) => {
  ElMessageBox.alert(`${feature} 当前功能还未开发`, '提示', {
    confirmButtonText: '确定',
    type: 'info',
  })
}
const props = defineProps<{ id?: string }>()
const route = useRoute()
const router = useRouter()

/**
 * 将任意值解析为正整数，否则返回 undefined。
 * @param value 要解析的值
 */
const parseNumber = (value: unknown) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
}

const markdownRenderer = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})

const defaultLinkRenderer =
  markdownRenderer.renderer.rules.link_open ||
  ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options))

markdownRenderer.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  token.attrSet('target', '_blank')
  token.attrSet('rel', 'noopener noreferrer')
  return defaultLinkRenderer(tokens, idx, options, env, self)
}

/**
 * 构造本地唯一 ID，用于临时消息等。
 * @param prefix ID 前缀
 */
const buildLocalId = (prefix: string) => `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`

/**
 * 会话附件在 sessionStorage 中的存储 key。
 * @param sessionId 会话 ID
 */
const attachmentStorageKey = (sessionId: number) => `erise-ai-attachments:${sessionId}`

/**
 * 生成附件的唯一键（用于 UI 列表等）。
 * @param attachment 附件对象
 */
const attachmentKeyOf = (attachment: Pick<AiAttachmentPayload, 'attachmentType' | 'sourceId'>) => `${attachment.attachmentType}:${attachment.sourceId}`

/**
 * 将内部引用类型映射为可读标签。
 * @param sourceType 引用类型
 */
const citationSourceLabel = (sourceType?: string) => ({
  DOCUMENT: '文档',
  FILE: '文件',
  TEMP_FILE: '临时文件',
  WEB: '网页',
  SHEET: '表格',
  BOARD: '画板',
  DATA_TABLE: '数据表',
}[sourceType || ''] || sourceType || '引用来源')
/**
 * 将字节大小格式化为易读字符串（B / KB / MB）。
 * @param size 字节数
 */
const formatBytes = (size?: number) => {
  const value = Number(size || 0)
  if (!Number.isFinite(value) || value <= 0) {
    return '0 B'
  }
  if (value < 1024) {
    return `${value} B`
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`
  }
  return `${(value / (1024 * 1024)).toFixed(1)} MB`
}

/**
 * 从后端错误对象中提取友好的错误消息。
 * @param error 后端返回的错误对象
 */
const errorMessageOf = (error: unknown) => {
  const candidate = error as { response?: { data?: { message?: string; msg?: string } }; message?: string }
  return candidate?.response?.data?.message || candidate?.response?.data?.msg || candidate?.message || '请求失败，请稍后重试。'
}

/**
 * 从原始字符串中解析后端可能返回的 JSON 错误消息。
 * @param raw 原始响应文本
 */
const extractErrorMessage = (raw: string) => {
  if (!raw) {
    return ''
  }
  try {
    const parsed = JSON.parse(raw)
    if (typeof parsed?.message === 'string') {
      return parsed.message
    }
    if (typeof parsed?.msg === 'string') {
      return parsed.msg
    }
  } catch {
    return raw.trim()
  }
  return raw.trim()
}

/**
 * 将时间字符串格式化为 `MM-DD HH:mm`，若为空返回 `--`。
 * @param value 时间字符串
 */
const formatTime = (value?: string) => (value ? dayjs(value).format('MM-DD HH:mm') : '--')

/**
 * 返回相对于当前时间的友好描述（几天前/几小时/几分钟/刚刚）。
 * @param value 时间字符串
 */
const relativeTime = (value?: string) => {
  if (!value) {
    return '--'
  }
  const time = dayjs(value)
  const days = dayjs().diff(time, 'day')
  if (days >= 1) {
    return `${days} 天前`
  }
  const hours = dayjs().diff(time, 'hour')
  if (hours >= 1) {
    return `${hours} 小时前`
  }
  const minutes = dayjs().diff(time, 'minute')
  if (minutes >= 1) {
    return `${minutes} 分钟前`
  }
  return '刚刚'
}

const sessions = ref<AiSessionSummaryView[]>([])
const messages = ref<UiMessage[]>([])
const availableModels = ref<AiModelView[]>([])
const selectableProjects = ref<ProjectDetailView[]>([])
const draftDocuments = ref<DocumentSummaryView[]>([])
const draftFiles = ref<FileView[]>([])
const draftAttachmentOptions = ref<DraftAttachmentOption[]>([])
const loadingModels = ref(false)
const loadingAttachmentOptions = ref(false)
const attachmentDialogVisible = ref(false)
const selectedModelCode = ref('')
const currentRequestId = ref('')
const searchKeyword = ref('')
const question = ref('')
const sending = ref(false)
const uploadingTempFile = ref(false)
const savingRetrievalSettings = ref(false)
const activeSessionId = ref<number | undefined>()
const selectedProjectId = ref<number | undefined>()
const draftProjectId = ref<number | undefined>()
const selectedAttachments = ref<AiAttachmentPayload[]>([])
const tempFiles = ref<AiTempFileView[]>([])
const retrievalSettings = ref<AiRetrievalSettingView>({ ...DEFAULT_RETRIEVAL_SETTINGS })
const draftAttachmentKeys = ref<string[]>([])
const networkError = ref('')
const messageListRef = ref<HTMLDivElement | null>(null)
const composerRef = ref<HTMLTextAreaElement | null>(null)
const tempFileInputRef = ref<HTMLInputElement | null>(null)
const sendStartedAt = ref<number>()
const clockNow = ref(Date.now())

const routeProjectId = computed(() => parseNumber(props.id))
const projectLocked = computed(() => Boolean(routeProjectId.value))
const projectLookup = computed(() => new Map(selectableProjects.value.map((project) => [project.id, project] as const)))
const activeProjectId = computed(() => routeProjectId.value || selectedProjectId.value)
const activeProject = computed(() => (activeProjectId.value ? projectLookup.value.get(activeProjectId.value) : undefined))
const selectedProjectDisplay = computed(() => activeProject.value?.name || (activeProjectId.value ? `项目 #${activeProjectId.value}` : ''))
const hasScopedContext = computed(() =>
  Boolean(activeProjectId.value || selectedAttachments.value.length || tempFiles.value.length),
)

const baseAiPath = computed(() => (props.id ? `/projects/${props.id}/ai` : '/ai'))
const visibleSessions = computed(() => {
  if (!projectLocked.value || !routeProjectId.value) {
    return sessions.value
  }
  return sessions.value.filter((session) => session.projectId === routeProjectId.value || session.id === activeSessionId.value)
})
const modelChoices = computed(() => {
  const preferredProviders = new Set(['openai', 'deepseek'])
  const filtered = availableModels.value.filter((model) => preferredProviders.has((model.providerCode || '').toLowerCase()))
  return sortAiModelsByPreference(filtered.length ? filtered : availableModels.value)
})
const activeSessionSummary = computed(() => sessions.value.find((session) => session.id === activeSessionId.value))
const activeModel = computed(() => {
  if (!modelChoices.value.length) {
    return undefined
  }
  return (
    modelChoices.value.find((model) => model.modelCode === selectedModelCode.value) ||
    modelChoices.value.find((model) => (model.providerCode || '').toLowerCase() === 'deepseek')
  )
})
const lastAssistantMessage = computed(() =>
  [...messages.value].reverse().find((message) => message.roleCode === 'ASSISTANT'),
)
const sessionTitleText = computed(() => activeSessionSummary.value?.title || (messages.value.length ? '当前对话' : '开始一段新对话'))
const sessionStatusText = computed(() => {
  if (sending.value) {
    return `正在回复 (${runningSeconds.value}s)`
  }
  if (networkError.value) {
    return '对话暂时中断'
  }
  return messages.value.length ? '对话已就绪' : '等待你的第一条消息'
})
const composerPlaceholder = computed(() =>
  routeProjectId.value
    ? '围绕当前项目继续提问，或先附加文档、文件、临时资料后再发起指令。'
    : '输入你的问题，或先附加项目资料与临时文件。',
)
const runningSeconds = computed(() => {
  if (!sending.value || !sendStartedAt.value) {
    return 0
  }
  return Math.max(1, Math.floor((clockNow.value - sendStartedAt.value) / 1000))
})
const canSend = computed(() => Boolean(question.value.trim()) && !sending.value)
const modelProviderLabel = computed(() => activeModel.value?.providerCode || (loadingModels.value ? '加载中' : '模型服务'))
const headerModelName = computed(() => {
  const modelCode = lastAssistantMessage.value?.modelCode
  if (modelCode) {
    const matched = modelChoices.value.find((model) => model.modelCode === modelCode)
    if (matched?.modelName) {
      return matched.modelName
    }
  }
  return activeModel.value?.modelName || '未选择模型'
})
const headerProviderName = computed(() =>
  lastAssistantMessage.value?.providerCode || activeModel.value?.providerCode || (loadingModels.value ? '加载中' : '模型服务'),
)
const thinkingStatusText = computed(() => {
  if (sending.value) {
    return `思考中 ${runningSeconds.value}s`
  }
  const latencyMs = lastAssistantMessage.value?.latencyMs
  if (latencyMs && latencyMs > 0) {
    return latencyMs >= 1000 ? `思考耗时 ${(latencyMs / 1000).toFixed(latencyMs >= 10000 ? 0 : 1)}s` : `思考耗时 ${latencyMs}ms`
  }
  return sessionStatusText.value
})
// const modelModeLabel = computed(() => (activeModel.value?.supportStream === false ? '普通回复' : '流式回复'))
const quickPrompts = computed(() => [
  selectedAttachments.value.length || indexedTempFiles.value.length
    ? '总结我发送给你的文档、文件和临时资料主要内容'
    : '基于当前项目上下文，帮我梳理下一步工作重点',
  '将发送给你的文档标题改为：“测试ai修改文档功能”',
  '根据这些附件列出重点、风险和待办',
])

let tickHandle: number | undefined
let tempFilePollHandle: number | undefined

const hoveredDeleteId = ref<number | undefined>(undefined)
const onDeleteMouseEnter = (id: number) => {
  hoveredDeleteId.value = id
}
const onDeleteMouseLeave = (id: number) => {
  if (hoveredDeleteId.value === id) {
    hoveredDeleteId.value = undefined
  }
}

const tempFileState = (file: AiTempFileView) => resolveKnowledgeReadiness(file.parseStatus, file.indexStatus)
const isTempFileReady = (file: AiTempFileView) => tempFileState(file) === 'ready'
const isTempFileFailed = (file: AiTempFileView) => tempFileState(file) === 'failed'
const isTempFilePending = (file: AiTempFileView) => ['pending', 'processing'].includes(tempFileState(file))
const indexedTempFiles = computed(() => tempFiles.value.filter((item) => isTempFileReady(item)))

const tempFileStatusLabel = (file: AiTempFileView) => {
  return `${knowledgeReadinessLabel(file.parseStatus, file.indexStatus)} · ${formatBytes(file.sizeBytes)}`
}

const tempFileSurfaceClass = (file: AiTempFileView) => ({
  'is-ready': isTempFileReady(file),
  'is-pending': isTempFilePending(file),
  'is-failed': isTempFileFailed(file),
})

const attachmentFocusedQuestion = (value: string) =>
  /(这个|这份|该|上传的|附加的|发给你的).{0,8}(文档|文件|附件|资料|pdf)|(?:总结|概括|介绍|解释|说明).{0,8}(文档|文件|附件|pdf)|(?:this|the)\s+(?:document|file|attachment|pdf)|(?:uploaded|attached)\s+(?:document|file|pdf)/i.test(value)

const knowledgeFileState = (file: FileView) => resolveKnowledgeReadiness(file.parseStatus, file.indexStatus)

const canAttachKnowledgeFile = (file: FileView) => knowledgeFileState(file) === 'ready'

const knowledgeFileStatusText = (file: FileView) => {
  const fileType = (file.fileExt || 'file').toUpperCase()
  return `${fileType} · ${knowledgeReadinessLabel(file.parseStatus, file.indexStatus)}`
}

const modelOptionLabel = (model: AiModelView) => {
  const provider = (model.providerCode || '').toUpperCase()
  if (provider === 'DEEPSEEK') {
    return `${model.modelName}  `
  }
  if (provider === 'OPENAI') {
    return `${model.modelName} `
  }
  return `${model.modelName} · ${model.providerCode}`
}

/**
 * 将后端消息 AiMessageView 转换为前端 UiMessage。
 * 默认将消息 `expanded` 设为 true，使助理回复默认展开以便阅读完整内容。
 */
const toUiMessage = (message: AiMessageView): UiMessage => ({
  id: String(message.id),
  serverId: message.id,
  roleCode: message.roleCode === 'USER' ? 'USER' : 'ASSISTANT',
  content: message.content,
  createdAt: message.createdAt,
  refusedReason: message.refusedReason,
  status: message.status === 'streaming' ? 'streaming' : 'sent',
  errorMessage: message.errorMessage,
  expanded: true,
  citationsExpanded: false,
  citations: message.citations || [],
  modelCode: message.modelCode,
  providerCode: message.providerCode,
  latencyMs: message.latencyMs,
})

const stripTrailingCitationAppendix = (content: string) => {
  const normalized = (content || '').trimEnd()
  if (!normalized) {
    return ''
  }
  return normalized
    .replace(/(?:\r?\n){2,}(?:#{1,6}\s*)?(?:引用来源|参考网页|参考链接|Sources?|References?)\s*[:：]?\s*[\s\S]*$/i, '')
    .trimEnd()
}

const assistantContentOf = (message: UiMessage) => {
  const cleaned = stripTrailingCitationAppendix(message.content || '')
  return cleaned || message.content || '...'
}

const renderAssistantContent = (message: UiMessage) => markdownRenderer.render(assistantContentOf(message))

const pageLabelFromNumbers = (pageNumbers: number[]) => {
  if (!pageNumbers.length) {
    return ''
  }
  const sorted = [...pageNumbers].sort((left, right) => left - right)
  return `第 ${sorted.join('、')} 页`
}

const urlLabelOf = (url?: string) => {
  if (!url) {
    return '网页引用'
  }
  try {
    return new URL(url).hostname
  } catch {
    return url
  }
}

const citationGroupsOf = (message: UiMessage) => {
  const privateGroups = new Map<string, CitationGroup>()
  const webGroups = new Map<string, CitationGroup>()

  for (const citation of message.citations || []) {
    if (citation.sourceType === 'WEB') {
      const key = citation.url || `WEB:${citation.sourceId}:${citation.sourceTitle}`
      const existing = webGroups.get(key)
      if (existing) {
        if (!existing.snippet && citation.snippet) {
          existing.snippet = citation.snippet
        }
        continue
      }
      webGroups.set(key, {
        key,
        sourceType: citation.sourceType,
        title: citation.sourceTitle || citation.url || '网页引用',
        snippet: citation.snippet,
        representative: citation,
        pageNumbers: [],
        urlLabel: urlLabelOf(citation.url),
      })
      continue
    }

    const key = `${citation.sourceType}:${citation.sourceId}`
    const existing = privateGroups.get(key)
    if (existing) {
      if (citation.pageNo && !existing.pageNumbers.includes(citation.pageNo)) {
        existing.pageNumbers.push(citation.pageNo)
        existing.pageLabel = pageLabelFromNumbers(existing.pageNumbers)
      }
      if (!existing.snippet && citation.snippet) {
        existing.snippet = citation.snippet
      }
      continue
    }
    const pageNumbers = citation.pageNo ? [citation.pageNo] : []
    privateGroups.set(key, {
      key,
      sourceType: citation.sourceType,
      title: citation.sourceTitle || `${citationSourceLabel(citation.sourceType)} #${citation.sourceId}`,
      snippet: citation.snippet,
      representative: citation,
      pageNumbers,
      pageLabel: pageLabelFromNumbers(pageNumbers) || undefined,
    })
  }

  return {
    privateGroups: Array.from(privateGroups.values()),
    webGroups: Array.from(webGroups.values()),
  }
}

const privateCitationGroups = (message: UiMessage) => citationGroupsOf(message).privateGroups
const webCitationGroups = (message: UiMessage) => citationGroupsOf(message).webGroups
const privateCitationMeta = (group: CitationGroup) =>
  [citationSourceLabel(group.sourceType), group.pageLabel].filter(Boolean).join(' · ') || '知识库引用'
const visibleWebCitationGroups = (message: UiMessage) => {
  const groups = webCitationGroups(message)
  return message.citationsExpanded ? groups : groups.slice(0, 1)
}
const hiddenWebCitationCount = (message: UiMessage) => Math.max(webCitationGroups(message).length - 1, 0)
const toggleCitationExpansion = (message: UiMessage) => {
  message.citationsExpanded = !message.citationsExpanded
}

// 判断消息是否足够长，可以折叠显示
const isCollapsible = (message: UiMessage) => {
  const content = message.roleCode === 'ASSISTANT' ? assistantContentOf(message) : message.content
  return content.length > 560 || (content.match(/\n/g)?.length ?? 0) > 12
}
// 判断消息当前是否处于折叠状态（可折叠且未展开）
const isCollapsed = (message: UiMessage) => isCollapsible(message) && !message.expanded
// 切换消息展开/折叠状态（保留接口以备将来使用）
const toggleExpanded = (message: UiMessage) => {
  message.expanded = !message.expanded
}
const surfaceClasses = (message: UiMessage) => ({
  'is-user-surface': message.roleCode === 'USER',
  'is-assistant-surface': message.roleCode === 'ASSISTANT',
  'is-failed': message.status === 'failed',
})
const draftAttachmentSelected = (attachmentType: 'DOCUMENT' | 'FILE', sourceId: number) =>
  draftAttachmentKeys.value.includes(`${attachmentType}:${sourceId}`)

/**
 * 根据输入框内容动态调整 composer 文本域高度，最大高度与 CSS 保持一致（160px）。
 */
const resizeComposer = async () => {
  await nextTick()
  if (!composerRef.value) {
    return
  }
  composerRef.value.style.height = '0px'
  composerRef.value.style.height = `${Math.min(composerRef.value.scrollHeight, 160)}px`
}

/**
 * 处理 composer 的回车按键：按 Enter（非 Shift+Enter）触发发送。
 */
const handleComposerKeydown = (event: KeyboardEvent) => {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) {
    return
  }
  event.preventDefault()
  void send()
}

/**
 * 滚动消息列表到底部，确保最新消息可见。
 */
const scrollToBottom = async () => {
  await nextTick()
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  }
}

/**
 * 启动页面内的时钟，用于显示相对时间/更新时间等实时信息。
 */
const startClock = () => {
  if (tickHandle) {
    window.clearInterval(tickHandle)
  }
  tickHandle = window.setInterval(() => {
    clockNow.value = Date.now()
  }, 1000)
}

/**
 * 停止页面内时钟，清理定时器。
 */
const stopClock = () => {
  if (tickHandle) {
    window.clearInterval(tickHandle)
    tickHandle = undefined
  }
}

/**
 * 停止临时文件解析的轮询定时器（如果存在）。
 */
const stopTempFilePolling = () => {
  if (tempFilePollHandle) {
    window.clearInterval(tempFilePollHandle)
    tempFilePollHandle = undefined
  }
}

/**
 * 启动针对当前会话的临时文件解析状态轮询（每 3 秒一次）。
 * @param sessionId 会话 ID
 */
const startTempFilePolling = (sessionId: number) => {
  stopTempFilePolling()
  tempFilePollHandle = window.setInterval(() => {
    if (activeSessionId.value !== sessionId) {
      stopTempFilePolling()
      return
    }
    void refreshTempFiles(sessionId)
  }, 3000)
}

const persistAttachmentState = (sessionId?: number) => {
  if (!sessionId) {
    return
  }
  window.sessionStorage.setItem(
    attachmentStorageKey(sessionId),
    JSON.stringify({
      projectId: selectedProjectId.value,
      attachments: selectedAttachments.value,
    }),
  )
}

const restoreAttachmentState = (sessionId?: number, fallbackProjectId?: number) => {
  selectedProjectId.value = routeProjectId.value || fallbackProjectId
  selectedAttachments.value = []
  if (!sessionId) {
    return
  }
  const raw = window.sessionStorage.getItem(attachmentStorageKey(sessionId))
  if (!raw) {
    return
  }
  try {
    const parsed = JSON.parse(raw) as { projectId?: number; attachments?: AiAttachmentPayload[] }
    selectedProjectId.value = routeProjectId.value || parsed.projectId || fallbackProjectId
    selectedAttachments.value = Array.isArray(parsed.attachments)
      ? parsed.attachments.filter((item) => item?.sourceId && item?.attachmentType)
      : []
  } catch {
    window.sessionStorage.removeItem(attachmentStorageKey(sessionId))
  }
}

const removeAttachmentState = (sessionId: number) => {
  window.sessionStorage.removeItem(attachmentStorageKey(sessionId))
}

const loadProjects = async (silent = true) => {
  try {
    const page = await getProjects({ pageNum: 1, pageSize: 100 })
    selectableProjects.value = page.records
  } catch (error) {
    if (!silent) {
      ElMessage.error(errorMessageOf(error))
    }
  }
}

const loadRetrievalPreference = async () => {
  try {
    retrievalSettings.value = await getRetrievalSettings()
  } catch {
    retrievalSettings.value = { ...DEFAULT_RETRIEVAL_SETTINGS }
  }
}

const handleWebSearchToggle = async (value: string | number | boolean) => {
  const nextValue = Boolean(value)
  const previousValue = !nextValue
  savingRetrievalSettings.value = true
  try {
    retrievalSettings.value = {
      ...retrievalSettings.value,
      webSearchEnabledDefault: nextValue,
    }
    await updateRetrievalSettings({
      similarityThreshold: retrievalSettings.value.similarityThreshold,
      topK: retrievalSettings.value.topK,
      webSearchEnabledDefault: nextValue,
    })
  } catch (error) {
    retrievalSettings.value = {
      ...retrievalSettings.value,
      webSearchEnabledDefault: previousValue,
    }
    ElMessage.error(errorMessageOf(error))
  } finally {
    savingRetrievalSettings.value = false
  }
}

const refreshTempFiles = async (sessionId = activeSessionId.value, silent = true) => {
  if (!sessionId) {
    tempFiles.value = []
    stopTempFilePolling()
    return
  }

  try {
    const records = await getTempFiles(sessionId)
    tempFiles.value = records
    if (records.some(isTempFilePending)) {
      startTempFilePolling(sessionId)
    } else {
      stopTempFilePolling()
    }
  } catch (error) {
    tempFiles.value = []
    stopTempFilePolling()
    if (!silent) {
      ElMessage.error(errorMessageOf(error))
    }
  }
}

const refreshAttachmentOptions = async (projectId?: number) => {
  draftDocuments.value = []
  draftFiles.value = []
  draftAttachmentOptions.value = []
  if (!projectId) {
    return
  }
  loadingAttachmentOptions.value = true
  try {
    const [documentsPage, filesPage] = await Promise.all([
      getDocuments({ projectId, pageNum: 1, pageSize: 100 }),
      getFiles({ projectId, pageNum: 1, pageSize: 100 }),
    ])
    draftDocuments.value = documentsPage.records
    draftFiles.value = filesPage.records
    draftAttachmentOptions.value = [
      ...documentsPage.records.map((document) => ({
        attachmentType: 'DOCUMENT' as const,
        sourceId: document.id,
        title: document.title,
      })),
      ...filesPage.records.map((file) => ({
        attachmentType: 'FILE' as const,
        sourceId: file.id,
        title: file.fileName,
      })),
    ]
  } catch (error) {
    ElMessage.error(errorMessageOf(error))
  } finally {
    loadingAttachmentOptions.value = false
  }
}

useKnowledgeStatusPolling({
  records: draftFiles,
  reload: async () => {
    await refreshAttachmentOptions(draftProjectId.value)
  },
  enabled: () => attachmentDialogVisible.value && !!draftProjectId.value,
})

const setDraftSelectionFromCurrent = () => {
  draftProjectId.value = activeProjectId.value
  draftAttachmentKeys.value = selectedAttachments.value.map((attachment) => attachmentKeyOf(attachment))
}

const openAttachmentPicker = async () => {
  await loadProjects(false)
  setDraftSelectionFromCurrent()
  await refreshAttachmentOptions(draftProjectId.value)
  attachmentDialogVisible.value = true
}

const toggleDraftAttachment = (attachmentType: 'DOCUMENT' | 'FILE', sourceId: number, title: string) => {
  const key = `${attachmentType}:${sourceId}`
  if (draftAttachmentKeys.value.includes(key)) {
    draftAttachmentKeys.value = draftAttachmentKeys.value.filter((item) => item !== key)
    return
  }
  draftAttachmentKeys.value = [...draftAttachmentKeys.value, key]
  if (!draftAttachmentOptions.value.some((item) => item.attachmentType === attachmentType && item.sourceId === sourceId)) {
    draftAttachmentOptions.value = [...draftAttachmentOptions.value, { attachmentType, sourceId, title }]
  }
}

const toggleDraftFileAttachment = (file: FileView) => {
  if (!canAttachKnowledgeFile(file)) {
    ElMessage.warning(file.parseErrorMessage || '当前文件尚未完成解析，暂时不能加入对话')
    return
  }
  toggleDraftAttachment('FILE', file.id, file.fileName)
}

const clearAttachmentSelection = () => {
  draftAttachmentKeys.value = []
  if (!projectLocked.value) {
    draftProjectId.value = undefined
    draftDocuments.value = []
    draftFiles.value = []
    draftAttachmentOptions.value = []
  }
}

const applyAttachmentSelection = () => {
  selectedProjectId.value = draftProjectId.value
  selectedAttachments.value = draftAttachmentOptions.value
    .filter((item) => draftAttachmentKeys.value.includes(`${item.attachmentType}:${item.sourceId}`))
    .map((item) => ({
      attachmentType: item.attachmentType,
      sourceId: item.sourceId,
      projectId: draftProjectId.value,
      title: item.title,
    }))
  attachmentDialogVisible.value = false
  persistAttachmentState(activeSessionId.value)
}

const removeAttachment = (attachment: AiAttachmentPayload) => {
  selectedAttachments.value = selectedAttachments.value.filter((item) => attachmentKeyOf(item) !== attachmentKeyOf(attachment))
  persistAttachmentState(activeSessionId.value)
}

const ensureTempFileSession = () => {
  if (activeSessionId.value) {
    return true
  }
  ElMessage.info('请先发送一条消息创建会话，再上传临时文件。')
  return false
}

const triggerTempFileUpload = () => {
  if (!ensureTempFileSession() || sending.value || uploadingTempFile.value) {
    return
  }
  tempFileInputRef.value?.click()
}

const handleTempFilePicked = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    return
  }
  if (!ensureTempFileSession()) {
    input.value = ''
    return
  }

  const formData = new FormData()
  formData.append('file', file)
  formData.append('sessionId', String(activeSessionId.value))
  if (activeProjectId.value) {
    formData.append('projectId', String(activeProjectId.value))
  }

  uploadingTempFile.value = true
  try {
    const uploaded = await uploadTempFile(formData)
    tempFiles.value = [uploaded, ...tempFiles.value.filter((item) => item.id !== uploaded.id)]
    if (isTempFilePending(uploaded)) {
      startTempFilePolling(uploaded.sessionId)
    }
    ElMessage.success('临时文件已加入当前会话，正在解析。')
  } catch (error) {
    ElMessage.error(errorMessageOf(error))
  } finally {
    uploadingTempFile.value = false
    input.value = ''
  }
}

const retryFailedTempFile = async (file: AiTempFileView) => {
  try {
    const retried = await retryTempFile(file.id)
    tempFiles.value = [retried, ...tempFiles.value.filter((item) => item.id !== retried.id)]
    if (isTempFilePending(retried)) {
      startTempFilePolling(retried.sessionId)
    }
    ElMessage.success('临时文件已重新进入解析队列。')
  } catch (error) {
    ElMessage.error(errorMessageOf(error))
  }
}

const removeTempFile = async (file: AiTempFileView) => {
  try {
    await ElMessageBox.confirm(`确定从当前会话移除“${file.fileName}”吗？`, '删除临时文件', { type: 'warning' })
    await deleteTempFile(file.id)
    tempFiles.value = tempFiles.value.filter((item) => item.id !== file.id)
    if (activeSessionId.value) {
      await refreshTempFiles(activeSessionId.value)
    }
    ElMessage.success('临时文件已删除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(errorMessageOf(error))
    }
  }
}

const refreshSessions = async () => {
  sessions.value = await getSessions()
}

const refreshModels = async () => {
  loadingModels.value = true
  try {
    const models = sortAiModelsByPreference(await getModels())
    availableModels.value = models
    if (!models.length) {
      selectedModelCode.value = ''
      return
    }
    const deepseekModels = models.filter((model) => (model.providerCode || '').toLowerCase() === 'deepseek')
    const preferredModel = deepseekModels[0]
    if (!models.some((model) => model.modelCode === selectedModelCode.value)) {
      selectedModelCode.value = preferredModel?.modelCode || ''
    }
  } catch (error) {
    availableModels.value = []
    selectedModelCode.value = ''
  } finally {
    loadingModels.value = false
  }
}

const showUnavailable = (feature = '当前功能还未开发') => {
  ElMessageBox.alert('当前功能还未开发', feature, { confirmButtonText: '确定', type: 'info' })
}

const openSearch = async () => {
  const keyword = searchKeyword.value.trim()
  await router.push({
    path: '/search',
    query: keyword ? { q: keyword } : {},
  })
}

const buildAiLocation = (sessionId?: number) => ({
  path: baseAiPath.value,
  query: sessionId ? { sessionId } : {},
})

const syncSessionRoute = async (sessionId?: number) => {
  if (!sessionId) {
    return
  }
  activeSessionId.value = sessionId
  persistAttachmentState(sessionId)
  const target = buildAiLocation(sessionId)
  if (router.currentRoute.value.fullPath !== router.resolve(target).fullPath) {
    await router.replace(target)
  }
}

/**
 * 将后端的 chat 响应应用到对应的本地消息上：更新状态、内容、引用及模型信息，并同步会话与临时文件状态。
 * @param response 后端返回的聊天响应
 * @param userMessage 本地用户消息对象
 * @param assistantMessage 本地助理占位消息对象
 */
const applyChatResponse = async (response: AiChatResponse, userMessage: UiMessage, assistantMessage: UiMessage) => {
  userMessage.status = 'sent'
  assistantMessage.status = 'sent'
  assistantMessage.serverId = response.messageId
  assistantMessage.content = response.answer
  assistantMessage.refusedReason = response.refusedReason
  assistantMessage.citations = response.citations || []
  assistantMessage.modelCode = response.modelCode
  assistantMessage.providerCode = response.providerCode
  assistantMessage.latencyMs = response.latencyMs
  if (response.modelCode && availableModels.value.some((model) => model.modelCode === response.modelCode)) {
    selectedModelCode.value = response.modelCode
  }
  await syncSessionRoute(response.sessionId)
  await refreshTempFiles(response.sessionId)
  await refreshSessions()
}

const syncFromRoute = async () => {
  const sessionId = parseNumber(route.query.sessionId)
  if (sessionId) {
    try {
      const detail = await getSession(sessionId)
      activeSessionId.value = detail.id
      messages.value = detail.messages.map(toUiMessage)
      restoreAttachmentState(detail.id, detail.projectId)
      await refreshTempFiles(detail.id)
      networkError.value = ''
      await scrollToBottom()
      return
    } catch (error) {
      messages.value = []
      activeSessionId.value = undefined
      tempFiles.value = []
      stopTempFilePolling()
      restoreAttachmentState(undefined, routeProjectId.value)
      networkError.value = errorMessageOf(error)
      await router.replace(buildAiLocation())
      return
    }
  }

  activeSessionId.value = undefined
  messages.value = []
  tempFiles.value = []
  stopTempFilePolling()
  restoreAttachmentState(undefined, routeProjectId.value)
  networkError.value = ''
}

const resetConversation = async () => {
  if (sending.value) {
    return
  }
  const preservedProjectId = activeProjectId.value
  activeSessionId.value = undefined
  messages.value = []
  currentRequestId.value = ''
  question.value = ''
  networkError.value = ''
  attachmentDialogVisible.value = false
  selectedAttachments.value = []
  tempFiles.value = []
  selectedProjectId.value = routeProjectId.value || preservedProjectId
  draftProjectId.value = selectedProjectId.value
  draftAttachmentKeys.value = []
  draftAttachmentOptions.value = []
  stopTempFilePolling()
  await router.push(buildAiLocation())
  await resizeComposer()
}

const openSession = async (sessionId: number) => {
  await router.push(buildAiLocation(sessionId))
}

const removeSession = async (sessionId: number) => {
  await ElMessageBox.confirm('删除后无法恢复，确定继续吗？', '删除会话', { type: 'warning' })
  await deleteSession(sessionId)
  removeAttachmentState(sessionId)
  ElMessage.success('会话已删除')
  await refreshSessions()
  if (sessionId === activeSessionId.value) {
    await resetConversation()
  }
}

/**
 * 发起一个流式聊天请求到后端；通过 handlers 回调传出打开、分片和完成事件。
 * @param payload 聊天请求负载
 * @param handlers onOpen/onChunk/onDone 事件回调
 */
const streamChat = async (
  payload: AiChatPayload,
  handlers: { onOpen?: (requestId?: string) => void; onChunk?: (chunk: string) => void; onDone?: (payload: StreamDonePayload) => Promise<void> | void },
) => {
  const token = localStorage.getItem('erise-access-token')
  const response = await fetch(resolveApiUrl('/v1/ai/chat/stream'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(payload),
  })

  if (!response.ok) {
    const text = await response.text()
    throw new Error(extractErrorMessage(text) || `Request failed with status ${response.status}`)
  }

  if (!response.body) {
    throw new Error('AI response stream is unavailable')
  }

  handlers.onOpen?.(response.headers.get('X-Trace-Id') || undefined)

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  const dispatchEvent = async (rawEvent: string) => {
    let eventName = 'message'
    const dataLines: string[] = []

    rawEvent.split('\n').forEach((line) => {
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trimStart())
      }
    })

    const payloadText = dataLines.join('\n')
    if (!payloadText) {
      return
    }

    if (eventName === 'chunk') {
      handlers.onChunk?.(payloadText)
      return
    }
    if (eventName === 'done') {
      try {
        await handlers.onDone?.(JSON.parse(payloadText) as StreamDonePayload)
      } catch {
        await handlers.onDone?.({})
      }
      return
    }
    if (eventName === 'error') {
      throw new Error(payloadText)
    }
  }

  while (true) {
    const { value, done } = await reader.read()
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done }).replace(/\r\n/g, '\n')

    let boundaryIndex = buffer.indexOf('\n\n')
    while (boundaryIndex >= 0) {
      const rawEvent = buffer.slice(0, boundaryIndex)
      buffer = buffer.slice(boundaryIndex + 2)
      if (rawEvent.trim()) {
        await dispatchEvent(rawEvent)
      }
      boundaryIndex = buffer.indexOf('\n\n')
    }

    if (done) {
      break
    }
  }

  if (buffer.trim()) {
    await dispatchEvent(buffer)
  }
}

/**
 * 标记发送失败：更新用户消息和助理消息的状态与错误信息，并显示错误提示。
 * @param userMessage 本地用户消息对象
 * @param assistantMessage 本地助理占位消息对象
 * @param message 错误描述
 * @param originalQuestion 原始问题文本（用于恢复到 pendingQuestion）
 */
const markSendFailed = (userMessage: UiMessage, assistantMessage: UiMessage, message: string, originalQuestion: string) => {
  userMessage.status = 'failed'
  userMessage.pendingQuestion = originalQuestion
  userMessage.errorMessage = message
  assistantMessage.status = 'failed'
  assistantMessage.errorMessage = message
  if (!assistantMessage.content) {
    assistantMessage.content = '这次请求失败了，请稍后重试。'
  }
  networkError.value = message
  ElMessage.error(message)
}

/**
 * 向后端发送取消当前生成的请求（stop / cancel）。
 * 若无进行中的请求则直接返回。
 */
const stopGeneration = async () => {
  if (!currentRequestId.value) {
    return
  }
  try {
    await cancelChat(currentRequestId.value)
    ElMessage.success('已发送停止请求')
  } catch (error) {
    ElMessage.error(errorMessageOf(error))
  }
}

/**
 * 发送用户问题：
 * - 创建本地用户消息与助理占位消息
 * - 调用后端（流式/非流式）处理并更新消息状态
 * @param presetQuestion 可选的预设问题（绕过输入框）
 */
const send = async (presetQuestion?: string) => {
  const raw = presetQuestion ?? question.value
  const text = raw.trim()
  if (!text || sending.value) {
    return
  }

  if (!selectedAttachments.value.length && !indexedTempFiles.value.length && tempFiles.value.some(isTempFilePending) && attachmentFocusedQuestion(text)) {
    const now = new Date().toISOString()
    question.value = ''
    messages.value.push(
      {
        id: buildLocalId('user'),
        roleCode: 'USER',
        content: text,
        createdAt: now,
        status: 'sent',
        pendingQuestion: text,
        expanded: true,
      },
      {
        id: buildLocalId('assistant'),
        roleCode: 'ASSISTANT',
        content: '你刚添加的资料还在解析中，暂时还不能作为可靠上下文。等解析完成后再问“这个文档写了什么”之类的问题，我会优先基于该资料回答。',
        createdAt: now,
        status: 'sent',
        expanded: true,
      },
    )
    await resizeComposer()
    await scrollToBottom()
    return
  }

  question.value = ''
  networkError.value = ''
  await resizeComposer()

  const now = new Date().toISOString()
  const userMessage: UiMessage = {
    id: buildLocalId('user'),
    roleCode: 'USER',
    content: text,
    createdAt: now,
    status: 'sending',
    pendingQuestion: text,
    expanded: true,
  }
  const assistantMessage: UiMessage = {
    id: buildLocalId('assistant'),
    roleCode: 'ASSISTANT',
    content: '',
    createdAt: now,
    status: 'streaming',
    expanded: true,
  }

  messages.value.push(userMessage, assistantMessage)
  await scrollToBottom()

  sending.value = true
  sendStartedAt.value = Date.now()
  startClock()
  currentRequestId.value = ''

  const payload: AiChatPayload = {
    projectId: activeProjectId.value,
    sessionId: activeSessionId.value,
    question: text,
    modelCode: selectedModelCode.value || undefined,
    mode: hasScopedContext.value ? 'SCOPED' : 'GENERAL',
    attachments: selectedAttachments.value.length ? selectedAttachments.value : undefined,
    tempFileIds: tempFiles.value.length ? tempFiles.value.map((item) => item.id) : undefined,
    webSearchEnabled: retrievalSettings.value.webSearchEnabledDefault,
    similarityThreshold: retrievalSettings.value.similarityThreshold,
    topK: retrievalSettings.value.topK,
  }

  const useStream = activeModel.value?.supportStream !== false
  let opened = false
  let chunkReceived = false

  try {
    if (!useStream) {
      const response = await chat(payload)
      await applyChatResponse(response, userMessage, assistantMessage)
      return
    }

    await streamChat(payload, {
      onOpen: (requestId) => {
        opened = true
        userMessage.status = 'sent'
        currentRequestId.value = requestId || ''
      },
      onChunk: (chunk) => {
        opened = true
        chunkReceived = true
        userMessage.status = 'sent'
        assistantMessage.content += chunk
      },
      onDone: async (donePayload) => {
        userMessage.status = 'sent'
        assistantMessage.status = 'sent'
        assistantMessage.serverId = donePayload.messageId
        assistantMessage.latencyMs = donePayload.latencyMs
        await syncSessionRoute(donePayload.sessionId)
        if (donePayload.sessionId) {
          const detail = await getSession(donePayload.sessionId)
          messages.value = detail.messages.map(toUiMessage)
          restoreAttachmentState(detail.id, detail.projectId)
          await refreshTempFiles(donePayload.sessionId)
        }
        await refreshSessions()
      },
    })
  } catch (error) {
    const message = errorMessageOf(error)
    if (/cancel|取消|停止/i.test(message)) {
      userMessage.status = 'sent'
      assistantMessage.status = 'failed'
      assistantMessage.errorMessage = '已停止生成。'
      if (!assistantMessage.content) {
        assistantMessage.content = '已停止生成。'
      }
      networkError.value = ''
    } else if (!opened && !chunkReceived) {
      try {
        const response = await chat(payload)
        await applyChatResponse(response, userMessage, assistantMessage)
      } catch (fallbackError) {
        markSendFailed(userMessage, assistantMessage, errorMessageOf(fallbackError), text)
      }
    } else {
      markSendFailed(userMessage, assistantMessage, message, text)
    }
  } finally {
    currentRequestId.value = ''
    sending.value = false
    sendStartedAt.value = undefined
    stopClock()
    await scrollToBottom()
  }
}

const retryMessage = async (message: UiMessage) => {
  if (!message.pendingQuestion || sending.value) {
    return
  }
  await send(message.pendingQuestion)
}

const usePrompt = async (prompt: string) => {
  await send(prompt)
}

const openCitation = async (citation: AiCitationView) => {
  if (citation.sourceType === 'DOCUMENT') {
    await router.push({ path: `/documents/${citation.sourceId}/edit`, query: { mode: 'preview' } })
    return
  }
  if (citation.sourceType === 'FILE') {
    await router.push(`/files/${citation.sourceId}`)
    return
  }
  if (citation.sourceType === 'WEB') {
    if (citation.url) {
      window.open(citation.url, '_blank', 'noopener,noreferrer')
      return
    }
    ElMessage.info('当前网页引用未提供可打开的链接')
    return
  }
  if (citation.sourceType === 'TEMP_FILE') {
    ElMessage.info('临时文件引用暂不支持直接预览，请在当前会话的临时文件区查看。')
    return
  }
  if (['SHEET', 'BOARD', 'DATA_TABLE'].includes(citation.sourceType)) {
    await router.push({ path: `/contents/${citation.sourceId}/edit`, query: { mode: 'preview' } })
    return
  }
  ElMessage.info('暂不支持打开该引用类型')
}

watch(
  () => route.fullPath,
  async () => {
    await syncFromRoute()
  },
  { immediate: true },
)

watch(
  messages,
  async () => {
    await scrollToBottom()
  },
  { deep: true },
)

watch(question, async () => {
  await resizeComposer()
})

watch(
  () => draftProjectId.value,
  async (projectId, previousProjectId) => {
    if (projectId === previousProjectId) {
      return
    }
    draftAttachmentKeys.value = []
    await refreshAttachmentOptions(projectId)
  },
)

watch(
  () => props.id,
  (value) => {
    const projectId = parseNumber(value)
    if (projectId) {
      selectedProjectId.value = projectId
      draftProjectId.value = projectId
    } else if (!activeSessionId.value) {
      selectedProjectId.value = undefined
      draftProjectId.value = undefined
    }
  },
  { immediate: true },
)

onMounted(async () => {
  await Promise.allSettled([refreshSessions(), refreshModels(), loadProjects(), loadRetrievalPreference()])
  await resizeComposer()
})

onBeforeUnmount(() => {
  stopClock()
  stopTempFilePolling()
})
</script>

<style scoped src="./css/AiView.css"></style>
