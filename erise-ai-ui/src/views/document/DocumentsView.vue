<template>
  <div class="section-stack">
    <div class="page-header">
      <div>
        <h1>在线文档</h1>
        <div class="page-subtitle">文档采用 Tiptap 编辑器，保存草稿并可发布为可追溯版本。</div>
      </div>
      <el-button type="primary" @click="create">新建文档</el-button>
    </div>

    <el-table :data="documents" class="glass-card" stripe>
      <el-table-column prop="title" label="标题" min-width="220" />
      <el-table-column prop="docStatus" label="状态" width="120" />
      <el-table-column prop="latestVersionNo" label="最新版本" width="120" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button text @click="$router.push(`/documents/${row.id}/edit`)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createDocument, getDocuments } from '@/api/document'
import type { DocumentSummaryView } from '@/types/models'

const props = defineProps<{ id: string }>()
const projectId = Number(props.id)
const documents = ref<DocumentSummaryView[]>([])

const load = async () => {
  const page = await getDocuments({ projectId, pageNum: 1, pageSize: 50 })
  documents.value = page.records
}

const create = async () => {
  const created = await createDocument({ projectId, title: '未命名文档', summary: '' })
  ElMessage.success('文档已创建')
  window.location.href = `/documents/${created.id}/edit`
}

onMounted(load)
</script>
