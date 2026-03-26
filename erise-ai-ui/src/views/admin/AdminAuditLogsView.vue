<template>
  <el-card class="glass-card" shadow="never">
    <template #header>审计日志</template>
    <el-table :data="logs" stripe>
      <el-table-column prop="operatorUsername" label="操作人" width="140" />
      <el-table-column prop="actionCode" label="动作" width="220" />
      <el-table-column prop="resourceType" label="资源类型" width="140" />
      <el-table-column prop="detailJson" label="详情" min-width="260" />
      <el-table-column prop="createdAt" label="时间" width="200" />
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getAdminAuditLogs, type AdminAuditLogView } from '@/api/admin'

const logs = ref<AdminAuditLogView[]>([])

onMounted(async () => {
  const page = await getAdminAuditLogs({ pageNum: 1, pageSize: 50 })
  logs.value = page.records
})
</script>
