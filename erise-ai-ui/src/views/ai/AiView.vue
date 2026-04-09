<template>
  <div class="page-shell ai-admin-page">
    <WorkspaceNavigationShell v-model="searchKeyword" active-nav="ai" brand-title="Erise AI"
      brand-subtitle="The Digital Curator" create-text="新建对话" :footer-title="selectedProjectDisplay || '知识工作台'"
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
              <!-- <div class="knowledge-card__head"> -->
              <!-- <span class="section-eyebrow">Knowledge Base</span> -->
              <!-- <button type="button" class="mini-link" @click="openAttachmentPicker">添加文件</button> -->
              <!-- </div> -->
              <div class="knowledge-subtabs">
                <span class="section-eyebrow">知识库文件</span>

                <!-- <button type="button" class="knowledge-subtabs__item is-active"
                  @click="openAttachmentPicker">添加文件</button> -->
                <!-- <button type="button" class="knowledge-subtabs__item" :disabled="sending || uploadingTempFile"
                  @click="triggerTempFileUpload">
                  {{ uploadingTempFile ? '上传中...' : '上传临时文件' }}
                </button> -->
              </div>
              <div v-if="selectedAttachments.length" class="knowledge-selected">
                <button v-for="attachment in selectedAttachments.filter((item) => item.attachmentType === 'FILE')"
                  :key="attachmentKeyOf(attachment)" type="button" class="selection-chip" :disabled="sending"
                  @click="removeAttachment(attachment)">
                  <span>{{ attachment.title || `文件 #${attachment.sourceId}` }}</span>
                  <span>×</span>
                </button>
                <button v-if="selectedAttachments.some((item) => item.attachmentType !== 'FILE')" type="button"
                  class="mini-link mini-link--block" @click="showUnavailable('当前只展示文件标签')">
                  还有其它类型资料已附加
                </button>
              </div>
              <div v-else class="knowledge-empty">还没有附加知识库文件。</div>

              <div class="knowledge-temp">
                <div class="knowledge-temp__head">
                  <span class="section-eyebrow">临时文件</span>
                  <!-- <span class="section-caption">{{ activeSessionId ? '仅当前会话可见' : '发送首条消息后可上传' }}</span> -->
                </div>
                <div v-if="tempFiles.length" class="temp-file-list">
                  <div v-for="tempFile in tempFiles" :key="tempFile.id" class="temp-file-chip"
                    :class="tempFileSurfaceClass(tempFile)">
                    <div class="temp-file-chip__copy">
                      <strong>{{ tempFile.fileName }}</strong>
                      <small>{{ tempFileStatusLabel(tempFile) }}</small>
                      <small v-if="tempFile.parseErrorMessage" class="temp-file-chip__error">{{ tempFile.parseErrorMessage }}</small>
                      <button
                        v-if="isTempFileFailed(tempFile)"
                        type="button"
                        class="temp-file-chip__retry"
                        :disabled="sending || uploadingTempFile"
                        @click="retryFailedTempFile(tempFile)"
                      >
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
                :class="{ 'is-active': session.id === activeSessionId }">
                <button type="button" class="thread-item__main" @click="openSession(session.id)">
                  <div class="thread-item__title">{{ session.title }}</div>
                  <div class="thread-item__meta">
                    <span>{{ relativeTime(session.lastMessageAt || session.createdAt) }}</span>
                  </div>
                </button>
                <button type="button" class="thread-item__delete" :disabled="sending"
                  @click="removeSession(session.id)">×</button>
              </div>
              <div v-if="!visibleSessions.length" class="thread-empty">这里还没有历史会话。发送第一条消息后，会自动生成会话记录。</div>
            </div>
          </aside>

          <section class="chat-stage">
            <div class="chat-stage__header">
              <div>
                <p class="section-eyebrow">当前会话</p>
                <h2>{{ sessionTitleText }}</h2>
              </div>
              <div class="chat-stage__meta">
                <div class="header-model-chip">
                  <span class="material-symbols-outlined">data_object</span>
                  <div class="header-model-chip__copy">
                    <strong>{{ headerModelName }}</strong>
                    <small>{{ headerProviderName }}</small>
                  </div>
                </div>
                <div class="web-search-toggle">
                  <span class="web-search-toggle__label">联网搜索</span>
                  <el-switch
                    v-model="retrievalSettings.webSearchEnabledDefault"
                    size="small"
                    inline-prompt
                    active-text="开"
                    inactive-text="关"
                    :loading="savingRetrievalSettings"
                    :disabled="sending || savingRetrievalSettings"
                    @change="handleWebSearchToggle"
                  />
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
                        <span class="transcript-item__label">{{ message.roleCode === 'USER' ? '你' : 'Erise AI' }}</span>
                        <span class="transcript-item__time">{{ formatTime(message.createdAt) }}</span>
                      </div>
                      <div class="transcript-item__body" :class="surfaceClasses(message)">
                        <div
                          v-if="message.roleCode === 'ASSISTANT' && message.status === 'streaming' && !message.content"
                          class="thinking-dots">
                          <span></span><span></span><span></span>
                        </div>
                        <div
                          v-else-if="message.roleCode === 'ASSISTANT'"
                          class="transcript-item__content transcript-item__content--markdown"
                          :class="{ 'is-collapsed': isCollapsed(message) }"
                          v-html="renderAssistantContent(message.content || '...')">
                        </div>
                        <div v-else class="transcript-item__content" :class="{ 'is-collapsed': isCollapsed(message) }">
                          {{ message.content || '...' }}
                        </div>
                        <div v-if="message.refusedReason" class="transcript-item__notice">{{ message.refusedReason }}
                        </div>
                        <div v-if="message.errorMessage" class="transcript-item__notice">{{ message.errorMessage }}
                        </div>

                        <div v-if="message.citations?.length" class="citation-panel citation-panel--modern">
                          <div class="citation-panel__title">引用来源</div>
                          <div v-if="privateCitationGroups(message).length" class="citation-panel__section">
                            <div class="citation-panel__section-title">知识库 / 附件</div>
                            <button
                              v-for="group in privateCitationGroups(message)"
                              :key="group.key"
                              type="button"
                              class="citation-card"
                              @click="openCitation(group.representative)">
                              <strong class="citation-card__title">{{ group.title }}</strong>
                              <span>
                                {{ citationSourceLabel(group.sourceType) }}
                                <template v-if="group.pageLabel"> · {{ group.pageLabel }}</template>
                              </span>
                              <small>{{ group.snippet || '暂无引用摘录' }}</small>
                            </button>
                          </div>
                          <div v-if="visibleWebCitationGroups(message).length" class="citation-panel__section">
                            <div class="citation-panel__section-title">联网搜索</div>
                            <button
                              v-for="group in visibleWebCitationGroups(message)"
                              :key="group.key"
                              type="button"
                              class="citation-card citation-card--web"
                              @click="openCitation(group.representative)">
                              <strong class="citation-card__title citation-card__title--single">{{ group.title }}</strong>
                              <span>{{ group.urlLabel }}</span>
                              <small>{{ group.snippet || '打开网页引用' }}</small>
                            </button>
                            <button
                              v-if="hiddenWebCitationCount(message)"
                              type="button"
                              class="citation-panel__toggle"
                              @click="toggleCitationExpansion(message)">
                              {{ message.citationsExpanded ? '收起网页引用' : `展开剩余 ${hiddenWebCitationCount(message)} 条网页引用` }}
                            </button>
                          </div>
                        </div>

                        <button v-if="isCollapsible(message)" type="button" class="transcript-item__toggle"
                          @click="toggleExpanded(message)">
                          {{ message.expanded ? '收起' : '展开全部' }}
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
                  <h2 class="empty-stage__title">Ask the digital curator anything.</h2>
                  <p class="empty-stage__copy">先附加文件，再让 AI 帮你总结内容、提炼风险、列出待办，或者直接围绕当前项目继续分析。</p>
                  <div class="empty-stage__prompts">
                    <button v-for="prompt in quickPrompts" :key="prompt" type="button" class="prompt-card"
                      :disabled="sending" @click="usePrompt(prompt)">
                      {{ prompt }}
                    </button>
                  </div>
                </div>
              </div>
            </section>

            <footer class="composer-wrap composer-wrap--architect">
              <div class="composer-box composer-box--architect">
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
                      <el-option v-for="model in modelChoices" :key="model.modelCode"
                        :label="modelOptionLabel(model)" :value="model.modelCode" />
                    </el-select>
                  </div>
                </div>

                <div class="composer-box__content">
                  <textarea ref="composerRef" v-model="question"
                    class="composer-box__input composer-box__input--architect" rows="1" :disabled="sending"
                    :placeholder="composerPlaceholder" @input="resizeComposer" @keydown="handleComposerKeydown" />

                  <div class="composer-box__toolbar">
                    <div class="composer-box__left-tools">
                      <button type="button" class="toolbar-chip" disabled>{{ modelProviderLabel }}</button>
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
              <p class="composer-footnote">
                AI Assistant may provide generated content that still requires your review.
                <button type="button" class="mini-link" @click="showUnavailable('服务条款')">Terms of Service</button>
              </p>
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
                  :disabled="!canAttachKnowledgeFile(file)"
                  @change="toggleDraftFileAttachment(file)" />
                <span class="attachment-option__copy">
                  <strong>{{ file.fileName }}</strong>
                  <small>{{ knowledgeFileStatusText(file) }}</small>
                  <small v-if="file.parseErrorMessage" class="attachment-option__error">{{ file.parseErrorMessage }}</small>
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
const showComingSoon = (feature: string) => {
  ElMessageBox.alert(`${feature} 当前功能还未开发`, '提示', {
    confirmButtonText: '确定',
    type: 'info',
  })
}
const props = defineProps<{ id?: string }>()
const route = useRoute()
const router = useRouter()

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

const buildLocalId = (prefix: string) => `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`
const attachmentStorageKey = (sessionId: number) => `erise-ai-attachments:${sessionId}`
const attachmentKeyOf = (attachment: Pick<AiAttachmentPayload, 'attachmentType' | 'sourceId'>) => `${attachment.attachmentType}:${attachment.sourceId}`
const citationSourceLabel = (sourceType?: string) => ({
  DOCUMENT: '文档',
  FILE: '文件',
  TEMP_FILE: '临时文件',
  WEB: '网页',
  SHEET: '表格',
  BOARD: '画板',
  DATA_TABLE: '数据表',
}[sourceType || ''] || sourceType || '引用来源')
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

const errorMessageOf = (error: unknown) => {
  const candidate = error as { response?: { data?: { message?: string; msg?: string } }; message?: string }
  return candidate?.response?.data?.message || candidate?.response?.data?.msg || candidate?.message || '请求失败，请稍后重试。'
}

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

const formatTime = (value?: string) => (value ? dayjs(value).format('MM-DD HH:mm') : '--')
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
    return `AI 正在回复 (${runningSeconds.value}s)`
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
    return `${model.modelName} · DeepSeek 默认推荐`
  }
  if (provider === 'OPENAI') {
    return `${model.modelName} · OpenAI 备用可选`
  }
  return `${model.modelName} · ${model.providerCode}`
}

const toUiMessage = (message: AiMessageView): UiMessage => ({
  id: String(message.id),
  serverId: message.id,
  roleCode: message.roleCode === 'USER' ? 'USER' : 'ASSISTANT',
  content: message.content,
  createdAt: message.createdAt,
  refusedReason: message.refusedReason,
  status: message.status === 'streaming' ? 'streaming' : 'sent',
  errorMessage: message.errorMessage,
  expanded: false,
  citationsExpanded: false,
  citations: message.citations || [],
  modelCode: message.modelCode,
  providerCode: message.providerCode,
  latencyMs: message.latencyMs,
})

const renderAssistantContent = (content: string) => markdownRenderer.render(content || '...')

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
const visibleWebCitationGroups = (message: UiMessage) => {
  const groups = webCitationGroups(message)
  return message.citationsExpanded ? groups : groups.slice(0, 2)
}
const hiddenWebCitationCount = (message: UiMessage) => Math.max(webCitationGroups(message).length - 2, 0)
const toggleCitationExpansion = (message: UiMessage) => {
  message.citationsExpanded = !message.citationsExpanded
}

const isCollapsible = (message: UiMessage) => message.content.length > 560 || (message.content.match(/\n/g)?.length ?? 0) > 12
const isCollapsed = (message: UiMessage) => isCollapsible(message) && !message.expanded
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

const resizeComposer = async () => {
  await nextTick()
  if (!composerRef.value) {
    return
  }
  composerRef.value.style.height = '0px'
  composerRef.value.style.height = `${Math.min(composerRef.value.scrollHeight, 220)}px`
}

const handleComposerKeydown = (event: KeyboardEvent) => {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) {
    return
  }
  event.preventDefault()
  void send()
}

const scrollToBottom = async () => {
  await nextTick()
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  }
}

const startClock = () => {
  if (tickHandle) {
    window.clearInterval(tickHandle)
  }
  tickHandle = window.setInterval(() => {
    clockNow.value = Date.now()
  }, 1000)
}

const stopClock = () => {
  if (tickHandle) {
    window.clearInterval(tickHandle)
    tickHandle = undefined
  }
}

const stopTempFilePolling = () => {
  if (tempFilePollHandle) {
    window.clearInterval(tempFilePollHandle)
    tempFilePollHandle = undefined
  }
}

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
        expanded: false,
      },
      {
        id: buildLocalId('assistant'),
        roleCode: 'ASSISTANT',
        content: '你刚添加的资料还在解析中，暂时还不能作为可靠上下文。等解析完成后再问“这个文档写了什么”之类的问题，我会优先基于该资料回答。',
        createdAt: now,
        status: 'sent',
        expanded: false,
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
    expanded: false,
  }
  const assistantMessage: UiMessage = {
    id: buildLocalId('assistant'),
    roleCode: 'ASSISTANT',
    content: '',
    createdAt: now,
    status: 'streaming',
    expanded: false,
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

<style scoped>
.workspace-shell-card {
  height: calc(128dvh - 148px);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(192, 199, 212, 0.5);
  background: #f8f9ff;
  box-shadow: 0 24px 64px rgba(0, 96, 169, 0.08);
}

.ai-admin-page {
  min-height: calc(100dvh - 120px);
}

.architect-shell {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  min-height: calc(100dvh - 148px);
  overflow: hidden;
  padding: 0;
  border: 1px solid rgba(192, 199, 212, 0.5);
  background: #f8f9ff;
  box-shadow: 0 24px 64px rgba(0, 96, 169, 0.08);
}

.architect-nav {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 24px 18px;
  background: #f1f3fa;
  border-right: 1px solid rgba(192, 199, 212, 0.5);
}

.architect-brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.architect-brand__icon,
.top-user__avatar,
.architect-account__avatar,
.empty-stage__robot {
  display: grid;
  place-items: center;
  color: #fff;
  font-weight: 800;
  background: linear-gradient(135deg, #409eff, #0060a9);
}

.architect-brand__icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
}

.architect-brand h1 {
  margin: 0;
  font-size: 18px;
  line-height: 1.1;
  font-weight: 800;
  color: #181c20;
}

.architect-brand p,
.architect-account__copy span,
.top-user__meta span,
.chat-stage__header p,
.knowledge-empty,
.composer-footnote,
.thread-empty {
  margin: 0;
  color: #5f6775;
}

.architect-brand p {
  font-size: 11px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.nav-primary-btn,
.architect-menu__item,
.architect-account,
.top-search,
.top-icon-btn,
.top-user,
.model-chip,
.toolbar-ghost,
.mini-link,
.selection-chip,
.soft-chip,
.send-button,
.thread-item__delete,
.thread-item__main,
.citation-card,
.prompt-card,
.retry-button,
.transcript-item__toggle {
  border: 0;
  font: inherit;
  cursor: pointer;
}

.nav-primary-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 12px 16px;
  border-radius: 14px;
  color: #fff;
  background: linear-gradient(135deg, #409eff, #0060a9);
  font-weight: 700;
  box-shadow: 0 16px 32px rgba(0, 96, 169, 0.18);
}

.architect-menu {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.architect-menu__item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 12px 14px;
  border-radius: 12px;
  background: transparent;
  color: #404752;
  text-align: left;
  transition: all 0.2s ease;
}

.architect-menu__item:hover {
  background: rgba(255, 255, 255, 0.72);
  color: #181c20;
}

.architect-menu__item.is-active {
  color: #0060a9;
  background: rgba(64, 158, 255, 0.1);
  box-shadow: inset 3px 0 0 #0060a9;
  font-weight: 700;
}

.architect-account {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: auto;
  padding: 14px 12px 0;
  border-top: 1px solid rgba(192, 199, 212, 0.5);
  background: transparent;
}

.architect-account__avatar,
.top-user__avatar {
  width: 38px;
  height: 38px;
  border-radius: 999px;
}

.architect-account__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.architect-account__copy strong,
.top-user__meta strong {
  color: #181c20;
  font-size: 13px;
}

.architect-main {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
}

.architect-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 18px 22px;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(192, 199, 212, 0.45);
}

.top-search {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  width: min(460px, 100%);
  padding: 12px 16px;
  border-radius: 999px;
  background: #f1f3fa;
  color: #5f6775;
  text-align: left;
}

.architect-topbar__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.top-icon-btn {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 999px;
  background: transparent;
  color: #5f6775;
}

.top-icon-btn:hover,
.top-search:hover,
.top-user:hover,
.toolbar-ghost:hover,
.soft-chip:hover,
.selection-chip:hover,
.model-chip:hover,
.temp-file-chip__remove:hover {
  background: #e6e8ef;
}

.top-user {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px;
  border-radius: 999px;
  background: transparent;
}

.top-user__meta {
  display: flex;
  flex-direction: column;
  text-align: right;
}

.ai-workspace {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.conversation-history {
  display: flex;
  height: 100%;
  flex-direction: column;
  gap: 16px;
  padding: 20px;
  background: #f1f3fa;
  border-right: 1px solid rgba(192, 199, 212, 0.45);
  overflow: hidden;
}

.conversation-history__head,
.knowledge-card__head,
.chat-stage__header,
.chat-stage__meta,
.composer-box__toptools,
.status-banner,
.attachment-dialog__footer,
.attachment-dialog__footer-actions,
.transcript-item__head,
.composer-box__toolbar,
.composer-box__left-tools,
.composer-box__right-tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.section-eyebrow,
.transcript-item__label,
.citation-panel__title {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #5f6775;
}

.section-caption {
  color: #7a8392;
  font-size: 12px;
}

.conversation-history__head h3,
.chat-stage__header h2 {
  margin: 4px 0 0;
  font-size: 22px;
  line-height: 1.2;
  font-weight: 800;
  color: #181c20;
}

.knowledge-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  border-radius: 18px;
  background: #ffffff;
  border: 1px solid rgba(192, 199, 212, 0.45);
}

.knowledge-subtabs {
  display: flex;
  gap: 10px;
}


.knowledge-subtabs__item,
.mini-link {
  padding: 0;
  background: transparent;
  color: #0060a9;
  font-weight: 700;
}

.knowledge-subtabs__item.is-active {
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(233, 233, 234, 0.6);
}

.knowledge-subtabs__item:hover {

  background: rgba(213, 213, 213, 0.6);
}

.knowledge-selected {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.knowledge-temp {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.knowledge-temp__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.temp-file-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.temp-file-chip {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid rgba(192, 199, 212, 0.45);
  background: #fff;
}

.temp-file-chip.is-ready {
  border-color: rgba(40, 108, 0, 0.16);
  background: rgba(85, 175, 40, 0.08);
}

.temp-file-chip.is-pending {
  border-color: rgba(0, 96, 169, 0.14);
  background: rgba(64, 158, 255, 0.08);
}

.temp-file-chip.is-failed {
  border-color: rgba(186, 26, 26, 0.16);
  background: rgba(217, 107, 107, 0.08);
}

.temp-file-chip__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.temp-file-chip__copy strong {
  color: #181c20;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.temp-file-chip__copy small {
  color: #5f6775;
}

.temp-file-chip__error {
  color: #ba1a1a !important;
}

.temp-file-chip__retry {
  width: fit-content;
  padding: 0;
  border: 0;
  background: transparent;
  color: #005ea6;
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
}

.temp-file-chip__remove {
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.78);
  color: #5f6775;
  cursor: pointer;
}

.mini-link--block {
  text-align: left;
}

.thread-list--history,
.thread-list--drawer {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  gap: 10px;
  overflow-y: auto;
  padding-right: 4px;
}

.thread-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  padding: 4px;
  border-radius: 16px;
  background: transparent;
  transition: background 0.2s ease;
}

.thread-item.is-active {
  background: #fff;
  box-shadow: 0 10px 24px rgba(0, 96, 169, 0.08);
}

.thread-item__main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  padding: 12px;
  border-radius: 12px;
  text-align: left;
  background: transparent;
}

.thread-item__title {
  color: #181c20;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.thread-item__meta,
.transcript-item__time,
.transcript-item__notice,
.attachment-option__copy small,
.citation-card span,
.citation-card small,
.composer-box__hint {
  color: #5f6775;
}

.attachment-option__error {
  color: #ba1a1a;
}

.thread-item__delete {
  width: 34px;
  height: 34px;
  align-self: center;
  border-radius: 999px;
  background: transparent;
  color: #5f6775;
}

.chat-stage {
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  position: relative;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(248, 249, 255, 0.96));
}

.chat-stage__header {
  padding: 22px 24px 16px;
  border-bottom: 1px solid rgba(192, 199, 212, 0.45);
}

.chat-stage__meta {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.header-model-chip,
.web-search-toggle {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 14px;
  background: #f1f3fa;
  color: #404752;
}

.header-model-chip {
  max-width: min(320px, 100%);
}

.header-model-chip__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.header-model-chip__copy strong,
.header-model-chip__copy small {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.header-model-chip__copy strong {
  color: #181c20;
}

.web-search-toggle__label {
  font-size: 13px;
  font-weight: 700;
}

.run-chip,
.model-chip,
.toolbar-chip,
.selection-chip,
.soft-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 999px;
  background: #e6e8ef;
  color: #404752;
}

.run-chip.is-live {
  background: rgba(64, 158, 255, 0.14);
  color: #0060a9;
}

.run-chip__dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: currentColor;
}

.message-board {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding: 0 24px 16px;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
  scroll-padding-bottom: 220px;
}

.message-board__inner {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  padding-bottom: 8px;
}

.transcript-list--modern {
  display: flex;
  flex-direction: column;
  gap: 24px;
  max-width: 980px;
  width: 100%;
  margin: 0 auto;
}

.transcript-item {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  gap: 14px;
}

.transcript-item.is-user {
  grid-template-columns: minmax(0, 1fr) 44px;
}

.transcript-item.is-user .transcript-item__avatar {
  order: 2;
}

.transcript-item.is-user .transcript-item__panel {
  order: 1;
}

.transcript-item__avatar {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 14px;
  background: rgba(85, 175, 40, 0.12);
  color: #286c00;
}

.transcript-item.is-user .transcript-item__avatar {
  border-radius: 999px;
  background: rgba(64, 158, 255, 0.12);
  color: #0060a9;
}

.transcript-item__panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.transcript-item.is-user .transcript-item__head {
  justify-content: flex-end;
}

.transcript-item__body {
  padding: 18px;
  border-radius: 20px;
  background: #f1f3fa;
  color: #181c20;
  box-shadow: 0 10px 28px rgba(0, 96, 169, 0.05);
}

.transcript-item__content {
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

.transcript-item__content.is-collapsed {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 12;
}

.transcript-item__content--markdown {
  white-space: normal;
}

.transcript-item__content--markdown :deep(p),
.transcript-item__content--markdown :deep(ul),
.transcript-item__content--markdown :deep(ol),
.transcript-item__content--markdown :deep(blockquote),
.transcript-item__content--markdown :deep(pre) {
  margin: 0 0 12px;
}

.transcript-item__content--markdown :deep(p:last-child),
.transcript-item__content--markdown :deep(ul:last-child),
.transcript-item__content--markdown :deep(ol:last-child),
.transcript-item__content--markdown :deep(blockquote:last-child),
.transcript-item__content--markdown :deep(pre:last-child) {
  margin-bottom: 0;
}

.transcript-item__content--markdown :deep(h1),
.transcript-item__content--markdown :deep(h2),
.transcript-item__content--markdown :deep(h3),
.transcript-item__content--markdown :deep(h4) {
  margin: 18px 0 10px;
  color: #181c20;
  line-height: 1.35;
}

.transcript-item__content--markdown :deep(ul),
.transcript-item__content--markdown :deep(ol) {
  padding-left: 20px;
}

.transcript-item__content--markdown :deep(li + li) {
  margin-top: 6px;
}

.transcript-item__content--markdown :deep(code) {
  padding: 2px 6px;
  border-radius: 8px;
  background: rgba(24, 28, 32, 0.08);
  font-size: 12px;
}

.transcript-item__content--markdown :deep(pre) {
  padding: 14px;
  overflow-x: auto;
  border-radius: 14px;
  background: rgba(24, 28, 32, 0.92);
  color: #f5f7fa;
}

.transcript-item__content--markdown :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
}

.transcript-item__content--markdown :deep(a) {
  color: #0060a9;
  text-decoration: none;
}

.transcript-item__content--markdown :deep(a:hover) {
  text-decoration: underline;
}

.transcript-item.is-user .transcript-item__body {
  background: rgba(64, 158, 255, 0.08);
  border: 1px solid rgba(64, 158, 255, 0.18);
}

.empty-stage--architect {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 40px 16px 24px;
  text-align: center;
}

.empty-stage__robot {
  width: 72px;
  height: 72px;
  border-radius: 24px;
  font-size: 30px;
  box-shadow: 0 20px 40px rgba(0, 96, 169, 0.18);
}

.empty-stage__title {
  margin: 0;
  font-size: 34px;
  line-height: 1.1;
  font-weight: 800;
  color: #181c20;
}

.empty-stage__copy {
  max-width: 620px;
}

.empty-stage__prompts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  width: 100%;
  max-width: 980px;
}

.prompt-card {
  padding: 18px;
  border-radius: 18px;
  background: #fff;
  border: 1px solid rgba(192, 199, 212, 0.45);
  color: #181c20;
  text-align: left;
  box-shadow: 0 14px 30px rgba(0, 96, 169, 0.05);
}

.prompt-card:hover,
.citation-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 18px 32px rgba(0, 96, 169, 0.08);
}

.composer-wrap--architect {
  flex-shrink: 0;
  margin-top: auto;
  position: sticky;
  bottom: 0;
  z-index: 4;
  padding: 18px 24px 24px;
  border-top: 1px solid rgba(192, 199, 212, 0.28);
  background: linear-gradient(180deg, rgba(248, 249, 255, 0), rgba(248, 249, 255, 1) 34%);
}

.composer-box--architect {
  border-radius: 24px;
  background: #fff;
  border: 1px solid rgba(192, 199, 212, 0.45);
  box-shadow: 0 20px 40px rgba(0, 96, 169, 0.08);
}

.composer-box__toptools {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px 0;
}

.toolbar-ghost {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-radius: 12px;
  background: transparent;
  color: #404752;
}

.toolbar-model-picker {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 12px;
  background: #f1f3fa;
  color: #404752;
}

.toolbar-model-select {
  width: 240px;
}

.composer-box__content {
  padding: 8px 16px 16px;
}

.composer-box__input--architect {
  min-height: 56px;
  width: 100%;
  resize: none;
  border: 0;
  background: transparent;
  font: inherit;
  color: #181c20;
  outline: none;
}

.send-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 16px;
  border-radius: 14px;
  background: linear-gradient(135deg, #409eff, #0060a9);
  color: #fff;
  font-weight: 700;
}

.send-button--architect {
  width: 48px;
  height: 48px;
  padding: 0;
  border-radius: 14px;
}

.send-button.is-danger {
  background: linear-gradient(135deg, #d96b6b, #ba1a1a);
}

.composer-footnote {
  margin: 12px 4px 0;
  text-align: center;
  font-size: 11px;
}

.citation-panel--modern {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 14px;
}

.citation-panel__section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.citation-panel__section-title {
  font-size: 12px;
  font-weight: 700;
  color: #404752;
}

.citation-panel__toggle {
  align-self: flex-start;
  padding: 0;
  background: transparent;
  color: #0060a9;
  font-weight: 700;
}

.citation-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  border-radius: 14px;
  background: #fff;
  border: 1px solid rgba(192, 199, 212, 0.45);
  text-align: left;
}

.citation-card--web {
  background: rgba(255, 255, 255, 0.92);
}

.citation-card__title {
  color: #181c20;
  font-weight: 700;
}

.citation-card__title--single {
  display: block;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.attachment-dialog--modern,
.attachment-dialog__grid,
.attachment-panel__list {
  display: flex;
}

.attachment-dialog--modern {
  flex-direction: column;
  gap: 18px;
}

.attachment-dialog__grid {
  gap: 16px;
}

.attachment-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 280px;
  padding: 16px;
  border-radius: 18px;
  background: #f8f9ff;
  border: 1px solid rgba(192, 199, 212, 0.45);
}

.attachment-panel--secondary {
  background: #f1f3fa;
}

.attachment-panel__title {
  font-size: 14px;
  font-weight: 700;
  color: #181c20;
}

.attachment-panel__list {
  flex-direction: column;
  gap: 10px;
  overflow-y: auto;
}

.attachment-option {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 14px;
  background: #fff;
}

.attachment-option.is-disabled {
  background: #f6f8fc;
  opacity: 0.78;
}

.attachment-option__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.status-banner {
  margin: 0 auto 16px;
  max-width: 980px;
  padding: 12px 14px;
  border-radius: 14px;
  background: #fff3f0;
  color: #ba1a1a;
  border: 1px solid rgba(186, 26, 26, 0.18);
}

.thinking-dots {
  display: inline-flex;
  gap: 6px;
}

.thinking-dots span {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #0060a9;
  opacity: 0.4;
  animation: pulse 1.2s infinite ease-in-out;
}

.thinking-dots span:nth-child(2) {
  animation-delay: 0.15s;
}

.thinking-dots span:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes pulse {

  0%,
  80%,
  100% {
    transform: scale(0.75);
    opacity: 0.35;
  }

  40% {
    transform: scale(1);
    opacity: 1;
  }
}

@media (max-width: 1200px) {
  .ai-workspace {
    grid-template-columns: 280px minmax(0, 1fr);
  }

  .empty-stage__prompts {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 960px) {

  .architect-shell,
  .ai-workspace,
  .attachment-dialog__grid {
    grid-template-columns: 1fr;
  }

  .architect-shell {
    display: flex;
    flex-direction: column;
  }

  .architect-nav {
    gap: 12px;
  }

  .architect-menu {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .conversation-history {
    border-right: 0;
    border-bottom: 1px solid rgba(192, 199, 212, 0.45);
  }

  .thread-list--history {
    max-height: 320px;
  }
}

@media (max-width: 720px) {

  .architect-topbar,
  .chat-stage__header,
  .composer-wrap--architect,
  .message-board,
  .conversation-history {
    padding-left: 14px;
    padding-right: 14px;
  }

  .architect-topbar,
  .chat-stage__header,
  .composer-box__toolbar,
  .composer-box__toptools,
  .architect-topbar__actions {
    flex-wrap: wrap;
  }

  .toolbar-model-select {
    width: 180px;
  }

  .transcript-item,
  .transcript-item.is-user {
    grid-template-columns: 1fr;
  }

  .transcript-item__avatar,
  .transcript-item.is-user .transcript-item__avatar {
    order: 0;
  }

  .transcript-item.is-user .transcript-item__panel {
    order: 0;
  }

  .transcript-item.is-user .transcript-item__head {
    justify-content: space-between;
  }

  .architect-menu {
    grid-template-columns: 1fr;
  }
}
</style>
