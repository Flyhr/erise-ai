<template>
  <div class="section-stack admin-users-page">
    <section class="admin-users-toolbar">
      <div class="admin-users-toolbar__search">
        <el-input
          v-model="keyword"
          clearable
          placeholder="搜索用户名、显示名称或邮箱"
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <span class="material-symbols-outlined">search</span>
          </template>
        </el-input>
      </div>

      <el-button type="primary" class="admin-users-toolbar__button" @click="handleSearch">搜索</el-button>
    </section>

    <section class="admin-users-toolbar admin-users-toolbar--subnav">
      <ProjectSubnav
        :project-id="0"
        mode="value"
        :model-value="roleFilter"
        :items="roleItems"
        @update:modelValue="handleRoleChange"
      />
    </section>

    <AppSectionCard title="用户管理" description="集中管理用户状态、角色和账号启停。" :unpadded="true">
      <AppDataTable :data="users" stripe>
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="displayName" label="显示名称" min-width="160" />
        <el-table-column prop="email" label="邮箱" min-width="220" />
        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            <AppStatusTag :label="roleLabel(row.roleCode)" :tone="row.roleCode === 'ADMIN' ? 'primary' : 'info'" />
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
            <el-button size="small" :disabled="row.roleCode === 'ADMIN'" @click="toggle(row)">
              {{ row.roleCode === 'ADMIN' ? '不可禁用' : row.enabled ? '禁用' : '启用' }}
            </el-button>
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
import ProjectSubnav from '@/components/common/ProjectSubnav.vue'
import { resolveErrorMessage } from '@/utils/formatters'

type AdminRoleFilter = 'ALL' | 'USER' | 'ADMIN'

const users = ref<AdminUserView[]>([])
const keyword = ref('')
const roleFilter = ref<AdminRoleFilter>('ALL')

const roleItems = [
  { key: 'ALL', label: '全部' },
  { key: 'USER', label: '用户' },
  { key: 'ADMIN', label: '管理员' },
]

const roleLabel = (value: string) => (value === 'ADMIN' ? '管理员' : value === 'USER' ? '普通用户' : value)

const load = async () => {
  const page = await getAdminUsers({
    pageNum: 1,
    pageSize: 50,
    q: keyword.value.trim() || undefined,
    roleCode: roleFilter.value === 'ALL' ? undefined : roleFilter.value,
  })
  users.value = page.records
}

const handleSearch = async () => {
  await load()
}

const handleRoleChange = async (value: string) => {
  roleFilter.value = value as AdminRoleFilter
  await load()
}

const toggle = async (user: AdminUserView) => {
  if (user.roleCode === 'ADMIN') {
    return
  }
  try {
    await updateAdminUserStatus(user.id, user.enabled ? 'DISABLED' : 'ACTIVE')
    ElMessage.success('用户状态已更新')
    await load()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '用户状态更新失败，请稍后重试'))
  }
}

onMounted(load)
</script>

<style scoped>
.admin-users-page {
  gap: 18px;
}

.admin-users-toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.admin-users-toolbar--subnav {
  margin-top: -4px;
}

.admin-users-toolbar__search {
  flex: 1;
  min-width: min(100%, 320px);
  max-width: 520px;
}

.admin-users-toolbar__search :deep(.el-input__wrapper) {
  min-height: 46px;
  border-radius: 14px;
  background: #e0e2e9;
  box-shadow: none;
}

.admin-users-toolbar__search :deep(.el-input__wrapper.is-focus) {
  background: #ffffff;
  box-shadow: 0 0 0 2px rgba(0, 96, 169, 0.12);
}

.admin-users-toolbar__search :deep(.el-input__prefix-inner) {
  color: #5f6775;
}

.admin-users-toolbar__button {
  min-height: 46px;
  padding: 0 18px;
  border-radius: 14px;
  font-weight: 800;
  box-shadow: 0 12px 24px rgba(0, 96, 169, 0.16);
}

@media (max-width: 768px) {
  .admin-users-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .admin-users-toolbar__search {
    max-width: none;
  }
}
</style>
