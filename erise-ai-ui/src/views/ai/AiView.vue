<template>
  <div class="grid-2" style="align-items: start">
    <el-card class="glass-card" shadow="never">
      <template #header>会话列表</template>
      <div class="section-stack">
        <el-button type="primary" plain @click="resetConversation">新会话</el-button>
        <div v-for="session in sessions" :key="session.id" class="glass-card" style="padding: 14px">
          <div style="display: flex; justify-content: space-between; gap: 12px">
            <div>
              <div style="font-weight: 600">{{ session.title }}</div>
              <div class="meta-row">
                <span>项目 #{{ session.projectId }}</span>
                <span>{{ session.lastMessageAt || session.createdAt }}</span>
              </div>
            </div>
            <el-button text @click="loadSession(session.id, session.projectId)">打开</el-button>
          </div>
        </div>
      </div>
    </el-card>

    <el-card class="glass-card" shadow="never">
      <template #header>AI 助手</template>
      <div class="section-stack">
        <el-input-number v-model="activeProjectId" :min="1" placeholder="项目 ID" />
        <div class="glass-card" style="padding: 16px; min-height: 360px">
          <div v-if="messages.length" class="section-stack">
            <div
              v-for="message in messages"
              :key="message.id || message.createdAt"
              :style="{ padding: '14px', borderRadius: '16px', background: message.roleCode === 'USER' ? '#ecfccb' : '#ffffff' }"
            >
              <div style="font-size: 12px; color: var(--muted); margin-bottom: 8px">{{ message.roleCode }}</div>
              <div style="white-space: pre-wrap; line-height: 1.7">{{ message.content }}</div>
              <div v-if="message.citations?.length" style="margin-top: 12px; display: flex; flex-wrap: wrap; gap: 8px">
                <el-tag v-for="citation in message.citations" :key="`${citation.sourceType}-${citation.sourceId}`" type="success">
                  {{ citation.sourceTitle }}
                </el-tag>
              </div>
            </div>
          </div>
          <div v-else class="empty-box">输入问题开始当前项目的知识问答。</div>
        </div>
        <div style="display: grid; grid-template-columns: 1fr 120px; gap: 12px">
          <el-input v-model="question" type="textarea" :rows="3" placeholder="输入你的问题" />
          <el-button type="primary" :loading="sending" @click="send">发送</el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { getSession, getSessions } from '@/api/ai'
import type { AiMessageView, AiSessionSummaryView } from '@/types/models'

const route = useRoute()
const sessions = ref<AiSessionSummaryView[]>([])
const messages = ref<AiMessageView[]>([])
const activeSessionId = ref<number | undefined>(route.query.sessionId ? Number(route.query.sessionId) : undefined)
const activeProjectId = ref<number | undefined>(
  route.params.id ? Number(route.params.id) : route.query.projectId ? Number(route.query.projectId) : undefined,
)
const question = ref('')
const sending = ref(false)
const apiBase = import.meta.env.VITE_API_BASE_URL

const loadSessions = async () => {
  sessions.value = await getSessions()
}

const loadSession = async (sessionId: number, projectId?: number) => {
  const session = await getSession(sessionId)
  activeSessionId.value = sessionId
  activeProjectId.value = projectId ?? session.projectId
  messages.value = session.messages
}

const send = async () => {
  if (!question.value || !activeProjectId.value) {
    return
  }
  sending.value = true
  try {
    const prompt = question.value
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
      await loadSession(activeSessionId.value, activeProjectId.value)
    }
    await loadSessions()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '问答失败')
  } finally {
    sending.value = false
  }
}

const resetConversation = () => {
  activeSessionId.value = undefined
  messages.value = []
}

onMounted(async () => {
  await loadSessions()
  if (activeSessionId.value) {
    await loadSession(activeSessionId.value, activeProjectId.value)
  }
})
</script>
