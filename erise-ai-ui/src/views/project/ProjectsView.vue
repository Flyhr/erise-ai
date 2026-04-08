<template>
  <div class="page-shell">
    <AppFilterBar>
      <el-input
        v-model="keyword"
        style="grid-column: span 6"
        clearable
        placeholder="按项目名称或描述搜索"
        @keyup.enter="runSearch"
      />
      <el-select v-model="statusFilter" style="grid-column: span 3" clearable placeholder="筛选状态">
        <el-option label="进行中" value="ACTIVE" />
        <el-option label="草稿" value="DRAFT" />
        <el-option label="已归档" value="ARCHIVED" />
      </el-select>
      <div class="projects-total">{{ total }} 个项目</div>
      <template #actions>
        <el-button @click="resetFilters">重置</el-button>
        <el-button type="primary" @click="openCreateDialog">新建项目</el-button>
      </template>
    </AppFilterBar>

    <div v-if="projects.length" class="project-grid">
      <AppSectionCard
        v-for="project in projects"
        :key="project.id"
        compact
        class="project-card"
        @click="openProject(project.id)"
      >
        <div class="project-card__top">
          <div class="project-card__title">{{ project.name }}</div>
          <div class="project-card__actions">
            <el-button text @click.stop="openEditDialog(project)">编辑</el-button>
            <el-button text class="project-card__danger" @click.stop="removeProject(project)">删除</el-button>
          </div>
        </div>
        <div class="project-card__summary">{{ project.description || '暂无项目简介' }}</div>
      </AppSectionCard>
    </div>
    <AppEmptyState v-else title="没有匹配的项目" description="调整筛选条件，或者先创建一个新的项目空间。">
      <el-button type="primary" @click="openCreateDialog">创建项目</el-button>
    </AppEmptyState>

    <div class="projects-footer">
      <span class="page-subtitle">每页最多展示 25 个项目</span>
      <CompactPager :page-num="pageNum" :page-size="pageSize" :total="total" @change="handlePageChange" />
    </div>

    <el-dialog v-model="dialogVisible" :title="editingProjectId ? '编辑项目' : '新建项目'" width="460px">
      <el-form :model="form" label-position="top">
        <el-form-item label="项目名称">
          <el-input v-model="form.name" maxlength="80" show-word-limit />
        </el-form-item>
        <el-form-item label="项目简介">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="240" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">
          {{ editingProjectId ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { createProject, deleteProject, getProjects, updateProject } from '@/api/project'
import AppEmptyState from '@/components/common/AppEmptyState.vue'
import AppFilterBar from '@/components/common/AppFilterBar.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'
import CompactPager from '@/components/common/CompactPager.vue'
import type { ProjectDetailView } from '@/types/models'
import { resolveErrorMessage } from '@/utils/formatters'

const router = useRouter()
const route = useRoute()
const projects = ref<ProjectDetailView[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitting = ref(false)
const editingProjectId = ref<number>()
const keyword = ref('')
const statusFilter = ref<string>()
const pageNum = ref(1)
const pageSize = 25
const form = reactive({ name: '', description: '' })

const load = async () => {
  const page = await getProjects({
    pageNum: pageNum.value,
    pageSize,
    q: keyword.value.trim() || undefined,
    status: statusFilter.value || undefined,
  })
  projects.value = page.records
  total.value = page.total
}

const syncFromRoute = async () => {
  keyword.value = typeof route.query.q === 'string' ? route.query.q : ''
  statusFilter.value = typeof route.query.status === 'string' ? route.query.status : undefined
  const nextPage = Number(route.query.pageNum)
  pageNum.value = Number.isFinite(nextPage) && nextPage > 0 ? nextPage : 1
  await load()
}

const pushRoute = async () => {
  await router.replace({
    path: '/projects',
    query: {
      ...(keyword.value.trim() ? { q: keyword.value.trim() } : {}),
      ...(statusFilter.value ? { status: statusFilter.value } : {}),
      ...(pageNum.value > 1 ? { pageNum: pageNum.value } : {}),
    },
  })
}

const runSearch = async () => {
  pageNum.value = 1
  await pushRoute()
}

const resetFilters = async () => {
  keyword.value = ''
  statusFilter.value = undefined
  pageNum.value = 1
  await pushRoute()
}

const handlePageChange = async (value: number) => {
  pageNum.value = value
  await pushRoute()
}

const openProject = (projectId: number) => {
  router.push(`/projects/${projectId}`)
}

const openCreateDialog = () => {
  editingProjectId.value = undefined
  form.name = ''
  form.description = ''
  dialogVisible.value = true
}

const openEditDialog = (project: ProjectDetailView) => {
  editingProjectId.value = project.id
  form.name = project.name
  form.description = project.description || ''
  dialogVisible.value = true
}

const ensureCurrentPage = async () => {
  if (!projects.value.length && total.value > 0 && pageNum.value > 1) {
    pageNum.value = Math.max(1, Math.ceil(total.value / pageSize))
    await pushRoute()
  }
}

const submit = async () => {
  submitting.value = true
  try {
    if (editingProjectId.value) {
      await updateProject(editingProjectId.value, form)
      ElMessage.success('项目已更新')
      dialogVisible.value = false
      await load()
      return
    }
    await createProject(form)
    ElMessage.success('项目已创建')
    dialogVisible.value = false
    pageNum.value = 1
    await pushRoute()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, editingProjectId.value ? '项目更新失败，请稍后重试' : '项目创建失败，请稍后重试'))
  } finally {
    submitting.value = false
  }
}

const removeProject = async (project: ProjectDetailView) => {
  try {
    await ElMessageBox.confirm(`确认删除项目“${project.name}”吗？此操作不可恢复。`, '删除项目', {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger',
    })
    await deleteProject(project.id)
    ElMessage.success('项目已删除')
    await load()
    await ensureCurrentPage()
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(resolveErrorMessage(error, '项目删除失败，请稍后重试'))
    }
  }
}

onMounted(syncFromRoute)

watch(
  () => route.fullPath,
  async () => {
    await syncFromRoute()
  },
)
</script>

<style scoped>
.projects-total {
  grid-column: span 3;
  display: flex;
  align-items: center;
  color: var(--muted);
  font-size: 13px;
}

.project-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
}

.project-card {
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.project-card:hover {
  transform: translateY(-2px);
  border-color: rgba(64, 158, 255, 0.35);
  box-shadow: 0 14px 32px rgba(15, 23, 42, 0.08);
}

.project-card__top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 16px;
}

.project-card__title {
  font-size: 18px;
  font-weight: 800;
  letter-spacing: -0.03em;
  line-height: 1.3;
}

.project-card__summary {
  color: var(--muted);
  line-height: 1.75;
  min-height: 72px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.project-card__actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.project-card__danger {
  color: var(--danger);
}

.projects-footer {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

@media (max-width: 1440px) {
  .project-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1024px) {
  .project-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .project-grid {
    grid-template-columns: 1fr;
  }

  .project-card__top {
    flex-direction: column;
  }
}
</style>
