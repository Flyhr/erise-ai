<template>
  <div class="workspace-content">

    <WorkspaceSearchBar search-placeholder="搜索项目、文件和文档" />
    <section class="workspace-metric-grid">
      <article class="workspace-chart-card workspace-card">
        <div class="workspace-card-head">
          <h3>项目活动</h3>
          <span class="workspace-trend-badge">
            <span class="material-symbols-outlined">trending_up</span>
            {{ projectTotal }} 个项目
          </span>
        </div>
        <div class="workspace-bar-chart">
          <div v-for="(metric, index) in projectBars" :key="metric.label" class="workspace-bar-column">
            <div class="workspace-bar-track">
              <div class="workspace-bar-fill" :class="{ 'is-accent': index === 2 }"
                :style="{ height: metric.height }" />
            </div>
            <span>{{ metric.label }}</span>
          </div>
        </div>
      </article>

      <div class="workspace-side-metrics">
        <article class="workspace-storage-card">
          <div class="workspace-storage-head">
            <span>存储文档数量</span>
            <span class="material-symbols-outlined">cloud_done</span>
          </div>
          <div class="workspace-storage-value">{{ totalFiles }} 个文档</div>
          <p>真会偷懒啊！！！</p>
          <div class="workspace-progress-track">
            <div class="workspace-progress-fill" :style="{ width: storageProgress }" />
          </div>
        </article>

        <article class="workspace-insight-card workspace-card">
          <h3>
            <span class="material-symbols-outlined">bolt</span>
            概览
          </h3>
          <p>
            当前共有 <strong>{{ totalDocuments }}</strong> 份文件、 文档、<strong>{{ sessions.length }}</strong>
            个近期 AI 会话，。
          </p>
        </article>
      </div>
    </section>

    <section id="knowledge-base" class="workspace-card workspace-section-card workspace-knowledge-section">
      <div class="workspace-section-head">
        <div>
          <div class="workspace-switcher">
            <button type="button" :class="{ 'is-selected': recentMode === 'viewed' }"
              @click="switchRecentMode('viewed')">
              最近浏览
            </button>
            <button type="button" :class="{ 'is-selected': recentMode === 'edited' }"
              @click="switchRecentMode('edited')">
              最近编辑
            </button>
          </div>
        </div>

        <div class="workspace-section-tools">
          <span class="workspace-section-count">共 {{ recentItems.length }} 条{{ recentMode === 'viewed' ? '浏览' : '编辑'
          }}记录</span>
        </div>
      </div>

      <div class="workspace-subtabs">
        <button type="button" :class="['workspace-subtab', { 'is-active': knowledgeTab === 'files' }]"
          @click="knowledgeTab = 'files'">
          文件
        </button>
        <button type="button" :class="['workspace-subtab', { 'is-active': knowledgeTab === 'documents' }]"
          @click="knowledgeTab = 'documents'">
          文档
        </button>
      </div>

      <div v-if="knowledgeTab === 'files'">
        <div v-if="recentItems.length" class="workspace-table-shell">
          <table class="workspace-data-table">
            <thead>
              <tr>
                <th>文件名</th>
                <th>项目</th>
                <th>类型 / 大小</th>
                <th>最近操作时间</th>
                <th class="align-right">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="file in recentItems" :key="`${file.assetType}-${file.assetId}`">
                <td>
                  <div class="workspace-file-name">{{ file.title }}</div>
                  <div class="workspace-table-desc">{{ recentActionLabel(file.actionCode) }}</div>
                </td>
                <td>{{ projectName(file.projectId) }}</td>
                <td>{{ normalizeFileTypeLabel(file.fileExt, file.mimeType) }} · {{ formatFileSize(file.fileSize) }}</td>
                <td>{{ formatDateTime(file.lastActionAt) }}</td>
                <td class="align-right">
                  <div class="workspace-inline-actions">
                    <el-button text @click="router.push(`/files/${file.assetId}`)">详情</el-button>
                    <el-button text @click="previewFile(file)">预览</el-button>
                    <el-button v-if="isOfficeEditableFile(file.fileExt)" text
                      @click="router.push(`/files/${file.assetId}/edit`)">
                      编辑
                    </el-button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <AppEmptyState v-else title="还没有最近文件记录" description="进入文件详情、预览文件或在线编辑后，这里会展示最近操作记录。" />
      </div>

      <div v-else>
        <div v-if="recentItems.length" class="workspace-table-shell">
          <table class="workspace-data-table">
            <thead>
              <tr>
                <th>文档标题</th>
                <th>项目</th>
                <th>状态</th>
                <th>最近操作时间</th>
                <th class="align-right">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="document in recentItems" :key="`${document.assetType}-${document.assetId}`">
                <td>
                  <div class="workspace-file-name">{{ document.title }}</div>
                  <div class="workspace-table-desc">{{ document.summary || recentActionLabel(document.actionCode) }}
                  </div>
                </td>
                <td>{{ projectName(document.projectId) }}</td>
                <td>
                  <AppStatusTag :label="documentStatusLabel(document.docStatus)"
                    :tone="documentStatusTone(document.docStatus)" />
                </td>
                <td>{{ formatDateTime(document.lastActionAt) }}</td>
                <td class="align-right">
                  <div class="workspace-inline-actions">
                    <el-button text
                      @click="router.push({ path: `/documents/${document.assetId}/edit`, query: { mode: 'preview' } })">
                      浏览
                    </el-button>
                    <el-button text @click="router.push(`/documents/${document.assetId}/edit`)">编辑</el-button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <AppEmptyState v-else title="还没有最近文档记录" description="浏览文档、进入编辑页或保存发布后，这里会展示最近操作记录。" />
      </div>
    </section>
  </div>

  <button type="button" class="workspace-fab" @click="router.push('/ai')">
    <span class="material-symbols-outlined">smart_toy</span>
  </button>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getWorkspaceRecent } from '@/api/workspace'
import { getProjects } from '@/api/project'
import { getSessions } from '@/api/ai'
import { previewFileBinary, previewOfficeFile } from '@/api/file'
import AppEmptyState from '@/components/common/AppEmptyState.vue'
import AppStatusTag from '@/components/common/AppStatusTag.vue'
import type { AiSessionSummaryView, ProjectDetailView, WorkspaceRecentItemView } from '@/types/models'
import {
  documentStatusLabel,
  documentStatusTone,
  formatDateTime,
  formatFileSize,
  isOfficeEditableFile,
  normalizeFileTypeLabel,
  resolveErrorMessage,
} from '@/utils/formatters'
import WorkspaceSearchBar from '@/components/common/WorkspaceSearchBar.vue'

const router = useRouter()
const projectTotal = ref(0)
const projects = ref<ProjectDetailView[]>([])
const sessions = ref<AiSessionSummaryView[]>([])
const recentItems = ref<WorkspaceRecentItemView[]>([])
const knowledgeTab = ref<'files' | 'documents'>('files')
const recentMode = ref<'viewed' | 'edited'>('viewed')

const projectLookup = computed(() => new Map(projects.value.map((project) => [project.id, project.name])))
const totalFiles = computed(() => projects.value.reduce((sum, project) => sum + project.fileCount, 0))
const totalDocuments = computed(() => projects.value.reduce((sum, project) => sum + project.documentCount, 0))
const storageProgress = computed(() => {
  const total = totalFiles.value + totalDocuments.value
  if (!total) return '12%'
  const percent = Math.min(88, Math.max(18, Math.round((totalFiles.value / total) * 100)))
  return `${percent}%`
})
const projectBars = computed(() => {
  const source = projects.value.slice(0, 7)
  const fallback = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
  if (!source.length) {
    return fallback.map((label, index) => ({ label, height: `${35 + index * 7}%` }))
  }
  const maxValue = Math.max(...source.map((project) => project.fileCount + project.documentCount), 1)
  return source.map((project, index) => ({
    label: fallback[index] || `P${index + 1}`,
    height: `${Math.max(30, Math.round(((project.fileCount + project.documentCount) / maxValue) * 100))}%`,
  }))
})

const projectName = (projectId?: number) =>
  projectId ? projectLookup.value.get(projectId) || `项目 #${projectId}` : '未绑定项目'

const recentActionLabel = (actionCode?: string) =>
  ({
    FILE_PREVIEW: '最近预览',
    FILE_DETAIL_OPEN: '最近查看详情',
    FILE_EDIT_OPEN: '最近进入编辑',
    FILE_EDIT_SAVE: '最近保存文件',
    DOCUMENT_VIEW: '最近浏览',
    DOCUMENT_EDIT_OPEN: '最近进入编辑',
    DOCUMENT_SAVE: '最近保存文档',
    DOCUMENT_PUBLISH: '最近发布文档',
  })[actionCode || ''] || '最近操作'

const previewFile = async (file: WorkspaceRecentItemView) => {
  try {
    if (isOfficeEditableFile(file.fileExt)) {
      await previewOfficeFile(file.assetId)
      return
    }
    await previewFileBinary(file.assetId)
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '文件预览失败，请稍后重试'))
  }
}

const loadRecent = async () => {
  const page = await getWorkspaceRecent({
    mode: recentMode.value,
    assetType: knowledgeTab.value === 'files' ? 'FILE' : 'DOCUMENT',
    pageNum: 1,
    pageSize: 6,
  })
  recentItems.value = page.records
}

const switchRecentMode = async (mode: 'viewed' | 'edited') => {
  if (recentMode.value === mode) {
    return
  }
  recentMode.value = mode
  await loadRecent()
}

const load = async () => {
  try {
    const [projectPage, aiSessions] = await Promise.all([
      getProjects({ pageNum: 1, pageSize: 8 }),
      getSessions(),
    ])
    projectTotal.value = projectPage.total
    projects.value = projectPage.records
    sessions.value = aiSessions.slice(0, 6)
    await loadRecent()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '工作台数据加载失败，请稍后重试'))
  }
}

onMounted(load)

watch(knowledgeTab, async () => {
  await loadRecent()
})
</script>

<style scoped src="./css/WorkspaceView.css"></style>
