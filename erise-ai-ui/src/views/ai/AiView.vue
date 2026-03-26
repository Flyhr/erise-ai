<template>
  <div class="grid-2 ai-layout">
    <el-card class="glass-card" shadow="never">
      <template #header>会话列表</template>
      <div class="section-stack">
        <div class="ai-session-actions">
          <el-button type="primary" plain @click="resetConversation">新对话</el-button>
          <el-button plain @click="openProjectDialog">添加项目</el-button>
        </div>

        <div v-if="sessions.length" class="section-stack">
          <div v-for="session in sessions" :key="session.id" class="glass-card ai-session-card">
            <div class="ai-session-card__body">
              <div>
                <div class="ai-session-card__title">{{ session.title }}</div>
                <div class="meta-row">
                  <span>{{ projectLabel(session.projectId) }}</span>
                  <span>{{ session.lastMessageAt || session.createdAt }}</span>
                </div>
              </div>
              <el-button text @click="openSession(session.id, session.projectId)">打开</el-button>
            </div>
          </div>
        </div>
        <div v-else class="empty-box">当前还没有会话，先选择一个项目开始提问。</div>
      </div>
    </el-card>

    <el-card class="glass-card" shadow="never">
      <template #header>AI 助手</template>
      <div class="section-stack">
        <div class="ai-project-banner glass-card">
          <div>
            <div class="muted">当前项目</div>
            <div class="ai-project-banner__title">{{ activeProjectName || '尚未选择项目' }}</div>
            <div class="page-subtitle">
              {{
                activeProjectId
                  ? 'AI 会基于当前项目的文档、文件和知识内容回答。'
                  : '点击“添加项目”，从当前用户下的项目列表里选择一个项目后再开始聊天。'
              }}
            </div>
          </div>
          <div class="ai-project-banner__actions">
            <el-button type="primary" @click="openProjectDialog">{{ activeProjectId ? '更换项目' : '添加项目' }}</el-button>
            <el-button v-if="activeProjectId" plain @click="clearProject">移除项目</el-button>
          </div>
        </div>

        <div class="glass-card ai-chat-box">
          <div v-if="messages.length" class="section-stack">
            <div
              v-for="message in messages"
              :key="message.id || message.createdAt"
              class="ai-message"
              :class="{ 'is-user': message.roleCode === 'USER' }"
            >
              <div class="ai-message__role">{{ message.roleCode }}</div>
              <div class="ai-message__content">{{ message.content }}</div>
              <div v-if="message.citations?.length" class="ai-message__citations">
                <el-tag v-for="citation in message.citations" :key="`${citation.sourceType}-${citation.sourceId}`" type="success">
                  {{ citation.sourceTitle }}
                </el-tag>
              </div>
            </div>
          </div>
          <div v-else class="empty-box">
            {{ activeProjectId ? '输入问题开始当前项目的 AI 问答。' : '先选择项目，再开始对话。' }}
          </div>
        </div>

        <div class="ai-input-row">
          <el-input v-model="question" type="textarea" :rows="3" :disabled="!activeProjectId" placeholder="输入你的问题" />
          <el-button type="primary" :loading="sending" :disabled="!canSend" @click="send">发送</el-button>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="projectDialogVisible" title="选择项目" width="760px">
      <div v-if="projects.length" class="project-picker">
        <button
          v-for="project in projects"
          :key="project.id"
          type="button"
          class="project-picker__item"
          :class="{ 'is-active': project.id === activeProjectId }"
          @click="selectProject(project.id)"
        >
          <div class="project-picker__title">{{ project.name }}</div>
          <div class="project-picker__desc">{{ project.description || '暂无项目说明' }}</div>
          <div class="meta-row">
            <span>{{ project.fileCount }} 文件</span>
            <span>{{ project.documentCount }} 文档</span>
          </div>
        </button>
      </div>
      <div v-else class="empty-box">当前用户下还没有项目，请先去项目中心创建。</div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getSession, getSessions } from '@/api/ai'
import type { AiMessageView, AiSessionSummaryView } from '@/types/models'
import { useProjectDirectory } from '@/composables/useProjectDirectory'

const route = useRoute()
const router = useRouter()
const sessions = ref<AiSessionSummaryView[]>([])
const messages = ref<AiMessageView[]>([])
const activeSessionId = ref<number | undefined>()
const activeProjectId = ref<number | undefined>()
const question = ref('')
const sending = ref(false)
const projectDialogVisible = ref(false)
const apiBase = import.meta.env.VITE_API_BASE_URL
const { projects, loadProjects, projectLabel } = useProjectDirectory()

const activeProjectName = computed(() => (activeProjectId.value ? projectLabel(activeProjectId.value) : ''))
const canSend = computed(() => Boolean(activeProjectId.value && question.value.trim()))

const parseNumber = (value: unknown) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
}

const loadSessions = async () => {
  sessions.value = await getSessions()
}

const loadSession = async (sessionId: number, projectId?: number) => {
  const session = await getSession(sessionId)
  activeSessionId.value = sessionId
  activeProjectId.value = projectId ?? session.projectId
  messages.value = session.messages
}

const syncFromRoute = async () => {
  const sessionId = parseNumber(route.query.sessionId)
  const projectId = route.params.id ? parseNumber(route.params.id) : parseNumber(route.query.projectId)
  activeProjectId.value = projectId
  if (sessionId) {
    if (activeSessionId.value !== sessionId || messages.value.length === 0) {
      await loadSession(sessionId, projectId)
    }
    return
  }
  activeSessionId.value = undefined
  messages.value = []
}

const openSession = async (sessionId: number, projectId?: number) => {
  await router.push({ path: '/ai', query: { sessionId, projectId } })
  await loadSession(sessionId, projectId)
}

const openProjectDialog = async () => {
  await loadProjects()
  projectDialogVisible.value = true
}

const selectProject = async (projectId: number) => {
  projectDialogVisible.value = false
  activeProjectId.value = projectId
  activeSessionId.value = undefined
  messages.value = []
  question.value = ''
  await router.push({ path: '/ai', query: { projectId } })
}

const clearProject = async () => {
  activeProjectId.value = undefined
  activeSessionId.value = undefined
  messages.value = []
  question.value = ''
  await router.push('/ai')
}

const resetConversation = async () => {
  activeSessionId.value = undefined
  messages.value = []
  question.value = ''
  await router.push({ path: '/ai', query: activeProjectId.value ? { projectId: activeProjectId.value } : {} })
}

const send = async () => {
  if (!canSend.value || !activeProjectId.value) return
  sending.value = true
  try {
    const prompt = question.value.trim()
    messages.value.push({
      id: Date.now(),
      roleCode: 'USER',
      content: prompt,
      citations: [],
      createdAt: new Date().toISOString(),
    })
    const placeholder: AiMessageView = {
      id: Date.now() + 1,
      roleCode: 'ASSISTANT',
      content: '',
      citations: [],
      createdAt: new Date().toISOString(),
    }
    messages.value.push(placeholder)
    question.value = ''

    const token = localStorage.getItem('erise-access-token')
    const response = await fetch(`${apiBase}/v1/ai/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({
        projectId: activeProjectId.value,
        sessionId: activeSessionId.value,
        question: prompt,
      }),
    })

    if (!response.ok || !response.body) {
      throw new Error('流式问答失败')
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    let currentEvent = 'message'

    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const rawLine of lines) {
        const line = rawLine.trim()
        if (!line) continue
        if (line.startsWith('event:')) {
          currentEvent = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          const data = line.slice(5).trim()
          if (currentEvent === 'chunk') {
            placeholder.content += data
          } else if (currentEvent === 'done') {
            const donePayload = JSON.parse(data) as { sessionId: number }
            activeSessionId.value = donePayload.sessionId
          } else if (currentEvent === 'error') {
            throw new Error(data)
          }
        }
      }
    }

    if (activeSessionId.value) {
      await router.replace({ path: '/ai', query: { sessionId: activeSessionId.value, projectId: activeProjectId.value } })
      await loadSession(activeSessionId.value, activeProjectId.value)
    }
    await loadSessions()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '问答失败')
  } finally {
    sending.value = false
  }
}

watch(
  () => [route.params.id, route.query.projectId, route.query.sessionId],
  () => {
    void syncFromRoute()
  },
  { immediate: true },
)

onMounted(async () => {
  await Promise.all([loadProjects(), loadSessions()])
  await syncFromRoute()
})
</script>

<style scoped>
.ai-layout {
  align-items: start;
}

.ai-session-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.ai-session-card {
  padding: 14px;
}

.ai-session-card__body {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: start;
}

.ai-session-card__title {
  font-weight: 600;
}

.ai-project-banner {
  padding: 18px;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: start;
}

.ai-project-banner__title {
  font-size: 22px;
  font-weight: 700;
  margin-top: 4px;
}

.ai-project-banner__actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.ai-chat-box {
  min-height: 360px;
  padding: 16px;
}

.ai-message {
  padding: 14px;
  border-radius: 16px;
  background: #ffffff;
}

.ai-message.is-user {
  background: #ecfccb;
}

.ai-message__role {
  font-size: 12px;
  color: var(--muted);
  margin-bottom: 8px;
}

.ai-message__content {
  white-space: pre-wrap;
  line-height: 1.7;
}

.ai-message__citations {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.ai-input-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 120px;
  gap: 12px;
}

.project-picker {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.project-picker__item {
  text-align: left;
  border: 1px solid var(--line);
  border-radius: 18px;
  padding: 16px;
  background: #fff;
  cursor: pointer;
}

.project-picker__item.is-active,
.project-picker__item:hover {
  border-color: rgba(20, 83, 45, 0.4);
  box-shadow: 0 16px 40px rgba(21, 31, 45, 0.08);
}

.project-picker__title {
  font-size: 18px;
  font-weight: 700;
}

.project-picker__desc {
  margin: 8px 0 12px;
  color: var(--muted);
}

@media (max-width: 900px) {
  .ai-project-banner,
  .ai-input-row,
  .project-picker {
    grid-template-columns: 1fr;
  }

  .ai-project-banner {
    flex-direction: column;
  }

  .ai-input-row {
    display: flex;
    flex-direction: column;
  }
}
</style>
