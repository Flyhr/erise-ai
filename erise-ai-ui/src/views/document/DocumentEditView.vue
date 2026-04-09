<template>
  <div class="page-shell">
    <AppPageHeader :title="form.title || (isPreview ? '文档浏览' : '文档阅读')" show-back back-label="返回文档列表"
      :back-to="documentBackTarget">
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
          <el-button :loading="saving" @click="save">保存草稿</el-button>
          <el-button plain @click="openPreviewMode">浏览</el-button>
          <el-button type="primary" :loading="publishing" @click="publish">发布</el-button>
        </template>
      </template>
    </AppPageHeader>

    <ProjectSubnav v-if="projectId" :project-id="projectId" />

    <div class="document-editor-grid" :class="{ 'is-toc-hidden': !tocVisible }">
      <section class="section-stack document-editor-main">
        <template v-if="isPreview">
          <AppSectionCard title="文档信息" description="浏览态只展示已保存内容，不复用可编辑编辑器外壳。">
            <div class="preview-meta">
              <AppStatusTag :label="documentStatusLabel(detailStatus)" :tone="documentStatusTone(detailStatus)" />
              <span>创建于 {{ formatDateTime(createdAt) }}</span>
              <span>更新于 {{ formatDateTime(updatedAt) }}</span>
            </div>
            <div v-if="form.summary" class="document-summary">{{ form.summary }}</div>
          </AppSectionCard>

          <AppSectionCard title="阅读视图" description="用于浏览导入后的正文内容、标题层级和表格结构。">
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
            <OfficeEditor ref="officeEditorRef" v-model="contentHtml" :readonly="false" :height="760"
              toolbar-locale="zh" placeholder="在这里输入文档正文内容" />
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
            <button v-for="item in tocItems" :key="`${item.index}-${item.text}`" type="button"
              class="document-toc__item" :style="{ paddingLeft: `${16 + (item.level - 1) * 14}px` }"
              @click="focusHeading(item.index)">
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
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getDocument, publishDocument, updateDocument } from '@/api/document'
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
import { documentStatusLabel, documentStatusTone, formatDateTime, resolveErrorMessage } from '@/utils/formatters'

const props = defineProps<{ id: string }>()
const route = useRoute()
const router = useRouter()
const documentId = Number(props.id)
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
// const pageSubtitle = computed(() =>
//   isPreview.value ? '当前为只读浏览态，适合阅读、校对和导出。' : '编辑态会同步保存正文快照、纯文本和文档状态。',
// )
const documentBackTarget = computed(() => (projectId.value ? `/projects/${projectId.value}/documents` : '/documents'))

const htmlToText = (html: string) => {
  const parser = new DOMParser()
  const doc = parser.parseFromString(html, 'text/html')
  return doc.body.textContent?.trim() || ''
}

const save = async () => {
  saving.value = true
  try {
    form.title = form.title.trim() || '未命名文档'
    const detail = await updateDocument(documentId, {
      title: form.title,
      summary: form.summary,
      contentJson: JSON.stringify({ type: 'TINYMCE', html: contentHtml.value }),
      contentHtmlSnapshot: contentHtml.value,
      plainText: htmlToText(contentHtml.value),
    })
    detailStatus.value = detail.docStatus
    updatedAt.value = detail.updatedAt
    ElMessage.success('草稿已保存')
    return true
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '保存失败，请稍后重试'))
    return false
  } finally {
    saving.value = false
  }
}

const publish = async () => {
  const saved = await save()
  if (!saved) {
    return
  }
  publishing.value = true
  try {
    const detail = await publishDocument(documentId)
    detailStatus.value = detail.docStatus
    updatedAt.value = detail.updatedAt
    ElMessage.success('文档已发布')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '发布失败，请稍后重试'))
  } finally {
    publishing.value = false
  }
}

const openPreviewMode = () => router.push({ path: `/documents/${documentId}/edit`, query: { mode: 'preview' } })
const openEditMode = () => router.push(`/documents/${documentId}/edit`)
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

onMounted(async () => {
  const detail = await getDocument(documentId)
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
      assetId: documentId,
      actionCode: isPreview.value ? 'DOCUMENT_VIEW' : 'DOCUMENT_EDIT_OPEN',
    })
  } catch {
    // best effort only
  }
})
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
