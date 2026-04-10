<template>
  <div class="page-shell knowledge-page">
    <AppFilterBar>
      <el-input v-model="keyword" style="grid-column: span 6" clearable placeholder="按名称、标题或摘要搜索"
        @keyup.enter="runSearch" />
      <el-select v-model="selectedProjectId" style="grid-column: span 4" clearable filterable placeholder="选择项目">
        <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
      </el-select>
      <div class="knowledge-count">共 {{ total }} 条 {{ activeTab === 'files' ? '文件' : '文档' }}记录</div>
      <template #actions>
        <el-button type="primary" @click="runSearch">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        <div class="knowledge-actions">
          <el-upload v-if="activeTab === 'files'" :show-file-list="false" :before-upload="beforeUpload"
            :disabled="!selectedProjectId" :accept="knowledgeFileAccept">
            <el-tooltip content="请先选择项目，再上传知识文件。" placement="top" :disabled="!!selectedProjectId">
              <el-button type="primary" :disabled="!selectedProjectId">上传文件</el-button>
            </el-tooltip>
          </el-upload>
          <el-button v-else type="primary" :disabled="!selectedProjectId" @click="createDoc">新建文档</el-button>
        </div>
      </template>
    </AppFilterBar>

    <AppSectionCard :title="activeTab === 'files' ? '文件列表' : '文档列表'" :unpadded="true">
      <div class="knowledge-tabs">
        <button type="button" :class="['knowledge-tabs__item', { 'is-active': activeTab === 'files' }]"
          @click="switchTab('files')">
          文件
        </button>
        <button type="button" :class="['knowledge-tabs__item', { 'is-active': activeTab === 'documents' }]"
          @click="switchTab('documents')">
          文档
        </button>
      </div>

      <AppDataTable v-if="assets.length" :data="assets" stripe>
        <el-table-column label="名称" min-width="280">
          <template #default="{ row }">
            <div class="asset-title">
              <strong>{{ row.title }}</strong>
              <span>{{ row.summary || secondaryLine(row) }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="所属项目" min-width="180">
          <template #default="{ row }">{{ projectLabel(row.projectId) }}</template>
        </el-table-column>

        <el-table-column label="类型" width="140">
          <template #default="{ row }">
            <AppStatusTag :label="statusLabel(row)"
              :tone="row.assetType === 'FILE' ? 'info' : documentStatusTone(row.docStatus)" />
          </template>
        </el-table-column>

        <el-table-column v-if="activeTab === 'files'" label="文件状态" min-width="140">
          <template #default="{ row }">
            <KnowledgeSyncStatus :parse-status="row.parseStatus" :index-status="row.indexStatus"
              :can-retry="isKnowledgeFailed(row.parseStatus, row.indexStatus)" @retry="retryKnowledgeAsset(row)" />
          </template>
        </el-table-column>

        <el-table-column label="更新时间" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>

        <el-table-column label="操作" min-width="260" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <template v-if="row.assetType === 'FILE'">
                <el-button text @click="router.push(`/files/${row.assetId}`)">详情</el-button>
                <el-button text @click="previewFile({ id: row.assetId, fileExt: row.fileExt })">预览</el-button>
                <el-button v-if="isOfficeEditableFile(row.fileExt)" text
                  @click="router.push(`/files/${row.assetId}/edit`)">
                  编辑
                </el-button>
                <el-popover placement="bottom" trigger="click" width="140">
                  <template #reference>
                    <el-button text>更多</el-button>
                  </template>
                  <div class="action-menu">
                    <el-button v-if="isKnowledgeFailed(row.parseStatus, row.indexStatus)" link
                      @click="retryKnowledgeAsset(row)">
                      重新解析
                    </el-button>
                    <el-button link @click="deleteAsset(row)">删除</el-button>
                  </div>
                </el-popover>
              </template>

              <template v-else>
                <el-button text
                  @click="router.push({ path: `/documents/${row.assetId}/edit`, query: { mode: 'preview' } })">
                  浏览
                </el-button>
                <el-button text @click="router.push(`/documents/${row.assetId}/edit`)">编辑</el-button>
                <el-popover placement="bottom" trigger="click" width="140">
                  <template #reference>
                    <el-button text>更多</el-button>
                  </template>
                  <div class="action-menu">
                    <el-button link @click="exportAsset(row)">导出 PDF</el-button>
                    <el-button link @click="deleteAsset(row)">删除</el-button>
                  </div>
                </el-popover>
              </template>
            </div>
          </template>
        </el-table-column>
      </AppDataTable>

      <AppEmptyState v-else :title="activeTab === 'files' ? '还没有知识文件' : '还没有在线文档'"
        :description="activeTab === 'files' ? '选择项目后即可上传 doc、docx、pdf、md、txt 文件。' : '选择项目后即可创建在线文档。'" />

      <template #footer>
        <div class="knowledge-footer">
          <span class="page-subtitle">每页最多显示 10 条记录</span>
          <CompactPager :page-num="pageNum" :page-size="pageSize" :total="total" @change="handlePageChange" />
        </div>
      </template>
    </AppSectionCard>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { deleteDocument } from '@/api/document'
import { deleteFile, retryFileParse } from '@/api/file'
import { getKnowledgeAssets } from '@/api/knowledge'
import AppDataTable from '@/components/common/AppDataTable.vue'
import AppEmptyState from '@/components/common/AppEmptyState.vue'
import AppFilterBar from '@/components/common/AppFilterBar.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'
import AppStatusTag from '@/components/common/AppStatusTag.vue'
import CompactPager from '@/components/common/CompactPager.vue'
import KnowledgeSyncStatus from '@/components/common/KnowledgeSyncStatus.vue'
import { useFilePreview } from '@/composables/useFilePreview'
import { knowledgeFileAccept, useKnowledgeFileUpload } from '@/composables/useKnowledgeFileUpload'
import { useKnowledgeStatusPolling } from '@/composables/useKnowledgeStatusPolling'
import { useProjectDirectory } from '@/composables/useProjectDirectory'
import type { KnowledgeAssetView } from '@/types/models'
import {
  documentStatusLabel,
  documentStatusTone,
  formatDateTime,
  formatFileSize,
  isKnowledgeFailed,
  isOfficeEditableFile,
  normalizeFileTypeLabel,
  resolveErrorMessage,
} from '@/utils/formatters'

const route = useRoute()
const router = useRouter()
const { projects, loadProjects, projectLabel } = useProjectDirectory()
const { previewFile } = useFilePreview()

const activeTab = ref<'files' | 'documents'>('files')
const keyword = ref('')
const selectedProjectId = ref<number>()
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const assets = ref<KnowledgeAssetView[]>([])

const loadAssets = async () => {
  const page = await getKnowledgeAssets({
    type: activeTab.value === 'files' ? 'FILE' : 'DOCUMENT',
    projectId: selectedProjectId.value,
    q: keyword.value.trim() || undefined,
    knowledgeOnly: activeTab.value === 'files',
    pageNum: pageNum.value,
    pageSize: pageSize.value,
  })
  assets.value = page.records
  total.value = page.total
}

const syncFromRoute = async () => {
  activeTab.value = route.query.tab === 'documents' ? 'documents' : 'files'
  keyword.value = typeof route.query.q === 'string' ? route.query.q : ''
  const projectId = Number(route.query.projectId)
  selectedProjectId.value = Number.isFinite(projectId) && projectId > 0 ? projectId : undefined
  const nextPage = Number(route.query.pageNum)
  pageNum.value = Number.isFinite(nextPage) && nextPage > 0 ? nextPage : 1
  await loadAssets()
}

const pushRoute = async () => {
  await router.replace({
    path: '/knowledge',
    query: {
      tab: activeTab.value,
      ...(keyword.value.trim() ? { q: keyword.value.trim() } : {}),
      ...(selectedProjectId.value ? { projectId: selectedProjectId.value } : {}),
      ...(pageNum.value > 1 ? { pageNum: pageNum.value } : {}),
    },
  })
}

const ensureCurrentPage = async () => {
  if (!assets.value.length && total.value > 0 && pageNum.value > 1) {
    pageNum.value = Math.max(1, Math.ceil(total.value / pageSize.value))
    await pushRoute()
  }
}

const runSearch = async () => {
  pageNum.value = 1
  await pushRoute()
}

const resetFilters = async () => {
  keyword.value = ''
  selectedProjectId.value = undefined
  pageNum.value = 1
  await pushRoute()
}

const handlePageChange = async (value: number) => {
  pageNum.value = value
  await pushRoute()
}

const switchTab = async (tab: 'files' | 'documents') => {
  if (activeTab.value === tab) return
  activeTab.value = tab
  pageNum.value = 1
  await pushRoute()
}

const { beforeUpload } = useKnowledgeFileUpload({
  resolveProjectId: () => selectedProjectId.value,
  onUploaded: async () => {
    await loadAssets()
  },
})

useKnowledgeStatusPolling({
  records: assets,
  reload: loadAssets,
  enabled: () => activeTab.value === 'files',
})

const secondaryLine = (row: KnowledgeAssetView) => {
  if (row.assetType === 'FILE') {
    return `${normalizeFileTypeLabel(row.fileExt, row.mimeType)} / ${formatFileSize(row.fileSize)}`
  }
  return row.summary || '暂无摘要'
}

const statusLabel = (row: KnowledgeAssetView) => {
  if (row.assetType === 'FILE') {
    return normalizeFileTypeLabel(row.fileExt, row.mimeType)
  }
  return documentStatusLabel(row.docStatus)
}

const retryKnowledgeAsset = async (row: KnowledgeAssetView) => {
  if (row.assetType !== 'FILE') {
    return
  }
  try {
    await retryFileParse(row.assetId)
    ElMessage.success('文件已重新进入解析队列')
    await loadAssets()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '重新解析失败，请稍后重试'))
  }
}

const createDoc = async () => {
  if (!selectedProjectId.value) {
    ElMessage.warning('请先选择一个项目，再创建文档。')
    return
  }
  await router.push({
    path: '/documents/new/edit',
    query: { projectId: selectedProjectId.value },
  })
}

const deleteAsset = async (row: KnowledgeAssetView) => {
  try {
    await ElMessageBox.confirm(`确认删除“${row.title}”吗？此操作不可恢复。`, `删除${row.assetType === 'FILE' ? '文件' : '文档'}`, {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger',
    })

    if (row.assetType === 'FILE') {
      await deleteFile(row.assetId)
    } else {
      await deleteDocument(row.assetId)
    }

    ElMessage.success(`${row.assetType === 'FILE' ? '文件' : '文档'}已删除`)
    await loadAssets()
    await ensureCurrentPage()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(resolveErrorMessage(error, '删除失败，请稍后重试'))
    }
  }
}

const exportAsset = async (row: KnowledgeAssetView) => {
  if (row.assetType !== 'DOCUMENT') return

  try {
    const exportUrl = `/api/v1/documents/${row.assetId}/export?format=pdf`
    const link = document.createElement('a')
    link.href = exportUrl
    link.download = `${row.title}.pdf`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    ElMessage.success('文档已导出为 PDF')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '导出失败，请稍后重试'))
  }
}

onMounted(async () => {
  await loadProjects()
  await syncFromRoute()
})

watch(
  () => route.fullPath,
  async () => {
    await syncFromRoute()
  },
)
</script>

<style scoped>
.knowledge-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.knowledge-count {
  grid-column: span 2;
  display: flex;
  align-items: center;
  color: var(--muted);
  font-size: 13px;
}

.knowledge-actions {
  display: flex;
  align-items: center;
}

.knowledge-tabs {
  display: inline-flex;
  gap: 8px;
  padding: 16px 20px 0;
}

.knowledge-tabs__item {
  border: 0;
  cursor: pointer;
  padding: 10px 16px;
  border-radius: 999px;
  background: var(--panel);
  color: var(--muted);
  font-weight: 700;
  transition: 0.2s ease;
}

.knowledge-tabs__item.is-active {
  background: rgba(64, 158, 255, 0.12);
  color: var(--brand);
}

.asset-title {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.asset-title strong {
  font-size: 15px;
  font-weight: 700;
}

.asset-title span {
  color: var(--muted);
  font-size: 12px;
}

.knowledge-footer {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}
</style>
