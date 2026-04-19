<template>
  <div class="page-shell project-detail-page">
    <el-skeleton v-if="projectLoading && !project && !pageError" class="project-detail__page-state" animated>
      <template #template>
        <el-skeleton-item variant="p" style="width: 180px; height: 18px;" />
        <el-skeleton-item variant="h1" style="width: min(520px, 72%); height: 42px; margin-top: 20px;" />
        <el-skeleton-item variant="text" style="width: min(720px, 88%); height: 16px; margin-top: 18px;" />
        <el-skeleton-item variant="rect" style="width: 100%; height: 420px; margin-top: 28px; border-radius: 24px;" />
      </template>
    </el-skeleton>

    <el-result v-else-if="pageError" class="project-detail__page-state" icon="error" title="项目加载失败"
      :sub-title="pageError">
      <template #extra>
        <el-button type="primary" @click="retryPageLoad">重新加载</el-button>
        <el-button @click="router.push('/projects')">返回项目中心</el-button>
      </template>
    </el-result>

    <template v-else-if="project">
      <section class="project-detail__hero">
        <div class="project-detail__breadcrumb">
          <button type="button" class="project-detail__breadcrumb-link" @click="router.push('/projects')">项目</button>
          <span class="material-symbols-outlined">chevron_right</span>
          <span class="project-detail__breadcrumb-current">{{ project.name }}</span>
        </div>
        <div class="project-detail__hero-body">
          <div class="project-detail__hero-main">
            <button type="button" class="project-detail__back" @click="router.push('/projects')">
              <span class="material-symbols-outlined">arrow_back</span>
              <span>返回项目中心</span>
            </button>
            <div class="project-detail__hero-copy">
              <h2>{{ project.name }}</h2>
              <p>{{ project.description || '在这里统一查看项目资料概览，并快速进入文件、文档或表格。' }}</p>
            </div>
          </div>
          <div class="project-detail__hero-actions">
            <el-button class="project-detail__action-button" @click="shareProject">
              <span class="material-symbols-outlined">share</span>
              <span>分享</span>
            </el-button>
            <el-button type="primary" class="project-detail__action-button" @click="openEditDialog">
              <span class="material-symbols-outlined">edit</span>
              <span>修改项目</span>
            </el-button>
          </div>
        </div>
      </section>

      <section class="project-detail__toolbar">
        <div class="project-detail__search">
          <el-input v-model="keyword" clearable placeholder="搜索项目资料..." @clear="runSearch" @keyup.enter="runSearch">
            <template #prefix>
              <span class="material-symbols-outlined">search</span>
            </template>
            <template #suffix>
              <SearchSuffixButton @click="runSearch" />
            </template>
          </el-input>
        </div>
        <span class="project-detail__meta-chip">
          <span class="material-symbols-outlined">folder</span>
          <span>{{ project.fileCount }} 个文件</span>
        </span>
        <span class="project-detail__meta-chip">
          <span class="material-symbols-outlined">description</span>
          <span>{{ project.documentCount }} 篇文档</span>
        </span>
        <span class="project-detail__meta-chip">
          <span class="material-symbols-outlined">schedule</span>
          <span>更新于 {{ relativeTime(project.updatedAt) }}</span>
        </span>
        <div class="project-detail__toolbar-actions">
          <ProjectSubnav :project-id="projectId" mode="value" :model-value="activeAssetTab" :items="projectSubnavItems"
            @update:modelValue="switchAssetTab" />
          <el-dropdown trigger="click" @command="handleDirectAddCommand">
            <el-button type="primary" class="project-detail__add-button">
              <span class="material-symbols-outlined">add</span>
              <span>添加</span>
              <span class="material-symbols-outlined project-detail__add-button-icon">expand_more</span>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="document"><span class="project-detail__dropdown-item"><span
                      class="material-symbols-outlined">description</span><span>新建文档</span></span></el-dropdown-item>
                <el-dropdown-item command="file"><span class="project-detail__dropdown-item"><span
                      class="material-symbols-outlined">upload_file</span><span>上传文件</span></span></el-dropdown-item>
                <el-dropdown-item command="table"><span class="project-detail__dropdown-item"><span
                      class="material-symbols-outlined">table_chart</span><span>新建表格</span></span></el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <input ref="fileInputRef" class="project-detail__hidden-input" type="file" :accept="knowledgeFileAccept"
            @change="handleFileSelected" />
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
                <th>类型</th>
                <th>状态</th>
                <th>大小</th>
                <th>更新时间</th>
                <th>上传时间</th>
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
                <td><span class="project-assets-table__type">{{ assetTypeLabel(row) }}</span></td>
                <td>
                  <div class="project-assets-table__status-stack">
                    <button v-if="isRetryableFailedFile(row)" type="button"
                      class="project-assets-table__status is-clickable" :class="`is-${assetTone(row)}`"
                      @click.stop="retryAssetFromStatus(row)">
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
                <td class="project-assets-table__mono">{{ row.assetType === 'FILE' ? formatFileSize(row.fileSize) : '--'
                }}
                </td>
                <td>{{ relativeTime(row.updatedAt) }}</td>
                <td>{{ formatDateTime(row.createdAt) }}</td>
                <td class="project-assets-table__actions-cell" @click.stop>
                  <el-dropdown trigger="click" @command="handleRowCommand(row, $event)">
                    <button type="button" class="project-assets-table__menu-trigger"
                      @click.stop><span>···</span></button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="preview">预览</el-dropdown-item>
                        <el-dropdown-item v-if="canEditAsset(row)" command="edit">修改</el-dropdown-item>
                        <el-dropdown-item v-if="canDownloadAsset(row)" command="download">下载</el-dropdown-item>
                        <el-dropdown-item command="delete">删除</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <el-empty v-else :image-size="84" :description="`当前筛选下还没有${assetCollectionLabel}，可以通过右上角添加入口继续补充。`" />
        </template>

        <div v-if="!assetError" class="project-detail__table-footer">
          <span class="project-detail__table-count">共 {{ total }} 条{{ assetCollectionLabel }}</span>
          <CompactPager variant="project" :page-num="pageNum" :page-size="pageSize" :total="total"
            @change="handlePageChange" />
        </div>
      </section>

      <el-dialog v-model="dialogVisible" title="修改项目" width="460px">
        <el-form :model="form" label-position="top">
          <el-form-item label="项目名称"><el-input v-model="form.name" maxlength="80" show-word-limit /></el-form-item>
          <el-form-item label="项目简介"><el-input v-model="form.description" type="textarea" :rows="4" maxlength="240"
              show-word-limit /></el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="submitEdit">保存</el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="exportDialogVisible" title="选择导出格式" width="440px">
        <div class="project-detail__export-dialog">
          <div class="project-detail__export-copy">
            <strong>{{ exportTarget?.title || '当前笔记' }}</strong>
            <span>导出格式与文档编辑页保持一致。</span>
          </div>
          <div class="project-detail__export-actions">
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
    </template>
  </div>
</template>

<script setup lang="ts">
import dayjs from 'dayjs'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { createContentItem, deleteContentItem } from '@/api/content'
import { deleteDocument, getDocument } from '@/api/document'
import { deleteFile, downloadFileContent, retryFileParse } from '@/api/file'
import { getKnowledgeAssets } from '@/api/knowledge'
import { getProject, updateProject } from '@/api/project'
import CompactPager from '@/components/common/CompactPager.vue'
import ProjectSubnav from '@/components/common/ProjectSubnav.vue'
import SearchSuffixButton from '@/components/common/SearchSuffixButton.vue'
import { useFilePreview } from '@/composables/useFilePreview'
import { knowledgeFileAccept, useKnowledgeFileUpload } from '@/composables/useKnowledgeFileUpload'
import { useVisibleFileStatusPolling } from '@/composables/useVisibleFileStatusPolling'
import type { FileView, KnowledgeAssetView, ProjectDetailView } from '@/types/models'
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
import { copyToClipboard } from '@/utils/object-operations'

type ProjectAssetTab = 'overview' | 'files' | 'documents' | 'content'
type DocumentExportFormat = 'doc' | 'pdf' | 'markdown' | 'jpg'

const props = defineProps<{ id: string }>()

const route = useRoute()
const router = useRouter()
const { previewFile } = useFilePreview()
const projectId = Number(props.id)
const project = ref<ProjectDetailView>()
const assets = ref<KnowledgeAssetView[]>([])
const projectLoading = ref(true)
const assetsLoading = ref(true)
const pageError = ref('')
const assetError = ref('')
const keyword = ref('')
const activeAssetTab = ref<ProjectAssetTab>('overview')
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const dialogVisible = ref(false)
const submitting = ref(false)
const exportDialogVisible = ref(false)
const exporting = ref(false)
const exportTarget = ref<KnowledgeAssetView>()
const fileInputRef = ref<HTMLInputElement>()
const form = reactive({ name: '', description: '' })
const optimisticUploadedFiles = ref<Record<number, FileView>>({})

const ACTIVE_FILE_STATUSES = new Set(['INIT', 'UPLOADING', 'PENDING', 'PROCESSING'])
const normalizeFileStatus = (value?: string) => (value || '').trim().toUpperCase()
const hasActiveFileStatus = (record?: { parseStatus?: string; indexStatus?: string }) =>
  ACTIVE_FILE_STATUSES.has(normalizeFileStatus(record?.parseStatus)) ||
  ACTIVE_FILE_STATUSES.has(normalizeFileStatus(record?.indexStatus))

const projectSubnavItems: Array<{ key: ProjectAssetTab; label: string }> = [
  { key: 'overview', label: '概览' },
  { key: 'files', label: '文件' },
  { key: 'documents', label: '文档' },
  { key: 'content', label: '表格' },
]

const selectedKnowledgeType = computed<'FILE' | 'DOCUMENT' | 'CONTENT' | undefined>(() => {
  if (activeAssetTab.value === 'files') return 'FILE'
  if (activeAssetTab.value === 'documents') return 'DOCUMENT'
  if (activeAssetTab.value === 'content') return 'CONTENT'
  return undefined
})

const assetCollectionLabel = computed(() => ({ overview: '项目资料', files: '文件', documents: '文档', content: '表格内容' }[activeAssetTab.value]))

const assetTypeLabel = (row: KnowledgeAssetView) => {
  if (row.assetType === 'FILE') return normalizeFileTypeLabel(row.fileExt, row.mimeType)
  if (row.assetType === 'DOCUMENT') return '笔记'
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
  ({ success: 'check_circle', warning: 'schedule', primary: 'visibility', danger: 'cancel', info: 'edit_note' }[assetTone(row)] || 'info') as string

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
  if (row.assetType === 'FILE') return `${normalizeFileTypeLabel(row.fileExt, row.mimeType)} · ${formatFileSize(row.fileSize)}`
  if (row.assetType === 'DOCUMENT') return row.summary || '在线笔记'
  return row.summary || `${contentTypeLabel(row.itemType)}内容`
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

const canEditAsset = (row: KnowledgeAssetView) => row.assetType === 'FILE' ? isOfficeEditableFile(row.fileExt) : true
const canDownloadAsset = (row: KnowledgeAssetView) => row.assetType === 'FILE' || row.assetType === 'DOCUMENT'
const isRetryableFailedFile = (row: KnowledgeAssetView) => row.assetType === 'FILE' && isKnowledgeFailed(row.parseStatus, row.indexStatus)
const shouldShowFailureReason = (row: KnowledgeAssetView) => isRetryableFailedFile(row)

const assetFailureReason = (row: KnowledgeAssetView) => {
  const message = (row.parseErrorMessage || '').trim().toLowerCase()
  if (!message) {
    return '解析异常，请重试'
  }
  if (message.includes('password') || message.includes('encrypted')) {
    return '文件已加密'
  }
  if (message.includes('timeout')) {
    return '解析超时'
  }
  if (message.includes('unsupported') || message.includes('not support')) {
    return '格式暂不支持'
  }
  if (message.includes('too long') || message.includes('data truncation')) {
    return '内容异常过长'
  }
  if (message.includes('empty') || message.includes('blank')) {
    return '文件内容为空'
  }
  if (message.includes('network') || message.includes('connect')) {
    return '连接解析失败'
  }
  return '解析异常，请重试'
}

const createTableDefaults = () => ({
  title: '未命名表格',
  summary: '适合记录项目清单、计划排期和轻量表格信息。',
  contentJson: JSON.stringify({
    columns: 6,
    rows: Array.from({ length: 8 }, () => Array.from({ length: 6 }, () => '')),
  }),
  plainText: '',
})

const toFileAssetRow = (file: FileView): KnowledgeAssetView => ({
  assetType: 'FILE',
  assetId: file.id,
  projectId: file.projectId,
  title: file.fileName,
  fileExt: file.fileExt,
  mimeType: file.mimeType,
  fileSize: file.fileSize,
  parseStatus: file.parseStatus,
  indexStatus: file.indexStatus,
  parseErrorMessage: file.parseErrorMessage,
  updatedAt: file.updatedAt,
  createdAt: file.createdAt,
})

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

const mergeOptimisticUploadedFiles = (rows: KnowledgeAssetView[]) => {
  if (activeAssetTab.value !== 'overview' && activeAssetTab.value !== 'files') {
    return rows
  }

  const existingFileIds = new Set(
    rows
      .filter((row) => row.assetType === 'FILE')
      .map((row) => row.assetId),
  )

  const optimisticRows = Object.values(optimisticUploadedFiles.value)
    .filter((file) => !existingFileIds.has(file.id))
    .map(toFileAssetRow)

  if (!optimisticRows.length) {
    return rows
  }

  return [...optimisticRows, ...rows]
}

const reconcileOptimisticUploadedFiles = (rows: KnowledgeAssetView[]) => {
  const nextFiles = { ...optimisticUploadedFiles.value }
  let changed = false

  rows.forEach((row) => {
    if (row.assetType !== 'FILE' || !nextFiles[row.assetId]) {
      return
    }
    delete nextFiles[row.assetId]
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

const mergeUploadedFile = (file: FileView) => {
  upsertOptimisticUploadedFile(file)
  const nextRow = toFileAssetRow(file)
  const existingIndex = assets.value.findIndex((row) => row.assetType === 'FILE' && row.assetId === file.id)
  if (existingIndex >= 0) {
    assets.value[existingIndex] = nextRow
    return
  }

  if (activeAssetTab.value === 'overview' || activeAssetTab.value === 'files') {
    assets.value = [nextRow, ...assets.value]
    total.value += 1
  }
}

const primeFilesViewWithUpload = (file: FileView) => {
  upsertOptimisticUploadedFile(file)
  const nextRow = toFileAssetRow(file)
  const alreadyExists = assets.value.some((row) => row.assetType === 'FILE' && row.assetId === file.id)
  const existingFileRows = assets.value.filter((row) => row.assetType === 'FILE' && row.assetId !== file.id)
  assets.value = [nextRow, ...existingFileRows]
  total.value = Math.max(total.value + (alreadyExists ? 0 : 1), assets.value.length)
}

const focusFilesAfterUpload = async (uploadedFile?: FileView) => {
  const shouldPushRoute = activeAssetTab.value !== 'files' || Boolean(keyword.value.trim()) || pageNum.value !== 1
  if (uploadedFile) {
    if (shouldPushRoute) {
      primeFilesViewWithUpload(uploadedFile)
    } else {
      mergeUploadedFile(uploadedFile)
    }
    if (project.value) {
      project.value = {
        ...project.value,
        fileCount: project.value.fileCount + 1,
      }
    }
  }
  activeAssetTab.value = 'files'
  keyword.value = ''
  pageNum.value = 1
  if (shouldPushRoute) {
    await pushRoute()
    return
  }
  if (uploadedFile) return
  await Promise.all([loadProject(), loadAssets()])
}

const { beforeUpload } = useKnowledgeFileUpload({
  resolveProjectId: () => projectId,
  onUploaded: async (uploadedFile) => {
    await focusFilesAfterUpload(uploadedFile)
  },
})

const loadProject = async () => {
  projectLoading.value = true
  pageError.value = ''
  try {
    project.value = await getProject(projectId)
  } catch (error) {
    project.value = undefined
    pageError.value = resolveErrorMessage(error, '项目加载失败，请稍后重试')
  } finally {
    projectLoading.value = false
  }
}

const loadAssets = async () => {
  assetsLoading.value = true
  assetError.value = ''
  try {
    const assetPage = await getKnowledgeAssets({
      type: selectedKnowledgeType.value,
      projectId,
      q: keyword.value.trim() || undefined,
      pageNum: pageNum.value,
      pageSize,
    })
    assets.value = mergeOptimisticUploadedFiles(assetPage.records)
    total.value = assetPage.total
    reconcileOptimisticUploadedFiles(assetPage.records)
  } catch (error) {
    assetError.value = resolveErrorMessage(error, '项目资料加载失败，请稍后重试')
  } finally {
    assetsLoading.value = false
  }
}

const load = async () => {
  await Promise.all([loadProject(), loadAssets()])
}

useVisibleFileStatusPolling({
  rows: assets,
  enabled: computed(() => !assetError.value && (activeAssetTab.value === 'overview' || activeAssetTab.value === 'files')),
  intervalMs: 3000,
  getFileId: (row) => (row.assetType === 'FILE' ? row.assetId : undefined),
  isFileActive: (row) => row.assetType === 'FILE' && hasActiveFileStatus(row),
  applyDetail: applyFileDetailToAssetRow,
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
    ElMessage.warning('文件解析仍在处理中，已暂停自动刷新，请稍后手动刷新查看结果')
  },
})

const normalizeAssetTab = (value: unknown): ProjectAssetTab =>
  ['files', 'documents', 'content'].includes(String(value)) ? (value as ProjectAssetTab) : 'overview'

const syncFromRoute = async () => {
  keyword.value = typeof route.query.q === 'string' ? route.query.q : ''
  activeAssetTab.value = normalizeAssetTab(route.query.tab)
  const nextPage = Number(route.query.pageNum)
  pageNum.value = Number.isFinite(nextPage) && nextPage > 0 ? nextPage : 1
  await load()
}

const openFilePicker = () => {
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
    fileInputRef.value.click()
  }
}

const handleFileSelected = async (event: Event) => {
  const input = event.target as HTMLInputElement | null
  const file = input?.files?.[0]
  if (!file) {
    return
  }
  await beforeUpload(file)
  input.value = ''
}

const retryPageLoad = async () => {
  await load()
}

const retryAssetLoad = async () => {
  await loadAssets()
}

const pushRoute = async () => {
  await router.replace({
    path: route.path,
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

const createDocumentDirectly = async () => {
  await router.push({
    path: '/documents/new/edit',
    query: { projectId },
  })
}

const createTableDirectly = async () => {
  const defaults = createTableDefaults()
  try {
    const created = await createContentItem({
      projectId,
      itemType: 'SHEET',
      title: defaults.title,
      summary: defaults.summary,
      contentJson: defaults.contentJson,
      plainText: defaults.plainText,
    })
    ElMessage.success('表格已创建')
    await router.push(`/contents/${created.id}/edit`)
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '表格创建失败，请稍后重试'))
  }
}

const openAsset = (row: KnowledgeAssetView) => {
  if (row.assetType === 'FILE') {
    router.push(`/files/${row.assetId}`)
    return
  }
  if (row.assetType === 'DOCUMENT') {
    router.push({ path: `/documents/${row.assetId}/edit`, query: { mode: 'preview' } })
    return
  }
  router.push({ path: `/contents/${row.assetId}/edit`, query: { mode: 'preview' } })
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

const retryAssetFromStatus = async (row: KnowledgeAssetView) => {
  try {
    await retryFileParse(row.assetId)
    ElMessage.success('文件已重新进入解析队列')
    await load()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '重新解析失败，请稍后重试'))
  }
}

const deleteAsset = async (row: KnowledgeAssetView) => {
  const targetLabel = row.assetType === 'FILE' ? '文件' : row.assetType === 'DOCUMENT' ? '笔记' : contentTypeLabel(row.itemType)
  const confirmText = row.assetType === 'FILE'
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
      ElMessage.success('笔记已删除')
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
    case 'delete':
      await deleteAsset(row)
      break
    default:
      break
  }
}

const openEditDialog = () => {
  if (!project.value) return
  form.name = project.value.name
  form.description = project.value.description || ''
  dialogVisible.value = true
}

const submitEdit = async () => {
  if (!project.value) return
  submitting.value = true
  try {
    const updated = await updateProject(project.value.id, form)
    project.value = updated
    dialogVisible.value = false
    ElMessage.success('项目已更新')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '项目更新失败，请稍后重试'))
  } finally {
    submitting.value = false
  }
}

const shareProject = async () => {
  await copyToClipboard(`${window.location.origin}/projects/${projectId}`, '项目链接')
}

const handleDirectAddCommand = async (command: string | number | object) => {
  switch (String(command)) {
    case 'document':
      await createDocumentDirectly()
      break
    case 'file':
      openFilePicker()
      break
    case 'table':
      await createTableDirectly()
      break
    default:
      break
  }
}

const handleAddCommand = async (command: string | number | object) => {
  switch (String(command)) {
    case 'document':
      await createDocumentDirectly()
      return
      ElMessage.info('已进入项目文档页，可继续新建文档。')
      break
    case 'file':
      openFilePicker()
      return
      ElMessage.info('已进入项目文件页，可继续上传文件。')
      break
    case 'table':
      await createTableDirectly()
      return
      break
    default:
      break
  }
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
.project-detail-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.project-detail__page-state {
  min-height: 420px;
  padding: 28px;
  border-radius: 24px;
  background: #ffffff;
  border: 1px solid rgba(192, 199, 212, 0.24);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.06);
}

.project-detail__hero {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.project-detail__breadcrumb {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary, #667085);
  font-size: 13px;
}

.project-detail__breadcrumb-link {
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  cursor: pointer;
  transition: color 0.2s ease;
}

.project-detail__breadcrumb-link:hover {
  color: var(--brand, #0060a9);
}

.project-detail__breadcrumb-current {
  font-weight: 600;
  color: var(--text, #101828);
}

.project-detail__hero-body {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20px;
}

.project-detail__hero-main {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.project-detail__back {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: fit-content;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--brand, #0060a9);
  font-weight: 700;
  font-size: 14px;
  cursor: pointer;
  transition: gap 0.2s ease, opacity 0.2s ease;
}

.project-detail__back:hover {
  gap: 10px;
  opacity: 0.9;
}

.project-detail__hero-copy {
  display: flex;
  flex-direction: column;
  gap: 12px;
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

.project-detail__hero-copy h1 {
  margin: 0;
  font-size: clamp(32px, 4vw, 42px);
  line-height: 1.08;
  font-weight: 800;
  letter-spacing: -0.03em;
  color: #181c20;
}

.project-detail__hero-copy p {
  max-width: 760px;
  margin: 0;
  color: #5f6775;
  font-size: 18px;
  line-height: 1.72;
}

.project-detail__hero-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.project-detail__action-button {
  min-height: 42px;
  border-radius: 12px;
  font-weight: 700;
}

.project-detail__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  flex-wrap: wrap;
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

.project-detail__toolbar-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.project-detail__add-button {
  min-height: 46px;
  padding: 0 18px;
  border-radius: 14px;
  font-weight: 800;
  box-shadow: 0 12px 24px rgba(0, 96, 169, 0.16);
}

.project-detail__add-button-icon {
  font-size: 18px;
}

.project-detail__hidden-input {
  display: none;
}

.project-detail__dropdown-item {
  display: inline-flex;
  align-items: center;
  gap: 10px;
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
  animation: project-detail-spin 1s linear infinite;
}

.project-assets-table {
  overflow-x: auto;
}

.project-assets-table table {
  width: 100%;
  border-collapse: collapse;
}

.project-assets-table thead th {
  padding: 16px 24px;
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
  padding: 18px 24px;
  border-top: 1px solid rgba(192, 199, 212, 0.16);
  color: #48505e;
  font-size: 14px;
}

.project-assets-table__name {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 280px;
}

.project-assets-table__icon {
  width: 38px;
  height: 38px;
  border-radius: 12px;
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

.project-assets-table__type {
  color: #626b77;
  font-weight: 600;
}

.project-assets-table__status-stack {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.project-assets-table__status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 0;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
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

@keyframes project-detail-spin {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}

.project-detail__export-dialog {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.project-detail__export-copy {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.project-detail__export-copy strong {
  font-size: 16px;
  font-weight: 700;
  color: #181c20;
}

.project-detail__export-copy span {
  color: #667085;
  font-size: 13px;
}

.project-detail__export-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

@media (max-width: 1100px) {
  .project-detail__hero-body {
    flex-direction: column;
  }

  .project-detail__hero-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 768px) {
  .project-detail__toolbar {
    align-items: stretch;
  }

  .project-detail__toolbar-actions {
    width: 100%;
    justify-content: space-between;
  }

  .project-detail__search {
    max-width: none;
  }

  .project-assets-table thead th,
  .project-assets-table tbody td,
  .project-detail__table-footer {
    padding-left: 16px;
    padding-right: 16px;
  }

  .project-detail__export-actions {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
