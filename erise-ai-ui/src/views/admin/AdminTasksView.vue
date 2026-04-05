<template>
  <div class="section-stack">
    <AppSectionCard title="后台任务" description="查看重试次数、错误信息和任务创建时间。" :unpadded="true">
      <AppDataTable :data="tasks" stripe>
        <el-table-column prop="taskType" label="类型" width="160" />
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <AppStatusTag :label="String(row.taskStatus)" :tone="taskTone(row.taskStatus)" />
          </template>
        </el-table-column>
        <el-table-column prop="retryCount" label="重试次数" width="140" />
        <el-table-column prop="lastError" label="最后错误" min-width="260" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" min-width="180" />
      </AppDataTable>
    </AppSectionCard>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getAdminTasks, type AdminTaskView } from '@/api/admin'
import AppDataTable from '@/components/common/AppDataTable.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'
import AppStatusTag from '@/components/common/AppStatusTag.vue'

const tasks = ref<AdminTaskView[]>([])

const taskTone = (status: number) => {
  if (status >= 3) return 'danger'
  if (status === 2) return 'warning'
  if (status === 1) return 'success'
  return 'info'
}

onMounted(async () => {
  const page = await getAdminTasks({ pageNum: 1, pageSize: 50 })
  tasks.value = page.records
})
</script>