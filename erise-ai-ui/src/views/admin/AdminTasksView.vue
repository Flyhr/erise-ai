<template>
  <div class="section-stack">
    <AppSectionCard
      title="后台任务"
      description="统一查看文件解析、知识索引和临时附件处理任务，并支持失败后直接重试。"
      :unpadded="true"
    >
      <AppDataTable :data="tasks" stripe>
        <el-table-column label="来源" width="140">
          <template #default="{ row }">
            <AppStatusTag :label="originLabel(row.taskOrigin)" :tone="originTone(row.taskOrigin)" />
          </template>
        </el-table-column>
        <el-table-column label="任务类型" min-width="150">
          <template #default="{ row }">
            {{ taskTypeLabel(row) }}
          </template>
        </el-table-column>
        <el-table-column label="对象" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            {{ resourceLabel(row) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <AppStatusTag :label="statusLabel(row.taskStatus)" :tone="taskTone(row.taskStatus)" />
          </template>
        </el-table-column>
        <el-table-column prop="retryCount" label="重试次数" width="120" />
        <el-table-column label="最后错误" min-width="280" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.lastError || '无' }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="180" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.retryable"
              size="small"
              type="primary"
              text
              @click="handleRetry(row)"
            >
              重试
            </el-button>
            <span v-else class="task-action-muted">-</span>
          </template>
        </el-table-column>
      </AppDataTable>
    </AppSectionCard>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAdminTasks, retryAdminTask, type AdminTaskView } from '@/api/admin'
import AppDataTable from '@/components/common/AppDataTable.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'
import AppStatusTag from '@/components/common/AppStatusTag.vue'

const tasks = ref<AdminTaskView[]>([])

const load = async () => {
  const page = await getAdminTasks({ pageNum: 1, pageSize: 100 })
  tasks.value = page.records
}

const handleRetry = async (task: AdminTaskView) => {
  await retryAdminTask(task.taskOrigin, task.id)
  ElMessage.success('任务已重新触发')
  await load()
}

const originLabel = (origin: AdminTaskView['taskOrigin']) => {
  if (origin === 'FILE_PARSE') return '文件解析'
  if (origin === 'TEMP_FILE_PARSE') return '临时附件'
  return '知识任务'
}

const originTone = (origin: AdminTaskView['taskOrigin']) => {
  if (origin === 'FILE_PARSE') return 'primary'
  if (origin === 'TEMP_FILE_PARSE') return 'warning'
  return 'info'
}

const taskTypeLabel = (task: AdminTaskView) => {
  if (task.taskOrigin === 'FILE_PARSE') return '文件解析'
  if (task.taskOrigin === 'TEMP_FILE_PARSE') return '临时附件解析'
  if (task.taskType === 'INDEX') return '知识索引'
  if (task.taskType === 'DELETE') return '知识删除'
  return task.taskType
}

const resourceLabel = (task: AdminTaskView) => {
  const title = task.resourceTitle?.trim()
  if (title) return title
  if (task.resourceType && task.resourceId) return `${task.resourceType} #${task.resourceId}`
  return '-'
}

const statusLabel = (status: string) => {
  const normalized = status.toUpperCase()
  if (normalized === 'SUCCESS' || normalized === 'READY' || normalized === 'INDEXED') return '成功'
  if (normalized === 'PROCESSING') return '处理中'
  if (normalized === 'PENDING') return '等待中'
  if (normalized === 'FAILED') return '失败'
  if (normalized === 'NEEDS_REPAIR') return '待修复'
  return status
}

const taskTone = (status: string) => {
  const normalized = status.toUpperCase()
  if (normalized === 'SUCCESS' || normalized === 'READY' || normalized === 'INDEXED') return 'success'
  if (normalized === 'PROCESSING') return 'primary'
  if (normalized === 'PENDING') return 'warning'
  if (normalized === 'FAILED' || normalized === 'NEEDS_REPAIR') return 'danger'
  return 'info'
}

onMounted(load)
</script>

<style scoped>
.task-action-muted {
  color: var(--el-text-color-placeholder);
}
</style>
