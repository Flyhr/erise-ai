<template>
  <ProjectScopedListShell
    v-if="scopedProjectId"
    :project-id="scopedProjectId"
    title="文档列表"
    :keyword="keyword"
    search-placeholder="按标题或摘要搜索文档"
    @update:keyword="keyword = $event"
    @search="handleSearch"
  >
    <template #actions>
      <el-button @click="resetFilters">重置</el-button>
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-button type="primary" @click="create">新建文档</el-button>
    </template>

    <AppSectionCard title="文档列表" :unpadded="Boolean(documents.length)">
      <AppDataTable v-if="documents.length" :data="documents" stripe :row-class-name="rowClassName">
        <el-table-column label="文档" min-width="220">
          <template #default="{ row }">
            <div class="document-title-cell">
              <strong>{{ row.title }}</strong>
              <span>{{ row.summary || '暂无摘要' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <AppStatusTag :label="documentStatusLabel(row.docStatus)" :tone="documentStatusTone(row.docStatus)" />
          </template>
        </el-table-column>
        <el-table-column label="版本" width="100">
          <template #default="{ row }">v{{ row.latestVersionNo }}</template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="220" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button text @click="router.push({ path: `/documents/${row.id}/edit`, query: { mode: 'preview' } })">
                浏览
              </el-button>
              <el-button text @click="router.push(`/documents/${row.id}/edit`)">编辑</el-button>
              <el-dropdown>
                <el-button text>更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="removeDocument(row)">删除文档</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </AppDataTable>
      <AppEmptyState
        v-else
        title="当前项目还没有文档"
        description="新建在线文档后，这里会显示文档状态、版本和最近更新时间。"
      />

      <template #footer>
        <div class="documents-footer">
          <span class="page-subtitle" style="margin: 0;">共 {{ total }} 份文档</span>
          <CompactPager :page-num="pageNum" :page-size="pageSize" :total="total" @change="handlePageChange" />
        </div>
      </template>
    </AppSectionCard>
  </ProjectScopedListShell>

  <div v-else class="page-shell">
    <AppFilterBar>
      <el-input
        v-model="keyword"
        style="grid-column: span 5"
        clearable
        placeholder="按标题或摘要搜索文档"
        @keyup.enter="handleSearch"
      />
      <el-select
        v-model="selectedProjectId"
        style="grid-column: span 4"
        filterable
        clearable
        placeholder="筛选项目"
      >
        <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
      </el-select>

      <div class="documents-filter-copy">支持按项目筛选后浏览、创建和管理在线文档。</div>

      <template #actions>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        <el-button type="primary" :disabled="!createProjectId" @click="create">新建文档</el-button>
      </template>
    </AppFilterBar>

    <AppSectionCard title="文档列表" :unpadded="Boolean(documents.length)">
      <AppDataTable v-if="documents.length" :data="documents" stripe :row-class-name="rowClassName">
        <el-table-column label="文档" min-width="180">
          <template #default="{ row }">
            <div class="document-title-cell">
              <strong>{{ row.title }}</strong>
              <span>{{ row.summary || '暂无摘要' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <AppStatusTag :label="documentStatusLabel(row.docStatus)" :tone="documentStatusTone(row.docStatus)" />
          </template>
        </el-table-column>
        <el-table-column label="所属项目" min-width="150">
          <template #default="{ row }">{{ projectLabel(row.projectId) }}</template>
        </el-table-column>
        <el-table-column label="版本" width="100">
          <template #default="{ row }">v{{ row.latestVersionNo }}</template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="220" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button text @click="router.push({ path: `/documents/${row.id}/edit`, query: { mode: 'preview' } })">
                浏览
              </el-button>
              <el-button text @click="router.push(`/documents/${row.id}/edit`)">编辑</el-button>
              <el-dropdown>
                <el-button text>更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="removeDocument(row)">删除文档</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </AppDataTable>
      <AppEmptyState
        v-else
        title="还没有匹配的文档"
        description="调整筛选条件，或者先选择项目创建一份新文档。"
      />

      <template #footer>
        <div class="documents-footer">
          <span class="page-subtitle" style="margin: 0;">共 {{ total }} 份文档</span>
          <CompactPager :page-num="pageNum" :page-size="pageSize" :total="total" @change="handlePageChange" />
        </div>
      </template>
    </AppSectionCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { deleteDocument, getDocuments } from '@/api/document'
import AppDataTable from '@/components/common/AppDataTable.vue'
import AppEmptyState from '@/components/common/AppEmptyState.vue'
import AppFilterBar from '@/components/common/AppFilterBar.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'
import AppStatusTag from '@/components/common/AppStatusTag.vue'
import CompactPager from '@/components/common/CompactPager.vue'
import ProjectScopedListShell from '@/components/common/ProjectScopedListShell.vue'
import { useProjectDirectory } from '@/composables/useProjectDirectory'
import type { DocumentSummaryView } from '@/types/models'
import { documentStatusLabel, documentStatusTone, formatDateTime, resolveErrorMessage } from '@/utils/formatters'

const props = defineProps<{ id?: string }>()
const route = useRoute()
const router = useRouter()
const scopedProjectId = props.id ? Number(props.id) : undefined
const { projects, loadProjects, projectLabel } = useProjectDirectory()
const documents = ref<DocumentSummaryView[]>([])
const keyword = ref('')
const selectedProjectId = ref<number | undefined>(scopedProjectId)
const pageNum = ref(1)
const pageSize = 12
const total = ref(0)

const createProjectId = computed(() => scopedProjectId || selectedProjectId.value)

const load = async () => {
  const page = await getDocuments({
    projectId: scopedProjectId || selectedProjectId.value,
    q: keyword.value.trim() || undefined,
    pageNum: pageNum.value,
    pageSize,
  })
  documents.value = page.records
  total.value = page.total
}

const syncFromRoute = async () => {
  keyword.value = typeof route.query.q === 'string' ? route.query.q : ''
  if (!scopedProjectId) {
    const nextProjectId = Number(route.query.projectId)
    selectedProjectId.value = Number.isFinite(nextProjectId) && nextProjectId > 0 ? nextProjectId : undefined
  }
  const nextPage = Number(route.query.pageNum)
  pageNum.value = Number.isFinite(nextPage) && nextPage > 0 ? nextPage : 1
  await load()
}

const pushRoute = async () => {
  await router.replace({
    path: route.path,
    query: {
      ...(keyword.value.trim() ? { q: keyword.value.trim() } : {}),
      ...(!scopedProjectId && selectedProjectId.value ? { projectId: selectedProjectId.value } : {}),
      ...(pageNum.value > 1 ? { pageNum: pageNum.value } : {}),
    },
  })
}

const ensureCurrentPage = async () => {
  if (!documents.value.length && total.value > 0 && pageNum.value > 1) {
    pageNum.value = Math.max(1, Math.ceil(total.value / pageSize))
    await pushRoute()
  }
}

const handleSearch = async () => {
  pageNum.value = 1
  await pushRoute()
}

const resetFilters = async () => {
  keyword.value = ''
  selectedProjectId.value = scopedProjectId
  pageNum.value = 1
  await pushRoute()
}

const handlePageChange = async (value: number) => {
  pageNum.value = value
  await pushRoute()
}

const rowClassName = ({ row }: { row: DocumentSummaryView }) => (row.docStatus === 'DRAFT' ? 'document-row--draft' : '')

const create = async () => {
  if (!createProjectId.value) {
    ElMessage.warning('请先选择一个项目，再创建文档。')
    return
  }
  await router.push({
    path: '/documents/new/edit',
    query: { projectId: createProjectId.value },
  })
}

const removeDocument = async (document: DocumentSummaryView) => {
  await ElMessageBox.confirm(`确认删除文档“${document.title}”吗？`, '删除文档', { type: 'warning' })
  await deleteDocument(document.id)
  ElMessage.success('文档已删除')
  await load()
  await ensureCurrentPage()
}

onMounted(async () => {
  await loadProjects()
  await syncFromRoute()
})

watch(
  () => route.fullPath,
  async () => {
    await syncFromRoute()
  },
)
</script>

<style scoped>
.documents-filter-copy {
  grid-column: span 3;
  display: flex;
  align-items: center;
  color: var(--muted);
  font-size: 13px;
}

.document-title-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.document-title-cell strong {
  font-size: 15px;
  font-weight: 700;
}

.document-title-cell span {
  color: var(--muted);
  font-size: 12px;
}

.documents-footer {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

:deep(.document-row--draft) {
  --el-table-tr-bg-color: rgba(180, 83, 9, 0.05);
}
</style>
