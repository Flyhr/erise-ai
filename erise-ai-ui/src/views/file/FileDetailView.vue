<template>
  <div v-if="file" class="page-shell">
    <AppPageHeader
      :title="file.fileName"
      eyebrow="文件详情"
      subtitle="查看文件元数据、预览方式、下载入口和在线编辑能力。"
      show-back
      back-label="返回项目文件"
      :back-to="backTarget"
    >
      <template #actions>
        <el-button plain @click="handlePreview">预览</el-button>
        <el-button v-if="isOfficeFile" @click="$router.push(`/files/${file.id}/edit`)">在线编辑</el-button>
        <el-button type="primary" @click="handleDownload">下载</el-button>
      </template>
    </AppPageHeader>

    <AppSectionCard title="文件信息">
      <div class="detail-grid">
        <div class="detail-item"><span>所属项目</span><strong>{{ projectLabel(file.projectId) }}</strong></div>
        <div class="detail-item"><span>类型</span><strong>{{ normalizeFileTypeLabel(file.fileExt, file.mimeType)
        }}</strong></div>
        <div class="detail-item"><span>大小</span><strong>{{ formatFileSize(file.fileSize) }}</strong></div>
        <div class="detail-item"><span>上传状态</span><strong>{{ uploadStatusLabel(file.uploadStatus) }}</strong></div>
        <div class="detail-item"><span>上传时间</span><strong>{{ formatDateTime(file.createdAt) }}</strong></div>
        <div class="detail-item"><span>更新时间</span><strong>{{ formatDateTime(file.updatedAt) }}</strong></div>
      </div>
      <div v-if="isOfficeFile" class="page-subtitle">可直接进入在线编辑，保存后会同步更新该文件的正文快照与检索内容。</div>
    </AppSectionCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { downloadFileContent, getFile, previewFileBinary, previewOfficeFile } from '@/api/file'
import { trackWorkspaceActivity } from '@/api/workspace'
import AppPageHeader from '@/components/common/AppPageHeader.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'
import { useProjectDirectory } from '@/composables/useProjectDirectory'
import type { FileView } from '@/types/models'
import { formatDateTime, formatFileSize, isOfficeEditableFile, normalizeFileTypeLabel, resolveErrorMessage, uploadStatusLabel } from '@/utils/formatters'

const props = defineProps<{ id: string }>()
const file = ref<FileView>()
const { loadProjects, projectLabel } = useProjectDirectory()
const isOfficeFile = computed(() => isOfficeEditableFile(file.value?.fileExt))
const backTarget = computed(() =>
  file.value
    ? {
        path: `/projects/${file.value.projectId}`,
        query: { tab: 'files' },
      }
    : '/files',
)

onMounted(async () => {
  await loadProjects()
  file.value = await getFile(Number(props.id))
  try {
    await trackWorkspaceActivity({
      assetType: 'FILE',
      assetId: Number(props.id),
      actionCode: 'FILE_DETAIL_OPEN',
    })
  } catch {
    // best effort only
  }
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
    ElMessage.error(resolveErrorMessage(error, '文件预览失败，请稍后重试'))
  }
}

const handleDownload = async () => {
  if (!file.value) return
  try {
    await downloadFileContent(file.value.id, file.value.fileName)
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '文件下载失败，请稍后重试'))
  }
}
</script>

<style scoped>
.detail-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px solid var(--line);
  background: var(--surface-strong);
}

.detail-item span {
  color: var(--muted);
  font-size: 13px;
}

.detail-item strong {
  font-size: 16px;
  letter-spacing: -0.02em;
}

@media (max-width: 900px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
