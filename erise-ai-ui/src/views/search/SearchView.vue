<template>
  <div class="page-shell">
    <!-- <AppPageHeader /> -->

    <!-- <AppSectionCard> -->
    <AppFilterBar>
      <el-input v-model="query" style="grid-column: span 6" clearable placeholder="输入关键词" @keyup.enter="runSearch" />
      <el-select v-model="projectId" style="grid-column: span 4" clearable filterable placeholder="选择项目">
        <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
      </el-select>
      <div class="search-filter-copy">
        {{ filteredResults.length }} 条结果
      </div>
      <template #actions>
        <el-button type="primary" :loading="loading" @click="runSearch">搜索</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </template>
    </AppFilterBar>
    <!-- </AppSectionCard> -->

    <AppSectionCard title="搜索结果" :unpadded="Boolean(filteredResults.length)">
      <el-tabs v-model="activeTab" style="margin: 16px">
        <el-tab-pane label="全部" name="ALL" />
        <el-tab-pane label="文档" name="DOCUMENT" />
        <el-tab-pane label="文件" name="FILE" />
      </el-tabs>

      <AppDataTable v-if="filteredResults.length" :data="filteredResults" stripe>
        <el-table-column label="名称" min-width="280">
          <template #default="{ row }">
            <div class="search-name-cell">
              <strong>{{ row.title }}</strong>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="所属项目" min-width="180">
          <template #default="{ row }">{{ projectLabel(row.projectId) }}</template>
        </el-table-column>

        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <AppStatusTag :label="resultTypeLabel(row)" tone="info" />
          </template>
        </el-table-column>

        <el-table-column label="大小" width="120">
          <template #default="{ row }">
            {{ isFileResult(row) ? formatFileSize(row.fileSize) : '--' }}
          </template>
        </el-table-column>

        <el-table-column label="状态" min-width="120">
          <template #default="{ row }">
            <template v-if="isFileResult(row)">
              <KnowledgeSyncStatus v-if="row.parseStatus || row.indexStatus" compact :parse-status="row.parseStatus"
                :index-status="row.indexStatus" />
              <AppStatusTag v-else :label="uploadStatusLabel(row.uploadStatus)"
                :tone="uploadStatusTone(row.uploadStatus)" />
            </template>
            <AppStatusTag v-else :label="documentStatusLabel(row.docStatus)"
              :tone="documentStatusTone(row.docStatus)" />
          </template>
        </el-table-column>

        <el-table-column label="更新时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>

        <el-table-column label="操作" min-width="260" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <template v-if="isFileResult(row)">
                <el-button text @click="viewFile(row.sourceId)">详情</el-button>
                <el-button text @click="previewFile(row)">预览</el-button>
                <el-button v-if="supportsOfficeEdit(row)" text @click="editFile(row.sourceId)">编辑</el-button>
                <el-dropdown>
                  <el-button text>更多</el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item v-if="canRetryParse(row)" @click="retryFile(row)">重新解析</el-dropdown-item>
                      <el-dropdown-item @click="removeFile(row)">删除文件</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>

              <template v-else>
                <el-button text @click="viewDocument(row.sourceId)">详情</el-button>
                <el-button text @click="previewDocument(row.sourceId)">预览</el-button>
                <el-button text @click="editDocument(row.sourceId)">编辑</el-button>
                <el-dropdown>
                  <el-button text>更多</el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item @click="exportDocument(row)">导出 PDF</el-dropdown-item>
                      <el-dropdown-item @click="removeDocument(row)">删除文档</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>
            </div>
          </template>
        </el-table-column>
      </AppDataTable>

      <AppEmptyState v-else :title="query ? '没有找到结果' : '请输入关键词开始搜索'" description="可以按项目范围筛选，并直接从结果进入详情、预览或编辑。" />
    </AppSectionCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { deleteDocument } from '@/api/document'
import { deleteFile, previewFileBinary, previewOfficeFile, retryFileParse } from '@/api/file'
import { resolveApiUrl } from '@/api/http'
import { search } from '@/api/search'
import AppDataTable from '@/components/common/AppDataTable.vue'
import AppEmptyState from '@/components/common/AppEmptyState.vue'
import AppFilterBar from '@/components/common/AppFilterBar.vue'
import AppPageHeader from '@/components/common/AppPageHeader.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'
import AppStatusTag from '@/components/common/AppStatusTag.vue'
import KnowledgeSyncStatus from '@/components/common/KnowledgeSyncStatus.vue'
import { useProjectDirectory } from '@/composables/useProjectDirectory'
import type { SearchResultView } from '@/types/models'
import {
  documentStatusLabel,
  documentStatusTone,
  formatDateTime,
  formatFileSize,
  isKnowledgeFailed,
  isOfficeEditableFile,
  normalizeFileTypeLabel,
  resolveErrorMessage,
  uploadStatusLabel,
} from '@/utils/formatters'

const route = useRoute()
const router = useRouter()
const { projects, loadProjects, projectLabel } = useProjectDirectory()

const query = ref('')
const projectId = ref<number | undefined>()
const activeTab = ref<'ALL' | 'DOCUMENT' | 'FILE'>('ALL')
const results = ref<SearchResultView[]>([])
const loading = ref(false)

const parseNumber = (value: unknown) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
}

const normalizeStatus = (value?: string) => (value || '').trim().toUpperCase()

const filteredResults = computed(() => {
  if (activeTab.value === 'ALL') {
    return results.value
  }
  return results.value.filter((item) => item.sourceType === activeTab.value)
})

const performSearch = async () => {
  const keyword = query.value.trim()
  if (!keyword) {
    results.value = []
    return
  }

  loading.value = true
  try {
    const page = await search({
      q: keyword,
      projectId: projectId.value,
      pageNum: 1,
      pageSize: 50,
    })
    results.value = page.records
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '搜索失败，请稍后重试'))
  } finally {
    loading.value = false
  }
}

const syncFromRoute = async () => {
  query.value = typeof route.query.q === 'string' ? route.query.q : ''
  projectId.value = parseNumber(route.query.projectId)
  await performSearch()
}

const runSearch = async () => {
  const keyword = query.value.trim()
  await router.push({
    path: '/search',
    query: {
      ...(keyword ? { q: keyword } : {}),
      ...(projectId.value ? { projectId: projectId.value } : {}),
    },
  })
}

const resetFilters = async () => {
  query.value = ''
  projectId.value = undefined
  activeTab.value = 'ALL'
  results.value = []
  await router.push('/search')
}

const isDocumentResult = (item: SearchResultView) => item.sourceType === 'DOCUMENT'
const isFileResult = (item: SearchResultView) => item.sourceType === 'FILE'
const supportsOfficeEdit = (item: SearchResultView) => isFileResult(item) && isOfficeEditableFile(item.fileExt)

const resultTypeLabel = (item: SearchResultView) =>
  isFileResult(item) ? normalizeFileTypeLabel(item.fileExt, item.mimeType) : '文档'

const uploadStatusTone = (status?: string) =>
  ({
    INIT: 'warning',
    UPLOADING: 'primary',
    READY: 'success',
    FAILED: 'danger',
  }[normalizeStatus(status)] || 'info') as 'primary' | 'success' | 'warning' | 'danger' | 'info'

const viewDocument = (id: number) => router.push({ path: `/documents/${id}/edit`, query: { mode: 'preview' } })
const editDocument = (id: number) => router.push(`/documents/${id}/edit`)
const previewDocument = (id: number) => router.push({ path: `/documents/${id}/edit`, query: { mode: 'preview' } })
const viewFile = (id: number) => router.push(`/files/${id}`)
const editFile = (id: number) => router.push(`/files/${id}/edit`)
const canRetryParse = (item: SearchResultView) => isFileResult(item) && isKnowledgeFailed(item.parseStatus, item.indexStatus)

const previewFile = async (item: SearchResultView) => {
  try {
    if (supportsOfficeEdit(item)) {
      await previewOfficeFile(item.sourceId)
      return
    }
    await previewFileBinary(item.sourceId)
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '文件预览失败，请稍后重试'))
  }
}

const retryFile = async (item: SearchResultView) => {
  try {
    await retryFileParse(item.sourceId)
    ElMessage.success('文件已重新进入解析队列')
    await performSearch()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '重新解析失败，请稍后重试'))
  }
}

const exportDocument = async (item: SearchResultView) => {
  try {
    const link = document.createElement('a')
    link.href = resolveApiUrl(`/v1/documents/${item.sourceId}/export?format=pdf`)
    link.download = `${item.title}.pdf`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    ElMessage.success('文档已导出为 PDF')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '导出失败，请稍后重试'))
  }
}

const removeDocument = async (item: SearchResultView) => {
  try {
    await ElMessageBox.confirm(`确认删除文档“${item.title}”吗？`, '删除文档', { type: 'warning' })
    await deleteDocument(item.sourceId)
    ElMessage.success('文档已删除')
    await performSearch()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(resolveErrorMessage(error, '删除文档失败，请稍后重试'))
    }
  }
}

const removeFile = async (item: SearchResultView) => {
  try {
    await ElMessageBox.confirm(`确认删除文件“${item.title}”吗？`, '删除文件', { type: 'warning' })
    await deleteFile(item.sourceId)
    ElMessage.success('文件已删除')
    await performSearch()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(resolveErrorMessage(error, '删除文件失败，请稍后重试'))
    }
  }
}

watch(
  () => [route.query.q, route.query.projectId],
  () => {
    void syncFromRoute()
  },
  { immediate: true },
)

onMounted(async () => {
  await loadProjects()
})
</script>

<style scoped>
.search-filter-copy {
  grid-column: span 2;
  display: flex;
  align-items: center;
  color: var(--muted);
  font-size: 13px;
}

.search-name-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.search-name-cell strong {
  font-size: 15px;
  font-weight: 700;
  word-break: break-word;
}
</style>
