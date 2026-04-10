<template>
  <div class="page-shell">
    <AppPageHeader :title="form.title || (isPreview ? '文档浏览' : '文档编辑')" show-back back-label="返回文档列表" :back-to="documentBackTarget">
      <template #actions>
        <el-button plain @click="toggleToc">{{ tocVisible ? '隐藏目录' : '显示目录' }}</el-button>
        <el-dropdown trigger="click" @command="handleExportCommand">
          <el-button plain>导出</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="doc">导出 .doc</el-dropdown-item>
              <el-dropdown-item command="pdf">导出 .pdf</el-dropdown-item>
              <el-dropdown-item command="markdown">导出 .md</el-dropdown-item>
              <el-dropdown-item command="jpg">导出 .jpg</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button v-if="isPreview" type="primary" @click="openEditMode">进入编辑</el-button>
        <template v-else>
          <el-button v-if="!isNewDraft" :loading="saving" @click="save()">保存草稿</el-button>
          <el-button plain @click="openPreviewMode">浏览</el-button>
          <el-button type="primary" :loading="publishing" @click="publish">发布</el-button>
        </template>
      </template>
    </AppPageHeader>

    <ProjectSubnav v-if="projectId" :project-id="projectId" />

    <div class="document-editor-grid" :class="{ 'is-toc-hidden': !tocVisible }">
      <section class="section-stack document-editor-main">
        <template v-if="isPreview">
          <AppSectionCard title="文档信息" :description="isNewDraft ? '当前为本地草稿预览，发布后才会真正创建文档。' : '浏览态只展示已保存内容，不复用可编辑编辑器外壳。'">
            <div class="preview-meta">
              <AppStatusTag :label="documentStatusLabel(detailStatus)" :tone="documentStatusTone(detailStatus)" />
              <span>创建于 {{ formatDateTime(createdAt) }}</span>
              <span>更新于 {{ formatDateTime(updatedAt) }}</span>
            </div>
            <div v-if="form.summary" class="document-summary">{{ form.summary }}</div>
          </AppSectionCard>

          <AppSectionCard title="阅读视图" description="用于浏览正文内容、标题层级和表格结构。">
            <article ref="readerContentRef" class="document-reader" v-html="contentHtml" />
          </AppSectionCard>
        </template>

        <template v-else>
          <AppSectionCard title="基础信息">
            <el-form :model="form" label-position="top">
              <el-form-item label="标题">
                <el-input v-model="form.title" />
              </el-form-item>
              <el-form-item label="摘要">
                <el-input v-model="form.summary" type="textarea" :rows="3" />
              </el-form-item>
            </el-form>
          </AppSectionCard>

          <AppSectionCard title="正文编辑器">
            <OfficeEditor
              ref="officeEditorRef"
              v-model="contentHtml"
              :readonly="false"
              :height="760"
              toolbar-locale="zh"
              placeholder="在这里输入文档正文内容"
            />
          </AppSectionCard>
        </template>
      </section>

      <aside v-if="tocVisible" class="document-toc app-card">
        <div class="app-card__body section-stack">
          <div class="document-toc__header">
            <div>
              <div class="app-eyebrow">目录</div>
              <div class="document-toc__title">文档目录</div>
            </div>
            <el-button text @click="toggleToc">收起</el-button>
          </div>

          <div v-if="tocItems.length" class="document-toc__list">
            <button
              v-for="item in tocItems"
              :key="`${item.index}-${item.text}`"
              type="button"
              class="document-toc__item"
              :style="{ paddingLeft: `${16 + (item.level - 1) * 14}px` }"
              @click="focusHeading(item.index)"
            >
              <span>{{ item.text }}</span>
              <small>{{ item.tagName.toUpperCase() }}</small>
            </button>
          </div>
          <AppEmptyState v-else title="还没有可导航的标题" description="补充 H1-H4 标题后，这里会自动生成目录。" />
        </div>
      </aside>
    </div>

    <div class="document-export-surface" aria-hidden="true">
      <div ref="exportSurfaceRef" class="document-export-surface__canvas" v-html="exportHtml" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getDocument, publishDocument, publishNewDocument, updateDocument } from '@/api/document'
import { trackWorkspaceActivity } from '@/api/workspace'
import AppEmptyState from '@/components/common/AppEmptyState.vue'
import AppPageHeader from '@/components/common/AppPageHeader.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'
import AppStatusTag from '@/components/common/AppStatusTag.vue'
import ProjectSubnav from '@/components/common/ProjectSubnav.vue'
import OfficeEditor from '@/components/editor/OfficeEditor.vue'
import {
  buildTocFromHtml,
  exportDocumentAsDoc,
  exportDocumentAsMarkdown,
  exportElementAsJpg,
  exportElementAsPdf,
  renderDocumentHtml,
} from '@/utils/documentExport'
import { documentStatusLabel, documentStatusTone, formatDateTime, isKnowledgeFailed, resolveErrorMessage } from '@/utils/formatters'
import type { DocumentDetailView } from '@/types/models'

interface LocalDraftDocument {
  projectId: number
  title: string
  summary: string
  contentHtml: string
  createdAt: string
  updatedAt: string
}

const props = defineProps<{ id?: string }>()
const route = useRoute()
const router = useRouter()
const documentId = computed(() => (props.id ? Number(props.id) : undefined))
const isNewDraft = computed(() => documentId.value === undefined)
const saving = ref(false)
const publishing = ref(false)
const tocVisible = ref(true)
const contentHtml = ref('<p></p>')
const exportSurfaceRef = ref<HTMLDivElement | null>(null)
const readerContentRef = ref<HTMLElement | null>(null)
const officeEditorRef = ref<{ focusHeading: (index: number) => void } | null>(null)
const form = reactive({ title: '', summary: '' })
const projectId = ref<number>()
const detailStatus = ref('DRAFT')
const createdAt = ref('')
const updatedAt = ref('')

const isPreview = computed(() => route.query.mode === 'preview')
const tocItems = computed(() => buildTocFromHtml(contentHtml.value))
const exportFileName = computed(() => form.title.trim() || '未命名文档')
const exportHtml = computed(() => renderDocumentHtml(form.title.trim() || '未命名文档', form.summary, contentHtml.value))
const documentBackTarget = computed(() => (projectId.value ? `/projects/${projectId.value}/documents` : '/documents'))
const localDraftKey = computed(() => (projectId.value ? `document-draft:${projectId.value}` : ''))

const showDocumentSyncFeedback = (detail: Pick<DocumentDetailView, 'parseStatus' | 'indexStatus' | 'parseErrorMessage'>, successText: string) => {
  if (isKnowledgeFailed(detail.parseStatus, detail.indexStatus)) {
    ElMessage.warning(detail.parseErrorMessage || `${successText}，但知识索引同步失败，可稍后重试`)
    return
  }
  ElMessage.success(successText)
}

const htmlToText = (html: string) => {
  const parser = new DOMParser()
  const doc = parser.parseFromString(html, 'text/html')
  return doc.body.textContent?.trim() || ''
}

const buildEditorRoute = (preview = false) =>
  isNewDraft.value
    ? {
        path: '/documents/new/edit',
        query: {
          projectId: projectId.value,
          ...(preview ? { mode: 'preview' } : {}),
        },
      }
    : {
        path: `/documents/${documentId.value}/edit`,
        query: preview ? { mode: 'preview' } : {},
      }

const parseProjectId = () => {
  const parsed = Number(route.query.projectId)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
}

const loadLocalDraft = (): LocalDraftDocument | undefined => {
  if (!localDraftKey.value) {
    return undefined
  }
  const raw = sessionStorage.getItem(localDraftKey.value)
  if (!raw) {
    return undefined
  }
  try {
    const parsed = JSON.parse(raw) as LocalDraftDocument
    if (parsed.projectId !== projectId.value) {
      return undefined
    }
    return parsed
  } catch {
    sessionStorage.removeItem(localDraftKey.value)
    return undefined
  }
}

const persistLocalDraft = () => {
  if (!isNewDraft.value || !projectId.value || !localDraftKey.value) {
    return
  }
  const existing = loadLocalDraft()
  const draft: LocalDraftDocument = {
    projectId: projectId.value,
    title: form.title.trim() || '未命名文档',
    summary: form.summary,
    contentHtml: contentHtml.value || '<p></p>',
    createdAt: existing?.createdAt || createdAt.value || new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  }
  sessionStorage.setItem(localDraftKey.value, JSON.stringify(draft))
  createdAt.value = draft.createdAt
  updatedAt.value = draft.updatedAt
}

const clearLocalDraft = () => {
  if (localDraftKey.value) {
    sessionStorage.removeItem(localDraftKey.value)
  }
}

const resetState = () => {
  form.title = ''
  form.summary = ''
  contentHtml.value = '<p></p>'
  projectId.value = undefined
  detailStatus.value = 'DRAFT'
  createdAt.value = ''
  updatedAt.value = ''
}

const initializeNewDraft = () => {
  const nextProjectId = parseProjectId()
  if (!nextProjectId) {
    ElMessage.warning('请先选择项目，再创建文档。')
    void router.replace('/documents')
    return
  }

  projectId.value = nextProjectId
  detailStatus.value = 'DRAFT'

  const localDraft = loadLocalDraft()
  const now = new Date().toISOString()
  if (localDraft) {
    form.title = localDraft.title || '未命名文档'
    form.summary = localDraft.summary || ''
    contentHtml.value = localDraft.contentHtml || '<p></p>'
    createdAt.value = localDraft.createdAt || now
    updatedAt.value = localDraft.updatedAt || now
    return
  }

  form.title = typeof route.query.title === 'string' && route.query.title.trim() ? route.query.title.trim() : '未命名文档'
  form.summary = typeof route.query.summary === 'string' ? route.query.summary : ''
  contentHtml.value = '<p></p>'
  createdAt.value = now
  updatedAt.value = now
  persistLocalDraft()
}

const initializeExistingDocument = async () => {
  if (!documentId.value) {
    return
  }
  const detail = await getDocument(documentId.value)
  projectId.value = detail.projectId
  form.title = detail.title
  form.summary = detail.summary || ''
  contentHtml.value = detail.contentHtmlSnapshot || '<p></p>'
  detailStatus.value = detail.docStatus
  createdAt.value = detail.createdAt
  updatedAt.value = detail.updatedAt
  try {
    await trackWorkspaceActivity({
      assetType: 'DOCUMENT',
      assetId: documentId.value,
      actionCode: isPreview.value ? 'DOCUMENT_VIEW' : 'DOCUMENT_EDIT_OPEN',
    })
  } catch {
    // best effort only
  }
}

const initializeView = async () => {
  resetState()
  if (isNewDraft.value) {
    initializeNewDraft()
    return
  }
  await initializeExistingDocument()
}

const save = async (options: { silent?: boolean } = {}) => {
  if (isNewDraft.value) {
    persistLocalDraft()
    if (!options.silent) {
      ElMessage.success('草稿已保存在本地')
    }
    return true
  }

  if (!documentId.value) {
    return false
  }

  saving.value = true
  try {
    form.title = form.title.trim() || '未命名文档'
    const detail = await updateDocument(documentId.value, {
      title: form.title,
      summary: form.summary,
      contentJson: JSON.stringify({ type: 'TINYMCE', html: contentHtml.value }),
      contentHtmlSnapshot: contentHtml.value,
      plainText: htmlToText(contentHtml.value),
    })
    detailStatus.value = detail.docStatus
    updatedAt.value = detail.updatedAt
    if (!options.silent) {
      showDocumentSyncFeedback(detail, '草稿已保存')
    }
    return true
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '保存失败，请稍后重试'))
    return false
  } finally {
    saving.value = false
  }
}

const publish = async () => {
  publishing.value = true
  try {
    form.title = form.title.trim() || '未命名文档'

    if (isNewDraft.value) {
      if (!projectId.value) {
        ElMessage.warning('请先选择项目，再发布文档。')
        return
      }
      const detail = await publishNewDocument({
        projectId: projectId.value,
        title: form.title,
        summary: form.summary,
        contentJson: JSON.stringify({ type: 'TINYMCE', html: contentHtml.value }),
        contentHtmlSnapshot: contentHtml.value,
        plainText: htmlToText(contentHtml.value),
      })
      clearLocalDraft()
      showDocumentSyncFeedback(detail, '文档已发布')
      await router.replace(`/documents/${detail.id}/edit`)
      return
    }

    const saved = await save({ silent: true })
    if (!saved || !documentId.value) {
      return
    }
    const detail = await publishDocument(documentId.value)
    detailStatus.value = detail.docStatus
    updatedAt.value = detail.updatedAt
    showDocumentSyncFeedback(detail, '文档已发布')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '发布失败，请稍后重试'))
  } finally {
    publishing.value = false
  }
}

const openPreviewMode = () => router.push(buildEditorRoute(true))
const openEditMode = () => router.push(buildEditorRoute(false))
const toggleToc = () => {
  tocVisible.value = !tocVisible.value
}

const focusHeading = async (headingIndex: number) => {
  if (!isPreview.value) {
    officeEditorRef.value?.focusHeading(headingIndex)
    return
  }
  await nextTick()
  const headings = readerContentRef.value?.querySelectorAll('h1, h2, h3, h4')
  headings?.[headingIndex]?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

const handleExportCommand = async (command: string | number | object) => {
  const exportType = String(command)
  await nextTick()

  if (!exportSurfaceRef.value) {
    ElMessage.error('导出视图尚未准备完成')
    return
  }

  if (exportType === 'doc') {
    await exportDocumentAsDoc({
      fileName: exportFileName.value,
      title: form.title.trim() || '未命名文档',
      summary: form.summary,
      bodyHtml: contentHtml.value,
    })
    ElMessage.success('已导出 .doc 文件')
    return
  }

  if (exportType === 'markdown') {
    await exportDocumentAsMarkdown({
      fileName: exportFileName.value,
      title: form.title.trim() || '未命名文档',
      summary: form.summary,
      bodyHtml: contentHtml.value,
    })
    ElMessage.success('已导出 Markdown 文件')
    return
  }

  if (exportType === 'pdf') {
    await exportElementAsPdf(exportSurfaceRef.value, exportFileName.value)
    ElMessage.success('已导出 .pdf 文件')
    return
  }

  if (exportType === 'jpg') {
    await exportElementAsJpg(exportSurfaceRef.value, exportFileName.value)
    ElMessage.success('已导出 .jpg 文件')
  }
}

watch(
  [isNewDraft, projectId, () => form.title, () => form.summary, contentHtml],
  () => {
    if (isNewDraft.value && projectId.value) {
      persistLocalDraft()
    }
  },
)

onMounted(async () => {
  await initializeView()
})

watch(
  [() => props.id, () => route.query.projectId],
  async () => {
    await initializeView()
  },
)
</script>

<style scoped>
.document-editor-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 18px;
  align-items: start;
}

.document-editor-grid.is-toc-hidden {
  grid-template-columns: minmax(0, 1fr);
}

.document-editor-main {
  min-width: 0;
}

.preview-meta {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
  color: var(--muted);
  font-size: 13px;
}

.document-summary {
  margin-top: 14px;
  padding: 16px 18px;
  border-radius: var(--radius-md);
  border: 1px solid var(--line);
  background: var(--surface-strong);
  line-height: 1.8;
}

.document-reader {
  min-height: 420px;
  color: var(--text);
  line-height: 1.85;
}

.document-reader :deep(h1),
.document-reader :deep(h2),
.document-reader :deep(h3),
.document-reader :deep(h4) {
  margin-top: 1.5em;
  margin-bottom: 0.6em;
  line-height: 1.3;
  letter-spacing: -0.03em;
}

.document-reader :deep(p) {
  margin: 0 0 1em;
}

.document-reader :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 1.2em 0;
}

.document-reader :deep(td),
.document-reader :deep(th) {
  border: 1px solid var(--line);
  padding: 10px 12px;
  vertical-align: top;
}

.document-toc {
  position: sticky;
  top: 84px;
}

.document-toc__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.document-toc__title {
  margin-top: 8px;
  font-size: 18px;
  font-weight: 700;
}

.document-toc__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.document-toc__item {
  width: 100%;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: var(--surface-strong);
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.document-toc__item small {
  color: var(--muted);
  letter-spacing: 0.08em;
}

.document-export-surface {
  position: fixed;
  inset: auto auto 0 -200vw;
  pointer-events: none;
  opacity: 0;
}

.document-export-surface__canvas {
  width: 794px;
  background: #ffffff;
}

@media (max-width: 1180px) {
  .document-editor-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .document-toc {
    position: static;
  }
}
</style>
