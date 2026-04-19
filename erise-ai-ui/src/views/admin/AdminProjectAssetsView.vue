<template>
  <div class="page-shell admin-assets-page">
    <section class="project-detail__toolbar admin-assets-toolbar">
      <div class="admin-assets-toolbar__search-group">
        <div class="project-detail__search">
          <el-input v-model="keyword" clearable placeholder="搜索名称、摘要或文件类型" @clear="handleSearch"
            @keyup.enter="handleSearch">
            <template #prefix>
              <span class="material-symbols-outlined">search</span>
            </template>
            <template #suffix>
              <SearchSuffixButton @click="handleSearch" />
            </template>
          </el-input>
        </div>

      </div>

      <div class="admin-assets-toolbar__actions">
        <ProjectSubnav :project-id="0" mode="value" :model-value="activeTab" :items="tabItems"
          @update:modelValue="handleTabChange" />
        <span class="project-detail__meta-chip">
          <span class="material-symbols-outlined">inventory_2</span>
          <span>共 {{ total }} 条{{ assetCollectionLabel }}</span>
        </span>
      </div>
    </section>

    <section class="project-detail__table-shell">
      <div v-if="assetsLoading && !assets.length" class="project-detail__state">
        <el-skeleton animated>
          <template #template>
            <el-skeleton-item variant="rect" style="width: 100%; height: 56px; border-radius: 18px;" />
            <el-skeleton-item variant="rect"
              style="width: 100%; height: 72px; margin-top: 14px; border-radius: 16px;" />
            <el-skeleton-item variant="rect"
              style="width: 100%; height: 72px; margin-top: 12px; border-radius: 16px;" />
            <el-skeleton-item variant="rect"
              style="width: 100%; height: 72px; margin-top: 12px; border-radius: 16px;" />
          </template>
        </el-skeleton>
      </div>

      <div v-else-if="assetError && !assets.length" class="project-detail__state">
        <el-result icon="warning" title="项目资料加载失败" :sub-title="assetError">
          <template #extra>
            <el-button type="primary" @click="retryAssetLoad">重试加载</el-button>
          </template>
        </el-result>
      </div>

      <template v-else>
        <div v-if="assetsLoading && assets.length" class="project-detail__refreshing">
          <span class="material-symbols-outlined project-detail__refreshing-icon">progress_activity</span>
          <span>正在刷新文件列表...</span>
        </div>

        <div v-if="assets.length" class="project-assets-table">
          <table>
            <thead>
              <tr>
                <th>名称</th>
                <th>所有者</th>
                <th>所属项目</th>
                <th>状态</th>
                <th>类型</th>
                <th>大小</th>
                <th>上传时间</th>
                <th>更新时间</th>
                <th class="project-assets-table__actions-head">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in assets" :key="`${row.assetType}-${row.assetId}`" @click="openAsset(row)">
                <td>
                  <div class="project-assets-table__name">
                    <div class="project-assets-table__icon" :class="`is-${assetTone(row)}`">
                      <span class="material-symbols-outlined">{{ assetIcon(row) }}</span>
                    </div>
                    <div class="project-assets-table__copy app-table-name-copy">
                      <strong class="app-table-name-copy__title">{{ row.title }}</strong>
                      <small class="app-table-name-copy__meta">{{ secondaryLine(row) }}</small>
                    </div>
                  </div>
                </td>
                <td>
                  <div class="project-assets-table__meta-stack">
                    <strong>{{ ownerLabel(row) }}</strong>
                  </div>
                </td>
                <td>
                  <div class="project-assets-table__meta-stack">
                    <strong>{{ projectName(row) }}</strong>
                  </div>
                </td>
                <td>
                  <div class="project-assets-table__status-stack">
                    <button v-if="isRetryableFailedFile(row)" type="button"
                      class="project-assets-table__status is-clickable" :class="`is-${assetTone(row)}`"
                      @click.stop="retryAsset(row)">
                      <span class="material-symbols-outlined">{{ assetStatusIcon(row) }}</span>
                      <span>{{ assetStatusLabel(row) }}</span>
                    </button>
                    <span v-else class="project-assets-table__status" :class="`is-${assetTone(row)}`">
                      <span class="material-symbols-outlined">{{ assetStatusIcon(row) }}</span>
                      <span>{{ assetStatusLabel(row) }}</span>
                    </span>
                    <small v-if="shouldShowFailureReason(row)" class="project-assets-table__status-reason">
                      {{ assetFailureReason(row) }}
                    </small>
                  </div>
                </td>
                <td><span class="project-assets-table__type">{{ assetTypeLabel(row) }}</span></td>
                <td class="project-assets-table__mono">
                  {{ row.assetType === 'FILE' ? formatFileSize(row.fileSize) : '--' }}
                </td>
                <td>{{ formatDateTime(row.createdAt) }}</td>
                <td>{{ relativeTime(row.updatedAt) }}</td>
                <td class="project-assets-table__actions-cell" @click.stop>
                  <el-dropdown trigger="click" @command="handleRowCommand(row, $event)">
                    <button type="button" class="project-assets-table__menu-trigger"
                      @click.stop><span>···</span></button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="preview">预览</el-dropdown-item>
                        <el-dropdown-item v-if="canEditAsset(row)" command="edit">修改</el-dropdown-item>
                        <el-dropdown-item v-if="canDownloadAsset(row)" command="download">下载</el-dropdown-item>
                        <el-dropdown-item v-if="canRetryAsset(row)" command="retry">重试</el-dropdown-item>
                        <el-dropdown-item command="delete">删除</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <el-empty v-else :image-size="84" :description="`当前筛选下还没有${assetCollectionLabel}。`" />
      </template>

      <div v-if="!assetError" class="project-detail__table-footer">
        <span class="project-detail__table-count">共 {{ total }} 条{{ assetCollectionLabel }}</span>
        <CompactPager variant="project" :page-num="pageNum" :page-size="pageSize" :total="total"
          @change="handlePageChange" />
      </div>
    </section>

    <el-dialog v-model="exportDialogVisible" title="选择导出格式" width="440px">
      <div class="admin-assets-export-dialog">
        <div class="admin-assets-export-dialog__copy">
          <strong>{{ exportTarget?.title || '当前文档' }}</strong>
        </div>
        <div class="admin-assets-export-dialog__actions">
          <el-button :loading="exporting" @click="exportDocumentAsset('doc')">导出 .doc</el-button>
          <el-button :loading="exporting" @click="exportDocumentAsset('pdf')">导出 .pdf</el-button>
          <el-button :loading="exporting" @click="exportDocumentAsset('markdown')">导出 .md</el-button>
          <el-button :loading="exporting" @click="exportDocumentAsset('jpg')">导出 .jpg</el-button>
        </div>
      </div>
      <template #footer>
        <el-button @click="exportDialogVisible = false">取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import dayjs from 'dayjs'
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { deleteContentItem } from '@/api/content'
import { deleteDocument, getDocument } from '@/api/document'
import { deleteFile, downloadFileContent, retryFileParse } from '@/api/file'
import { getKnowledgeAssets } from '@/api/knowledge'
import CompactPager from '@/components/common/CompactPager.vue'
import ProjectSubnav from '@/components/common/ProjectSubnav.vue'
import SearchSuffixButton from '@/components/common/SearchSuffixButton.vue'
import { useFilePreview } from '@/composables/useFilePreview'
import { useProjectDirectory } from '@/composables/useProjectDirectory'
import { useVisibleFileStatusPolling } from '@/composables/useVisibleFileStatusPolling'
import type { FileView, KnowledgeAssetView } from '@/types/models'
import {
  contentTypeLabel,
  documentStatusLabel,
  documentStatusTone,
  formatDateTime,
  formatFileSize,
  isKnowledgeFailed,
  isOfficeEditableFile,
  knowledgeReadinessLabel,
  knowledgeReadinessTone,
  normalizeFileTypeLabel,
  resolveErrorMessage,
} from '@/utils/formatters'
import {
  exportDocumentAsDoc,
  exportDocumentAsMarkdown,
  exportElementAsJpg,
  exportElementAsPdf,
  renderDocumentHtml,
} from '@/utils/documentExport'

type AdminAssetTab = 'overview' | 'files' | 'documents' | 'content'
type DocumentExportFormat = 'doc' | 'pdf' | 'markdown' | 'jpg'

const router = useRouter()
const { previewFile } = useFilePreview()
const { loadProjects, projectLabel } = useProjectDirectory()
const keyword = ref('')
const activeTab = ref<AdminAssetTab>('overview')
const pageNum = ref(1)
const pageSize = 20
const total = ref(0)
const assets = ref<KnowledgeAssetView[]>([])
const assetsLoading = ref(true)
const assetError = ref('')
const exportDialogVisible = ref(false)
const exporting = ref(false)
const exportTarget = ref<KnowledgeAssetView>()

const ACTIVE_FILE_STATUSES = new Set(['INIT', 'UPLOADING', 'PENDING', 'PROCESSING'])
const normalizeFileStatus = (value?: string) => (value || '').trim().toUpperCase()
const hasActiveFileStatus = (record?: { parseStatus?: string; indexStatus?: string }) =>
  ACTIVE_FILE_STATUSES.has(normalizeFileStatus(record?.parseStatus)) ||
  ACTIVE_FILE_STATUSES.has(normalizeFileStatus(record?.indexStatus))

const tabItems = [
  { key: 'overview', label: '概览' },
  { key: 'files', label: '文件' },
  { key: 'documents', label: '文档' },
  { key: 'content', label: '表格' },
]

const selectedKnowledgeType = computed<'FILE' | 'DOCUMENT' | 'CONTENT' | undefined>(() => {
  if (activeTab.value === 'files') return 'FILE'
  if (activeTab.value === 'documents') return 'DOCUMENT'
  if (activeTab.value === 'content') return 'CONTENT'
  return undefined
})

const assetCollectionLabel = computed(
  () => ({ overview: '项目资料', files: '文件', documents: '文档', content: '表格内容' }[activeTab.value]),
)

const assetTypeLabel = (row: KnowledgeAssetView) => {
  if (row.assetType === 'FILE') return normalizeFileTypeLabel(row.fileExt, row.mimeType)
  if (row.assetType === 'DOCUMENT') return '文档'
  return contentTypeLabel(row.itemType)
}

const assetStatusLabel = (row: KnowledgeAssetView) =>
  row.assetType === 'DOCUMENT'
    ? documentStatusLabel(row.docStatus)
    : knowledgeReadinessLabel(row.parseStatus, row.indexStatus)

const assetTone = (row: KnowledgeAssetView) =>
  row.assetType === 'DOCUMENT'
    ? documentStatusTone(row.docStatus)
    : knowledgeReadinessTone(row.parseStatus, row.indexStatus)

const assetStatusIcon = (row: KnowledgeAssetView) =>
  ({ success: 'check_circle', warning: 'schedule', primary: 'visibility', danger: 'cancel', info: 'edit_note' }[assetTone(row)] ||
    'info') as string

const assetIcon = (row: KnowledgeAssetView) => {
  if (row.assetType === 'DOCUMENT') return 'sticky_note_2'
  if (row.assetType === 'CONTENT') {
    if (row.itemType === 'BOARD') return 'draw'
    if (row.itemType === 'DATA_TABLE') return 'dataset'
    return 'table_chart'
  }
  const ext = (row.fileExt || '').toLowerCase()
  if (ext === 'pdf') return 'picture_as_pdf'
  if (['doc', 'docx'].includes(ext)) return 'article'
  if (ext === 'md') return 'code'
  if (ext === 'txt') return 'notes'
  if (['jpg', 'jpeg', 'png', 'gif', 'webp'].includes(ext)) return 'image'
  if (['xls', 'xlsx', 'csv'].includes(ext)) return 'table_chart'
  if (['dwg', 'dxf'].includes(ext)) return 'home_work'
  return 'insert_drive_file'
}

const secondaryLine = (row: KnowledgeAssetView) => {
  if (row.assetType === 'FILE') return `${formatFileSize(row.fileSize)}`
  if (row.assetType === 'DOCUMENT') return row.summary || '在线文档'
  return row.summary || `${contentTypeLabel(row.itemType)}内容`
}

const ownerLabel = (row: KnowledgeAssetView) => row.ownerName || '--'
const projectName = (row: KnowledgeAssetView) => projectLabel(row.projectId)

const relativeTime = (value?: string) => {
  if (!value) return '--'
  const time = dayjs(value)
  const days = dayjs().diff(time, 'day')
  if (days >= 1) return `${days} 天前`
  const hours = dayjs().diff(time, 'hour')
  if (hours >= 1) return `${hours} 小时前`
  const minutes = dayjs().diff(time, 'minute')
  if (minutes >= 1) return `${minutes} 分钟前`
  return '刚刚'
}

const canEditAsset = (row: KnowledgeAssetView) => (row.assetType === 'FILE' ? isOfficeEditableFile(row.fileExt) : true)
const canDownloadAsset = (row: KnowledgeAssetView) => row.assetType === 'FILE' || row.assetType === 'DOCUMENT'
const canRetryAsset = (row: KnowledgeAssetView) => row.assetType === 'FILE' && isKnowledgeFailed(row.parseStatus, row.indexStatus)
const isRetryableFailedFile = (row: KnowledgeAssetView) => row.assetType === 'FILE' && isKnowledgeFailed(row.parseStatus, row.indexStatus)
const shouldShowFailureReason = (row: KnowledgeAssetView) => isRetryableFailedFile(row)

const assetFailureReason = (row: KnowledgeAssetView) => {
  const message = (row.parseErrorMessage || '').trim().toLowerCase()
  if (!message) return '解析异常，请稍后重试'
  if (message.includes('password') || message.includes('encrypted')) return '文件已加密'
  if (message.includes('timeout')) return '解析超时'
  if (message.includes('unsupported') || message.includes('not support')) return '格式暂不支持'
  if (message.includes('too long') || message.includes('data truncation')) return '内容异常过长'
  if (message.includes('empty') || message.includes('blank')) return '文件内容为空'
  if (message.includes('network') || message.includes('connect')) return '连接解析服务失败'
  return '解析异常，请稍后重试'
}

const applyFileDetailToAssetRow = (row: KnowledgeAssetView, detail: FileView) => {
  row.title = detail.fileName
  row.fileExt = detail.fileExt
  row.mimeType = detail.mimeType
  row.fileSize = detail.fileSize
  row.parseStatus = detail.parseStatus
  row.indexStatus = detail.indexStatus
  row.parseErrorMessage = detail.parseErrorMessage
  row.createdAt = detail.createdAt
  row.updatedAt = detail.updatedAt
}

const load = async () => {
  assetsLoading.value = true
  assetError.value = ''
  try {
    const page = await getKnowledgeAssets({
      type: selectedKnowledgeType.value,
      q: keyword.value.trim() || undefined,
      knowledgeOnly: activeTab.value === 'overview' || activeTab.value === 'files',
      pageNum: pageNum.value,
      pageSize,
    })
    assets.value = page.records
    total.value = page.total
  } catch (error) {
    assetError.value = resolveErrorMessage(error, '项目资料加载失败，请稍后重试')
  } finally {
    assetsLoading.value = false
  }
}

const ensureCurrentPage = async () => {
  if (!assets.value.length && total.value > 0 && pageNum.value > 1) {
    pageNum.value = Math.max(1, Math.ceil(total.value / pageSize))
    await load()
  }
}

const handleSearch = async () => {
  pageNum.value = 1
  await load()
}

const handleTabChange = async (value: string) => {
  activeTab.value = value as AdminAssetTab
  pageNum.value = 1
  await load()
}

const handlePageChange = async (value: number) => {
  pageNum.value = value
  await load()
}

useVisibleFileStatusPolling({
  rows: assets,
  enabled: computed(() => !assetError.value && (activeTab.value === 'overview' || activeTab.value === 'files')),
  intervalMs: 3000,
  getFileId: (row) => (row.assetType === 'FILE' ? row.assetId : undefined),
  isFileActive: (row) => row.assetType === 'FILE' && hasActiveFileStatus(row),
  applyDetail: applyFileDetailToAssetRow,
  onTimeout: () => {
    ElMessage.warning('文件解析仍在处理中，已暂停自动刷新，请稍后手动刷新查看结果')
  },
})

const openAsset = (row: KnowledgeAssetView) => {
  if (row.assetType === 'FILE') {
    router.push(`/admin/files/${row.assetId}`)
    return
  }
  if (row.assetType === 'DOCUMENT') {
    router.push({ path: `/admin/documents/${row.assetId}/edit`, query: { mode: 'preview' } })
    return
  }
  router.push({ path: `/admin/contents/${row.assetId}/edit`, query: { mode: 'preview' } })
}

const previewAsset = async (row: KnowledgeAssetView) => {
  if (row.assetType === 'FILE') {
    await previewFile({ id: row.assetId, fileExt: row.fileExt })
    return
  }
  if (row.assetType === 'DOCUMENT') {
    await router.push({ path: `/admin/documents/${row.assetId}/edit`, query: { mode: 'preview' } })
    return
  }
  await router.push({ path: `/admin/contents/${row.assetId}/edit`, query: { mode: 'preview' } })
}

const editAsset = async (row: KnowledgeAssetView) => {
  if (row.assetType === 'FILE') {
    if (!isOfficeEditableFile(row.fileExt)) return
    await router.push(`/admin/files/${row.assetId}/edit`)
    return
  }
  if (row.assetType === 'DOCUMENT') {
    await router.push(`/admin/documents/${row.assetId}/edit`)
    return
  }
  await router.push(`/admin/contents/${row.assetId}/edit`)
}

const openExportDialog = (row: KnowledgeAssetView) => {
  exportTarget.value = row
  exportDialogVisible.value = true
}

const buildExportSurface = (title: string, summary: string | undefined, bodyHtml: string) => {
  const wrapper = document.createElement('div')
  wrapper.style.position = 'fixed'
  wrapper.style.left = '-200vw'
  wrapper.style.top = '0'
  wrapper.style.opacity = '0'
  wrapper.style.pointerEvents = 'none'
  const canvas = document.createElement('div')
  canvas.style.width = '794px'
  canvas.style.background = '#ffffff'
  canvas.innerHTML = renderDocumentHtml(title, summary, bodyHtml)
  wrapper.appendChild(canvas)
  document.body.appendChild(wrapper)
  return { canvas, dispose: () => document.body.removeChild(wrapper) }
}

const exportDocumentAsset = async (format: DocumentExportFormat) => {
  if (!exportTarget.value) return
  exporting.value = true
  try {
    const detail = await getDocument(exportTarget.value.assetId)
    const fileName = detail.title?.trim() || exportTarget.value.title || '未命名文档'
    const summary = detail.summary || exportTarget.value.summary
    const bodyHtml = detail.contentHtmlSnapshot || '<p></p>'
    if (format === 'doc') {
      await exportDocumentAsDoc({ fileName, title: fileName, summary, bodyHtml })
      ElMessage.success('已导出 .doc 文件')
    } else if (format === 'markdown') {
      await exportDocumentAsMarkdown({ fileName, title: fileName, summary, bodyHtml })
      ElMessage.success('已导出 Markdown 文件')
    } else {
      const { canvas, dispose } = buildExportSurface(fileName, summary, bodyHtml)
      try {
        if (format === 'pdf') {
          await exportElementAsPdf(canvas, fileName)
          ElMessage.success('已导出 .pdf 文件')
        } else {
          await exportElementAsJpg(canvas, fileName)
          ElMessage.success('已导出 .jpg 文件')
        }
      } finally {
        dispose()
      }
    }
    exportDialogVisible.value = false
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '导出失败，请稍后重试'))
  } finally {
    exporting.value = false
  }
}

const downloadAsset = async (row: KnowledgeAssetView) => {
  if (row.assetType === 'DOCUMENT') {
    openExportDialog(row)
    return
  }
  if (row.assetType !== 'FILE') return
  try {
    await downloadFileContent(row.assetId, row.title)
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '下载失败，请稍后重试'))
  }
}

const retryAsset = async (row: KnowledgeAssetView) => {
  if (row.assetType !== 'FILE') return
  try {
    await retryFileParse(row.assetId)
    row.parseStatus = 'PENDING'
    row.indexStatus = 'PENDING'
    row.parseErrorMessage = undefined
    ElMessage.success('文件已重新进入解析队列')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '重试失败，请稍后重试'))
  }
}

const deleteAsset = async (row: KnowledgeAssetView) => {
  const targetLabel = row.assetType === 'FILE' ? '文件' : row.assetType === 'DOCUMENT' ? '文档' : contentTypeLabel(row.itemType)
  const confirmText =
    row.assetType === 'FILE'
      ? `确认删除“${row.title}”吗？文件会被移入项目 trash 回收文件夹。`
      : `确认删除“${row.title}”吗？`
  try {
    await ElMessageBox.confirm(confirmText, `删除${targetLabel}`, {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger',
    })
    if (row.assetType === 'FILE') {
      await deleteFile(row.assetId)
      ElMessage.success('文件已移入回收文件夹')
    } else if (row.assetType === 'DOCUMENT') {
      await deleteDocument(row.assetId)
      ElMessage.success('文档已删除')
    } else {
      await deleteContentItem(row.assetId)
      ElMessage.success(`${contentTypeLabel(row.itemType)}已删除`)
    }
    await load()
    await ensureCurrentPage()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(resolveErrorMessage(error, '删除失败，请稍后重试'))
    }
  }
}

const handleRowCommand = async (row: KnowledgeAssetView, command: string | number | object) => {
  switch (String(command)) {
    case 'preview':
      await previewAsset(row)
      break
    case 'edit':
      await editAsset(row)
      break
    case 'download':
      await downloadAsset(row)
      break
    case 'retry':
      await retryAsset(row)
      break
    case 'delete':
      await deleteAsset(row)
      break
    default:
      break
  }
}

const retryAssetLoad = async () => {
  await load()
}

onMounted(async () => {
  await loadProjects()
  await load()
})
</script>

<style scoped>
.admin-assets-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.admin-assets-toolbar {
  justify-content: space-between;
}

.admin-assets-toolbar__search-group {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: min(100%, 420px);
  flex: 1;
}

.admin-assets-toolbar__actions {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.admin-assets-page .project-assets-table__name {
  min-width: 108px;
}

.admin-assets-page .project-assets-table__copy {
  max-width: 132px;
}

.admin-assets-page .project-assets-table__copy strong,
.admin-assets-page .project-assets-table__copy small {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.project-detail__search {
  flex: 1;
  min-width: min(100%, 320px);
  max-width: 460px;
}

.project-detail__search :deep(.el-input__wrapper) {
  min-height: 46px;
  border-radius: 14px;
  background: #e0e2e9;
  box-shadow: none;
}

.project-detail__search :deep(.el-input__wrapper.is-focus) {
  background: #ffffff;
  box-shadow: 0 0 0 2px rgba(0, 96, 169, 0.12);
}

.project-detail__search :deep(.el-input__prefix-inner) {
  color: #5f6775;
}

.project-detail__meta-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 34px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(241, 243, 250, 0.95);
  color: #56606f;
  font-size: 12px;
  font-weight: 700;
}

.project-detail__add-button {
  min-height: 46px;
  padding: 0 18px;
  border-radius: 14px;
  font-weight: 800;
  box-shadow: 0 12px 24px rgba(0, 96, 169, 0.16);
}

.project-detail__table-shell {
  overflow: hidden;
  border-radius: 20px;
  background: #ffffff;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.06);
  border: 1px solid rgba(192, 199, 212, 0.24);
}

.project-detail__state {
  padding: 24px;
}

.project-detail__refreshing {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 14px 24px 0;
  color: #667085;
  font-size: 13px;
  font-weight: 600;
}

.project-detail__refreshing-icon {
  font-size: 18px;
  animation: admin-assets-spin 1s linear infinite;
}

.project-assets-table {
  overflow-x: auto;
}

.project-assets-table table {
  width: 100%;
  border-collapse: collapse;
}

.project-assets-table thead th {
  padding: 14px 20px;
  background: #f1f3fa;
  color: #5f6775;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  text-align: left;
}

.project-assets-table__actions-head {
  width: 80px;
  text-align: right;
}

.project-assets-table tbody tr {
  cursor: pointer;
  transition: background 0.18s ease;
}

.project-assets-table tbody tr:hover {
  background: #f8f9ff;
}

.project-assets-table tbody td {
  padding: 14px 20px;
  border-top: 1px solid rgba(192, 199, 212, 0.16);
  color: #48505e;
  font-size: 14px;
  vertical-align: middle;
}

.project-assets-table__name {
  display: flex;
  align-items: center;
  gap: 12px;
}

.project-assets-table__icon {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: rgba(64, 158, 255, 0.14);
  color: #0060a9;
  flex-shrink: 0;
}

.project-assets-table__icon.is-danger {
  background: rgba(186, 26, 26, 0.12);
  color: #ba1a1a;
}

.project-assets-table__icon.is-success {
  background: rgba(85, 175, 40, 0.16);
  color: #286c00;
}

.project-assets-table__icon.is-warning {
  background: rgba(255, 171, 0, 0.16);
  color: #a15c00;
}

.project-assets-table__copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.project-assets-table__copy strong {
  font-size: 15px;
  font-weight: 700;
  color: #181c20;
}

.project-assets-table__copy small {
  color: #7a8392;
  font-size: 12px;
}

.project-assets-table__meta-stack {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 112px;
}

.project-assets-table__meta-stack strong {
  font-size: 14px;
  font-weight: 700;
  color: #181c20;
}

.project-assets-table__type {
  color: #626b77;
  font-weight: 600;
  white-space: nowrap;
}

.project-assets-table__status-stack {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.project-assets-table__status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 28px;
  padding: 4px 8px;
  border: 0;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 800;
}

.project-assets-table__status .material-symbols-outlined {
  font-size: 16px;
}

.project-assets-table__status.is-clickable {
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease, opacity 0.18s ease;
}

.project-assets-table__status.is-clickable:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 18px rgba(186, 26, 26, 0.16);
  opacity: 0.92;
}

.project-assets-table__status.is-success {
  background: rgba(85, 175, 40, 0.18);
  color: #206100;
}

.project-assets-table__status.is-warning {
  background: rgba(255, 171, 0, 0.16);
  color: #9a5600;
}

.project-assets-table__status.is-primary {
  background: rgba(64, 158, 255, 0.14);
  color: #005ea6;
}

.project-assets-table__status.is-danger {
  background: rgba(255, 218, 214, 0.95);
  color: #93000a;
}

.project-assets-table__status.is-info {
  background: rgba(225, 226, 231, 0.95);
  color: #4b5563;
}

.project-assets-table__status-reason {
  max-width: 180px;
  color: #ba1a1a;
  font-size: 12px;
  line-height: 1.35;
}

.project-assets-table__mono {
  font-family: Consolas, 'Courier New', monospace;
}

.project-assets-table__actions-cell {
  text-align: right;
}

.project-assets-table__menu-trigger {
  min-width: 40px;
  height: 34px;
  padding: 0 10px;
  border: 1px solid rgba(192, 199, 212, 0.3);
  border-radius: 10px;
  background: #ffffff;
  color: #475467;
  font-size: 16px;
  font-weight: 800;
  cursor: pointer;
  transition: color 0.18s ease, border-color 0.18s ease, background-color 0.18s ease;
}

.project-assets-table__menu-trigger:hover {
  color: #0060a9;
  border-color: rgba(0, 96, 169, 0.24);
  background: #f8f9ff;
}

.project-detail__table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid rgba(192, 199, 212, 0.16);
  background: rgba(241, 243, 250, 0.4);
  flex-wrap: wrap;
}

.project-detail__table-count {
  color: #667085;
  font-size: 13px;
  font-weight: 600;
}

.admin-assets-export-dialog {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.admin-assets-export-dialog__copy {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.admin-assets-export-dialog__copy strong {
  font-size: 16px;
  font-weight: 700;
  color: #181c20;
}

.admin-assets-export-dialog__copy span {
  color: #667085;
  font-size: 13px;
}

.admin-assets-export-dialog__actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

@keyframes admin-assets-spin {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1100px) {
  .admin-assets-toolbar {
    align-items: stretch;
  }

  .admin-assets-toolbar__search-group {
    width: 100%;
  }

  .admin-assets-toolbar__actions {
    width: 100%;
    justify-content: space-between;
  }

  .project-detail__search {
    max-width: none;
  }
}

@media (max-width: 768px) {
  .admin-assets-export-dialog__actions {
    grid-template-columns: minmax(0, 1fr);
  }

  .project-assets-table thead th,
  .project-assets-table tbody td,
  .project-detail__table-footer {
    padding-left: 14px;
    padding-right: 14px;
  }
}
</style>
