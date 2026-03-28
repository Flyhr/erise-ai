<template>
  <div class="ai-page">
    <div class="glass-card ai-shell">
      <aside class="ai-sidebar">
        <div class="ai-sidebar__header">
          <div>
            <div class="ai-sidebar__eyebrow">AI</div>
            <div class="ai-sidebar__title">项目智能协作</div>
          </div>
        </div>

        <div class="model-card">
          <div class="model-card__label">当前模型</div>
          <div class="model-card__title">{{ modelCardTitle }}</div>
          <div class="model-card__copy">{{ modelCardCopy }}</div>
          <div v-if="availableModels.length" class="model-card__list">
            <button
              v-for="model in availableModels"
              :key="model.modelCode"
              type="button"
              class="model-card__item"
              :class="{ 'is-active': model.modelCode === selectedModelCode }"
              :disabled="sending"
              @click="selectModel(model.modelCode)"
            >
              <span class="model-card__item-name">{{ model.modelName }}</span>
              <span class="model-card__item-meta">{{ model.providerCode }}</span>
            </button>
          </div>
          <div v-else-if="!loadingModels" class="model-card__empty">当前没有可用模型。</div>
          <div v-if="modelError" class="model-card__hint is-error">{{ modelError }}</div>
        </div>

        <div class="thread-list">
          <div
            v-for="session in visibleSessions"
            :key="session.id"
            class="thread-item"
            :class="{ 'is-active': session.id === activeSessionId }"
          >
            <button type="button" class="thread-item__main" @click="openSession(session.id)">
              <div class="thread-item__title">{{ session.title }}</div>
              <div class="thread-item__meta">
                <span>{{ relativeTime(session.lastMessageAt || session.createdAt) }}</span>
              </div>
            </button>
            <button type="button" class="thread-item__delete" :disabled="sending" @click="removeSession(session.id)">×</button>
          </div>

          <div v-if="!visibleSessions.length" class="thread-empty">
            这里还没有历史会话。发送第一条消息后，会自动生成会话记录。
          </div>
        </div>
      </aside>

      <section class="ai-main">
        <header class="ai-main__header">
          <div class="run-chip">
            <span>{{ sending ? 'AI 正在回复' : activeSessionSummary?.title || '新对话' }}</span>
            <span v-if="sending">({{ runningSeconds }}s)</span>
          </div>
          <div class="ai-main__actions">
            <button
              type="button"
              class="icon-button ai-main__mobile-trigger"
              :disabled="sending"
              @click="sessionDrawerVisible = true"
            >
              =
            </button>
            <div class="ai-main__model">{{ modelHeaderTitle }}</div>
            <button type="button" class="soft-chip" :disabled="sending" @click="resetConversation">新对话</button>
          </div>
        </header>

        <section ref="messageListRef" class="ai-stream" :class="{ 'is-empty': !messages.length }">
          <div class="ai-stream__inner">
            <div v-if="networkError" class="status-banner status-banner--error">
              <span>{{ networkError }}</span>
              <button type="button" @click="networkError = ''">关闭</button>
            </div>

            <div v-if="messages.length" class="transcript-list">
              <article
                v-for="message in messages"
                :key="message.id"
                class="transcript-item"
                :class="message.roleCode === 'USER' ? 'is-user' : 'is-assistant'"
              >
                <div class="transcript-item__head">
                  <span class="transcript-item__label">{{ message.roleCode === 'USER' ? '你' : 'Erise AI' }}</span>
                  <span class="transcript-item__time">{{ formatTime(message.createdAt) }}</span>
                </div>

                <div class="transcript-item__body" :class="surfaceClasses(message)">
                  <div v-if="message.roleCode === 'ASSISTANT' && message.status === 'streaming' && !message.content" class="thinking-dots">
                    <span />
                    <span />
                    <span />
                  </div>
                  <div v-else class="transcript-item__content" :class="{ 'is-collapsed': isCollapsed(message) }">
                    {{ message.content || '...' }}
                  </div>

                  <div v-if="message.refusedReason" class="transcript-item__notice">{{ message.refusedReason }}</div>
                  <div v-if="message.errorMessage" class="transcript-item__notice">{{ message.errorMessage }}</div>

                  <button v-if="isCollapsible(message)" type="button" class="transcript-item__toggle" @click="toggleExpanded(message)">
                    {{ message.expanded ? '收起' : '展开全部' }}
                  </button>
                  <button
                    v-if="message.roleCode === 'USER' && message.pendingQuestion && message.status === 'failed'"
                    type="button"
                    class="retry-button"
                    :disabled="sending"
                    @click="retryMessage(message)"
                  >
                    重新发送
                  </button>
                </div>
              </article>
            </div>

            <div v-else class="empty-stage">
              <div class="empty-stage__eyebrow">{{ activeModel?.modelName || 'Erise AI Chat' }}</div>
              <h2 class="empty-stage__title">把项目文档交给 AI，一起总结和修改</h2>
              <p class="empty-stage__copy">
                点击输入框左侧的 +，先选择一个项目，再勾选一个或多个文档/文件。然后你可以直接说：
                “总结这些文档的主要内容”，或“将发送给你的文档标题改为：测试ai修改文档功能”。
              </p>
              <div class="empty-stage__prompts">
                <button v-for="prompt in quickPrompts" :key="prompt" type="button" class="prompt-card" :disabled="sending" @click="usePrompt(prompt)">
                  {{ prompt }}
                </button>
              </div>
            </div>
          </div>
        </section>

        <footer class="composer-wrap">
          <div class="composer-box">
            <button type="button" class="composer-box__attach" :disabled="sending" @click="openAttachmentPicker">+</button>
            <div class="composer-box__content">
              <div v-if="selectedProjectDisplay || selectedAttachments.length" class="composer-box__selection">
                <button
                  v-if="selectedProjectDisplay"
                  type="button"
                  class="selection-chip is-project"
                  :disabled="sending || projectLocked"
                  @click="clearSelectedProject"
                >
                  <span>项目：{{ selectedProjectDisplay }}</span>
                  <span v-if="!projectLocked">×</span>
                </button>
                <button
                  v-for="attachment in selectedAttachments"
                  :key="attachmentKeyOf(attachment)"
                  type="button"
                  class="selection-chip"
                  :disabled="sending"
                  @click="removeAttachment(attachment)"
                >
                  <span>{{ attachmentLabel(attachment) }}</span>
                  <span>×</span>
                </button>
              </div>

              <textarea
                ref="composerRef"
                v-model="question"
                class="composer-box__input"
                rows="1"
                :disabled="sending"
                placeholder="输入你的指令，例如：总结这些附件；把发送给你的文档标题改为“测试ai修改文档功能”；或结合当前项目继续分析。"
                @input="resizeComposer"
                @keydown.enter.exact.prevent="send()"
              />

              <div class="composer-box__toolbar">
                <div class="composer-box__left-tools">
                  <button type="button" class="toolbar-chip" disabled>{{ modelProviderLabel }}</button>
                  <button type="button" class="toolbar-chip" disabled>{{ modelModeLabel }}</button>
                  <button v-if="selectedAttachments.length" type="button" class="toolbar-chip" disabled>
                    已附加 {{ selectedAttachments.length }} 份资料
                  </button>
                </div>
                <div class="composer-box__right-tools">
                  <button v-if="sending" type="button" class="send-button" :disabled="!currentRequestId" @click="stopGeneration">
                    停止生成
                  </button>
                  <button v-else type="button" class="send-button" :disabled="!canSend" @click="send()">
                    发送
                  </button>
                </div>
              </div>
            </div>
          </div>
        </footer>
      </section>
    </div>

    <el-dialog v-model="attachmentDialogVisible" title="选择项目文件或文档" width="760px">
      <div class="attachment-dialog">
        <div class="attachment-dialog__project">
          <div class="attachment-dialog__label">项目</div>
          <el-select
            v-model="draftProjectId"
            class="attachment-dialog__project-select"
            filterable
            :clearable="!projectLocked"
            :disabled="projectLocked || loadingAttachmentOptions"
            placeholder="请选择项目"
          >
            <el-option v-for="project in selectableProjects" :key="project.id" :label="project.name" :value="project.id" />
          </el-select>
          <div class="attachment-dialog__hint">
            先选项目，再从该项目里勾选文档和文件。支持一次发送多个文档/文件给 AI。
          </div>
        </div>

        <div class="attachment-dialog__grid">
          <section class="attachment-panel">
            <div class="attachment-panel__title">文档</div>
            <div v-if="!draftProjectId" class="attachment-panel__empty">请先选择项目。</div>
            <div v-else-if="loadingAttachmentOptions" class="attachment-panel__empty">正在加载文档和文件列表...</div>
            <div v-else-if="draftDocuments.length" class="attachment-panel__list">
              <label v-for="document in draftDocuments" :key="`document-${document.id}`" class="attachment-option">
                <input
                  type="checkbox"
                  :checked="draftAttachmentSelected('DOCUMENT', document.id)"
                  @change="toggleDraftAttachment('DOCUMENT', document.id, document.title)"
                />
                <span class="attachment-option__copy">
                  <strong>{{ document.title }}</strong>
                  <small>{{ document.summary || '暂无摘要' }}</small>
                </span>
              </label>
            </div>
            <div v-else class="attachment-panel__empty">当前项目还没有文档。</div>
          </section>

          <section class="attachment-panel">
            <div class="attachment-panel__title">文件</div>
            <div v-if="!draftProjectId" class="attachment-panel__empty">请先选择项目。</div>
            <div v-else-if="loadingAttachmentOptions" class="attachment-panel__empty">正在加载文档和文件列表...</div>
            <div v-else-if="draftFiles.length" class="attachment-panel__list">
              <label v-for="file in draftFiles" :key="`file-${file.id}`" class="attachment-option">
                <input
                  type="checkbox"
                  :checked="draftAttachmentSelected('FILE', file.id)"
                  @change="toggleDraftAttachment('FILE', file.id, file.fileName)"
                />
                <span class="attachment-option__copy">
                  <strong>{{ file.fileName }}</strong>
                  <small>{{ file.fileExt.toUpperCase() }} · {{ file.parseStatus || 'PENDING' }}</small>
                </span>
              </label>
            </div>
            <div v-else class="attachment-panel__empty">当前项目还没有文件。</div>
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

    <el-drawer v-model="sessionDrawerVisible" title="历史会话" direction="ltr" size="320px">
      <div class="thread-list thread-list--drawer">
        <div
          v-for="session in visibleSessions"
          :key="session.id"
          class="thread-item"
          :class="{ 'is-active': session.id === activeSessionId }"
        >
          <button type="button" class="thread-item__main" @click="openSession(session.id)">
            <div class="thread-item__title">{{ session.title }}</div>
            <div class="thread-item__meta">
              <span>{{ relativeTime(session.lastMessageAt || session.createdAt) }}</span>
            </div>
          </button>
          <button type="button" class="thread-item__delete" :disabled="sending" @click="removeSession(session.id)">×</button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import dayjs from 'dayjs'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { cancelChat, chat, deleteSession, getModels, getSession, getSessions } from '@/api/ai'
import type { AiChatPayload } from '@/api/ai'
import { getDocuments } from '@/api/document'
import { getFiles } from '@/api/file'
import { getProjects } from '@/api/project'
import { resolveApiUrl } from '@/api/http'
import type {
  AiAttachmentPayload,
  AiChatResponse,
  AiMessageView,
  AiModelView,
  AiSessionSummaryView,
  DocumentSummaryView,
  FileView,
  ProjectDetailView,
} from '@/types/models'

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
}

interface StreamDonePayload {
  sessionId?: number
  messageId?: number
}

interface DraftAttachmentOption {
  attachmentType: 'DOCUMENT' | 'FILE'
  sourceId: number
  title: string
}

const props = defineProps<{ id?: string }>()
const route = useRoute()
const router = useRouter()

const parseNumber = (value: unknown) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
}

const buildLocalId = (prefix: string) => `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`
const attachmentStorageKey = (sessionId: number) => `erise-ai-attachments:${sessionId}`
const attachmentKeyOf = (attachment: Pick<AiAttachmentPayload, 'attachmentType' | 'sourceId'>) => `${attachment.attachmentType}:${attachment.sourceId}`
const attachmentTypeLabel = (type: AiAttachmentPayload['attachmentType']) => (type === 'DOCUMENT' ? '文档' : '文件')
const attachmentLabel = (attachment: AiAttachmentPayload) => `${attachmentTypeLabel(attachment.attachmentType)}：${attachment.title || `#${attachment.sourceId}`}`

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
const modelError = ref('')
const selectedModelCode = ref('')
const currentRequestId = ref('')
const question = ref('')
const sending = ref(false)
const activeSessionId = ref<number | undefined>()
const selectedProjectId = ref<number | undefined>()
const draftProjectId = ref<number | undefined>()
const selectedAttachments = ref<AiAttachmentPayload[]>([])
const draftAttachmentKeys = ref<string[]>([])
const sessionDrawerVisible = ref(false)
const networkError = ref('')
const messageListRef = ref<HTMLDivElement | null>(null)
const composerRef = ref<HTMLTextAreaElement | null>(null)
const sendStartedAt = ref<number>()
const clockNow = ref(Date.now())

const routeProjectId = computed(() => parseNumber(props.id))
const projectLocked = computed(() => Boolean(routeProjectId.value))
const projectLookup = computed(() => new Map(selectableProjects.value.map((project) => [project.id, project] as const)))
const activeProjectId = computed(() => routeProjectId.value || selectedProjectId.value)
const activeProject = computed(() => (activeProjectId.value ? projectLookup.value.get(activeProjectId.value) : undefined))
const selectedProjectDisplay = computed(() => activeProject.value?.name || (activeProjectId.value ? `项目 #${activeProjectId.value}` : ''))
const baseAiPath = computed(() => (props.id ? `/projects/${props.id}/ai` : '/ai'))
const visibleSessions = computed(() => {
  if (!projectLocked.value || !routeProjectId.value) {
    return sessions.value
  }
  return sessions.value.filter((session) => session.projectId === routeProjectId.value || session.id === activeSessionId.value)
})
const activeSessionSummary = computed(() => sessions.value.find((session) => session.id === activeSessionId.value))
const activeModel = computed(() => {
  if (!availableModels.value.length) {
    return undefined
  }
  return availableModels.value.find((model) => model.modelCode === selectedModelCode.value) || availableModels.value[0]
})
const runningSeconds = computed(() => {
  if (!sending.value || !sendStartedAt.value) {
    return 0
  }
  return Math.max(1, Math.floor((clockNow.value - sendStartedAt.value) / 1000))
})
const canSend = computed(() => Boolean(question.value.trim()) && !sending.value)
const modelCardTitle = computed(() => activeModel.value?.modelName || (loadingModels.value ? '正在加载模型...' : '未选择模型'))
const modelHeaderTitle = computed(() => activeModel.value?.modelName || '未选择模型')
const modelProviderLabel = computed(() => activeModel.value?.providerCode || (loadingModels.value ? '加载中' : '模型服务'))
const modelModeLabel = computed(() => (activeModel.value?.supportStream === false ? '普通回复' : '流式回复'))
const modelCardCopy = computed(() => {
  if (modelError.value) {
    return '模型列表加载失败，请检查 Python AI 服务和 Java 网关是否已启动。'
  }
  if (loadingModels.value) {
    return '正在从后端读取可用模型列表。'
  }
  if (!availableModels.value.length) {
    return '当前还没有启用的模型，请先在后台完成模型配置。'
  }
  if (selectedAttachments.value.length) {
    return `当前会优先参考 ${selectedAttachments.value.length} 份已附加资料${selectedProjectDisplay.value ? `，并结合${selectedProjectDisplay.value}的项目上下文` : ''}。`
  }
  if (activeProjectId.value) {
    return '当前会携带项目上下文。点击输入框左侧的 +，还可以继续附加具体文档或文件。'
  }
  return '点击输入框左侧的 + 先选择项目、文档或文件，再让 AI 帮你总结、改标题或继续分析。'
})
const quickPrompts = computed(() => [
  selectedAttachments.value.length ? '总结我发送给你的文档和文件主要内容' : '基于当前项目上下文，帮我梳理下一步工作重点',
  '将发送给你的文档标题改为：“测试ai修改文档功能”',
  '根据这些附件列出重点、风险和待办',
])

let tickHandle: number | undefined

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
})

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

const clearSelectedProject = () => {
  if (projectLocked.value) {
    return
  }
  selectedProjectId.value = undefined
  selectedAttachments.value = []
  persistAttachmentState(activeSessionId.value)
}

const refreshSessions = async () => {
  sessions.value = await getSessions()
}

const refreshModels = async () => {
  loadingModels.value = true
  modelError.value = ''
  try {
    const models = await getModels()
    availableModels.value = models
    if (!models.length) {
      selectedModelCode.value = ''
      return
    }
    if (!models.some((model) => model.modelCode === selectedModelCode.value)) {
      selectedModelCode.value = models[0].modelCode
    }
  } catch (error) {
    availableModels.value = []
    selectedModelCode.value = ''
    modelError.value = errorMessageOf(error)
  } finally {
    loadingModels.value = false
  }
}

const selectModel = (modelCode: string) => {
  selectedModelCode.value = modelCode
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
  if (response.modelCode && availableModels.value.some((model) => model.modelCode === response.modelCode)) {
    selectedModelCode.value = response.modelCode
  }
  await syncSessionRoute(response.sessionId)
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
      networkError.value = ''
      await scrollToBottom()
      return
    } catch (error) {
      messages.value = []
      activeSessionId.value = undefined
      restoreAttachmentState(undefined, routeProjectId.value)
      networkError.value = errorMessageOf(error)
      await router.replace(buildAiLocation())
      return
    }
  }

  activeSessionId.value = undefined
  messages.value = []
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
  sessionDrawerVisible.value = false
  attachmentDialogVisible.value = false
  selectedAttachments.value = []
  selectedProjectId.value = routeProjectId.value || preservedProjectId
  draftProjectId.value = selectedProjectId.value
  draftAttachmentKeys.value = []
  draftAttachmentOptions.value = []
  await router.push(buildAiLocation())
  await resizeComposer()
}

const openSession = async (sessionId: number) => {
  sessionDrawerVisible.value = false
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
    attachments: selectedAttachments.value.length ? selectedAttachments.value : undefined,
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
        await syncSessionRoute(donePayload.sessionId)
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
  await Promise.allSettled([refreshSessions(), refreshModels(), loadProjects()])
  await resizeComposer()
})

onBeforeUnmount(() => {
  stopClock()
})
</script>

<style scoped>
.ai-page {
  min-height: calc(100vh - 96px);
}

.ai-shell {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  min-height: calc(100vh - 120px);
  overflow: hidden;
}

.ai-sidebar {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 20px;
  border-right: 1px solid var(--line);
  background: linear-gradient(180deg, var(--surface-strong), var(--panel));
}

.ai-sidebar__header,
.ai-main__header,
.ai-main__actions,
.transcript-item__head,
.composer-box__toolbar,
.composer-box__left-tools,
.composer-box__right-tools,
.status-banner,
.attachment-dialog__footer,
.attachment-dialog__footer-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ai-sidebar__header,
.ai-main__header,
.transcript-item__head,
.composer-box__toolbar,
.status-banner,
.attachment-dialog__footer {
  justify-content: space-between;
}

.ai-sidebar__eyebrow,
.empty-stage__eyebrow,
.model-card__label {
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.ai-sidebar__title {
  margin-top: 6px;
  font-size: 28px;
  font-weight: 800;
  letter-spacing: -0.04em;
}

.icon-button,
.soft-chip,
.toolbar-chip,
.send-button,
.thread-item__delete,
.prompt-card,
.retry-button,
.transcript-item__toggle,
.model-card__item {
  border: 1px solid transparent;
  font: inherit;
  background: transparent;
}

.icon-button {
  width: 34px;
  height: 34px;
  border-radius: 12px;
  color: var(--muted);
  cursor: pointer;
}

.ai-main__mobile-trigger {
  display: none;
}

.icon-button:hover,
.thread-item__delete:hover,
.soft-chip:hover,
.toolbar-chip:hover,
.prompt-card:hover,
.model-card__item:hover {
  background: rgba(15, 23, 42, 0.05);
}

.model-card {
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.62);
}

.model-card__title {
  margin-top: 8px;
  font-size: 18px;
  font-weight: 700;
}

.model-card__copy,
.thread-item__meta,
.transcript-item__time,
.empty-stage__copy,
.status-banner,
.transcript-item__notice {
  color: var(--muted);
}

.model-card__copy,
.model-card__empty,
.model-card__hint,
.thread-empty,
.attachment-dialog__hint,
.attachment-panel__empty,
.attachment-option__copy small {
  line-height: 1.6;
}

.model-card__copy,
.model-card__empty,
.model-card__hint {
  margin-top: 12px;
}

.model-card__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
}

.model-card__item {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-color: var(--line);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.64);
  color: var(--text);
  cursor: pointer;
}

.model-card__item.is-active {
  border-color: rgba(15, 23, 42, 0.18);
  background: rgba(15, 23, 42, 0.08);
}

.model-card__item-name,
.thread-item__title,
.transcript-item__label,
.attachment-dialog__label,
.attachment-panel__title {
  font-weight: 700;
}

.model-card__item-meta,
.model-card__empty,
.model-card__hint,
.thread-item__meta,
.transcript-item__time {
  font-size: 13px;
}

.model-card__hint.is-error,
.transcript-item__meta .is-error,
.transcript-item__notice,
.status-banner--error {
  color: var(--danger);
}

.thread-list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  overflow-y: auto;
}

.thread-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: start;
}

.thread-item__main {
  width: 100%;
  padding: 14px 16px;
  border: 1px solid transparent;
  border-radius: 18px;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.thread-item__main:hover,
.thread-item.is-active .thread-item__main {
  background: rgba(255, 255, 255, 0.66);
  border-color: var(--line);
}

.thread-item__meta {
  margin-top: 8px;
}

.thread-item__delete {
  width: 30px;
  height: 30px;
  border-radius: 10px;
  color: var(--muted);
  cursor: pointer;
}

.thread-empty {
  padding: 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.6);
}

.ai-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.ai-main__header {
  padding: 20px 22px;
  border-bottom: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(18px);
}

.run-chip,
.soft-chip,
.toolbar-chip,
.ai-main__model {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.82);
}

.run-chip {
  color: var(--text);
  font-weight: 600;
}

.ai-stream {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.ai-stream__inner,
.composer-box {
  width: min(100%, 920px);
  margin: 0 auto;
}

.transcript-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.transcript-item {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.transcript-item.is-user .transcript-item__head {
  justify-content: flex-end;
}

.transcript-item__body {
  max-width: 88%;
  padding: 16px 18px;
  border-radius: 22px;
  border: 1px solid var(--line);
  line-height: 1.8;
}

.transcript-item.is-user .transcript-item__body {
  margin-left: auto;
  background: var(--panel);
}

.transcript-item.is-assistant .transcript-item__body {
  background: rgba(255, 255, 255, 0.72);
}

.transcript-item__body.is-failed {
  border-color: rgba(190, 24, 60, 0.22);
}

.transcript-item__content {
  white-space: pre-wrap;
  word-break: break-word;
}

.transcript-item__content.is-collapsed {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 12;
  -webkit-box-orient: vertical;
}

.transcript-item__toggle,
.retry-button {
  margin-top: 10px;
  padding: 0;
  color: var(--accent);
  cursor: pointer;
}

.empty-stage {
  display: flex;
  min-height: 520px;
  flex-direction: column;
  justify-content: center;
  gap: 16px;
}

.empty-stage__title {
  margin: 0;
  font-size: clamp(32px, 3.2vw, 42px);
  font-weight: 800;
  letter-spacing: -0.04em;
}

.empty-stage__copy {
  max-width: 720px;
  margin: 0;
  line-height: 1.8;
}

.empty-stage__prompts {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.prompt-card {
  padding: 14px 16px;
  border-radius: 18px;
  border-color: var(--line);
  color: var(--text);
  cursor: pointer;
}

.status-banner {
  margin-bottom: 18px;
  padding: 12px 16px;
  border-radius: 16px;
  border: 1px solid rgba(190, 24, 60, 0.16);
  background: rgba(254, 242, 242, 0.86);
}

.status-banner button {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.thinking-dots {
  display: inline-flex;
  gap: 6px;
}

.thinking-dots span {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--muted);
  animation: pulse 1.1s ease-in-out infinite;
}

.thinking-dots span:nth-child(2) {
  animation-delay: 0.15s;
}

.thinking-dots span:nth-child(3) {
  animation-delay: 0.3s;
}

.composer-wrap {
  position: sticky;
  bottom: 0;
  padding: 0 24px 24px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0) 0%, rgba(244, 247, 251, 0.74) 28%, rgba(244, 247, 251, 0.96) 100%);
}

.composer-box {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  border: 1px solid var(--line);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 18px 36px var(--shadow-color);
  padding: 16px 18px;
}

.composer-box__attach,
.selection-chip,
.attachment-option {
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.78);
}

.composer-box__attach {
  width: 44px;
  height: 44px;
  flex: 0 0 auto;
  border-radius: 16px;
  color: var(--text);
  font-size: 24px;
  line-height: 1;
  cursor: pointer;
}

.composer-box__content {
  flex: 1;
  min-width: 0;
}

.composer-box__selection {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
}

.selection-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 999px;
  color: var(--text);
  cursor: pointer;
}

.selection-chip.is-project {
  background: rgba(15, 23, 42, 0.08);
}

.composer-box__input {
  width: 100%;
  min-height: 56px;
  max-height: 220px;
  border: none;
  outline: none;
  resize: none;
  background: transparent;
  color: var(--text);
  font-size: 16px;
  line-height: 1.8;
}

.composer-box__input::placeholder {
  color: var(--muted);
}

.send-button {
  padding: 10px 18px;
  border-radius: 999px;
  background: var(--accent);
  color: #ffffff;
  cursor: pointer;
}

.send-button:disabled,
.model-card__item:disabled,
.icon-button:disabled,
.soft-chip:disabled,
.thread-item__delete:disabled,
.prompt-card:disabled,
.composer-box__attach:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.attachment-dialog {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.attachment-dialog__project,
.attachment-panel {
  border: 1px solid var(--line);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.86);
  padding: 16px;
}

.attachment-dialog__project-select {
  width: 100%;
  margin-top: 10px;
}

.attachment-dialog__hint {
  margin-top: 10px;
}

.attachment-dialog__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.attachment-panel {
  min-height: 280px;
}

.attachment-panel__list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 12px;
  max-height: 320px;
  overflow-y: auto;
}

.attachment-option {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 12px;
  border-radius: 16px;
  cursor: pointer;
}

.attachment-option input {
  margin-top: 4px;
}

.attachment-option__copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.thread-list--drawer {
  padding-top: 6px;
}

@keyframes pulse {
  0%,
  80%,
  100% {
    transform: scale(0.8);
    opacity: 0.45;
  }

  40% {
    transform: scale(1);
    opacity: 1;
  }
}

@media (max-width: 1200px) {
  .ai-shell {
    grid-template-columns: 1fr;
  }

  .ai-sidebar {
    display: none;
  }

  .ai-main__mobile-trigger {
    display: inline-flex;
  }
}

@media (max-width: 900px) {
  .ai-main__header,
  .ai-stream,
  .composer-wrap {
    padding-left: 16px;
    padding-right: 16px;
  }

  .ai-main__header,
  .composer-box__toolbar,
  .ai-main__actions {
    flex-direction: column;
    align-items: flex-start;
  }

  .composer-box__left-tools,
  .composer-box__right-tools,
  .empty-stage__prompts {
    flex-wrap: wrap;
  }

  .transcript-item__body {
    max-width: 100%;
  }

  .attachment-dialog__grid {
    grid-template-columns: 1fr;
  }
}
</style>
