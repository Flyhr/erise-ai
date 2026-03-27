<template>
  <div class="workspace-stack section-stack">
    <section class="workspace-hero glass-card">
      <div>
        <div class="workspace-hero__eyebrow">Workspace</div>
        <h1 class="workspace-hero__title">围绕项目知识的工作首页</h1>
        <div class="page-subtitle">
          把最近项目、最近 AI 会话、常用入口和能力概览集中到一个首页里。管理员账号也先从普通工作台进入，不再被迫停留在后台首页。
        </div>
      </div>

      <div class="workspace-hero__actions">
        <el-button type="primary" @click="router.push('/projects')">进入项目中心</el-button>
        <el-button plain @click="router.push('/ai')">打开 AI 助手</el-button>
        <el-button v-if="authStore.isAdmin" plain @click="router.push('/admin')">管理后台</el-button>
      </div>
    </section>

    <div class="grid-4 workspace-metrics">
      <div v-for="metric in metrics" :key="metric.label" class="glass-card metric-card">
        <div class="metric-card__label">{{ metric.label }}</div>
        <div class="metric-card__value">{{ metric.value }}</div>
        <div class="metric-card__hint">{{ metric.hint }}</div>
      </div>
    </div>

    <div class="workspace-action-grid">
      <button v-for="action in quickActions" :key="action.title" type="button" class="quick-card" @click="router.push(action.route)">
        <div class="quick-card__title">{{ action.title }}</div>
        <div class="quick-card__desc">{{ action.description }}</div>
      </button>
    </div>

    <div class="grid-2 workspace-main-grid">
      <el-card class="glass-card workspace-panel" shadow="never">
        <template #header>
          <div class="workspace-panel__header">
            <div>
              <div class="workspace-panel__title">最近项目</div>
              <div class="workspace-panel__subtitle">从这里继续文档、文件、结构化内容和 AI 工作。</div>
            </div>
            <el-button text @click="router.push('/projects')">查看全部</el-button>
          </div>
        </template>

        <el-skeleton v-if="loading" :rows="5" animated />
        <div v-else-if="projects.length" class="project-grid">
          <article v-for="project in projects" :key="project.id" class="project-card">
            <div class="project-card__title-row">
              <div>
                <div class="project-card__title">{{ project.name }}</div>
                <div class="project-card__desc">{{ project.description || '暂无项目描述' }}</div>
              </div>
              <span class="status-pill">{{ statusLabel(project.projectStatus) }}</span>
            </div>
            <div class="meta-row">
              <span>{{ project.fileCount }} 个文件</span>
              <span>{{ project.documentCount }} 篇文档</span>
              <span>{{ formatTime(project.updatedAt) }}</span>
            </div>
            <div class="project-card__actions">
              <el-button text @click="router.push(`/projects/${project.id}`)">项目详情</el-button>
              <el-button text @click="router.push(`/projects/${project.id}/documents`)">文档</el-button>
              <el-button text @click="router.push(`/projects/${project.id}/ai`)">AI</el-button>
            </div>
          </article>
        </div>
        <div v-else class="empty-box">还没有项目，先创建一个开始沉淀知识。</div>
      </el-card>

      <el-card class="glass-card workspace-panel" shadow="never">
        <template #header>
          <div class="workspace-panel__header">
            <div>
              <div class="workspace-panel__title">最近 AI 会话</div>
              <div class="workspace-panel__subtitle">继续追问、补充上下文或查看已有对话。</div>
            </div>
            <el-button text @click="router.push('/ai')">进入 AI 助手</el-button>
          </div>
        </template>

        <el-skeleton v-if="loading" :rows="5" animated />
        <div v-else-if="sessions.length" class="session-grid">
          <article v-for="session in sessions" :key="session.id" class="session-card">
            <div class="session-card__title">{{ session.title }}</div>
            <div class="meta-row">
              <span>{{ projectName(session.projectId) }}</span>
              <span>{{ formatTime(session.lastMessageAt || session.createdAt) }}</span>
            </div>
            <div class="session-card__hint">通用聊天和项目上下文聊天都会汇总到这里，后续可以继续追问。</div>
            <div class="session-card__actions">
              <el-button text @click="openSession(session.id, session.projectId)">继续对话</el-button>
              <el-button v-if="session.projectId" text @click="router.push(`/projects/${session.projectId}/ai`)">进入项目 AI</el-button>
            </div>
          </article>
        </div>
        <div v-else class="empty-box">还没有 AI 会话，先去 AI 助手发起第一条消息。</div>
      </el-card>
    </div>

    <div class="grid-3 capability-grid">
      <div class="glass-card capability-card">
        <div class="capability-card__title">知识沉淀</div>
        <div class="capability-card__copy">围绕项目集中管理文档、文件和结构化内容，形成可持续积累的知识库。</div>
      </div>
      <div class="glass-card capability-card">
        <div class="capability-card__title">结构化协作</div>
        <div class="capability-card__copy">表格、画板和数据表让信息不只停留在正文，还能进入结构化表达和复用。</div>
      </div>
      <div class="glass-card capability-card">
        <div class="capability-card__title">AI 追问与引用</div>
        <div class="capability-card__copy">AI 助手既能直接通用聊天，也能在需要时附加项目上下文，形成持续会话。</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import dayjs from 'dayjs'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getProjects } from '@/api/project'
import { getSessions } from '@/api/ai'
import { useAuthStore } from '@/stores/auth'
import type { AiSessionSummaryView, ProjectDetailView } from '@/types/models'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(true)
const projectTotal = ref(0)
const projects = ref<ProjectDetailView[]>([])
const sessions = ref<AiSessionSummaryView[]>([])

const projectLookup = computed(() => new Map(projects.value.map((project) => [project.id, project.name])))
const totalFiles = computed(() => projects.value.reduce((sum, project) => sum + project.fileCount, 0))
const totalDocuments = computed(() => projects.value.reduce((sum, project) => sum + project.documentCount, 0))

const metrics = computed(() => [
  {
    label: '项目总数',
    value: projectTotal.value,
    hint: '工作空间里的知识容器',
  },
  {
    label: '最近会话',
    value: sessions.value.length,
    hint: '最近继续中的 AI 对话',
  },
  {
    label: '文件数量',
    value: totalFiles.value,
    hint: '最近项目中的文件规模',
  },
  {
    label: '文档数量',
    value: totalDocuments.value,
    hint: '最近项目中的文档规模',
  },
])

const quickActions = [
  {
    title: '新建项目',
    description: '为新的知识主题或业务方向建立独立空间。',
    route: '/projects',
  },
  {
    title: '上传资料',
    description: '把 PDF、Word、Markdown 等资料纳入项目上下文。',
    route: '/projects',
  },
  {
    title: '结构化内容',
    description: '维护表格、画板和数据表，补足正文之外的信息组织。',
    route: '/projects',
  },
  {
    title: 'AI 助手',
    description: '直接发问，或者按需附加项目上下文继续对话。',
    route: '/ai',
  },
]

const formatTime = (value?: string) => (value ? dayjs(value).format('MM-DD HH:mm') : '--')
const projectName = (projectId?: number) => (projectId ? projectLookup.value.get(projectId) || `项目 #${projectId}` : '通用对话')
const statusLabel = (status: string) => ({ ACTIVE: '进行中', ARCHIVED: '已归档', DRAFT: '草稿' }[status] || status)

const openSession = (sessionId: number, projectId?: number) => {
  const query: Record<string, number> = { sessionId }
  if (projectId) {
    query.projectId = projectId
  }
  router.push({ path: '/ai', query })
}

const load = async () => {
  loading.value = true
  try {
    const [projectPage, aiSessions] = await Promise.all([
      getProjects({ pageNum: 1, pageSize: 8 }),
      getSessions(),
    ])
    projectTotal.value = projectPage.total
    projects.value = projectPage.records
    sessions.value = aiSessions.slice(0, 6)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.workspace-stack {
  gap: 22px;
}

.workspace-hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: flex-start;
  padding: 28px;
}

.workspace-hero__eyebrow {
  font-size: 12px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--muted);
}

.workspace-hero__title {
  margin: 10px 0 0;
  font-size: clamp(30px, 4vw, 44px);
  letter-spacing: -0.04em;
}

.workspace-hero__actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.metric-card {
  padding: 20px;
}

.metric-card__label {
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.metric-card__value {
  margin-top: 10px;
  font-size: 32px;
  font-weight: 800;
  letter-spacing: -0.05em;
}

.metric-card__hint {
  margin-top: 10px;
  color: var(--muted);
  line-height: 1.7;
}

.workspace-action-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.quick-card,
.project-card,
.session-card,
.capability-card {
  padding: 18px;
  border-radius: 22px;
  border: 1px solid var(--line);
  background: var(--surface-strong);
  color: inherit;
  text-align: left;
}

.quick-card {
  cursor: pointer;
}

.quick-card__title,
.workspace-panel__title,
.capability-card__title,
.project-card__title,
.session-card__title {
  font-size: 18px;
  font-weight: 700;
}

.quick-card__desc,
.workspace-panel__subtitle,
.capability-card__copy,
.project-card__desc,
.session-card__hint {
  margin-top: 8px;
  color: var(--muted);
  line-height: 1.7;
}

.workspace-panel__header,
.project-card__title-row,
.project-card__actions,
.session-card__actions {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.project-grid,
.session-grid {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.project-card__actions,
.session-card__actions {
  margin-top: 12px;
}

.capability-grid {
  gap: 16px;
}

@media (max-width: 1180px) {
  .workspace-action-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .workspace-hero,
  .workspace-panel__header,
  .project-card__title-row,
  .project-card__actions,
  .session-card__actions {
    flex-direction: column;
  }

  .workspace-action-grid {
    grid-template-columns: 1fr;
  }
}
</style>