<template>
  <div class="document-editor-page section-stack">
    <div class="page-header">
      <div>
        <h1>{{ form.title || (isPreview ? '文档预览' : '文档编辑') }}</h1>
        <div class="page-subtitle">
          {{
            isPreview
              ? '当前为只读预览模式。'
              : '支持 Office 风格编辑、右侧目录导航，以及 doc/pdf/Markdown/jpg 导出。'
          }}
        </div>
      </div>
      <div class="table-actions">
        <el-button plain @click="toggleToc">{{ tocVisible ? '隐藏目录' : '显示目录' }}</el-button>
        <el-dropdown trigger="click" @command="handleExportCommand">
          <el-button plain>导出文档</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="doc">导出为 .doc</el-dropdown-item>
              <el-dropdown-item command="pdf">导出为 .pdf</el-dropdown-item>
              <el-dropdown-item command="markdown">导出为 .md</el-dropdown-item>
              <el-dropdown-item command="jpg">导出为 .jpg</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button v-if="isPreview" type="primary" @click="openEditMode">进入编辑</el-button>
        <template v-else>
          <el-button :loading="saving" @click="save">保存草稿</el-button>
          <el-button plain @click="openPreviewMode">预览</el-button>
          <el-button type="primary" :loading="publishing" @click="publish">发布版本</el-button>
        </template>
      </div>
    </div>

    <div class="document-editor-grid" :class="{ 'is-toc-hidden': !tocVisible }">
      <section class="section-stack document-editor-main">
        <el-card class="glass-card" shadow="never">
          <el-form :model="form" label-position="top">
            <el-form-item label="标题">
              <el-input v-model="form.title" :disabled="isPreview" />
            </el-form-item>
            <el-form-item label="摘要">
              <el-input v-model="form.summary" type="textarea" :rows="3" :disabled="isPreview" />
            </el-form-item>
          </el-form>
        </el-card>

        <div class="document-editor-panel glass-card">
          <OfficeEditor
            ref="officeEditorRef"
            v-model="contentHtml"
            :readonly="isPreview"
            :height="760"
            toolbar-locale="zh"
            placeholder="开始撰写文档内容"
          />
        </div>
      </section>

      <aside v-if="tocVisible" class="document-toc glass-card">
        <div class="document-toc__header">
          <div>
            <div class="document-toc__eyebrow">TABLE OF CONTENTS</div>
            <div class="document-toc__title">文档目录</div>
          </div>
          <el-button text @click="toggleToc">关闭</el-button>
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
        <div v-else class="empty-box">当前内容还没有标题结构。添加 H1-H4 标题后，这里会自动生成目录。</div>
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
import OfficeEditor from '@/components/editor/OfficeEditor.vue'
import {
  buildTocFromHtml,
  exportDocumentAsDoc,
  exportDocumentAsMarkdown,
  exportElementAsJpg,
  exportElementAsPdf,
  renderDocumentHtml,
} from '@/utils/documentExport'

const props = defineProps<{ id: string }>()
const route = useRoute()
const router = useRouter()
const documentId = Number(props.id)
const saving = ref(false)
const publishing = ref(false)
const tocVisible = ref(true)
const contentHtml = ref('<p></p>')
const exportSurfaceRef = ref<HTMLDivElement | null>(null)
const officeEditorRef = ref<{ focusHeading: (index: number) => void } | null>(null)
const form = reactive({ title: '', summary: '' })

const isPreview = computed(() => route.query.mode === 'preview')
const tocItems = computed(() => buildTocFromHtml(contentHtml.value))
const exportFileName = computed(() => form.title.trim() || 'untitled-document')
const exportHtml = computed(() => renderDocumentHtml(form.title.trim() || 'Untitled document', form.summary, contentHtml.value))

const htmlToText = (html: string) => {
  const parser = new DOMParser()
  const doc = parser.parseFromString(html, 'text/html')
  return doc.body.textContent?.trim() || ''
}

const save = async () => {
  saving.value = true
  try {
    form.title = form.title.trim() || '未命名文档'
    await updateDocument(documentId, {
      title: form.title,
      summary: form.summary,
      contentJson: JSON.stringify({ type: 'TINYMCE', html: contentHtml.value }),
      contentHtmlSnapshot: contentHtml.value,
      plainText: htmlToText(contentHtml.value),
    })
    ElMessage.success('草稿已保存')
  } finally {
    saving.value = false
  }
}

const publish = async () => {
  await save()
  publishing.value = true
  try {
    await publishDocument(documentId)
    ElMessage.success('文档已发布')
  } finally {
    publishing.value = false
  }
}

const openPreviewMode = () => router.push({ path: `/documents/${documentId}/edit`, query: { mode: 'preview' } })
const openEditMode = () => router.push(`/documents/${documentId}/edit`)
const toggleToc = () => {
  tocVisible.value = !tocVisible.value
}

const focusHeading = (headingIndex: number) => {
  officeEditorRef.value?.focusHeading(headingIndex)
}

const handleExportCommand = async (command: string | number | object) => {
  const exportType = String(command)
  await nextTick()

  if (!exportSurfaceRef.value) {
    ElMessage.error('导出区域尚未准备完成')
    return
  }

  if (exportType === 'doc') {
    await exportDocumentAsDoc({
      fileName: exportFileName.value,
      title: form.title.trim() || 'Untitled document',
      summary: form.summary,
      bodyHtml: contentHtml.value,
    })
    ElMessage.success('已导出 .doc 文件')
    return
  }

  if (exportType === 'markdown') {
    await exportDocumentAsMarkdown({
      fileName: exportFileName.value,
      title: form.title.trim() || 'Untitled document',
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
  form.title = detail.title
  form.summary = detail.summary || ''
  contentHtml.value = detail.contentHtmlSnapshot || '<p></p>'
})
</script>

<style scoped>
.document-editor-page {
  gap: 20px;
}

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

.document-editor-panel {
  padding: 18px;
}

.document-toc {
  position: sticky;
  top: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px;
}

.document-toc__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.document-toc__eyebrow {
  font-size: 11px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--muted);
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
