<template>
  <div class="section-stack">
    <div class="page-header">
      <div>
        <h1>{{ form.title || (isPreview ? '文档预览' : '文档编辑') }}</h1>
        <div class="page-subtitle">
          {{
            isPreview
              ? '当前为只读预览模式，可从这里直接切回编辑。'
              : '支持标题、字号、文字颜色、超链接、本地图片与列表编辑，保存后会同步项目知识。'
          }}
        </div>
      </div>
      <div class="editor-header-actions">
        <el-button v-if="isPreview" type="primary" @click="openEditMode">进入编辑</el-button>
        <template v-else>
          <el-button :loading="saving" @click="save">保存草稿</el-button>
          <el-button plain @click="openPreviewMode">预览</el-button>
          <el-button type="primary" :loading="publishing" @click="publish">发布版本</el-button>
        </template>
      </div>
    </div>

    <el-card class="glass-card" shadow="never">
      <el-form :model="form" label-position="top">
        <el-form-item label="标题">
          <el-input v-model="form.title" :disabled="isPreview" />
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="form.summary" type="textarea" :rows="3" :disabled="isPreview" />
        </el-form-item>
      </el-form>

      <div class="rich-editor">
        <div v-if="!isPreview" class="rich-toolbar">
          <div class="rich-toolbar__group">
            <label class="toolbar-label" for="heading-select">标题</label>
            <select id="heading-select" class="toolbar-select" @change="handleBlockChange">
              <option value="p">正文</option>
              <option value="h1">标题 1</option>
              <option value="h2">标题 2</option>
              <option value="h3">标题 3</option>
              <option value="blockquote">引用</option>
            </select>
          </div>

          <div class="rich-toolbar__group">
            <label class="toolbar-label" for="size-select">字号</label>
            <select id="size-select" class="toolbar-select" v-model="fontSize" @change="applyFontSize">
              <option value="14px">14</option>
              <option value="16px">16</option>
              <option value="18px">18</option>
              <option value="24px">24</option>
              <option value="32px">32</option>
            </select>
          </div>

          <div class="rich-toolbar__group">
            <button type="button" class="toolbar-button" @mousedown.prevent="exec('bold')">加粗</button>
            <button type="button" class="toolbar-button" @mousedown.prevent="exec('italic')">斜体</button>
            <button type="button" class="toolbar-button" @mousedown.prevent="exec('underline')">下划线</button>
          </div>

          <div class="rich-toolbar__group">
            <button type="button" class="toolbar-button" @mousedown.prevent="exec('insertUnorderedList')">无序列表</button>
            <button type="button" class="toolbar-button" @mousedown.prevent="exec('insertOrderedList')">有序列表</button>
          </div>

          <div class="rich-toolbar__group toolbar-color">
            <label class="toolbar-label" for="color-input">颜色</label>
            <input id="color-input" v-model="textColor" type="color" @input="applyTextColor" />
          </div>

          <div class="rich-toolbar__group">
            <button type="button" class="toolbar-button" @mousedown.prevent="insertLink">插入链接</button>
            <button type="button" class="toolbar-button" @mousedown.prevent="pickImage">插入图片</button>
            <button type="button" class="toolbar-button" @mousedown.prevent="clearFormatting">清除格式</button>
          </div>
        </div>

        <input ref="imageInputRef" class="hidden-input" type="file" accept="image/*" @change="handleImageChange" />

        <div
          ref="editorRef"
          class="rich-content"
          :class="{ 'is-preview': isPreview }"
          :contenteditable="!isPreview"
          @input="syncEditorState"
          @blur="syncEditorState"
          @mouseup="saveSelection"
          @keyup="saveSelection"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getDocument, publishDocument, updateDocument } from '@/api/document'

const props = defineProps<{ id: string }>()
const route = useRoute()
const router = useRouter()
const form = reactive({ title: '', summary: '' })
const saving = ref(false)
const publishing = ref(false)
const editorRef = ref<HTMLDivElement>()
const imageInputRef = ref<HTMLInputElement>()
const fontSize = ref('16px')
const textColor = ref('#14532d')
const editorHtml = ref('<p></p>')
const plainText = ref('')
const savedRange = ref<Range | null>(null)
const documentId = Number(props.id)
const isPreview = computed(() => route.query.mode === 'preview')

const syncEditorDom = async () => {
  await nextTick()
  if (editorRef.value) {
    editorRef.value.innerHTML = editorHtml.value || '<p></p>'
  }
}

const syncEditorState = () => {
  if (!editorRef.value) return
  editorHtml.value = editorRef.value.innerHTML || '<p></p>'
  plainText.value = editorRef.value.innerText.trim()
}

const saveSelection = () => {
  if (isPreview.value || !editorRef.value) return
  const selection = window.getSelection()
  if (!selection || selection.rangeCount === 0) return
  const range = selection.getRangeAt(0)
  if (!editorRef.value.contains(range.commonAncestorContainer)) return
  savedRange.value = range.cloneRange()
}

const restoreSelection = () => {
  if (!savedRange.value) return
  const selection = window.getSelection()
  if (!selection) return
  selection.removeAllRanges()
  selection.addRange(savedRange.value)
}

const focusEditor = () => {
  if (!editorRef.value) return false
  editorRef.value.focus()
  restoreSelection()
  return true
}

const exec = (command: string, value?: string) => {
  if (isPreview.value || !focusEditor()) return
  document.execCommand('styleWithCSS', false, 'true')
  document.execCommand(command, false, value)
  syncEditorState()
  saveSelection()
}

const applyBlock = (block: string) => {
  exec('formatBlock', block)
}

const handleBlockChange = (event: Event) => {
  const target = event.target as HTMLSelectElement | null
  if (!target) return
  applyBlock(target.value)
}

const applyInlineStyle = (styleText: string) => {
  if (isPreview.value || !focusEditor()) return
  const selection = window.getSelection()
  if (!selection || selection.rangeCount === 0) {
    ElMessage.warning('请先选中文本')
    return
  }
  const range = selection.getRangeAt(0)
  if (range.collapsed) {
    ElMessage.warning('请先选中文本')
    return
  }
  const span = document.createElement('span')
  span.setAttribute('style', styleText)
  span.appendChild(range.extractContents())
  range.insertNode(span)
  selection.removeAllRanges()
  const newRange = document.createRange()
  newRange.selectNodeContents(span)
  selection.addRange(newRange)
  savedRange.value = newRange.cloneRange()
  syncEditorState()
}

const applyFontSize = () => {
  applyInlineStyle(`font-size: ${fontSize.value};`)
}

const applyTextColor = () => {
  exec('foreColor', textColor.value)
}

const insertLink = () => {
  if (isPreview.value || !focusEditor()) return
  const url = window.prompt('请输入链接地址')?.trim()
  if (!url) return
  const selection = window.getSelection()
  const selectedText = selection?.toString().trim()
  if (selectedText) {
    exec('createLink', url)
    return
  }
  const label = window.prompt('请输入链接文字', url)?.trim() || url
  document.execCommand(
    'insertHTML',
    false,
    `<a href="${escapeAttribute(url)}" target="_blank" rel="noreferrer">${escapeHtml(label)}</a>`,
  )
  syncEditorState()
}

const pickImage = () => {
  if (isPreview.value) return
  imageInputRef.value?.click()
}

const handleImageChange = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => {
    if (!focusEditor()) return
    document.execCommand('insertImage', false, String(reader.result))
    syncEditorState()
  }
  reader.readAsDataURL(file)
  input.value = ''
}

const clearFormatting = () => {
  exec('removeFormat')
  exec('unlink')
}

const save = async () => {
  if (!editorRef.value) return
  syncEditorState()
  saving.value = true
  try {
    form.title = form.title.trim() || '未命名文档'
    await updateDocument(documentId, {
      title: form.title,
      summary: form.summary,
      contentJson: JSON.stringify({ type: 'html', html: editorHtml.value }),
      contentHtmlSnapshot: editorHtml.value,
      plainText: plainText.value,
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

const openPreviewMode = () => {
  router.push({ path: `/documents/${documentId}/edit`, query: { mode: 'preview' } })
}

const openEditMode = () => {
  router.push(`/documents/${documentId}/edit`)
}

const escapeHtml = (value: string) =>
  value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;')

const escapeAttribute = (value: string) => escapeHtml(value).replaceAll("'", '&#39;')

onMounted(async () => {
  const detail = await getDocument(documentId)
  form.title = detail.title
  form.summary = detail.summary || ''
  editorHtml.value = detail.contentHtmlSnapshot || '<p></p>'
  plainText.value = detail.plainText || ''
  await syncEditorDom()
})
</script>

<style scoped>
.editor-header-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.rich-editor {
  border: 1px solid var(--line);
  border-radius: 22px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.92);
}

.rich-toolbar {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: end;
  padding: 14px;
  border-bottom: 1px solid var(--line);
  background: linear-gradient(180deg, rgba(249, 250, 244, 0.9), rgba(255, 255, 255, 0.95));
}

.rich-toolbar__group {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.toolbar-label {
  font-size: 12px;
  color: var(--muted);
}

.toolbar-select,
.toolbar-button,
.toolbar-color input {
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #fff;
}

.toolbar-select {
  min-width: 92px;
  padding: 8px 12px;
  color: var(--text);
}

.toolbar-button {
  padding: 8px 12px;
  cursor: pointer;
  color: var(--text);
}

.toolbar-button:hover,
.toolbar-select:hover {
  border-color: rgba(20, 83, 45, 0.35);
}

.toolbar-color input {
  width: 40px;
  height: 40px;
  padding: 4px;
  cursor: pointer;
}

.rich-content {
  min-height: 420px;
  padding: 20px;
  line-height: 1.8;
  outline: none;
}

.rich-content.is-preview {
  background: rgba(248, 246, 241, 0.6);
}

.rich-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 14px;
  margin: 16px 0;
}

.hidden-input {
  display: none;
}

@media (max-width: 900px) {
  .rich-toolbar {
    align-items: stretch;
  }

  .rich-toolbar__group {
    width: 100%;
  }
}
</style>


