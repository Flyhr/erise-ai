<template>
  <div class="workspace-content">

    <WorkspaceSearchBar>
      v-model="searchKeyword"
      :placeholder="searchPlaceholder"
      @search="openSearch"
    </WorkspaceSearchBar>
    <section class="workspace-metric-grid">
      <article class="workspace-chart-card workspace-card">
        <div class="workspace-card-head">
          <h3>Active Projects Status</h3>
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
            <span>Storage Usage</span>
            <span class="material-symbols-outlined">cloud_done</span>
          </div>
          <div class="workspace-storage-value">{{ totalFiles }} 个文件</div>
          <p>跨项目沉淀的知识文件总量</p>
          <div class="workspace-progress-track">
            <div class="workspace-progress-fill" :style="{ width: storageProgress }" />
          </div>
        </article>

        <article class="workspace-insight-card workspace-card">
          <h3>
            <span class="material-symbols-outlined">bolt</span>
            Quick Insights
          </h3>
          <p>
            当前共有 <strong>{{ totalDocuments }}</strong> 份文档、<strong>{{ sessions.length }}</strong>
            个近期 AI 会话，可直接继续处理。
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

<style scoped>
.workspace-content {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.workspace-hero,
.workspace-card,
.workspace-table-shell {
  border-radius: 24px;
  border: 1px solid #dde4ef;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.04);
}

.workspace-hero {
  padding: 26px 28px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.workspace-breadcrumb,
.workspace-switcher,
.workspace-card-head,
.workspace-storage-head,
.workspace-inline-actions,
.workspace-section-head {
  display: flex;
  align-items: center;
}

.workspace-breadcrumb {
  gap: 6px;
  color: #0060a9;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  margin-bottom: 10px;
}

.workspace-hero h1,
.workspace-section-head h2,
.workspace-card-head h3,
.workspace-insight-card h3,
.workspace-storage-value {
  margin: 0;
  font-family: 'Manrope', 'Plus Jakarta Sans', sans-serif;
}

.workspace-hero h1 {
  font-size: 36px;
  line-height: 1.1;
  letter-spacing: -0.05em;
  margin-bottom: 8px;
}

.workspace-section-head p,
.workspace-table-desc,
.workspace-insight-card p {
  color: #667085;
}

.workspace-section-head p {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
}

.workspace-section-count {
  color: #667085;
  font-size: 13px;
  font-weight: 600;
}

.workspace-switcher {
  gap: 6px;
  padding: 5px;
  border-radius: 16px;
  background: #eef3f9;
}

.workspace-switcher button,
.workspace-tool-btn,
.workspace-subtab,
.workspace-fab {
  border: 0;
  cursor: pointer;
  transition: all 0.22s ease;
}

.workspace-switcher button {
  padding: 10px 16px;
  border-radius: 12px;
  background: transparent;
  font-size: 13px;
  font-weight: 700;
  color: #667085;
}

.workspace-switcher button:hover,
.workspace-tool-btn:hover {
  background: #edf5ff;
  color: #0060a9;
}

.workspace-switcher .is-selected {
  background: #fff;
  color: #0060a9;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.05);
}

.workspace-metric-grid {
  display: grid;
  gap: 22px;
  grid-template-columns: minmax(0, 1.6fr) minmax(320px, 0.9fr);
}

.workspace-section-card,
.workspace-chart-card,
.workspace-storage-card,
.workspace-insight-card {
  padding: 24px;
}

.workspace-card-head,
.workspace-section-head,
.workspace-storage-head {
  justify-content: space-between;
  gap: 16px;
}

.workspace-card-head h3,
.workspace-section-head h2 {
  font-size: 22px;
  line-height: 1.1;
  letter-spacing: -0.04em;
}

.workspace-trend-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: rgba(64, 158, 255, 0.12);
  color: #0060a9;
  font-size: 12px;
  font-weight: 700;
  padding: 6px 10px;
  border-radius: 999px;
}

.workspace-bar-chart {
  margin-top: 28px;
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 10px;
  align-items: end;
}

.workspace-bar-column {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.workspace-bar-track {
  width: 100%;
  height: 148px;
  display: flex;
  align-items: flex-end;
}

.workspace-bar-fill {
  width: 100%;
  border-radius: 18px 18px 8px 8px;
  background: rgba(64, 158, 255, 0.16);
}

.workspace-bar-fill.is-accent {
  background: linear-gradient(180deg, #409eff 0%, #0060a9 100%);
}

.workspace-bar-column span {
  font-size: 11px;
  color: #667085;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.workspace-side-metrics {
  display: grid;
  gap: 22px;
}

.workspace-storage-card {
  border-radius: 24px;
  color: #fff;
  background: linear-gradient(135deg, #55af28 0%, #286c00 100%);
  box-shadow: 0 22px 48px rgba(40, 108, 0, 0.22);
}

.workspace-storage-head span:first-child {
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  opacity: 0.88;
}

.workspace-storage-value {
  font-size: 34px;
  line-height: 1.1;
  margin-top: 12px;
}

.workspace-storage-card p {
  margin: 8px 0 16px;
  color: rgba(255, 255, 255, 0.85);
  font-size: 13px;
}

.workspace-progress-track {
  width: 100%;
  height: 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.26);
  overflow: hidden;
}

.workspace-progress-fill {
  height: 100%;
  border-radius: inherit;
  background: #fff;
}

.workspace-insight-card h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  margin-bottom: 12px;
}

.workspace-insight-card p {
  margin: 0;
  font-size: 14px;
  line-height: 1.75;
}

.workspace-knowledge-section {
  scroll-margin-top: 24px;
}

.workspace-section-tools {
  display: flex;
  gap: 10px;
}

.workspace-tool-btn,
.workspace-subtab {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 14px;
  background: #f2f5fa;
  color: #667085;
  font-size: 13px;
  font-weight: 700;
}

.workspace-subtabs {
  display: flex;
  gap: 8px;
  margin: 18px 0 20px;
}

.workspace-subtab.is-active {
  background: linear-gradient(135deg, #409eff 0%, #0060a9 100%);
  color: #fff;
  box-shadow: 0 14px 30px rgba(0, 96, 169, 0.16);
}

.workspace-table-shell {
  overflow: hidden;
}

.workspace-data-table {
  width: 100%;
  border-collapse: collapse;
}

.workspace-data-table thead {
  background: #f4f7fb;
}

.workspace-data-table th,
.workspace-data-table td {
  padding: 16px 18px;
  border-bottom: 1px solid #e6ebf2;
  text-align: left;
  vertical-align: top;
}

.workspace-data-table th {
  font-size: 12px;
  color: #667085;
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.workspace-data-table td {
  font-size: 13px;
  color: #344054;
}

.workspace-file-name {
  font-weight: 800;
  letter-spacing: -0.02em;
  color: #1f2937;
  font-size: 16px;
  margin-bottom: 6px;
}

.workspace-table-desc {
  font-size: 13px;
  line-height: 1.65;
}

.workspace-inline-actions {
  gap: 4px;
  flex-wrap: wrap;
}

.align-right {
  text-align: right !important;
}

.workspace-fab {
  position: fixed;
  right: 34px;
  bottom: 34px;
  width: 58px;
  height: 58px;
  border-radius: 999px;
  color: #fff;
  background: linear-gradient(135deg, #409eff 0%, #0060a9 100%);
  box-shadow: 0 22px 38px rgba(0, 96, 169, 0.32);
}

.workspace-fab:hover,
.workspace-subtab:hover {
  transform: translateY(-1px);
}

@media (max-width: 960px) {
  .workspace-metric-grid {
    grid-template-columns: 1fr;
  }

  .workspace-hero,
  .workspace-section-head,
  .workspace-card-head {
    flex-direction: column;
    align-items: stretch;
  }

  .workspace-section-tools {
    justify-content: flex-start;
  }
}

@media (max-width: 720px) {
  .workspace-content {
    gap: 16px;
  }

  .workspace-hero,
  .workspace-section-card,
  .workspace-chart-card,
  .workspace-storage-card,
  .workspace-insight-card {
    border-radius: 18px;
  }

  .workspace-data-table,
  .workspace-data-table thead,
  .workspace-data-table tbody,
  .workspace-data-table tr,
  .workspace-data-table th,
  .workspace-data-table td {
    display: block;
    width: 100%;
  }

  .workspace-data-table thead {
    display: none;
  }

  .workspace-data-table tr {
    padding: 14px 0;
  }

  .workspace-data-table td {
    padding: 8px 0;
    border-bottom: 0;
  }

  .align-right {
    text-align: left !important;
  }
}
</style>
