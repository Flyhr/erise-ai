<template>
  <div class="section-stack">
    <AppSectionCard title="用户管理" description="集中管理用户状态、角色和账号启停。" :unpadded="true">
      <AppDataTable :data="users" stripe>
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="displayName" label="显示名称" min-width="160" />
        <el-table-column prop="email" label="邮箱" min-width="220" />
        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            <AppStatusTag :label="row.roleCode" :tone="row.roleCode === 'ADMIN' ? 'primary' : 'info'" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <AppStatusTag :label="row.enabled ? '启用' : '停用'" :tone="row.enabled ? 'success' : 'warning'" />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="toggle(row)">{{ row.enabled ? '禁用' : '启用' }}</el-button>
          </template>
        </el-table-column>
      </AppDataTable>
    </AppSectionCard>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAdminUsers, updateAdminUserStatus, type AdminUserView } from '@/api/admin'
import AppDataTable from '@/components/common/AppDataTable.vue'
import AppSectionCard from '@/components/common/AppSectionCard.vue'
import AppStatusTag from '@/components/common/AppStatusTag.vue'

const users = ref<AdminUserView[]>([])

const load = async () => {
  const page = await getAdminUsers({ pageNum: 1, pageSize: 50 })
  users.value = page.records
}

const toggle = async (user: AdminUserView) => {
  await updateAdminUserStatus(user.id, user.enabled ? 'DISABLED' : 'ACTIVE')
  ElMessage.success('用户状态已更新')
  load()
}

onMounted(load)
</script>