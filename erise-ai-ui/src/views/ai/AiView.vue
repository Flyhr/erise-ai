<template>
  <div class="ai-page">
    <div class="glass-card ai-shell">
      <aside class="ai-sidebar">
        <div class="ai-sidebar__header">
          <div>
            <div class="ai-sidebar__eyebrow">AI</div>
            <div class="ai-sidebar__title">会话</div>
          </div>
          <div class="ai-sidebar__actions">
            <button type="button" class="icon-button" :disabled="sending" @click="resetConversation">+</button>
            <button type="button" class="icon-button ai-sidebar__mobile-trigger" :disabled="sending" @click="sessionDrawerVisible = true">=</button>
          </div>
        </div>

        <div class="model-card">
          <div class="model-card__label">当前模型</div>
          <div class="model-card__title">DeepSeek</div>
          <div class="model-card__copy">AI 助手现在直接走 DeepSeek 回复，不再依赖项目检索摘要兜底。</div>
        </div>

        <div class="thread-list">
          <div
            v-for="session in sessions"
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

          <div v-if="!sessions.length" class="thread-empty">还没有会话，发送第一条消息后会自动创建。</div>
        </div>
      </aside>

      <section class="ai-main">
        <header class="ai-main__header">
          <div class="run-chip">
            <span>{{ sending ? 'AI 正在回复' : activeSessionSummary?.title || '新会话' }}</span>
            <span v-if="sending">({{ runningSeconds }}s)</span>
          </div>
          <div class="ai-main__actions">
            <div class="ai-main__model">DeepSeek Chat</div>
            <button type="button" class="soft-chip" :disabled="sending" @click="resetConversation">新会话</button>
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
                  <button v-if="isCollapsible(message)" type="button" class="transcript-item__toggle" @click="toggleExpanded(message)">
                    {{ message.expanded ? '收起' : '展开' }}
                  </button>
                </div>

                <div class="transcript-item__meta">
                  <span v-if="message.roleCode === 'USER' && message.status === 'sending'">发送中...</span>
                  <span v-else-if="message.roleCode === 'ASSISTANT' && message.status === 'streaming'">生成中...</span>
                  <span v-else-if="message.status === 'failed'" class="is-error">{{ message.errorMessage || '请求失败' }}</span>
                  <button
                    v-if="message.roleCode === 'USER' && message.status === 'failed' && message.pendingQuestion"
                    type="button"
                    class="retry-button"
                    :disabled="sending"
                    @click="retryMessage(message)"
                  >
                    重试
                  </button>
                </div>

                <div v-if="message.refusedReason" class="transcript-item__notice">{{ message.refusedReason }}</div>
              </article>
            </div>

            <div v-else class="empty-stage">
              <div class="empty-stage__eyebrow">DeepSeek Chat</div>
              <h2 class="empty-stage__title">开始一个新会话</h2>
              <p class="empty-stage__copy">
                AI 助手现在是标准聊天界面，直接调用 DeepSeek 返回内容。发送消息后会自动创建会话并保留上下文。
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
            <textarea
              ref="composerRef"
              v-model="question"
              class="composer-box__input"
              rows="1"
              :disabled="sending"
              placeholder="输入你的问题"
              @input="resizeComposer"
              @keydown.enter.exact.prevent="send()"
            />

            <div class="composer-box__toolbar">
              <div class="composer-box__left-tools">
                <button type="button" class="toolbar-chip" disabled>DeepSeek</button>
                <button type="button" class="toolbar-chip" disabled>多轮会话</button>
              </div>
              <div class="composer-box__right-tools">
                <button type="button" class="send-button" :disabled="!canSend" @click="send()">发送</button>
              </div>
            </div>
          </div>
        </footer>
      </section>
    </div>

    <el-drawer v-model="sessionDrawerVisible" title="会话" direction="ltr" size="320px">
      <div class="thread-list thread-list--drawer">
        <div
          v-for="session in sessions"
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
import { chat, deleteSession, getSession, getSessions } from '@/api/ai'
import { resolveApiUrl } from '@/api/http'
import type { AiMessageView, AiSessionSummaryView } from '@/types/models'

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

const props = defineProps<{ id?: string }>()
const route = useRoute()
const router = useRouter()
const sessions = ref<AiSessionSummaryView[]>([])
const messages = ref<UiMessage[]>([])
const question = ref('')
const sending = ref(false)
const activeSessionId = ref<number | undefined>()
const sessionDrawerVisible = ref(false)
const networkError = ref('')
const messageListRef = ref<HTMLDivElement | null>(null)
const composerRef = ref<HTMLTextAreaElement | null>(null)
const sendStartedAt = ref<number>()
const clockNow = ref(Date.now())
const baseAiPath = computed(() => (props.id ? `/projects/${props.id}/ai` : '/ai'))

let tickHandle: number | undefined

const parseNumber = (value: unknown) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
}

const buildLocalId = (prefix: string) => `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`
const activeSessionSummary = computed(() => sessions.value.find((session) => session.id === activeSessionId.value))
const runningSeconds = computed(() => {
  if (!sending.value || !sendStartedAt.value) {
    return 0
  }
  return Math.max(1, Math.floor((clockNow.value - sendStartedAt.value) / 1000))
})
const canSend = computed(() => Boolean(question.value.trim()) && !sending.value)
const quickPrompts = ['帮我做一个实现方案。', '把这段需求整理成可执行任务。', '分析一个报错并给出排查路径。']

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
const buildAiLocation = (sessionId?: number) => ({
  path: baseAiPath.value,
  query: sessionId ? { sessionId } : {},
})

const toUiMessage = (message: AiMessageView): UiMessage => ({
  id: String(message.id),
  serverId: message.id,
  roleCode: message.roleCode === 'USER' ? 'USER' : 'ASSISTANT',
  content: message.content,
  createdAt: message.createdAt,
  refusedReason: message.refusedReason,
  status: 'sent',
  expanded: false,
})

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

const errorMessageOf = (error: unknown) => {
  const candidate = error as { response?: { data?: { message?: string } }; message?: string }
  return candidate?.response?.data?.message || candidate?.message || '请求失败'
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
  } catch {
    return raw.trim()
  }
  return raw.trim()
}

const refreshSessions = async () => {
  sessions.value = await getSessions()
}

const syncFromRoute = async () => {
  const sessionId = parseNumber(route.query.sessionId)

  if (sessionId) {
    try {
      const detail = await getSession(sessionId)
      activeSessionId.value = detail.id
      messages.value = detail.messages.map(toUiMessage)
      networkError.value = ''
      await scrollToBottom()
      return
    } catch (error) {
      messages.value = []
      activeSessionId.value = undefined
      networkError.value = errorMessageOf(error)
      await router.replace(buildAiLocation())
      return
    }
  }

  activeSessionId.value = undefined
  messages.value = []
  networkError.value = ''
}

const resetConversation = async () => {
  if (sending.value) {
    return
  }
  activeSessionId.value = undefined
  messages.value = []
  question.value = ''
  networkError.value = ''
  sessionDrawerVisible.value = false
  await router.push(buildAiLocation())
  await resizeComposer()
}

const openSession = async (sessionId: number) => {
  sessionDrawerVisible.value = false
  await router.push(buildAiLocation(sessionId))
}

const removeSession = async (sessionId: number) => {
  await ElMessageBox.confirm('删除后无法恢复，确认删除这个会话吗？', '删除会话', { type: 'warning' })
  await deleteSession(sessionId)
  ElMessage.success('会话已删除')
  await refreshSessions()
  if (sessionId === activeSessionId.value) {
    await resetConversation()
  }
}

const streamChat = async (
  payload: { sessionId?: number; question: string },
  handlers: { onOpen?: () => void; onChunk?: (chunk: string) => void; onDone?: (payload: StreamDonePayload) => Promise<void> | void },
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

  handlers.onOpen?.()

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
    assistantMessage.content = '本次回复没有成功返回内容。'
  }
  networkError.value = message
  ElMessage.error(message)
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

  const payload = {
    sessionId: activeSessionId.value,
    question: text,
  }

  let opened = false
  let chunkReceived = false

  try {
    await streamChat(payload, {
      onOpen: () => {
        opened = true
        userMessage.status = 'sent'
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
        if (donePayload.sessionId) {
          activeSessionId.value = donePayload.sessionId
          const target = buildAiLocation(donePayload.sessionId)
          if (router.currentRoute.value.fullPath !== router.resolve(target).fullPath) {
            await router.replace(target)
          }
        }
        await refreshSessions()
      },
    })
  } catch (error) {
    if (!opened && !chunkReceived) {
      try {
        const response = await chat(payload)
        userMessage.status = 'sent'
        assistantMessage.status = 'sent'
        assistantMessage.serverId = response.messageId
        assistantMessage.content = response.answer
        assistantMessage.refusedReason = response.refusedReason
        activeSessionId.value = response.sessionId
        const target = buildAiLocation(response.sessionId)
        if (router.currentRoute.value.fullPath !== router.resolve(target).fullPath) {
          await router.replace(target)
        }
        await refreshSessions()
      } catch (fallbackError) {
        markSendFailed(userMessage, assistantMessage, errorMessageOf(fallbackError), text)
      }
    } else {
      markSendFailed(userMessage, assistantMessage, errorMessageOf(error), text)
    }
  } finally {
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

onMounted(async () => {
  await refreshSessions()
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
.ai-sidebar__actions,
.ai-main__header,
.ai-main__actions,
.transcript-item__head,
.transcript-item__meta,
.composer-box__toolbar,
.composer-box__left-tools,
.composer-box__right-tools,
.status-banner {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ai-sidebar__header,
.ai-main__header,
.transcript-item__head,
.transcript-item__meta,
.composer-box__toolbar,
.status-banner {
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
.transcript-item__toggle {
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

.icon-button:hover,
.thread-item__delete:hover,
.soft-chip:hover,
.toolbar-chip:hover,
.prompt-card:hover,
.model-card:hover {
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
.transcript-item__meta,
.empty-stage__copy,
.status-banner,
.transcript-item__notice {
  color: var(--muted);
}

.model-card__copy {
  margin-top: 8px;
  line-height: 1.7;
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

.thread-item__title {
  font-weight: 700;
}

.thread-item__meta {
  margin-top: 8px;
  font-size: 13px;
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
  color: var(--muted);
  line-height: 1.7;
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

.soft-chip,
.toolbar-chip,
.ai-main__model {
  color: var(--muted);
}

.soft-chip {
  cursor: pointer;
}

.ai-stream {
  flex: 1;
  overflow-y: auto;
  padding: 22px 24px 180px;
}

.ai-stream.is-empty {
  overflow: hidden;
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

.transcript-item.is-user .transcript-item__head,
.transcript-item.is-user .transcript-item__meta {
  justify-content: flex-end;
}

.transcript-item__label {
  font-weight: 700;
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

.transcript-item__meta {
  font-size: 13px;
}

.transcript-item__meta .is-error,
.transcript-item__notice,
.status-banner--error {
  color: var(--danger);
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
  border: 1px solid var(--line);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 18px 36px var(--shadow-color);
  padding: 16px 18px;
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

.send-button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
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
}

@media (max-width: 900px) {
  .ai-main__header,
  .ai-stream,
  .composer-wrap {
    padding-left: 16px;
    padding-right: 16px;
  }

  .ai-main__header,
  .composer-box__toolbar {
    flex-direction: column;
    align-items: flex-start;
  }

  .composer-box__left-tools,
  .composer-box__right-tools,
  .ai-main__actions,
  .empty-stage__prompts {
    flex-wrap: wrap;
  }

  .transcript-item__body {
    max-width: 100%;
  }
}
</style>


