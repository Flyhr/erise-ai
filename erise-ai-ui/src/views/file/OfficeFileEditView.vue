<template>
  <div class="section-stack">
    <div class="page-header">
      <div>
        <h1>{{ file?.fileName || 'Office 文件编辑' }}</h1>
        <div class="page-subtitle">支持 doc/docx 在线编辑、Office 风格工具栏、本地图片插入和在线预览。</div>
      </div>
      <div class="table-actions">
        <el-button plain @click="goBack">返回文件详情</el-button>
        <el-button @click="openPreview">在线预览</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存内容</el-button>
      </div>
    </div>

    <el-card v-if="file" class="glass-card" shadow="never">
      <div class="meta-row">
        <span>{{ file.fileExt.toUpperCase() }}</span>
        <span>{{ file.updatedAt }}</span>
      </div>
    </el-card>

    <OfficeEditor
      v-model="contentHtml"
      :height="760"
      toolbar-locale="zh"
      placeholder="开始编辑 Office 内容"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { getEditableOfficeFile, previewOfficeFile, updateEditableOfficeFile } from '@/api/file'
import type { EditableOfficeFileView } from '@/types/models'
import OfficeEditor from '@/components/editor/OfficeEditor.vue'

const props = defineProps<{ id: string }>()
const router = useRouter()
const file = ref<EditableOfficeFileView>()
const contentHtml = ref('<p></p>')
const saving = ref(false)
const fileId = Number(props.id)

const htmlToText = (html: string) => {
  const parser = new DOMParser()
  const doc = parser.parseFromString(html, 'text/html')
  return doc.body.textContent?.trim() || ''
}

const load = async () => {
  file.value = await getEditableOfficeFile(fileId)
  contentHtml.value = file.value.contentHtmlSnapshot || '<p></p>'
}

const save = async () => {
  saving.value = true
  try {
    file.value = await updateEditableOfficeFile(fileId, {
      contentHtmlSnapshot: contentHtml.value,
      plainText: htmlToText(contentHtml.value),
    })
    ElMessage.success('Office 文件内容已保存')
  } finally {
    saving.value = false
  }
}

const openPreview = async () => {
  await previewOfficeFile(fileId)
}

const goBack = () => router.push(`/files/${fileId}`)

onMounted(load)
</script>
