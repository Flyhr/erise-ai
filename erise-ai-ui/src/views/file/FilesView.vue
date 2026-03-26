<template>
  <div class="section-stack">
    <div class="page-header">
      <div>
        <h1>项目文件</h1>
        <div class="page-subtitle">支持三段式上传，PDF/Markdown/TXT 会自动进入解析与知识化流程。</div>
      </div>
      <el-upload :show-file-list="false" :before-upload="beforeUpload">
        <el-button type="primary">上传文件</el-button>
      </el-upload>
    </div>

    <el-table :data="files" class="glass-card" style="width: 100%" stripe>
      <el-table-column prop="fileName" label="文件名" min-width="220" />
      <el-table-column prop="mimeType" label="类型" width="180" />
      <el-table-column prop="fileSize" label="大小" width="120" />
      <el-table-column prop="parseStatus" label="解析状态" width="140" />
      <el-table-column prop="indexStatus" label="索引状态" width="140" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button text @click="$router.push(`/files/${row.id}`)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { completeUpload, getFiles, initUpload, uploadFileBinary } from '@/api/file'
import type { FileView } from '@/types/models'

const props = defineProps<{ id: string }>()
const projectId = Number(props.id)
const files = ref<FileView[]>([])

const load = async () => {
  const page = await getFiles({ projectId, pageNum: 1, pageSize: 50 })
  files.value = page.records
}

const beforeUpload = async (rawFile: File) => {
  const init = await initUpload({
    projectId,
    fileName: rawFile.name,
    fileSize: rawFile.size,
    mimeType: rawFile.type || 'application/octet-stream',
  })
  await uploadFileBinary(init.fileId, rawFile)
  await completeUpload(init.fileId)
  ElMessage.success('文件已上传并进入解析队列')
  await load()
  return false
}

onMounted(load)
</script>
