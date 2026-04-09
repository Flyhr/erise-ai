<template>
  <div class="page-shell">
    <AppPageHeader title="搜索" eyebrow="全局检索" subtitle="统一搜索文件、文档与结构化内容，支持从结果直接跳转处理。" />

    <AppSectionCard>
      <AppFilterBar>
        <el-input v-model="query" style="grid-column: span 6" clearable placeholder="输入关键词" @keyup.enter="runSearch" />
        <el-select v-model="projectId" style="grid-column: span 4" clearable filterable placeholder="选择项目">
          <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
        </el-select>
        <div class="search-filter-copy">
          {{ filteredResults.length }} 条结果
        </div>
        <template #actions>
          <el-button type="primary" :loading="loading" @click="runSearch">搜索</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </template>
      </AppFilterBar>
    </AppSectionCard>

    <AppSectionCard title="搜索结果">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="全部" name="ALL" />
        <el-tab-pane label="文档" name="DOCUMENT" />
        <el-tab-pane label="文件" name="FILE" />
        <el-tab-pane label="表格" name="CONTENT" />
      </el-tabs>

      <div v-if="filteredResults.length" class="search-result-list">
        <article v-for="item in filteredResults" :key="`${item.sourceType}-${item.sourceId}`" class="search-result-card">
          <div class="search-result-card__main">
            <div class="search-result-card__head">
              <div>
                <div class="search-result-card__title">{{ item.title }}</div>
                <div class="meta-row">
                  <span>{{ resultMatchLabel(item) }}</span>
                  <span>{{ resultSourceLabel(item) }}</span>
                  <span>{{ projectLabel(item.projectId) }}</span>
                </div>
              </div>
              <AppStatusTag :label="resultSourceLabel(item)" tone="info" />
            </div>
            <div class="page-subtitle">{{ item.snippet || '暂无结果摘要' }}</div>
          </div>

          <div class="table-actions">
            <template v-if="isDocumentResult(item)">
              <el-button text @click="previewDocument(item.sourceId)">浏览</el-button>
              <el-button text @click="editDocument(item.sourceId)">编辑</el-button>
              <el-dropdown>
                <el-button text>更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="removeDocument(item)">删除文档</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>

            <template v-else-if="isFileResult(item)">
              <el-button text @click="viewFile(item.sourceId)">详情</el-button>
              <el-button text @click="previewFile(item)">预览</el-button>
              <el-dropdown>
                <el-button text>更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="supportsOfficeEdit(item)" @click="editFile(item.sourceId)">在线编辑</el-dropdown-item>
                    <el-dropdown-item @click="removeFile(item)">删除文件</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>

            <template v-else>
              <el-button text @click="previewContent(item.sourceId)">浏览</el-button>
              <el-button text @click="editContent(item.sourceId)">编辑</el-button>
              <el-dropdown>
                <el-button text>更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="removeContent(item)">删除{{ resultSourceLabel(item) }}</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </div>
        </article>
      </div>
      <AppEmptyState v-else :title="query ? '没有找到结果' : '请输入关键词开始搜索'" description="可以按项目范围筛选，并直接从结果跳转到详情、预览或编辑。" />
    </AppSectionCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { deleteContentItem } from '@/api/content'
import { deleteDocument } from '@/api/document'
import { deleteFile, previewFileBinary, previewOfficeFile } from '@/api/file'
import { search } from '@/api/search'
import AppEmptyState from '@/components/common/AppEmptyState.vue'
import AppFilterBar from '@/components/common/AppFilterBar.vue'
import AppPageHeader from '@/components/common/AppPageHeader.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'
import AppStatusTag from '@/components/common/AppStatusTag.vue'
import type { SearchResultView } from '@/types/models'
import { useProjectDirectory } from '@/composables/useProjectDirectory'
import { resolveErrorMessage } from '@/utils/formatters'

const route = useRoute()
const router = useRouter()
const { projects, loadProjects, projectLabel } = useProjectDirectory()
const query = ref('')
const projectId = ref<number | undefined>()
const activeTab = ref<'ALL' | 'DOCUMENT' | 'FILE' | 'CONTENT'>('ALL')
const results = ref<SearchResultView[]>([])
const loading = ref(false)

const parseNumber = (value: unknown) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
}

const filteredResults = computed(() => {
  if (activeTab.value === 'ALL') return results.value
  if (activeTab.value === 'CONTENT') {
    return results.value.filter((item) => ['SHEET', 'BOARD', 'DATA_TABLE'].includes(item.sourceType))
  }
  return results.value.filter((item) => item.sourceType === activeTab.value)
})

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
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '搜索失败，请稍后重试'))
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

const resetFilters = async () => {
  query.value = ''
  projectId.value = undefined
  activeTab.value = 'ALL'
  results.value = []
  await router.push('/search')
}

const isDocumentResult = (item: SearchResultView) => item.sourceType === 'DOCUMENT'
const isFileResult = (item: SearchResultView) => item.sourceType === 'FILE'
const isContentResult = (item: SearchResultView) => ['SHEET', 'BOARD', 'DATA_TABLE'].includes(item.sourceType)
const supportsOfficeEdit = (item: SearchResultView) => /\.(doc|docx|txt)$/i.test(item.title)

const resultSourceLabel = (item: SearchResultView) => {
  if (item.sourceType === 'DOCUMENT') return '文档'
  if (item.sourceType === 'FILE') return '文件'
  if (item.sourceType === 'SHEET') return '表格'
  if (item.sourceType === 'BOARD') return '画板'
  if (item.sourceType === 'DATA_TABLE') return '数据表'
  return item.sourceType
}

const resultMatchLabel = (item: SearchResultView) => {
  if (item.mimeType === 'KNOWLEDGE') return '知识片段'
  if (item.sourceType === 'DOCUMENT') return '文档命中'
  if (item.sourceType === 'FILE') return '文件命中'
  if (isContentResult(item)) return '结构化内容命中'
  return item.mimeType || '结果项'
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
    ElMessage.error(resolveErrorMessage(error, '文件预览失败，请稍后重试'))
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

watch(() => [route.query.q, route.query.projectId], () => { void syncFromRoute() }, { immediate: true })
onMounted(async () => { await loadProjects() })
</script>

<style scoped>
.search-filter-copy {
  grid-column: span 2;
  display: flex;
  align-items: center;
  color: var(--muted);
  font-size: 13px;
}

.search-result-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.search-result-card {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 18px;
  border-radius: var(--radius-md);
  border: 1px solid var(--line);
  background: var(--surface-strong);
}

.search-result-card__main {
  min-width: 0;
}

.search-result-card__head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 10px;
}

.search-result-card__title {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.03em;
}

@media (max-width: 900px) {
  .search-result-card,
  .search-result-card__head {
    flex-direction: column;
  }
}
</style>