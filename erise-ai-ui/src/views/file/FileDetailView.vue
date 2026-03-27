<template>
  <div class="section-stack">
    <div class="page-header">
      <div>
        <h1>{{ file?.fileName }}</h1>
        <div class="page-subtitle">查看文件元数据、索引状态，并执行预览、下载或在线编辑。</div>
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
        <el-button v-if="isOfficeFile" plain @click="$router.push(`/files/${file.id}/edit`)">在线编辑</el-button>
        <el-button plain @click="handleDownload">下载</el-button>
      </div>

      <div v-if="isOfficeFile" class="page-subtitle">当前文件支持在线 Office 风格编辑，保存后会同步项目知识索引。</div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { downloadFileContent, getFile, previewFileBinary, previewOfficeFile } from '@/api/file'
import type { FileView } from '@/types/models'
import { useProjectDirectory } from '@/composables/useProjectDirectory'

const props = defineProps<{ id: string }>()
const file = ref<FileView>()
const { loadProjects, projectLabel } = useProjectDirectory()
const isOfficeFile = computed(() => ['doc', 'docx'].includes((file.value?.fileExt || '').toLowerCase()))

onMounted(async () => {
  await loadProjects()
  file.value = await getFile(Number(props.id))
})

const handlePreview = async () => {
  if (!file.value) return
  try {
    if (isOfficeFile.value) {
      await previewOfficeFile(file.value.id)
    } else {
      await previewFileBinary(file.value.id)
    }
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