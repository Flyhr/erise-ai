<template>
  <div class="page-shell">

    <AppFilterBar>
      <el-input v-model="keyword" style="grid-column: span 6" clearable placeholder="按项目名称或描述搜索" />
      <el-select v-model="statusFilter" style="grid-column: span 3" clearable placeholder="筛选状态">
        <el-option label="进行中" value="ACTIVE" />
        <el-option label="草稿" value="DRAFT" />
        <el-option label="已归档" value="ARCHIVED" />
      </el-select>
      <div style="grid-column: span 3; display: flex; align-items: center; color: var(--muted); font-size: 13px;">
        {{ filteredProjects.length }} 个项目
      </div>
      <template #actions>

        <el-button @click="resetFilters">重置</el-button>
        <el-button type="primary" @click="dialogVisible = true">新建项目</el-button>

      </template>
    </AppFilterBar>

    <div v-if="filteredProjects.length" class="project-grid">
      <AppSectionCard v-for="project in filteredProjects" :key="project.id" compact>
        <div class="project-card__head">
          <div>
            <div class="project-card__title">{{ project.name }}</div>
            <div class="page-subtitle">{{ project.description || '暂无项目说明' }}</div>
          </div>
          <AppStatusTag :label="projectStatusLabel(project.projectStatus)"
            :tone="projectStatusTone(project.projectStatus)" />
        </div>

        <div class="project-card__metrics">
          <div>
            <strong>{{ project.fileCount }}</strong>
            <span>文件</span>
          </div>
          <div>
            <strong>{{ project.documentCount }}</strong>
            <span>文档</span>
          </div>
          <div>
            <strong>{{ formatDateTime(project.updatedAt, 'MM-DD HH:mm') }}</strong>
            <span>最近更新</span>
          </div>
        </div>

        <div class="table-actions">
          <el-button text @click="$router.push(`/projects/${project.id}`)">打开项目</el-button>
          <el-button text @click="$router.push(`/projects/${project.id}/ai`)">AI 对话</el-button>
          <el-dropdown>
            <el-button text>更多</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="$router.push(`/projects/${project.id}/files`)">查看文件</el-dropdown-item>
                <el-dropdown-item @click="$router.push(`/projects/${project.id}/documents`)">查看文档</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </AppSectionCard>
    </div>
    <AppEmptyState v-else title="没有匹配的项目" description="调整筛选条件，或者先创建一个新的项目空间。">
      <el-button type="primary" @click="dialogVisible = true">创建项目</el-button>
    </AppEmptyState>

    <el-dialog v-model="dialogVisible" title="新建项目" width="460px">
      <el-form :model="form" label-position="top">
        <el-form-item label="项目名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="项目说明">
          <el-input v-model="form.description" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createProject, getProjects } from '@/api/project'
import AppEmptyState from '@/components/common/AppEmptyState.vue'
import AppFilterBar from '@/components/common/AppFilterBar.vue'
import AppPageHeader from '@/components/common/AppPageHeader.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'
import AppStatusTag from '@/components/common/AppStatusTag.vue'
import type { ProjectDetailView } from '@/types/models'
import { formatDateTime, projectStatusLabel, projectStatusTone, resolveErrorMessage } from '@/utils/formatters'

const projects = ref<ProjectDetailView[]>([])
const dialogVisible = ref(false)
const submitting = ref(false)
const keyword = ref('')
const statusFilter = ref<string>()
const form = reactive({ name: '', description: '' })

const filteredProjects = computed(() =>
  projects.value.filter((project) => {
    const matchedKeyword = !keyword.value.trim() || [project.name, project.description || ''].join(' ').toLowerCase().includes(keyword.value.trim().toLowerCase())
    const matchedStatus = !statusFilter.value || project.projectStatus === statusFilter.value
    return matchedKeyword && matchedStatus
  }),
)

const load = async () => {
  const page = await getProjects({ pageNum: 1, pageSize: 60 })
  projects.value = page.records
}

const resetFilters = () => {
  keyword.value = ''
  statusFilter.value = undefined
}

const submit = async () => {
  submitting.value = true
  try {
    await createProject(form)
    ElMessage.success('项目已创建')
    dialogVisible.value = false
    form.name = ''
    form.description = ''
    await load()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '项目创建失败，请稍后重试'))
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.project-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.project-card__head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 14px;
}

.project-card__title {
  font-size: 18px;
  font-weight: 800;
  letter-spacing: -0.03em;
}

.project-card__metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 14px;
}

.project-card__metrics div {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 14px;
  background: var(--panel);
  border: 1px solid var(--line);
}

.project-card__metrics strong {
  font-size: 18px;
  letter-spacing: -0.03em;
}

.project-card__metrics span {
  color: var(--muted);
  font-size: 13px;
}

@media (max-width: 1180px) {
  .project-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {

  .project-grid,
  .project-card__metrics {
    grid-template-columns: 1fr;
  }

  .project-card__head {
    flex-direction: column;
  }
}
</style>
