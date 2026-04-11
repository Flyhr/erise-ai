<template>
  <div class="section-stack">
    <AppSectionCard title="项目文件管理" description="跨项目查看文件、文档与表格资料。" :unpadded="true">
      <template #actions>
        <div class="admin-assets__filters">
          <el-input
            v-model="keyword"
            clearable
            placeholder="搜索名称、摘要或文件类型"
            style="width: 280px"
            @clear="load"
            @keyup.enter="load"
          />
          <el-segmented v-model="activeTab" :options="tabOptions" @change="load" />
        </div>
      </template>

      <AppDataTable :data="assets" stripe>
        <el-table-column label="名称" min-width="280">
          <template #default="{ row }">
            <div class="admin-assets__title">
              <strong>{{ row.title }}</strong>
              <span>{{ secondaryLine(row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="项目" min-width="160">
          <template #default="{ row }">{{ projectLabel(row.projectId) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="140">
          <template #default="{ row }">
            <AppStatusTag :label="assetTypeLabel(row)" tone="info" />
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="150">
          <template #default="{ row }">
            <AppStatusTag :label="assetStatusLabel(row)" :tone="assetTone(row)" />
          </template>
        </el-table-column>
        <el-table-column label="大小" width="120">
          <template #default="{ row }">{{ row.assetType === 'FILE' ? formatFileSize(row.fileSize) : '--' }}</template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="220" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button text @click="previewAsset(row)">预览</el-button>
              <el-button v-if="canEditAsset(row)" text @click="editAsset(row)">修改</el-button>
              <el-button v-if="canDownloadAsset(row)" text @click="downloadAsset(row)">下载</el-button>
              <el-button v-if="canRetryAsset(row)" text @click="retryAsset(row)">重试</el-button>
            </div>
          </template>
        </el-table-column>
      </AppDataTable>
    </AppSectionCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { retryDocumentIndex } from '@/api/document'
import { downloadFileContent, retryFileParse } from '@/api/file'
import { getKnowledgeAssets } from '@/api/knowledge'
import AppDataTable from '@/components/common/AppDataTable.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'
import AppStatusTag from '@/components/common/AppStatusTag.vue'
import { useFilePreview } from '@/composables/useFilePreview'
import { useProjectDirectory } from '@/composables/useProjectDirectory'
import type { KnowledgeAssetView } from '@/types/models'
import {
  contentTypeLabel,
  formatDateTime,
  formatFileSize,
  isKnowledgeFailed,
  isOfficeEditableFile,
  knowledgeReadinessLabel,
  knowledgeReadinessTone,
  normalizeFileTypeLabel,
  resolveErrorMessage,
} from '@/utils/formatters'

type AdminAssetTab = 'overview' | 'files' | 'documents' | 'content'

const router = useRouter()
const { previewFile } = useFilePreview()
const { loadProjects, projectLabel } = useProjectDirectory()
const keyword = ref('')
const activeTab = ref<AdminAssetTab>('overview')
const assets = ref<KnowledgeAssetView[]>([])

const tabOptions = [
  { label: '概览', value: 'overview' },
  { label: '文件', value: 'files' },
  { label: '文档', value: 'documents' },
  { label: '表格', value: 'content' },
]

const selectedKnowledgeType = computed<'FILE' | 'DOCUMENT' | 'CONTENT' | undefined>(() => {
  if (activeTab.value === 'files') return 'FILE'
  if (activeTab.value === 'documents') return 'DOCUMENT'
  if (activeTab.value === 'content') return 'CONTENT'
  return undefined
})

const load = async () => {
  assets.value = (await getKnowledgeAssets({
    type: selectedKnowledgeType.value,
    q: keyword.value.trim() || undefined,
    knowledgeOnly: activeTab.value === 'overview' || activeTab.value === 'files',
    pageNum: 1,
    pageSize: 100,
  })).records
}

const assetTypeLabel = (row: KnowledgeAssetView) => {
  if (row.assetType === 'FILE') return normalizeFileTypeLabel(row.fileExt, row.mimeType)
  if (row.assetType === 'DOCUMENT') return '文档'
  return contentTypeLabel(row.itemType)
}

const assetStatusLabel = (row: KnowledgeAssetView) => knowledgeReadinessLabel(row.parseStatus, row.indexStatus)
const assetTone = (row: KnowledgeAssetView) => knowledgeReadinessTone(row.parseStatus, row.indexStatus)
const canRetryAsset = (row: KnowledgeAssetView) =>
  (row.assetType === 'FILE' || row.assetType === 'DOCUMENT') && isKnowledgeFailed(row.parseStatus, row.indexStatus)
const canEditAsset = (row: KnowledgeAssetView) => (row.assetType === 'FILE' ? isOfficeEditableFile(row.fileExt) : true)
const canDownloadAsset = (row: KnowledgeAssetView) => row.assetType === 'FILE'

const secondaryLine = (row: KnowledgeAssetView) => {
  if (row.assetType === 'FILE') {
    return `${projectLabel(row.projectId)} · ${normalizeFileTypeLabel(row.fileExt, row.mimeType)} · ${formatFileSize(row.fileSize)}`
  }
  if (row.assetType === 'DOCUMENT') {
    return row.summary ? `${projectLabel(row.projectId)} · ${row.summary}` : `${projectLabel(row.projectId)} · 在线文档`
  }
  return row.summary ? `${projectLabel(row.projectId)} · ${row.summary}` : `${projectLabel(row.projectId)} · ${contentTypeLabel(row.itemType)}内容`
}

const previewAsset = async (row: KnowledgeAssetView) => {
  if (row.assetType === 'FILE') {
    await previewFile({ id: row.assetId, fileExt: row.fileExt })
    return
  }
  if (row.assetType === 'DOCUMENT') {
    await router.push({ path: `/documents/${row.assetId}/edit`, query: { mode: 'preview' } })
    return
  }
  await router.push({ path: `/contents/${row.assetId}/edit`, query: { mode: 'preview' } })
}

const editAsset = async (row: KnowledgeAssetView) => {
  if (row.assetType === 'FILE') {
    if (!isOfficeEditableFile(row.fileExt)) return
    await router.push(`/files/${row.assetId}/edit`)
    return
  }
  if (row.assetType === 'DOCUMENT') {
    await router.push(`/documents/${row.assetId}/edit`)
    return
  }
  await router.push(`/contents/${row.assetId}/edit`)
}

const downloadAsset = async (row: KnowledgeAssetView) => {
  if (row.assetType !== 'FILE') return
  try {
    await downloadFileContent(row.assetId, row.title)
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '下载失败，请稍后重试'))
  }
}

const retryAsset = async (row: KnowledgeAssetView) => {
  try {
    if (row.assetType === 'FILE') {
      await retryFileParse(row.assetId)
      ElMessage.success('文件已重新进入解析队列')
    } else if (row.assetType === 'DOCUMENT') {
      await retryDocumentIndex(row.assetId)
      ElMessage.success('文档已重新进入索引队列')
    }
    await load()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '重试失败，请稍后重试'))
  }
}

onMounted(async () => {
  await loadProjects()
  await load()
})
</script>

<style scoped>
.admin-assets__filters {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.admin-assets__title {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.admin-assets__title strong {
  font-size: 15px;
  font-weight: 700;
}

.admin-assets__title span {
  color: var(--muted);
  font-size: 12px;
}
</style>
