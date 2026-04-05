<template>
  <div v-if="file" class="page-shell">
    <AppPageHeader
      :title="file.fileName"
      eyebrow="文件编辑"
      :subtitle="editorSubtitle"
      show-back
      back-label="返回文件详情"
      :back-to="`/files/${fileId}`"
    >
      <template #actions>
        <el-button plain @click="openPreview">预览</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </AppPageHeader>

    <AppSectionCard title="当前文件" description="txt 文件使用纯文本编辑方式，doc 和 docx 则使用 Office 编辑器。">
      <div class="meta-row">
        <span>{{ file.fileExt.toUpperCase() }}</span>
        <span>{{ formatDateTime(file.updatedAt) }}</span>
      </div>
    </AppSectionCard>

    <AppSectionCard :title="isTxtFile ? 'TXT 编辑器' : 'Office 编辑器'" :description="isTxtFile ? '纯文本内容会同步转换为 HTML 快照，方便预览和索引。' : 'doc 和 docx 文件将通过富文本方式进行在线编辑。'">
      <el-input v-if="isTxtFile" v-model="plainTextContent" type="textarea" :rows="26" resize="vertical" placeholder="请输入文本内容" />
      <OfficeEditor v-else v-model="contentHtml" :height="760" toolbar-locale="zh" placeholder="请输入 Office 文档内容" />
    </AppSectionCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { getEditableOfficeFile, previewOfficeFile, updateEditableOfficeFile } from '@/api/file'
import AppPageHeader from '@/components/common/AppPageHeader.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'
import OfficeEditor from '@/components/editor/OfficeEditor.vue'
import type { EditableOfficeFileView } from '@/types/models'
import { formatDateTime, plainTextToHtml, resolveErrorMessage } from '@/utils/formatters'

const props = defineProps<{ id: string }>()
const router = useRouter()
const file = ref<EditableOfficeFileView>()
const contentHtml = ref('<p></p>')
const plainTextContent = ref('')
const saving = ref(false)
const fileId = Number(props.id)

const isTxtFile = computed(() => (file.value?.fileExt || '').toLowerCase() === 'txt')
const editorSubtitle = computed(() =>
  isTxtFile.value ? 'txt 文件会按纯文本方式编辑，并同步保存为可预览的 HTML 快照。' : 'doc 和 docx 文件会进入 Office 编辑流程并保留正文快照。',
)

const htmlToText = (html: string) => {
  const parser = new DOMParser()
  const doc = parser.parseFromString(html, 'text/html')
  return doc.body.textContent?.trim() || ''
}

const load = async () => {
  file.value = await getEditableOfficeFile(fileId)
  contentHtml.value = file.value.contentHtmlSnapshot || '<p></p>'
  plainTextContent.value = file.value.plainText || ''
}

const save = async () => {
  saving.value = true
  try {
    const payload = isTxtFile.value
      ? { contentHtmlSnapshot: plainTextToHtml(plainTextContent.value), plainText: plainTextContent.value }
      : { contentHtmlSnapshot: contentHtml.value, plainText: htmlToText(contentHtml.value) }
    file.value = await updateEditableOfficeFile(fileId, payload)
    contentHtml.value = file.value.contentHtmlSnapshot || '<p></p>'
    plainTextContent.value = file.value.plainText || ''
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

onMounted(load)
</script>
