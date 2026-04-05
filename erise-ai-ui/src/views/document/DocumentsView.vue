<template>
  <div class="page-shell">

    <!-- <ProjectSubnav v-if="scopedProjectId" :project-id="scopedProjectId" /> -->

    <AppFilterBar>
      <el-input v-model="keyword" style="grid-column: span 5" clearable placeholder="按标题或摘要搜索文档"
        @keyup.enter="handleSearch" />
      <el-select v-model="selectedProjectId" style="grid-column: span 4" filterable clearable
        :disabled="Boolean(scopedProjectId)" placeholder="筛选项目">
        <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
      </el-select>

      <template #actions>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        <el-button type="primary" :disabled="!createProjectId" @click="create">新建文档</el-button>

      </template>
    </AppFilterBar>

    <AppSectionCard :unpadded="true">

      <AppDataTable :data="documents" stripe :row-class-name="rowClassName">
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
              <el-button text
                @click="router.push({ path: `/documents/${row.id}/edit`, query: { mode: 'preview' } })">浏览</el-button>
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

      <template #footer>
        <div class="documents-footer">
          <span class="page-subtitle" style="margin: 0;">共 {{ total }} 份文档</span>
          <el-pagination background layout="prev, pager, next" :current-page="pageNum" :page-size="pageSize"
            :total="total" @current-change="handlePageChange" />
        </div>
      </template>
    </AppSectionCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { getProject } from '@/api/project'
import { createDocument, deleteDocument, getDocuments } from '@/api/document'
import AppDataTable from '@/components/common/AppDataTable.vue'
import AppFilterBar from '@/components/common/AppFilterBar.vue'
import AppPageHeader from '@/components/common/AppPageHeader.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'
import AppStatusTag from '@/components/common/AppStatusTag.vue'
import ProjectSubnav from '@/components/common/ProjectSubnav.vue'
import { useProjectDirectory } from '@/composables/useProjectDirectory'
import type { DocumentSummaryView } from '@/types/models'
import { documentStatusLabel, documentStatusTone, formatDateTime, resolveErrorMessage } from '@/utils/formatters'

const props = defineProps<{ id?: string }>()
const router = useRouter()
const scopedProjectId = props.id ? Number(props.id) : undefined
const { projects, loadProjects, projectLabel } = useProjectDirectory()
const projectName = ref('')
const documents = ref<DocumentSummaryView[]>([])
const keyword = ref('')
const selectedProjectId = ref<number | undefined>(scopedProjectId)
const pageNum = ref(1)
const pageSize = ref(12)
const total = ref(0)

const createProjectId = computed(() => scopedProjectId || selectedProjectId.value)
// const pageTitle = computed(() => (scopedProjectId ? `${projectName.value || '当前项目'} / 文档` : '全部文档'))
// const pageSubtitle = computed(() =>
//   scopedProjectId
//     ? '项目内文档与全局文档中心使用同一套列表规则，创建后会直接进入浏览态。'
//     : '集中查看所有文档，支持按项目筛选、搜索和直接进入浏览或编辑。',
// )

const load = async () => {
  const page = await getDocuments({
    projectId: scopedProjectId || selectedProjectId.value,
    q: keyword.value.trim() || undefined,
    pageNum: pageNum.value,
    pageSize: pageSize.value,
  })
  documents.value = page.records
  total.value = page.total
}

const handleSearch = async () => {
  pageNum.value = 1
  await load()
}

const resetFilters = async () => {
  keyword.value = ''
  selectedProjectId.value = scopedProjectId
  pageNum.value = 1
  await load()
}

const handlePageChange = async (value: number) => {
  pageNum.value = value
  await load()
}

const rowClassName = ({ row }: { row: DocumentSummaryView }) => (row.docStatus === 'DRAFT' ? 'document-row--draft' : '')

const create = async () => {
  if (!createProjectId.value) {
    ElMessage.warning('请先选择一个项目，再创建文档。')
    return
  }
  try {
    const created = await createDocument({ projectId: createProjectId.value, title: '未命名文档', summary: '' })
    ElMessage.success('文档已创建')
    router.push({ path: `/documents/${created.id}/edit`, query: { mode: 'preview' } })
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '文档创建失败'))
  }
}

const removeDocument = async (document: DocumentSummaryView) => {
  await ElMessageBox.confirm(`确认删除文档“${document.title}”吗？`, '删除文档', { type: 'warning' })
  await deleteDocument(document.id)
  ElMessage.success('文档已删除')
  await load()
}

onMounted(async () => {
  await loadProjects()
  if (scopedProjectId) {
    const project = await getProject(scopedProjectId)
    projectName.value = project.name
  }
  await load()
})
</script>

<style scoped>
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
