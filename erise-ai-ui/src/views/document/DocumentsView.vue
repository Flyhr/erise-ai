<template>
  <div class="section-stack">
    <div class="page-header">
      <div>
        <h1>在线文档</h1>
        <div class="page-subtitle">支持富文本草稿、在线预览与版本发布，保存后会同步到项目知识库。</div>
      </div>
      <el-button type="primary" @click="create">新建文档</el-button>
    </div>

    <el-table :data="documents" class="glass-card" stripe>
      <el-table-column prop="title" label="标题" min-width="260" />
      <el-table-column prop="docStatus" label="状态" width="120" />
      <el-table-column prop="latestVersionNo" label="最新版本" width="120" />
      <el-table-column prop="updatedAt" label="更新时间" min-width="180" />
      <el-table-column label="操作" width="240">
        <template #default="{ row }">
          <el-button text @click="editDocument(row.id)">编辑</el-button>
          <el-button text @click="previewDocument(row.id)">预览</el-button>
          <el-button text type="danger" @click="removeDocument(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { createDocument, deleteDocument, getDocuments } from '@/api/document'
import type { DocumentSummaryView } from '@/types/models'

const props = defineProps<{ id: string }>()
const router = useRouter()
const projectId = Number(props.id)
const documents = ref<DocumentSummaryView[]>([])

const load = async () => {
  const page = await getDocuments({ projectId, pageNum: 1, pageSize: 50 })
  documents.value = page.records
}

const create = async () => {
  const created = await createDocument({ projectId, title: '未命名文档', summary: '' })
  ElMessage.success('文档已创建')
  router.push(`/documents/${created.id}/edit`)
}

const editDocument = (id: number) => {
  router.push(`/documents/${id}/edit`)
}

const previewDocument = (id: number) => {
  router.push({ path: `/documents/${id}/edit`, query: { mode: 'preview' } })
}

const removeDocument = async (document: DocumentSummaryView) => {
  await ElMessageBox.confirm(`确认删除文档“${document.title}”吗？`, '删除文档', {
    type: 'warning',
  })
  await deleteDocument(document.id)
  ElMessage.success('文档已删除')
  await load()
}

onMounted(load)
</script>
