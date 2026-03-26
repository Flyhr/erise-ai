<template>
  <div class="section-stack">
    <div class="page-header">
      <div>
        <h1>工作台</h1>
        <div class="page-subtitle">聚焦最近项目与 AI 会话，快速回到当前知识上下文。</div>
      </div>
      <el-button type="primary" @click="$router.push('/projects')">进入项目中心</el-button>
    </div>

    <div class="grid-2">
      <el-card class="glass-card" shadow="never">
        <template #header>最近项目</template>
        <el-skeleton v-if="loading" :rows="4" animated />
        <div v-else-if="projects.length" class="section-stack">
          <div v-for="project in projects" :key="project.id" class="glass-card" style="padding: 16px">
            <div style="display: flex; justify-content: space-between; gap: 12px">
              <div>
                <div style="font-size: 16px; font-weight: 600">{{ project.name }}</div>
                <div class="meta-row">
                  <span>{{ project.fileCount }} 文件</span>
                  <span>{{ project.documentCount }} 文档</span>
                  <span>{{ project.projectStatus }}</span>
                </div>
              </div>
              <el-button text @click="$router.push(`/projects/${project.id}`)">查看</el-button>
            </div>
          </div>
        </div>
        <div v-else class="empty-box">还没有项目，先创建一个开始。</div>
      </el-card>

      <el-card class="glass-card" shadow="never">
        <template #header>最近 AI 会话</template>
        <el-skeleton v-if="loading" :rows="4" animated />
        <div v-else-if="sessions.length" class="section-stack">
          <div v-for="session in sessions" :key="session.id" class="glass-card" style="padding: 16px">
            <div style="display: flex; justify-content: space-between; gap: 12px">
              <div>
                <div style="font-size: 16px; font-weight: 600">{{ session.title }}</div>
                <div class="meta-row">
                  <span>项目 #{{ session.projectId }}</span>
                  <span>{{ session.lastMessageAt || session.createdAt }}</span>
                </div>
              </div>
              <el-button text @click="$router.push({ path: '/ai', query: { sessionId: session.id, projectId: session.projectId } })">继续</el-button>
            </div>
          </div>
        </div>
        <div v-else class="empty-box">还没有 AI 会话，先在项目里提一个问题。</div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getProjects } from '@/api/project'
import { getSessions } from '@/api/ai'
import type { AiSessionSummaryView, ProjectDetailView } from '@/types/models'

const loading = ref(true)
const projects = ref<ProjectDetailView[]>([])
const sessions = ref<AiSessionSummaryView[]>([])

const load = async () => {
  loading.value = true
  try {
    const [projectPage, aiSessions] = await Promise.all([
      getProjects({ pageNum: 1, pageSize: 5 }),
      getSessions(),
    ])
    projects.value = projectPage.records
    sessions.value = aiSessions.slice(0, 5)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
