<template>
  <div v-if="project" class="page-shell">

    <AppPageHeader :title="project.name" :subtitle="project.description || '在这里查看项目状态、最近文件、最近文档和 AI 协作入口。'" show-back
      back-label="返回项目中心" back-to="/projects">
      <template #meta>
        <AppStatusTag :label="projectStatusLabel(project.projectStatus)"
          :tone="projectStatusTone(project.projectStatus)" />
      </template>
    </AppPageHeader>

    <ProjectSubnav :project-id="projectId" />

    <div class="grid-2">
      <AppSectionCard title="最近浏览文件">
        <template #actions>
          <el-button text @click="$router.push(`/projects/${projectId}/files`)">查看全部文件</el-button>
        </template>
        <div v-if="files.length" class="overview-list">
          <article v-for="file in files" :key="file.id" class="overview-row">
            <div>
              <div class="overview-row__title">{{ file.fileName }}</div>
              <div class="meta-row">
                <span>{{ normalizeFileTypeLabel(file.fileExt, file.mimeType) }}</span>
                <span>{{ formatFileSize(file.fileSize) }}</span>
                <span>{{ formatDateTime(file.updatedAt) }}</span>
              </div>
            </div>
            <div class="table-actions">
              <el-button text @click="$router.push(`/files/${file.id}`)">详情</el-button>
              <el-button v-if="isOfficeEditableFile(file.fileExt)" text
                @click="$router.push(`/files/${file.id}/edit`)">在线编辑</el-button>
            </div>
          </article>
        </div>
        <AppEmptyState v-else title="项目内还没有文件" />

      </AppSectionCard>

      <AppSectionCard title="最近浏览文档">
        <template #actions>
          <el-button text @click="$router.push(`/projects/${projectId}/documents`)">查看全部文档</el-button>
        </template>
        <div v-if="documents.length" class="overview-list">
          <article v-for="document in documents" :key="document.id" class="overview-row">
            <div>
              <div class="overview-row__title">{{ document.title }}</div>
              <div class="meta-row">
                <span>{{ documentStatusLabel(document.docStatus) }}</span>
                <span>创建于 {{ formatDateTime(document.createdAt) }}</span>
                <span>更新于 {{ formatDateTime(document.updatedAt) }}</span>
              </div>
            </div>
            <div class="table-actions">
              <el-button text
                @click="$router.push({ path: `/documents/${document.id}/edit`, query: { mode: 'preview' } })">浏览</el-button>
              <el-button text @click="$router.push(`/documents/${document.id}/edit`)">编辑</el-button>
            </div>
          </article>
        </div>
        <AppEmptyState v-else title="项目内还没有文档" />
      </AppSectionCard>
    </div>

    <!-- <AppSectionCard title="重点入口" description="保留最常用的项目级扩展入口，减少和上方列表区的重复跳转。">
      <div class="entry-grid">
        <button class="entry-card" @click="$router.push(`/projects/${projectId}/contents/sheet`)">
          <strong>结构化内容</strong>
          <span>维护表格、画板和数据表，让 AI 能引用结构化信息。</span>
        </button>
        <button class="entry-card" @click="$router.push(`/projects/${projectId}/ai`)">
          <strong>AI 助理</strong>
          <span>结合当前项目上下文继续总结、修改与核对内容。</span>
        </button>
      </div>
    </AppSectionCard> -->
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getProject } from '@/api/project'
import { getDocuments } from '@/api/document'
import { getFiles } from '@/api/file'
import AppEmptyState from '@/components/common/AppEmptyState.vue'
import AppPageHeader from '@/components/common/AppPageHeader.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'
import AppStatCard from '@/components/common/AppStatCard.vue'
import AppStatusTag from '@/components/common/AppStatusTag.vue'
import ProjectSubnav from '@/components/common/ProjectSubnav.vue'
import type { DocumentSummaryView, FileView, ProjectDetailView } from '@/types/models'
import { documentStatusLabel, formatDateTime, formatFileSize, isOfficeEditableFile, normalizeFileTypeLabel, projectStatusLabel, projectStatusTone } from '@/utils/formatters'

const props = defineProps<{ id: string }>()
const projectId = Number(props.id)
const project = ref<ProjectDetailView>()
const files = ref<FileView[]>([])
const documents = ref<DocumentSummaryView[]>([])

const load = async () => {
  const [projectDetail, filePage, documentPage] = await Promise.all([
    getProject(projectId),
    getFiles({ projectId, pageNum: 1, pageSize: 4 }),
    getDocuments({ projectId, pageNum: 1, pageSize: 4 }),
  ])
  project.value = projectDetail
  files.value = filePage.records
  documents.value = documentPage.records
}

onMounted(load)
</script>

<style scoped>
.overview-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.overview-row,
.entry-card {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  padding: 18px;
  border-radius: var(--radius-md);
  border: 1px solid var(--line);
  background: var(--surface-strong);
  text-align: left;
}

.overview-row__title {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.03em;
  margin-bottom: 8px;
}

.entry-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.entry-card {
  flex-direction: column;
  cursor: pointer;
}

.entry-card strong {
  font-size: 18px;
}

.entry-card span {
  color: var(--muted);
  line-height: 1.7;
}

@media (max-width: 1180px) {
  .entry-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .overview-row {
    flex-direction: column;
  }

  .entry-grid {
    grid-template-columns: 1fr;
  }
}
</style>
