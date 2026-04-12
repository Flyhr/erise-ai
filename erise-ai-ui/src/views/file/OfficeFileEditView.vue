<template>
  <div v-if="file" class="page-shell file-edit-page">
    <AppPageHeader
      :title="headerTitle"
      eyebrow="文件编辑"
      :subtitle="editorSubtitle"
      show-back
      back-label="返回项目文件"
      :back-to="backTarget"
    >
      <template #actions>
        <el-button plain @click="openPreview">预览</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </AppPageHeader>

    <AppSectionCard title="编辑信息">
      <div class="editor-meta-card">
        <div class="editor-meta-card__field">
          <label for="file-edit-title">标题</label>
          <el-input id="file-edit-title" v-model="metaForm.title" maxlength="120" placeholder="请输入正文标题" />
        </div>
        <div class="editor-meta-card__divider" />
        <div class="editor-meta-card__field">
          <label for="file-edit-summary">简介</label>
          <el-input id="file-edit-summary" v-model="metaForm.summary" type="textarea" :rows="3" resize="none"
            maxlength="240" show-word-limit placeholder="补充内容简介，方便预览和检索" />
        </div>
      </div>
    </AppSectionCard>

    <AppSectionCard :title="editorCardTitle">
      <el-input v-if="isTxtFile" v-model="plainTextContent" type="textarea" :rows="26" resize="vertical"
        placeholder="请输入正文内容" />
      <OfficeEditor v-else v-model="bodyHtml" :height="760" toolbar-locale="zh" placeholder="请输入文件正文内容" />
    </AppSectionCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { getEditableOfficeFile, previewOfficeFile, updateEditableOfficeFile } from '@/api/file'
import AppPageHeader from '@/components/common/AppPageHeader.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'
import OfficeEditor from '@/components/editor/OfficeEditor.vue'
import type { EditableOfficeFileView } from '@/types/models'
import { formatDateTime, plainTextToHtml, resolveErrorMessage } from '@/utils/formatters'

const props = defineProps<{ id: string }>()

const file = ref<EditableOfficeFileView>()
const route = useRoute()
const bodyHtml = ref('<p></p>')
const plainTextContent = ref('')
const saving = ref(false)
const metaForm = reactive({
  title: '',
  summary: '',
})

const fileId = Number(props.id)

const isTxtFile = computed(() => (file.value?.fileExt || '').toLowerCase() === 'txt')
const isAdminContext = computed(() => route.path.startsWith('/admin/'))
const headerTitle = computed(() => metaForm.title.trim() || stripExtension(file.value?.fileName || '文件编辑'))
const editorSubtitle = computed(() => '支持 Ctrl+S 快捷保存，返回后会定位到项目详情中的文件列表。')
const editorCardTitle = computed(() => (isTxtFile.value ? '正文文本编辑器' : '在线正文编辑器'))
const backTarget = computed(() =>
  isAdminContext.value && file.value
    ? `/admin/files/${file.value.id}`
    : file.value
    ? {
      path: `/projects/${file.value.projectId}`,
      query: { tab: 'files' },
    }
    : '/files',
)

const stripExtension = (fileName: string) => {
  const extensionIndex = fileName.lastIndexOf('.')
  return extensionIndex > 0 ? fileName.slice(0, extensionIndex) : fileName
}

const escapeHtml = (value: string) =>
  value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')

const normalizeStoredHtml = (html: string) => {
  const parser = new DOMParser()
  const document = parser.parseFromString(html || '<p></p>', 'text/html')
  const article = document.body.querySelector('main > article')
  if (article) {
    article.querySelector('.docx-meta')?.remove()
    article.querySelector('.eyebrow')?.remove()
    return article.innerHTML.trim() || '<p></p>'
  }
  return document.body.innerHTML.trim() || '<p></p>'
}

const extractEditorState = (html: string, fallbackTitle: string) => {
  const container = document.createElement('div')
  container.innerHTML = normalizeStoredHtml(html)

  const metaRoot = container.querySelector('[data-erise-office-meta="true"]')
  if (metaRoot instanceof HTMLElement) {
    const title = metaRoot.querySelector('[data-erise-office-title="true"]')?.textContent?.trim() || fallbackTitle
    const summary = metaRoot.querySelector('[data-erise-office-summary="true"]')?.textContent?.trim() || ''
    metaRoot.remove()
    return {
      title,
      summary,
      bodyHtml: container.innerHTML.trim() || '<p></p>',
    }
  }

  const meaningfulChildren = Array.from(container.children).filter((element) => element.textContent?.trim())
  let title = fallbackTitle
  let summary = ''

  const firstElement = meaningfulChildren[0]
  if (firstElement?.matches('h1,h2,h3')) {
    title = firstElement.textContent?.trim() || fallbackTitle
    firstElement.remove()

    const secondElement = Array.from(container.children).find((element) => element.textContent?.trim())
    if (secondElement?.matches('p')) {
      summary = secondElement.textContent?.trim() || ''
      secondElement.remove()
    }
  }

  return {
    title,
    summary,
    bodyHtml: container.innerHTML.trim() || '<p></p>',
  }
}

const htmlToText = (html: string) => {
  const parser = new DOMParser()
  const document = parser.parseFromString(html, 'text/html')
  return document.body.textContent?.trim() || ''
}

const htmlFragmentToText = (html: string) => {
  const container = document.createElement('div')
  container.innerHTML = html || '<p></p>'
  return container.innerText?.trim() || container.textContent?.trim() || ''
}

const composeMetaHtml = () => {
  const title = escapeHtml(metaForm.title.trim() || stripExtension(file.value?.fileName || '未命名正文'))
  const summary = metaForm.summary.trim()
  return `
    <div data-erise-office-meta="true" style="padding:0 0 24px;margin:0 0 24px;border-bottom:1px dashed #d0d7de;">
      <h1 data-erise-office-title="true" style="margin:0;font-size:28px;line-height:1.25;color:#181c20;font-weight:700;">${title}</h1>
      ${summary
      ? `<p data-erise-office-summary="true" style="margin:12px 0 0;color:#5f6775;font-size:15px;line-height:1.8;">${escapeHtml(summary).replace(/\n/g, '<br />')}</p>`
      : ''
    }
    </div>
  `.trim()
}

const buildContentHtmlSnapshot = () => `${composeMetaHtml()}${isTxtFile.value ? plainTextToHtml(plainTextContent.value) : bodyHtml.value || '<p></p>'}`

const buildPlainTextPayload = () =>
  [
    metaForm.title.trim() || stripExtension(file.value?.fileName || '未命名正文'),
    metaForm.summary.trim(),
    isTxtFile.value ? plainTextContent.value.trim() : htmlToText(bodyHtml.value),
  ]
    .filter(Boolean)
    .join('\n\n')

const applyEditableState = (detail: EditableOfficeFileView) => {
  const parsed = extractEditorState(detail.contentHtmlSnapshot || '<p></p>', stripExtension(detail.fileName || '未命名正文'))
  metaForm.title = parsed.title
  metaForm.summary = parsed.summary
  bodyHtml.value = parsed.bodyHtml
  plainTextContent.value = isTxtFile.value ? htmlFragmentToText(parsed.bodyHtml) || detail.plainText || '' : ''
}

const load = async () => {
  const detail = await getEditableOfficeFile(fileId)
  file.value = detail
  applyEditableState(detail)
}

const save = async () => {
  if (!file.value) {
    return
  }

  saving.value = true
  try {
    const detail = await updateEditableOfficeFile(fileId, {
      contentHtmlSnapshot: buildContentHtmlSnapshot(),
      plainText: buildPlainTextPayload(),
    })
    file.value = detail
    applyEditableState(detail)
    ElMessage.success('文件内容已保存')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '文件保存失败，请稍后重试'))
  } finally {
    saving.value = false
  }
}

const openPreview = async () => {
  try {
    await previewOfficeFile(fileId)
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '文件预览失败，请稍后重试'))
  }
}

const handleKeydown = (event: KeyboardEvent) => {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
    event.preventDefault()
    if (!saving.value) {
      void save()
    }
  }
}

onMounted(async () => {
  window.addEventListener('keydown', handleKeydown)
  await load()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<style scoped>
.file-edit-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.meta-row span {
  display: inline-flex;
  align-items: center;
  min-height: 34px;
  padding: 0 12px;
  border-radius: 999px;
  background: var(--surface-strong);
  color: var(--muted);
  font-size: 13px;
  font-weight: 600;
}

.editor-meta-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: stretch;
  gap: 18px;
  padding: 20px 22px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: var(--surface-strong);
}

.editor-meta-card__field {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.editor-meta-card__field label {
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
}

.editor-meta-card__divider {
  width: 1px;
  align-self: stretch;
  border-left: 1px dashed var(--line);
}

.editor-meta-card :deep(.el-input__wrapper),
.editor-meta-card :deep(.el-textarea__inner) {
  background: #fff;
}

@media (max-width: 900px) {
  .editor-meta-card {
    grid-template-columns: 1fr;
  }

  .editor-meta-card__divider {
    width: 100%;
    height: 1px;
    border-left: 0;
    border-top: 1px dashed var(--line);
  }
}
</style>
