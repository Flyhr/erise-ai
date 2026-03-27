<template>
  <div class="section-stack">
    <div class="page-header">
      <div>
        <h1>项目文件</h1>
        <div class="page-subtitle">上传后的文件可以直接预览、下载、删除；doc/docx 还支持在线编辑。</div>
      </div>
      <el-upload :show-file-list="false" :before-upload="beforeUpload">
        <el-button type="primary">上传文件</el-button>
      </el-upload>
    </div>

    <el-table :data="files" class="glass-card" style="width: 100%" stripe>
      <el-table-column prop="fileName" label="文件名" min-width="240" />
      <el-table-column prop="mimeType" label="类型" min-width="180" />
      <el-table-column prop="fileSize" label="大小" width="120" />
      <el-table-column prop="parseStatus" label="解析状态" width="140" />
      <el-table-column prop="indexStatus" label="索引状态" width="140" />
      <el-table-column label="操作" min-width="280">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button text @click="$router.push(`/files/${row.id}`)">详情</el-button>
            <el-button text @click="preview(row)">预览</el-button>
            <el-button v-if="isOfficeFile(row.fileExt)" text @click="$router.push(`/files/${row.id}/edit`)">在线编辑</el-button>
            <el-button text type="danger" @click="removeFileItem(row.id, row.fileName)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { completeUpload, deleteFile, getFiles, initUpload, previewFileBinary, previewOfficeFile, uploadFileBinary } from '@/api/file'
import type { FileView } from '@/types/models'

const props = defineProps<{ id: string }>()
const projectId = Number(props.id)
const files = ref<FileView[]>([])

const isOfficeFile = (ext: string) => ['doc', 'docx'].includes(ext.toLowerCase())

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
  ElMessage.success('文件已上传并进入解析流程')
  await load()
  return false
}

const preview = async (file: FileView) => {
  if (isOfficeFile(file.fileExt)) {
    await previewOfficeFile(file.id)
    return
  }
  await previewFileBinary(file.id)
}

const removeFileItem = async (id: number, fileName: string) => {
  await ElMessageBox.confirm(`确认删除“${fileName}”吗？`, '删除文件', { type: 'warning' })
  await deleteFile(id)
  ElMessage.success('文件已删除')
  await load()
}

onMounted(load)
</script>