<template>
  <el-card class="glass-card" shadow="never">
    <template #header>用户管理</template>
    <el-table :data="users" stripe>
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="displayName" label="显示名称" />
      <el-table-column prop="roleCode" label="角色" width="120" />
      <el-table-column prop="status" label="状态" width="120" />
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button size="small" @click="toggle(row)">{{ row.enabled ? '禁用' : '启用' }}</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAdminUsers, updateAdminUserStatus, type AdminUserView } from '@/api/admin'

const users = ref<AdminUserView[]>([])

const load = async () => {
  const page = await getAdminUsers({ pageNum: 1, pageSize: 50 })
  users.value = page.records
}

const toggle = async (user: AdminUserView) => {
  await updateAdminUserStatus(user.id, user.enabled ? 'DISABLED' : 'ACTIVE')
  ElMessage.success('状态已更新')
  load()
}

onMounted(load)
</script>
