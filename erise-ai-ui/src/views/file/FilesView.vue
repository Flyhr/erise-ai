<template>
  <component :is="rootShell" v-bind="rootShellProps" @update:keyword="handleScopedKeywordUpdate" @search="handleSearch">
    <template v-if="scopedProjectId" #actions>
      <el-button @click="resetFilters">重置</el-button>
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-upload :show-file-list="false" :before-upload="beforeUpload" :accept="knowledgeFileAccept">
        <el-button type="primary">上传文件</el-button>
      </el-upload>
    </template>

    <AppFilterBar v-if="!scopedProjectId">
      <el-input v-model="keyword" style="grid-column: span 5" clearable placeholder="按文件名搜索"
        @keyup.enter="handleSearch" />
      <el-select v-model="selectedProjectId" style="grid-column: span 4" filterable clearable placeholder="筛选项目">
        <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
      </el-select>
      <div class="files-filter-copy">支持按项目浏览、预览、重试解析并查看知识状态。</div>
      <template #actions>
        <el-button @click="resetFilters">重置</el-button>
        <el-button type="primary" @click="handleSearch">查询</el-button>
      </template>
    </AppFilterBar>

    <AppSectionCard title="文件列表" :unpadded="Boolean(files.length)">
      <AppDataTable v-if="files.length" :data="files" stripe>
        <el-table-column label="名称" min-width="260">
          <template #default="{ row }">
            <div class="file-name-cell">
              <strong>{{ row.fileName }}</strong>
              <span>{{ uploadStatusLabel(row.uploadStatus) }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <AppStatusTag :label="normalizeFileTypeLabel(row.fileExt, row.mimeType)" tone="info" />
          </template>
        </el-table-column>

        <el-table-column v-if="!scopedProjectId" label="所属项目" min-width="180">
          <template #default="{ row }">{{ projectLabel(row.projectId) }}</template>
        </el-table-column>

        <el-table-column label="文件状态" min-width="140">
          <template #default="{ row }">
            <KnowledgeSyncStatus :parse-status="row.parseStatus" :index-status="row.indexStatus"
              :can-retry="isKnowledgeFailed(row.parseStatus, row.indexStatus)" @retry="retryKnowledgeFile(row)" />
          </template>
        </el-table-column>

        <el-table-column label="大小" width="120">
          <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
        </el-table-column>

        <el-table-column label="上传时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>

        <el-table-column label="更新时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>

        <el-table-column label="操作" min-width="240" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button text @click="router.push(`/files/${row.id}`)">详情</el-button>
              <el-button text @click="previewFile({ id: row.id, fileExt: row.fileExt })">预览</el-button>
              <el-dropdown>
                <el-button text>更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="isOfficeEditableFile(row.fileExt)"
                      @click="router.push(`/files/${row.id}/edit`)">
                      在线编辑
                    </el-dropdown-item>
                    <el-dropdown-item v-if="isKnowledgeFailed(row.parseStatus, row.indexStatus)"
                      @click="retryKnowledgeFile(row)">
                      重新解析
                    </el-dropdown-item>
                    <el-dropdown-item @click="removeFileItem(row.id, row.fileName)">删除文件</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </AppDataTable>

      <AppEmptyState v-else :title="scopedProjectId ? '当前项目还没有文件' : '还没有匹配的文件'"
        :description="scopedProjectId ? '上传 PDF、Word、TXT 或 Markdown 文件后，这里会显示知识解析状态。' : '调整筛选条件，或进入具体项目后上传文件。'" />

      <template #footer>
        <div class="files-footer">
          <span class="page-subtitle">共 {{ total }} 个文件</span>
          <CompactPager :page-num="pageNum" :page-size="pageSize" :total="total" @change="handlePageChange" />
        </div>
      </template>
    </AppSectionCard>
  </component>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { deleteFile, getFiles, retryFileParse } from '@/api/file'
import AppDataTable from '@/components/common/AppDataTable.vue'
import AppEmptyState from '@/components/common/AppEmptyState.vue'
import AppFilterBar from '@/components/common/AppFilterBar.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'
import AppStatusTag from '@/components/common/AppStatusTag.vue'
import CompactPager from '@/components/common/CompactPager.vue'
import KnowledgeSyncStatus from '@/components/common/KnowledgeSyncStatus.vue'
import ProjectScopedListShell from '@/components/common/ProjectScopedListShell.vue'
import { useFilePreview } from '@/composables/useFilePreview'
import { knowledgeFileAccept, useKnowledgeFileUpload } from '@/composables/useKnowledgeFileUpload'
import { useProjectDirectory } from '@/composables/useProjectDirectory'
import { useVisibleFileStatusPolling } from '@/composables/useVisibleFileStatusPolling'
import type { FileView } from '@/types/models'
import {
  formatDateTime,
  formatFileSize,
  isKnowledgeFailed,
  isOfficeEditableFile,
  normalizeFileTypeLabel,
  resolveErrorMessage,
  uploadStatusLabel,
} from '@/utils/formatters'

const props = defineProps<{ id?: string }>()

const router = useRouter()
const route = useRoute()
const { projects, loadProjects, projectLabel } = useProjectDirectory()
const { previewFile } = useFilePreview()
const files = ref<FileView[]>([])
const keyword = ref('')
const pageNum = ref(1)
const pageSize = 12
const total = ref(0)
const optimisticUploadedFiles = ref<Record<number, FileView>>({})

const parsedScopedProjectId = Number(props.id)
const scopedProjectId = Number.isFinite(parsedScopedProjectId) && parsedScopedProjectId > 0 ? parsedScopedProjectId : undefined
const selectedProjectId = ref<number | undefined>(scopedProjectId)
const ACTIVE_FILE_STATUSES = new Set(['INIT', 'UPLOADING', 'PENDING', 'PROCESSING', 'RETRYING', 'TIMEOUT_RETRYING'])
const normalizeFileStatus = (value?: string) => (value || '').trim().toUpperCase()
const hasActiveFileStatus = (record?: { parseStatus?: string; indexStatus?: string }) =>
  ACTIVE_FILE_STATUSES.has(normalizeFileStatus(record?.parseStatus)) ||
  ACTIVE_FILE_STATUSES.has(normalizeFileStatus(record?.indexStatus))

const rootShell = computed(() => (scopedProjectId ? ProjectScopedListShell : 'div'))
const rootShellProps = computed(() =>
  scopedProjectId
    ? {
      projectId: scopedProjectId,
      title: '文件列表',
      keyword: keyword.value,
      searchPlaceholder: '按文件名搜索',
    }
    : {
      class: 'page-shell',
    },
)

const upsertOptimisticUploadedFile = (file: FileView) => {
  optimisticUploadedFiles.value = {
    ...optimisticUploadedFiles.value,
    [file.id]: file,
  }
}

const removeOptimisticUploadedFile = (fileId: number) => {
  if (!optimisticUploadedFiles.value[fileId]) {
    return
  }
  const nextFiles = { ...optimisticUploadedFiles.value }
  delete nextFiles[fileId]
  optimisticUploadedFiles.value = nextFiles
}

const mergeOptimisticUploadedFiles = (rows: FileView[]) => {
  const existingIds = new Set(rows.map((row) => row.id))
  const optimisticRows = Object.values(optimisticUploadedFiles.value).filter((file) => !existingIds.has(file.id))
  return optimisticRows.length ? [...optimisticRows, ...rows] : rows
}

const reconcileOptimisticUploadedFiles = (rows: FileView[]) => {
  const nextFiles = { ...optimisticUploadedFiles.value }
  let changed = false

  rows.forEach((row) => {
    if (!nextFiles[row.id]) {
      return
    }
    delete nextFiles[row.id]
    changed = true
  })

  Object.values(nextFiles).forEach((file) => {
    if (hasActiveFileStatus(file)) {
      return
    }
    delete nextFiles[file.id]
    changed = true
  })

  if (changed) {
    optimisticUploadedFiles.value = nextFiles
  }
}

const applyFileDetail = (row: FileView, detail: FileView) => {
  row.fileName = detail.fileName
  row.fileExt = detail.fileExt
  row.mimeType = detail.mimeType
  row.fileSize = detail.fileSize
  row.uploadStatus = detail.uploadStatus
  row.parseStatus = detail.parseStatus
  row.indexStatus = detail.indexStatus
  row.parseErrorMessage = detail.parseErrorMessage
  row.createdAt = detail.createdAt
  row.updatedAt = detail.updatedAt
}

const mergeUploadedFile = (file: FileView) => {
  upsertOptimisticUploadedFile(file)
  const existingIndex = files.value.findIndex((item) => item.id === file.id)
  if (existingIndex >= 0) {
    files.value[existingIndex] = file
    return
  }
  files.value = [file, ...files.value]
  total.value = Math.max(total.value + 1, files.value.length)
}

const removeFileFromView = (fileId: number) => {
  removeOptimisticUploadedFile(fileId)
  const nextFiles = files.value.filter((item) => item.id !== fileId)
  if (nextFiles.length === files.value.length) {
    return
  }
  files.value = nextFiles
  total.value = Math.max(0, total.value - 1)
}

const load = async () => {
  const page = await getFiles({
    projectId: scopedProjectId || selectedProjectId.value,
    q: keyword.value.trim() || undefined,
    pageNum: pageNum.value,
    pageSize,
  })
  files.value = mergeOptimisticUploadedFiles(page.records)
  total.value = page.total
  reconcileOptimisticUploadedFiles(page.records)
}

const ensureCurrentPage = async () => {
  if (!files.value.length && total.value > 0 && pageNum.value > 1) {
    pageNum.value = Math.max(1, Math.ceil(total.value / pageSize))
    await pushRoute()
  }
}

const pushRoute = async () => {
  await router.replace({
    path: route.path,
    query: {
      ...(keyword.value.trim() ? { q: keyword.value.trim() } : {}),
      ...(!scopedProjectId && selectedProjectId.value ? { projectId: selectedProjectId.value } : {}),
      ...(pageNum.value > 1 ? { pageNum: pageNum.value } : {}),
    },
  })
}

const syncFromRoute = async () => {
  keyword.value = typeof route.query.q === 'string' ? route.query.q : ''
  if (!scopedProjectId) {
    const nextProjectId = Number(route.query.projectId)
    selectedProjectId.value = Number.isFinite(nextProjectId) && nextProjectId > 0 ? nextProjectId : undefined
  }
  const nextPage = Number(route.query.pageNum)
  pageNum.value = Number.isFinite(nextPage) && nextPage > 0 ? nextPage : 1
  await load()
}

const handleSearch = async () => {
  pageNum.value = 1
  await pushRoute()
}

const resetFilters = async () => {
  keyword.value = ''
  selectedProjectId.value = scopedProjectId
  pageNum.value = 1
  await pushRoute()
}

const handlePageChange = async (value: number) => {
  pageNum.value = value
  await pushRoute()
}

const handleScopedKeywordUpdate = (value: string) => {
  if (scopedProjectId) {
    keyword.value = value
  }
}

const { beforeUpload } = useKnowledgeFileUpload({
  resolveProjectId: () => scopedProjectId || selectedProjectId.value,
  onUploaded: async (uploadedFile) => {
    mergeUploadedFile(uploadedFile)
  },
})

useVisibleFileStatusPolling({
  rows: files,
  intervalMs: 3000,
  getFileId: (row) => row.id,
  isFileActive: (row) => hasActiveFileStatus(row),
  applyDetail: applyFileDetail,
  onDetails: (details) => {
    details.forEach((detail) => {
      if (hasActiveFileStatus(detail)) {
        upsertOptimisticUploadedFile(detail)
        return
      }
      removeOptimisticUploadedFile(detail.id)
    })
  },
  onTimeout: () => {
    ElMessage.warning('文件解析耗时较长，系统会继续自动刷新状态。')
  },
})

const retryKnowledgeFile = async (file: FileView) => {
  try {
    await retryFileParse(file.id)
    ElMessage.success('文件已重新进入解析队列')
    await load()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '重新解析失败，请稍后重试'))
  }
}

const removeFileItem = async (id: number, fileName: string) => {
  try {
    await ElMessageBox.confirm(`确定删除文件“${fileName}”吗？`, '删除文件', { type: 'warning' })
    await deleteFile(id)
    removeFileFromView(id)
    ElMessage.success('文件已删除')
    await load()
    await ensureCurrentPage()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(resolveErrorMessage(error, '删除文件失败，请稍后重试'))
    }
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
.files-filter-copy {
  grid-column: span 3;
  display: flex;
  align-items: center;
  color: var(--muted);
  font-size: 13px;
}

.file-name-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.file-name-cell strong {
  font-size: 15px;
  font-weight: 700;
}

.file-name-cell span {
  color: var(--muted);
  font-size: 12px;
}

.files-footer {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}
</style>
