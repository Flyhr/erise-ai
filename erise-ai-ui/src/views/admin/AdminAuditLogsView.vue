<template>
  <div class="section-stack">
    <AppSectionCard title="审计日志" description="查看关键操作、资源类型和明细字段。" :unpadded="true">
      <AppDataTable :data="logs" stripe>
        <el-table-column prop="operatorUsername" label="操作人" width="140" />
        <el-table-column prop="actionCode" label="动作" min-width="220" />
        <el-table-column prop="resourceType" label="资源类型" width="140" />
        <el-table-column prop="resourceId" label="资源 ID" width="120" />
        <el-table-column prop="detailJson" label="详情" min-width="260" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="时间" min-width="180" />
      </AppDataTable>
    </AppSectionCard>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getAdminAuditLogs, type AdminAuditLogView } from '@/api/admin'
import AppDataTable from '@/components/common/AppDataTable.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'

const logs = ref<AdminAuditLogView[]>([])

onMounted(async () => {
  const page = await getAdminAuditLogs({ pageNum: 1, pageSize: 50 })
  logs.value = page.records
})
</script>