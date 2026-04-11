<template>
  <div class="office-editor-shell">
    <Editor v-model="content" :license-key="licenseKey" :init="editorInit" @onInit="handleInit" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import Editor from '@tinymce/tinymce-vue'
import type { Editor as TinyMceEditor } from 'tinymce'
import 'tinymce/tinymce'
import 'tinymce/models/dom'
import 'tinymce/icons/default'
import 'tinymce/themes/silver'
import 'tinymce/plugins/advlist'
import 'tinymce/plugins/autolink'
import 'tinymce/plugins/autoresize'
import 'tinymce/plugins/charmap'
import 'tinymce/plugins/code'
import 'tinymce/plugins/image'
import 'tinymce/plugins/link'
import 'tinymce/plugins/lists'
import 'tinymce/plugins/preview'
import 'tinymce/plugins/searchreplace'
import 'tinymce/plugins/table'
import 'tinymce/plugins/visualblocks'
import 'tinymce/plugins/wordcount'
import 'tinymce/skins/ui/oxide/skin.min.css'
import contentUiCss from 'tinymce/skins/ui/oxide/content.min.css?inline'
import contentCss from 'tinymce/skins/content/default/content.min.css?inline'

type ToolbarLocale = 'zh' | 'en'

const localeMeta: Record<ToolbarLocale, { placeholder: string; blockFormats: string; fontFamilies: string }> = {
  zh: {
    placeholder: '开始编辑内容',
    blockFormats: '正文=p; 标题 1=h1; 标题 2=h2; 标题 3=h3; 标题 4=h4; 引用=blockquote',
    fontFamilies:
      "Aptos=Aptos,'Microsoft YaHei','PingFang SC',sans-serif; 宋体=SimSun,serif; 黑体=SimHei,sans-serif; 楷体=KaiTi,serif; Times New Roman='Times New Roman',serif; Calibri=Calibri,sans-serif",
  },
  en: {
    placeholder: 'Start writing here',
    blockFormats: 'Paragraph=p; Heading 1=h1; Heading 2=h2; Heading 3=h3; Heading 4=h4; Quote=blockquote',
    fontFamilies:
      "Aptos=Aptos,'Microsoft YaHei','PingFang SC',sans-serif; SimSun=SimSun,serif; SimHei=SimHei,sans-serif; KaiTi=KaiTi,serif; Times New Roman='Times New Roman',serif; Calibri=Calibri,sans-serif",
  },
}

const props = withDefaults(
  defineProps<{
    modelValue: string
    readonly?: boolean
    height?: number
    placeholder?: string
    toolbarLocale?: ToolbarLocale
  }>(),
  {
    readonly: false,
    height: 680,
    placeholder: undefined,
    toolbarLocale: 'zh',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const licenseKey = import.meta.env.VITE_TINYMCE_LICENSE_KEY || 'gpl'
const editorInstance = ref<TinyMceEditor | null>(null)

const content = computed({
  get: () => props.modelValue,
  set: (value: string) => emit('update:modelValue', value),
})

const resolvedLocale = computed(() => localeMeta[props.toolbarLocale])
const resolvedPlaceholder = computed(() => props.placeholder || resolvedLocale.value.placeholder)
const resolvedLanguage = computed(() => (props.toolbarLocale === 'zh' ? 'zh-CN' : 'en'))
const resolvedLanguageUrl = computed(() => (props.toolbarLocale === 'zh' ? '/tinymce/langs/zh-CN.js' : undefined))

const filePickerCallback = (callback: (url: string, meta?: Record<string, string>) => void, _value: string, meta: { filetype?: string }) => {
  if (meta.filetype !== 'image') {
    return
  }

  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/*'
  input.onchange = () => {
    const file = input.files?.[0]
    if (!file) {
      return
    }
    const reader = new FileReader()
    reader.onload = () => {
      callback(String(reader.result), {
        alt: file.name,
        title: file.name,
      })
    }
    reader.readAsDataURL(file)
  }
  input.click()
}

const handleInit = (_event: unknown, editor: TinyMceEditor) => {
  editorInstance.value = editor
}

const focusHeading = (headingIndex: number) => {
  const editor = editorInstance.value
  if (!editor) {
    return
  }
  const headings = Array.from(editor.getBody().querySelectorAll('h1, h2, h3, h4')) as HTMLElement[]
  const target = headings[headingIndex]
  if (!target) {
    return
  }
  editor.focus()
  target.scrollIntoView({ behavior: 'smooth', block: 'center' })
  editor.selection.select(target, true)
}

defineExpose({
  focusHeading,
})

const editorInit = computed(() => ({
  license_key: licenseKey,
  height: props.height,
  menubar: false,
  branding: false,
  promotion: false,
  help_accessibility: false,
  language: resolvedLanguage.value,
  language_url: resolvedLanguageUrl.value,
  readonly: props.readonly,
  skin: false,
  content_css: false,
  content_style: `${contentUiCss}\n${contentCss}\nbody { font-family: Aptos, 'Microsoft YaHei', 'PingFang SC', sans-serif; font-size: 14px; line-height: 1.8; color: #1c242b; margin: 18px; } img { max-width: 100%; height: auto; } table { border-collapse: collapse; width: 100%; } td, th { border: 1px solid #d0d7de; padding: 8px 10px; }`,
  placeholder: resolvedPlaceholder.value,
  plugins: [
    'advlist',
    'autolink',
    'autoresize',
    'charmap',
    'code',
    'image',
    'link',
    'lists',
    'preview',
    'searchreplace',
    'table',
    'visualblocks',
    'wordcount',
  ],
  toolbar:
    'undo redo | blocks fontfamily fontsize | bold italic underline strikethrough forecolor backcolor | alignleft aligncenter alignright alignjustify | bullist numlist outdent indent | link image table | removeformat code preview',
  block_formats: resolvedLocale.value.blockFormats,
  font_family_formats: resolvedLocale.value.fontFamilies,
  fontsize_formats: '8pt 9pt 10pt 11pt 12pt 14pt 16pt 18pt 20pt 24pt 28pt 32pt 36pt 48pt 60pt 72pt',
  toolbar_sticky: true,
  quickbars_selection_toolbar: false,
  paste_data_images: true,
  automatic_uploads: false,
  file_picker_types: 'image',
  file_picker_callback: filePickerCallback,
  table_default_attributes: {
    border: '1',
  },
  table_default_styles: {
    width: '100%',
    borderCollapse: 'collapse',
  },
}))
</script>

<style scoped>
.office-editor-shell :deep(.tox) {
  border: 1px solid var(--line);
  border-radius: 22px;
  overflow: hidden;
  box-shadow: 0 18px 40px var(--shadow-color);
}

.office-editor-shell :deep(.tox-editor-header) {
  background: linear-gradient(180deg, var(--surface-strong), var(--surface-soft));
  border-bottom: 1px solid var(--line);
}

.office-editor-shell :deep(.tox-toolbar__group) {
  gap: 4px;
}
</style>
