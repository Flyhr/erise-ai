<template>
  <div v-if="project" class="page-shell">
    <AppPageHeader
      :title="project.name"
      :subtitle="project.description || '在这里统一查看项目资料概览，并快速进入文件、文档或表格。'"
      show-back
      back-label="返回项目中心"
      back-to="/projects"
    >
      <template #meta>
        <AppStatusTag :label="projectStatusLabel(project.projectStatus)" :tone="projectStatusTone(project.projectStatus)" />
      </template>
    </AppPageHeader>

    <ProjectSubnav :project-id="projectId" />

    <AppSectionCard title="项目概览" :unpadded="Boolean(assets.length)">
      <AppDataTable v-if="assets.length" :data="assets" stripe>
        <el-table-column label="资源名称" min-width="200">
          <template #default="{ row }">
            <div class="asset-name-cell">
              <strong>{{ row.title }}</strong>
              <span>{{ row.summary || secondaryLine(row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <AppStatusTag :label="assetTypeLabel(row)" tone="info" />
          </template>
        </el-table-column>
        <el-table-column label="所属项目" min-width="180">
          <template #default>{{ project.name }}</template>
        </el-table-column>
        <el-table-column label="大小" width="140">
          <template #default="{ row }">{{ row.assetType === 'FILE' ? formatFileSize(row.fileSize) : '--' }}</template>
        </el-table-column>
        <el-table-column label="上传时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="220" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <template v-if="row.assetType === 'FILE'">
                <el-button text @click="$router.push(`/files/${row.assetId}`)">详情</el-button>
                <el-button v-if="isOfficeEditableFile(row.fileExt)" text @click="$router.push(`/files/${row.assetId}/edit`)">
                  编辑
                </el-button>
              </template>
              <template v-else>
                <el-button text @click="$router.push({ path: `/documents/${row.assetId}/edit`, query: { mode: 'preview' } })">
                  浏览
                </el-button>
                <el-button text @click="$router.push(`/documents/${row.assetId}/edit`)">编辑</el-button>
              </template>
            </div>
          </template>
        </el-table-column>
      </AppDataTable>
      <AppEmptyState
        v-else
        title="项目内还没有资料"
        description="上传文件或创建文档后，这里会统一展示该项目的最新知识资产。"
      />

      <template #footer>
        <div class="overview-footer">
          <span class="page-subtitle" style="margin: 0;">共 {{ total }} 条项目资料</span>
          <CompactPager :page-num="pageNum" :page-size="pageSize" :total="total" @change="handlePageChange" />
        </div>
      </template>
    </AppSectionCard>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getKnowledgeAssets } from '@/api/knowledge'
import { getProject } from '@/api/project'
import AppDataTable from '@/components/common/AppDataTable.vue'
import AppEmptyState from '@/components/common/AppEmptyState.vue'
import AppPageHeader from '@/components/common/AppPageHeader.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'
import AppStatusTag from '@/components/common/AppStatusTag.vue'
import CompactPager from '@/components/common/CompactPager.vue'
import ProjectSubnav from '@/components/common/ProjectSubnav.vue'
import type { KnowledgeAssetView, ProjectDetailView } from '@/types/models'
import {
  formatDateTime,
  formatFileSize,
  isOfficeEditableFile,
  normalizeFileTypeLabel,
  projectStatusLabel,
  projectStatusTone,
} from '@/utils/formatters'

const props = defineProps<{ id: string }>()

const route = useRoute()
const router = useRouter()
const projectId = Number(props.id)
const project = ref<ProjectDetailView>()
const assets = ref<KnowledgeAssetView[]>([])
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)

const assetTypeLabel = (row: KnowledgeAssetView) =>
  row.assetType === 'FILE' ? normalizeFileTypeLabel(row.fileExt, row.mimeType) : '在线文档'

const secondaryLine = (row: KnowledgeAssetView) => {
  if (row.assetType === 'FILE') {
    return `${normalizeFileTypeLabel(row.fileExt, row.mimeType)} · ${formatFileSize(row.fileSize)}`
  }
  return row.summary || '暂无摘要'
}

const load = async () => {
  const [projectDetail, assetPage] = await Promise.all([
    getProject(projectId),
    getKnowledgeAssets({ projectId, pageNum: pageNum.value, pageSize }),
  ])
  project.value = projectDetail
  assets.value = assetPage.records
  total.value = assetPage.total
}

const syncFromRoute = async () => {
  const nextPage = Number(route.query.pageNum)
  pageNum.value = Number.isFinite(nextPage) && nextPage > 0 ? nextPage : 1
  await load()
}

const pushRoute = async () => {
  await router.replace({
    path: route.path,
    query: {
      ...(pageNum.value > 1 ? { pageNum: pageNum.value } : {}),
    },
  })
}

const handlePageChange = async (value: number) => {
  pageNum.value = value
  await pushRoute()
}

onMounted(syncFromRoute)

watch(
  () => route.fullPath,
  async () => {
    await syncFromRoute()
  },
)
</script>

<style scoped>
.asset-name-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.asset-name-cell strong {
  font-size: 15px;
  font-weight: 700;
}

.asset-name-cell span {
  color: var(--muted);
  font-size: 12px;
}

.overview-footer {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}
</style>