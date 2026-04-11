<template>
  <div class="page-shell knowledge-page">
    <section class="knowledge-toolbar">
      <div class="knowledge-toolbar__search">
        <el-input
          v-model="keyword"
          clearable
          placeholder="搜索知识资料..."
          @clear="runSearch"
          @keyup.enter="runSearch"
        >
          <template #prefix>
            <span class="material-symbols-outlined">search</span>
          </template>
        </el-input>
      </div>
    </section>

    <section class="knowledge-subnav">
      <ProjectSubnav
        :project-id="0"
        mode="value"
        :model-value="activeAssetTab"
        :items="knowledgeSubnavItems"
        @update:modelValue="switchAssetTab"
      />
    </section>

    <section class="knowledge-table-shell">
      <div v-if="assetsLoading" class="knowledge-table-shell__state">
        <el-skeleton animated>
          <template #template>
            <el-skeleton-item variant="rect" style="width: 100%; height: 56px; border-radius: 18px;" />
            <el-skeleton-item variant="rect" style="width: 100%; height: 72px; margin-top: 14px; border-radius: 16px;" />
            <el-skeleton-item variant="rect" style="width: 100%; height: 72px; margin-top: 12px; border-radius: 16px;" />
            <el-skeleton-item variant="rect" style="width: 100%; height: 72px; margin-top: 12px; border-radius: 16px;" />
          </template>
        </el-skeleton>
      </div>

      <div v-else-if="assetError" class="knowledge-table-shell__state">
        <el-result icon="warning" title="知识资料加载失败" :sub-title="assetError">
          <template #extra>
            <el-button type="primary" @click="retryAssetLoad">重试加载</el-button>
          </template>
        </el-result>
      </div>

      <div v-else-if="assets.length" class="knowledge-assets-table">
        <table>
          <thead>
            <tr>
              <th>名称</th>
              <th>类型</th>
              <th>状态</th>
              <th>大小</th>
              <th>更新时间</th>
              <th>上传时间</th>
              <th class="knowledge-assets-table__actions-head">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in assets" :key="`${row.assetType}-${row.assetId}`" @click="openAsset(row)">
              <td>
                <div class="knowledge-assets-table__name">
                  <div class="knowledge-assets-table__icon" :class="`is-${assetTone(row)}`">
                    <span class="material-symbols-outlined">{{ assetIcon(row) }}</span>
                  </div>
                  <div class="knowledge-assets-table__copy">
                    <strong>{{ row.title }}</strong>
                    <small>{{ secondaryLine(row) }}</small>
                  </div>
                </div>
              </td>
              <td><span class="knowledge-assets-table__type">{{ assetTypeLabel(row) }}</span></td>
              <td>
                <div class="knowledge-assets-table__status-stack">
                  <button
                    v-if="canRetryAsset(row)"
                    type="button"
                    class="knowledge-assets-table__status is-clickable"
                    :class="`is-${assetTone(row)}`"
                    @click.stop="retryAsset(row)"
                  >
                    <span class="material-symbols-outlined">{{ assetStatusIcon(row) }}</span>
                    <span>{{ assetStatusLabel(row) }}</span>
                  </button>
                  <span v-else class="knowledge-assets-table__status" :class="`is-${assetTone(row)}`">
                    <span class="material-symbols-outlined">{{ assetStatusIcon(row) }}</span>
                    <span>{{ assetStatusLabel(row) }}</span>
                  </span>
                  <small v-if="shouldShowFailureReason(row)" class="knowledge-assets-table__status-reason">
                    {{ assetFailureReason(row) }}
                  </small>
                </div>
              </td>
              <td class="knowledge-assets-table__mono">{{ row.assetType === 'FILE' ? formatFileSize(row.fileSize) : '--' }}</td>
              <td>{{ relativeTime(row.updatedAt) }}</td>
              <td>{{ formatDateTime(row.createdAt) }}</td>
              <td class="knowledge-assets-table__actions-cell" @click.stop>
                <el-dropdown trigger="click" @command="handleRowCommand(row, $event)">
                  <button type="button" class="knowledge-assets-table__menu-trigger" @click.stop><span>···</span></button>
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

      <el-empty
        v-else
        :image-size="84"
        :description="`当前筛选下还没有${assetCollectionLabel}，可以通过右上角添加入口继续补充。`"
      />

      <div v-if="!assetError" class="knowledge-table-shell__footer">
        <span class="knowledge-table-shell__count">共 {{ total }} 条{{ assetCollectionLabel }}</span>
        <CompactPager variant="project" :page-num="pageNum" :page-size="pageSize" :total="total" @change="handlePageChange" />
      </div>
    </section>

    <el-dialog v-model="exportDialogVisible" title="选择导出格式" width="440px">
      <div class="knowledge-export-dialog">
        <div class="knowledge-export-dialog__copy">
          <strong>{{ exportTarget?.title || '当前文档' }}</strong>
          <span>导出格式与文档编辑页保持一致。</span>
        </div>
        <div class="knowledge-export-dialog__actions">
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
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { deleteContentItem } from '@/api/content'
import { deleteDocument, getDocument, retryDocumentIndex } from '@/api/document'
import { deleteFile, downloadFileContent, retryFileParse } from '@/api/file'
import { getKnowledgeAssets } from '@/api/knowledge'
import CompactPager from '@/components/common/CompactPager.vue'
import ProjectSubnav from '@/components/common/ProjectSubnav.vue'
import { useFilePreview } from '@/composables/useFilePreview'
import { useKnowledgeStatusPolling } from '@/composables/useKnowledgeStatusPolling'
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
import {
  exportDocumentAsDoc,
  exportDocumentAsMarkdown,
  exportElementAsJpg,
  exportElementAsPdf,
  renderDocumentHtml,
} from '@/utils/documentExport'

type KnowledgeAssetTab = 'overview' | 'files' | 'documents' | 'content'
type DocumentExportFormat = 'doc' | 'pdf' | 'markdown' | 'jpg'

const route = useRoute()
const router = useRouter()
const { loadProjects, projectLabel } = useProjectDirectory()
const { previewFile } = useFilePreview()

const knowledgeSubnavItems: Array<{ key: KnowledgeAssetTab; label: string }> = [
  { key: 'overview', label: '概览' },
  { key: 'files', label: '文件' },
  { key: 'documents', label: '文档' },
  { key: 'content', label: '表格' },
]

const keyword = ref('')
const activeAssetTab = ref<KnowledgeAssetTab>('overview')
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const assets = ref<KnowledgeAssetView[]>([])
const assetsLoading = ref(true)
const assetError = ref('')
const exportDialogVisible = ref(false)
const exporting = ref(false)
const exportTarget = ref<KnowledgeAssetView>()

const selectedKnowledgeType = computed<'FILE' | 'DOCUMENT' | 'CONTENT' | undefined>(() => {
  if (activeAssetTab.value === 'files') return 'FILE'
  if (activeAssetTab.value === 'documents') return 'DOCUMENT'
  if (activeAssetTab.value === 'content') return 'CONTENT'
  return undefined
})

const assetCollectionLabel = computed(
  () => ({ overview: '知识资料', files: '文件', documents: '文档', content: '表格内容' }[activeAssetTab.value]),
)

const normalizeAssetTab = (value: unknown): KnowledgeAssetTab =>
  ['files', 'documents', 'content'].includes(String(value)) ? (value as KnowledgeAssetTab) : 'overview'

const assetTypeLabel = (row: KnowledgeAssetView) => {
  if (row.assetType === 'FILE') return normalizeFileTypeLabel(row.fileExt, row.mimeType)
  if (row.assetType === 'DOCUMENT') return '文档'
  return contentTypeLabel(row.itemType)
}

const assetStatusLabel = (row: KnowledgeAssetView) => knowledgeReadinessLabel(row.parseStatus, row.indexStatus)
const assetTone = (row: KnowledgeAssetView) => knowledgeReadinessTone(row.parseStatus, row.indexStatus)
const assetStatusIcon = (row: KnowledgeAssetView) =>
  ({ success: 'check_circle', warning: 'schedule', primary: 'visibility', danger: 'cancel', info: 'edit_note' }[assetTone(row)] ||
    'info') as string

const assetIcon = (row: KnowledgeAssetView) => {
  if (row.assetType === 'DOCUMENT') return 'description'
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
  return 'insert_drive_file'
}

const secondaryLine = (row: KnowledgeAssetView) => {
  const projectName = projectLabel(row.projectId)
  if (row.assetType === 'FILE') {
    return `${projectName} · ${normalizeFileTypeLabel(row.fileExt, row.mimeType)} · ${formatFileSize(row.fileSize)}`
  }
  if (row.assetType === 'DOCUMENT') {
    return row.summary ? `${projectName} · ${row.summary}` : `${projectName} · 在线文档`
  }
  return row.summary ? `${projectName} · ${row.summary}` : `${projectName} · ${contentTypeLabel(row.itemType)}内容`
}

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
const canRetryAsset = (row: KnowledgeAssetView) =>
  (row.assetType === 'FILE' || row.assetType === 'DOCUMENT') && isKnowledgeFailed(row.parseStatus, row.indexStatus)
const shouldShowFailureReason = (row: KnowledgeAssetView) => isKnowledgeFailed(row.parseStatus, row.indexStatus)

const assetFailureReason = (row: KnowledgeAssetView) => {
  const message = (row.parseErrorMessage || '').trim().toLowerCase()
  if (!message) return '解析异常，请稍后重试'
  if (message.includes('password') || message.includes('encrypted')) return '文件已加密'
  if (message.includes('timeout')) return '解析超时'
  if (message.includes('unsupported') || message.includes('not support')) return '格式暂不支持'
  if (message.includes('too long') || message.includes('data truncation')) return '内容过长'
  if (message.includes('empty') || message.includes('blank')) return '内容为空'
  if (message.includes('network') || message.includes('connect')) return '连接解析服务失败'
  return '解析异常，请稍后重试'
}

const loadAssets = async () => {
  assetsLoading.value = true
  assetError.value = ''
  try {
    const page = await getKnowledgeAssets({
      type: selectedKnowledgeType.value,
      q: keyword.value.trim() || undefined,
      knowledgeOnly: activeAssetTab.value === 'overview' || activeAssetTab.value === 'files',
      pageNum: pageNum.value,
      pageSize,
    })
    assets.value = page.records
    total.value = page.total
  } catch (error) {
    assetError.value = resolveErrorMessage(error, '知识资料加载失败，请稍后重试')
  } finally {
    assetsLoading.value = false
  }
}

const syncFromRoute = async () => {
  activeAssetTab.value = normalizeAssetTab(route.query.tab)
  keyword.value = typeof route.query.q === 'string' ? route.query.q : ''
  const nextPage = Number(route.query.pageNum)
  pageNum.value = Number.isFinite(nextPage) && nextPage > 0 ? nextPage : 1
  await loadAssets()
}

const pushRoute = async () => {
  await router.replace({
    path: '/knowledge',
    query: {
      ...(activeAssetTab.value !== 'overview' ? { tab: activeAssetTab.value } : {}),
      ...(keyword.value.trim() ? { q: keyword.value.trim() } : {}),
      ...(pageNum.value > 1 ? { pageNum: pageNum.value } : {}),
    },
  })
}

const ensureCurrentPage = async () => {
  if (!assets.value.length && total.value > 0 && pageNum.value > 1) {
    pageNum.value = Math.max(1, Math.ceil(total.value / pageSize))
    await pushRoute()
  }
}

const runSearch = async () => {
  pageNum.value = 1
  await pushRoute()
}

const handlePageChange = async (value: number) => {
  pageNum.value = value
  await pushRoute()
}

const switchAssetTab = async (value: string) => {
  const nextTab = normalizeAssetTab(value)
  if (nextTab === activeAssetTab.value) return
  activeAssetTab.value = nextTab
  pageNum.value = 1
  await pushRoute()
}

useKnowledgeStatusPolling({
  records: assets,
  reload: loadAssets,
  enabled: computed(() => !assetError.value),
  intervalMs: 5000,
})

const openAsset = (row: KnowledgeAssetView) => {
  if (row.assetType === 'FILE') {
    void router.push(`/files/${row.assetId}`)
    return
  }
  if (row.assetType === 'DOCUMENT') {
    void router.push({ path: `/documents/${row.assetId}/edit`, query: { mode: 'preview' } })
    return
  }
  void router.push({ path: `/contents/${row.assetId}/edit`, query: { mode: 'preview' } })
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

const retryAsset = async (row: KnowledgeAssetView) => {
  try {
    if (row.assetType === 'FILE') {
      await retryFileParse(row.assetId)
      ElMessage.success('文件已重新进入解析队列')
    } else if (row.assetType === 'DOCUMENT') {
      await retryDocumentIndex(row.assetId)
      ElMessage.success('文档已重新进入索引队列')
    }
    await loadAssets()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, row.assetType === 'FILE' ? '重新解析失败，请稍后重试' : '重试索引失败，请稍后重试'))
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
    await loadAssets()
    await ensureCurrentPage()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(resolveErrorMessage(error, '删除失败，请稍后重试'))
    }
  }
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
  await loadAssets()
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
  gap: 24px;
}

.knowledge-toolbar {
  display: flex;
  align-items: center;
  gap: 18px;
}

.knowledge-toolbar__search {
  flex: 1;
  min-width: min(100%, 320px);
  max-width: 520px;
}

.knowledge-toolbar__search :deep(.el-input__wrapper) {
  min-height: 46px;
  border-radius: 14px;
  background: #e0e2e9;
  box-shadow: none;
}

.knowledge-toolbar__search :deep(.el-input__wrapper.is-focus) {
  background: #ffffff;
  box-shadow: 0 0 0 2px rgba(0, 96, 169, 0.12);
}

.knowledge-toolbar__search :deep(.el-input__prefix-inner) {
  color: #5f6775;
}

.knowledge-subnav {
  display: flex;
  align-items: center;
}

.knowledge-table-shell {
  overflow: hidden;
  border-radius: 20px;
  background: #ffffff;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.06);
  border: 1px solid rgba(192, 199, 212, 0.24);
}

.knowledge-table-shell__state {
  padding: 24px;
}

.knowledge-assets-table {
  overflow-x: auto;
}

.knowledge-assets-table table {
  width: 100%;
  border-collapse: collapse;
}

.knowledge-assets-table thead th {
  padding: 16px 24px;
  background: #f1f3fa;
  color: #5f6775;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  text-align: left;
}

.knowledge-assets-table__actions-head {
  width: 80px;
  text-align: right;
}

.knowledge-assets-table tbody tr {
  cursor: pointer;
  transition: background 0.18s ease;
}

.knowledge-assets-table tbody tr:hover {
  background: #f8f9ff;
}

.knowledge-assets-table tbody td {
  padding: 18px 24px;
  border-top: 1px solid rgba(192, 199, 212, 0.16);
  color: #48505e;
  font-size: 14px;
}

.knowledge-assets-table__name {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 280px;
}

.knowledge-assets-table__icon {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  background: rgba(64, 158, 255, 0.14);
  color: #0060a9;
  flex-shrink: 0;
}

.knowledge-assets-table__icon.is-danger {
  background: rgba(186, 26, 26, 0.12);
  color: #ba1a1a;
}

.knowledge-assets-table__icon.is-success {
  background: rgba(85, 175, 40, 0.16);
  color: #286c00;
}

.knowledge-assets-table__icon.is-warning {
  background: rgba(255, 171, 0, 0.16);
  color: #a15c00;
}

.knowledge-assets-table__copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.knowledge-assets-table__copy strong {
  font-size: 15px;
  font-weight: 700;
  color: #181c20;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.knowledge-assets-table__copy small {
  color: #7a8392;
  font-size: 12px;
}

.knowledge-assets-table__type {
  color: #626b77;
  font-weight: 600;
}

.knowledge-assets-table__status-stack {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.knowledge-assets-table__status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 0;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
}

.knowledge-assets-table__status.is-clickable {
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease, opacity 0.18s ease;
}

.knowledge-assets-table__status.is-clickable:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 18px rgba(186, 26, 26, 0.16);
  opacity: 0.92;
}

.knowledge-assets-table__status.is-success {
  background: rgba(85, 175, 40, 0.18);
  color: #206100;
}

.knowledge-assets-table__status.is-warning {
  background: rgba(255, 171, 0, 0.16);
  color: #9a5600;
}

.knowledge-assets-table__status.is-primary {
  background: rgba(64, 158, 255, 0.14);
  color: #005ea6;
}

.knowledge-assets-table__status.is-danger {
  background: rgba(255, 218, 214, 0.95);
  color: #93000a;
}

.knowledge-assets-table__status.is-info {
  background: rgba(225, 226, 231, 0.95);
  color: #4b5563;
}

.knowledge-assets-table__status-reason {
  max-width: 180px;
  color: #ba1a1a;
  font-size: 12px;
  line-height: 1.35;
}

.knowledge-assets-table__mono {
  font-family: Consolas, 'Courier New', monospace;
}

.knowledge-assets-table__actions-cell {
  text-align: right;
}

.knowledge-assets-table__menu-trigger {
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

.knowledge-assets-table__menu-trigger:hover {
  color: #0060a9;
  border-color: rgba(0, 96, 169, 0.24);
  background: #f8f9ff;
}

.knowledge-table-shell__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid rgba(192, 199, 212, 0.16);
  background: rgba(241, 243, 250, 0.4);
  flex-wrap: wrap;
}

.knowledge-table-shell__count {
  color: #667085;
  font-size: 13px;
  font-weight: 600;
}

.knowledge-export-dialog {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.knowledge-export-dialog__copy {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.knowledge-export-dialog__copy strong {
  font-size: 16px;
  font-weight: 700;
  color: #181c20;
}

.knowledge-export-dialog__copy span {
  color: #667085;
  font-size: 13px;
}

.knowledge-export-dialog__actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

@media (max-width: 1100px) {
  .knowledge-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .knowledge-toolbar__search {
    max-width: none;
  }
}

@media (max-width: 768px) {
  .knowledge-subnav {
    width: 100%;
  }

  .knowledge-assets-table thead th,
  .knowledge-assets-table tbody td,
  .knowledge-table-shell__footer {
    padding-left: 16px;
    padding-right: 16px;
  }

  .knowledge-export-dialog__actions {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
