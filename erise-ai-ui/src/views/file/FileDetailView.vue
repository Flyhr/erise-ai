<template>
  <div class="section-stack">
    <div class="page-header">
      <div>
        <h1>{{ file?.fileName }}</h1>
        <div class="page-subtitle">查看元数据、解析状态，并在后端权限校验后预览或下载。</div>
      </div>
    </div>

    <el-card v-if="file" class="glass-card" shadow="never">
      <div class="meta-row">
        <span>项目 #{{ file.projectId }}</span>
        <span>{{ file.mimeType }}</span>
        <span>上传：{{ file.uploadStatus }}</span>
        <span>解析：{{ file.parseStatus }}</span>
      </div>
      <div style="margin-top: 20px; display: flex; gap: 12px">
        <el-button type="primary" @click="openBlob(true)">预览</el-button>
        <el-button plain @click="openBlob(false)">下载</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getFile } from '@/api/file'
import type { FileView } from '@/types/models'

const props = defineProps<{ id: string }>()
const file = ref<FileView>()
const apiBase = import.meta.env.VITE_API_BASE_URL

onMounted(async () => {
  file.value = await getFile(Number(props.id))
})

const openBlob = async (preview: boolean) => {
  if (!file.value) return
  const token = localStorage.getItem('erise-access-token')
  const response = await fetch(`${apiBase}/v1/files/${file.value.id}/${preview ? 'preview' : 'download'}`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  })
  if (!response.ok) {
    ElMessage.error('文件获取失败')
    return
  }
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  if (preview) {
    window.open(url, '_blank')
  } else {
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = file.value.fileName
    anchor.click()
  }
  setTimeout(() => URL.revokeObjectURL(url), 5000)
}
</script>
