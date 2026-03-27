<template>
  <div class="section-stack">
    <div class="page-header">
      <div>
        <h1>全局搜索</h1>
        <div class="page-subtitle">按项目范围搜索文档、文件、表格、画板、数据表以及知识片段，并直接执行常用操作。</div>
      </div>
    </div>

    <el-card class="glass-card" shadow="never">
      <div class="search-toolbar">
        <el-input v-model="query" clearable placeholder="输入关键词搜索项目内容" @keyup.enter="runSearch" />
        <el-select v-model="projectId" clearable filterable placeholder="全部项目">
          <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="runSearch">搜索</el-button>
      </div>
    </el-card>

    <el-card class="glass-card" shadow="never">
      <template #header>搜索结果</template>
      <div v-if="results.length" class="section-stack">
        <div v-for="item in results" :key="`${item.sourceType}-${item.sourceId}`" class="glass-card search-result">
          <div class="search-result__main">
            <div class="search-result__title">{{ item.title }}</div>
            <div class="meta-row">
              <span>{{ resultMatchLabel(item) }}</span>
              <span>来源：{{ resultSourceLabel(item) }}</span>
              <span>{{ projectLabel(item.projectId) }}</span>
            </div>
            <div class="page-subtitle">{{ item.snippet || '暂无摘要' }}</div>
          </div>

          <div class="search-result__actions">
            <template v-if="isDocumentResult(item)">
              <el-button text @click="editDocument(item.sourceId)">编辑</el-button>
              <el-button text @click="previewDocument(item.sourceId)">预览</el-button>
              <el-button text type="danger" @click="removeDocument(item)">删除</el-button>
            </template>

            <template v-else-if="isFileResult(item)">
              <el-button text @click="viewFile(item.sourceId)">详情</el-button>
              <el-button text @click="previewFile(item)">预览</el-button>
              <el-button v-if="supportsOfficeEdit(item)" text @click="editFile(item.sourceId)">在线编辑</el-button>
              <el-button text type="danger" @click="removeFile(item)">删除</el-button>
            </template>

            <template v-else>
              <el-button text @click="editContent(item.sourceId)">编辑</el-button>
              <el-button text @click="previewContent(item.sourceId)">预览</el-button>
              <el-button text type="danger" @click="removeContent(item)">删除</el-button>
            </template>
          </div>
        </div>
      </div>
      <div v-else class="empty-box">{{ query ? '没有找到匹配结果。' : '输入关键词开始搜索。' }}</div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { deleteContentItem } from '@/api/content'
import { deleteDocument } from '@/api/document'
import { deleteFile, previewFileBinary, previewOfficeFile } from '@/api/file'
import { search } from '@/api/search'
import type { SearchResultView } from '@/types/models'
import { useProjectDirectory } from '@/composables/useProjectDirectory'

const route = useRoute()
const router = useRouter()
const { projects, loadProjects, projectLabel } = useProjectDirectory()
const query = ref('')
const projectId = ref<number | undefined>()
const results = ref<SearchResultView[]>([])
const loading = ref(false)

const parseNumber = (value: unknown) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
}

const performSearch = async () => {
  const keyword = query.value.trim()
  if (!keyword) {
    results.value = []
    return
  }
  loading.value = true
  try {
    const page = await search({ q: keyword, projectId: projectId.value, pageNum: 1, pageSize: 50 })
    results.value = page.records
  } finally {
    loading.value = false
  }
}

const syncFromRoute = async () => {
  query.value = typeof route.query.q === 'string' ? route.query.q : ''
  projectId.value = parseNumber(route.query.projectId)
  await performSearch()
}

const runSearch = async () => {
  const keyword = query.value.trim()
  await router.push({
    path: '/search',
    query: {
      ...(keyword ? { q: keyword } : {}),
      ...(projectId.value ? { projectId: projectId.value } : {}),
    },
  })
}

const isDocumentResult = (item: SearchResultView) => item.sourceType === 'DOCUMENT'
const isFileResult = (item: SearchResultView) => item.sourceType === 'FILE'
const isContentResult = (item: SearchResultView) => ['SHEET', 'BOARD', 'DATA_TABLE'].includes(item.sourceType)

const supportsOfficeEdit = (item: SearchResultView) => /\.docx?$/i.test(item.title)

const resultSourceLabel = (item: SearchResultView) => {
  if (item.sourceType === 'DOCUMENT') return '文档'
  if (item.sourceType === 'FILE') return '文件'
  if (item.sourceType === 'SHEET') return '表格'
  if (item.sourceType === 'BOARD') return '画板'
  if (item.sourceType === 'DATA_TABLE') return '数据表'
  return item.sourceType
}

const resultMatchLabel = (item: SearchResultView) => {
  if (item.mimeType === 'KNOWLEDGE') return '命中知识片段'
  if (item.sourceType === 'DOCUMENT') return '命中文档'
  if (item.sourceType === 'FILE') return '命中文件'
  if (isContentResult(item)) return '命中内容实体'
  return item.mimeType || '搜索结果'
}

const editDocument = (id: number) => router.push(`/documents/${id}/edit`)
const previewDocument = (id: number) => router.push({ path: `/documents/${id}/edit`, query: { mode: 'preview' } })
const viewFile = (id: number) => router.push(`/files/${id}`)
const editFile = (id: number) => router.push(`/files/${id}/edit`)
const editContent = (id: number) => router.push(`/contents/${id}/edit`)
const previewContent = (id: number) => router.push({ path: `/contents/${id}/edit`, query: { mode: 'preview' } })

const previewFile = async (item: SearchResultView) => {
  try {
    if (supportsOfficeEdit(item)) {
      await previewOfficeFile(item.sourceId)
    } else {
      await previewFileBinary(item.sourceId)
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '文件预览失败')
  }
}

const removeDocument = async (item: SearchResultView) => {
  await ElMessageBox.confirm(`确认删除文档“${item.title}”吗？`, '删除文档', { type: 'warning' })
  await deleteDocument(item.sourceId)
  ElMessage.success('文档已删除')
  await performSearch()
}

const removeFile = async (item: SearchResultView) => {
  await ElMessageBox.confirm(`确认删除文件“${item.title}”吗？`, '删除文件', { type: 'warning' })
  await deleteFile(item.sourceId)
  ElMessage.success('文件已删除')
  await performSearch()
}

const removeContent = async (item: SearchResultView) => {
  await ElMessageBox.confirm(`确认删除“${item.title}”吗？`, `删除${resultSourceLabel(item)}`, { type: 'warning' })
  await deleteContentItem(item.sourceId)
  ElMessage.success(`${resultSourceLabel(item)}已删除`)
  await performSearch()
}

watch(
  () => [route.query.q, route.query.projectId],
  () => {
    void syncFromRoute()
  },
  { immediate: true },
)

onMounted(async () => {
  await loadProjects()
})
</script>

<style scoped>
.search-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 240px 120px;
  gap: 12px;
}

.search-result {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
}

.search-result__main {
  min-width: 0;
}

.search-result__title {
  font-size: 18px;
  font-weight: 700;
}

.search-result__actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
  justify-content: flex-end;
  min-width: 220px;
}

@media (max-width: 900px) {
  .search-toolbar {
    grid-template-columns: 1fr;
  }

  .search-result {
    flex-direction: column;
  }

  .search-result__actions {
    justify-content: flex-start;
    min-width: 0;
  }
}
</style>