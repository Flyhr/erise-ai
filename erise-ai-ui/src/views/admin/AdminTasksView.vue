<template>
  <el-card class="glass-card" shadow="never">
    <template #header>任务监控</template>
    <el-table :data="tasks" stripe>
      <el-table-column prop="taskType" label="类型" width="140" />
      <el-table-column prop="taskStatus" label="状态" width="140" />
      <el-table-column prop="retryCount" label="重试次数" width="140" />
      <el-table-column prop="lastError" label="最后错误" min-width="220" />
      <el-table-column prop="createdAt" label="创建时间" width="200" />
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getAdminTasks, type AdminTaskView } from '@/api/admin'

const tasks = ref<AdminTaskView[]>([])

onMounted(async () => {
  const page = await getAdminTasks({ pageNum: 1, pageSize: 50 })
  tasks.value = page.records
})
</script>
