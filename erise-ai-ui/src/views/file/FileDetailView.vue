<template>
  <div class="section-stack">
    <div class="page-header">
      <div>
        <h1>{{ file?.fileName }}</h1>
        <div class="page-subtitle">查看文件元数据、解析状态，并执行在线预览或下载。</div>
      </div>
    </div>

    <el-card v-if="file" class="glass-card" shadow="never">
      <div class="meta-row">
        <span>{{ projectLabel(file.projectId) }}</span>
        <span>{{ file.mimeType }}</span>
        <span>上传：{{ file.uploadStatus }}</span>
        <span>解析：{{ file.parseStatus }}</span>
        <span>索引：{{ file.indexStatus }}</span>
      </div>

      <div class="file-actions">
        <el-button type="primary" @click="handlePreview">在线预览</el-button>
        <el-button plain @click="handleDownload">下载</el-button>
      </div>

      <div v-if="file.fileExt === 'docx'" class="page-subtitle">
        DOCX 文件会以在线 HTML 预览方式打开，不再直接走下载。
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { downloadFileContent, getFile, previewFileBinary } from '@/api/file'
import type { FileView } from '@/types/models'
import { useProjectDirectory } from '@/composables/useProjectDirectory'

const props = defineProps<{ id: string }>()
const file = ref<FileView>()
const { loadProjects, projectLabel } = useProjectDirectory()

onMounted(async () => {
  await loadProjects()
  file.value = await getFile(Number(props.id))
})

const handlePreview = async () => {
  if (!file.value) return
  try {
    await previewFileBinary(file.value.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '文件预览失败')
  }
}

const handleDownload = async () => {
  if (!file.value) return
  try {
    await downloadFileContent(file.value.id, file.value.fileName)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '文件下载失败')
  }
}
</script>

<style scoped>
.file-actions {
  margin-top: 20px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
</style>
