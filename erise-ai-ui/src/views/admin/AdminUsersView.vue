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
          <template #suffix>
            <SearchSuffixButton @click="handleSearch" />
          </template>
        </el-input>
      </div>
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

    <section class="admin-users-table-shell">
      <div v-if="listLoading && !users.length" class="admin-users-table-shell__state">
        <el-skeleton animated>
          <template #template>
            <el-skeleton-item variant="rect" style="width: 100%; height: 56px; border-radius: 18px;" />
            <el-skeleton-item
              variant="rect"
              style="width: 100%; height: 72px; margin-top: 14px; border-radius: 16px;"
            />
            <el-skeleton-item
              variant="rect"
              style="width: 100%; height: 72px; margin-top: 12px; border-radius: 16px;"
            />
            <el-skeleton-item
              variant="rect"
              style="width: 100%; height: 72px; margin-top: 12px; border-radius: 16px;"
            />
          </template>
        </el-skeleton>
      </div>

      <div v-else-if="loadError && !users.length" class="admin-users-table-shell__state">
        <el-result icon="warning" title="用户列表加载失败" :sub-title="loadError">
          <template #extra>
            <el-button type="primary" @click="loadUsers">重试加载</el-button>
          </template>
        </el-result>
      </div>

      <template v-else>
        <div v-if="listLoading && users.length" class="admin-users-table-shell__refreshing">
          <span class="material-symbols-outlined admin-users-table-shell__refreshing-icon">progress_activity</span>
          <span>正在刷新用户列表...</span>
        </div>

        <div v-if="users.length" class="admin-users-table">
          <table>
            <thead>
              <tr>
                <th>用户信息</th>
                <th>角色</th>
                <th>状态</th>
                <th>邮箱</th>
                <th>创建时间</th>
                <th class="admin-users-table__actions-head">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in users" :key="row.id">
                <td>
                  <div class="admin-users-table__identity">
                    <div class="admin-users-table__avatar" :class="{ 'is-admin': row.roleCode === 'ADMIN' }">
                      {{ userInitial(row) }}
                    </div>
                    <div class="admin-users-table__copy">
                      <strong>{{ row.displayName || row.username }}</strong>
                      <small>@{{ row.username }}</small>
                    </div>
                  </div>
                </td>
                <td>
                  <span class="admin-users-table__badge" :class="row.roleCode === 'ADMIN' ? 'is-primary' : 'is-info'">
                    {{ roleLabel(row.roleCode) }}
                  </span>
                </td>
                <td>
                  <span class="admin-users-table__badge" :class="row.enabled ? 'is-success' : 'is-warning'">
                    {{ row.enabled ? '启用' : '停用' }}
                  </span>
                </td>
                <td class="admin-users-table__email">{{ row.email || '--' }}</td>
                <td>{{ formatDateTime(row.createdAt) }}</td>
                <td class="admin-users-table__actions-cell">
                  <el-dropdown trigger="click" @command="handleRowCommand(row, $event)">
                    <button type="button" class="admin-users-table__menu-trigger">
                      <span>···</span>
                    </button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="edit">编辑</el-dropdown-item>
                        <el-dropdown-item :disabled="!canToggleUser(row)" command="toggle">
                          {{ row.enabled ? '禁用' : '启用' }}
                        </el-dropdown-item>
                        <el-dropdown-item :disabled="!canDeleteUser(row)" command="delete">
                          删除
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <el-empty v-else :image-size="84" description="当前筛选下没有用户，可调整条件后重试。" />
      </template>

      <div v-if="!loadError" class="admin-users-table-shell__footer">
        <span class="admin-users-table-shell__count">共 {{ total }} 位用户</span>
        <CompactPager variant="project" :page-num="pageNum" :page-size="pageSize" :total="total" @change="handlePageChange" />
      </div>
    </section>

    <el-dialog
      v-model="editDialogVisible"
      title="编辑用户信息"
      width="520px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-position="top">
        <el-form-item label="用户名" prop="username">
          <el-input v-model.trim="editForm.username" maxlength="64" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="显示名称" prop="displayName">
          <el-input v-model.trim="editForm.displayName" maxlength="128" placeholder="请输入显示名称" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model.trim="editForm.email" maxlength="128" placeholder="请输入邮箱地址" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitEdit">保存修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  deleteAdminUser,
  getAdminUsers,
  updateAdminUser,
  updateAdminUserStatus,
  type AdminUserView,
} from '@/api/admin'
import CompactPager from '@/components/common/CompactPager.vue'
import ProjectSubnav from '@/components/common/ProjectSubnav.vue'
import SearchSuffixButton from '@/components/common/SearchSuffixButton.vue'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime, resolveErrorMessage } from '@/utils/formatters'

type AdminRoleFilter = 'ALL' | 'USER' | 'ADMIN'

const authStore = useAuthStore()
const users = ref<AdminUserView[]>([])
const keyword = ref('')
const roleFilter = ref<AdminRoleFilter>('ALL')
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const listLoading = ref(false)
const loadError = ref('')
const editDialogVisible = ref(false)
const saving = ref(false)
const editingUserId = ref<number>()
const editFormRef = ref<FormInstance>()
const editForm = reactive({
  username: '',
  displayName: '',
  email: '',
})

const currentUserId = computed(() => authStore.user?.id)

const roleItems = [
  { key: 'ALL', label: '全部' },
  { key: 'USER', label: '用户' },
  { key: 'ADMIN', label: '管理员' },
]

const editRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        const normalized = (value || '').trim()
        if (!normalized) {
          callback(new Error('请输入用户名'))
          return
        }
        if (normalized.includes(' ')) {
          callback(new Error('用户名不能包含空格'))
          return
        }
        callback()
      },
      trigger: ['blur', 'change'],
    },
  ],
  displayName: [{ required: true, message: '请输入显示名称', trigger: 'blur' }],
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: ['blur', 'change'] },
  ],
}

const roleLabel = (value: string) => (value === 'ADMIN' ? '管理员' : value === 'USER' ? '普通用户' : value)
const canToggleUser = (user: AdminUserView) => user.roleCode !== 'ADMIN' && user.id !== currentUserId.value
const canDeleteUser = (user: AdminUserView) => user.roleCode !== 'ADMIN' && user.id !== currentUserId.value
const userInitial = (user: AdminUserView) => (user.displayName || user.username || 'U').trim().slice(0, 1).toUpperCase()

const loadUsers = async () => {
  listLoading.value = true
  loadError.value = ''
  try {
    const page = await getAdminUsers({
      pageNum: pageNum.value,
      pageSize,
      q: keyword.value.trim() || undefined,
      roleCode: roleFilter.value === 'ALL' ? undefined : roleFilter.value,
    })
    users.value = page.records
    total.value = page.total
  } catch (error) {
    loadError.value = resolveErrorMessage(error, '用户列表加载失败，请稍后重试')
  } finally {
    listLoading.value = false
  }
}

const handleSearch = async () => {
  pageNum.value = 1
  await loadUsers()
}

const handleRoleChange = async (value: string) => {
  roleFilter.value = value as AdminRoleFilter
  pageNum.value = 1
  await loadUsers()
}

const handlePageChange = async (value: number) => {
  pageNum.value = value
  await loadUsers()
}

const openEditDialog = (user: AdminUserView) => {
  editingUserId.value = user.id
  editForm.username = user.username || ''
  editForm.displayName = user.displayName || ''
  editForm.email = user.email || ''
  editDialogVisible.value = true
  setTimeout(() => editFormRef.value?.clearValidate(), 0)
}

const submitEdit = async () => {
  const valid = await editFormRef.value?.validate().catch(() => false)
  if (!valid || !editingUserId.value) {
    return
  }
  saving.value = true
  try {
    await updateAdminUser(editingUserId.value, {
      username: editForm.username.trim(),
      displayName: editForm.displayName.trim(),
      email: editForm.email.trim(),
    })
    ElMessage.success('用户信息已更新')
    editDialogVisible.value = false
    await loadUsers()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '用户信息更新失败，请稍后重试'))
  } finally {
    saving.value = false
  }
}

const toggleUserStatus = async (user: AdminUserView) => {
  if (!canToggleUser(user)) return
  const nextStatus = user.enabled ? 'DISABLED' : 'ACTIVE'
  const nextLabel = user.enabled ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(`确认${nextLabel}用户“${user.displayName || user.username}”吗？`, `${nextLabel}用户`, {
      confirmButtonText: `确认${nextLabel}`,
      cancelButtonText: '取消',
      type: user.enabled ? 'warning' : 'info',
    })
    await updateAdminUserStatus(user.id, nextStatus)
    ElMessage.success(`用户已${nextLabel}`)
    await loadUsers()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(resolveErrorMessage(error, '用户状态更新失败，请稍后重试'))
    }
  }
}

const removeUser = async (user: AdminUserView) => {
  if (!canDeleteUser(user)) return
  try {
    await ElMessageBox.confirm(
      `确认删除用户“${user.displayName || user.username}”吗？删除后不可恢复。`,
      '删除用户',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger',
      },
    )
    await deleteAdminUser(user.id)
    ElMessage.success('用户已删除')
    if (users.value.length === 1 && pageNum.value > 1) {
      pageNum.value -= 1
    }
    await loadUsers()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(resolveErrorMessage(error, '用户删除失败，请稍后重试'))
    }
  }
}

const handleRowCommand = async (user: AdminUserView, command: string | number | object) => {
  switch (String(command)) {
    case 'edit':
      openEditDialog(user)
      break
    case 'toggle':
      await toggleUserStatus(user)
      break
    case 'delete':
      await removeUser(user)
      break
    default:
      break
  }
}

onMounted(loadUsers)
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

.admin-users-table-shell {
  overflow: hidden;
  border-radius: 20px;
  background: #ffffff;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.06);
  border: 1px solid rgba(192, 199, 212, 0.24);
}

.admin-users-table-shell__state {
  padding: 24px;
}

.admin-users-table-shell__refreshing {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 14px 24px 0;
  color: #667085;
  font-size: 13px;
  font-weight: 600;
}

.admin-users-table-shell__refreshing-icon {
  font-size: 18px;
  animation: admin-users-spin 1s linear infinite;
}

.admin-users-table {
  overflow-x: auto;
}

.admin-users-table table {
  width: 100%;
  border-collapse: collapse;
}

.admin-users-table thead th {
  padding: 16px 24px;
  background: #f1f3fa;
  color: #5f6775;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  text-align: left;
}

.admin-users-table__actions-head {
  width: 80px;
  text-align: right;
}

.admin-users-table tbody td {
  padding: 18px 24px;
  border-top: 1px solid rgba(192, 199, 212, 0.16);
  color: #48505e;
  font-size: 14px;
}

.admin-users-table tbody tr:hover {
  background: #f8f9ff;
}

.admin-users-table__identity {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 240px;
}

.admin-users-table__avatar {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  background: rgba(64, 158, 255, 0.14);
  color: #0060a9;
  font-size: 14px;
  font-weight: 800;
  flex-shrink: 0;
}

.admin-users-table__avatar.is-admin {
  background: rgba(83, 109, 254, 0.16);
  color: #3747c8;
}

.admin-users-table__copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.admin-users-table__copy strong {
  color: #181c20;
  font-size: 15px;
  font-weight: 700;
}

.admin-users-table__copy small {
  color: #7a8392;
  font-size: 12px;
}

.admin-users-table__email {
  color: #626b77;
}

.admin-users-table__badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
}

.admin-users-table__badge.is-primary {
  background: rgba(83, 109, 254, 0.14);
  color: #3646c8;
}

.admin-users-table__badge.is-info {
  background: rgba(225, 226, 231, 0.95);
  color: #4b5563;
}

.admin-users-table__badge.is-success {
  background: rgba(85, 175, 40, 0.18);
  color: #206100;
}

.admin-users-table__badge.is-warning {
  background: rgba(255, 171, 0, 0.16);
  color: #9a5600;
}

.admin-users-table__actions-cell {
  text-align: right;
}

.admin-users-table__menu-trigger {
  min-width: 40px;
  height: 34px;
  padding: 0 10px;
  border: 1px solid rgba(192, 199, 212, 0.3);
  border-radius: 10px;
  background: #ffffff;
  color: #475467;
  font-size: 16px;
  font-weight: 800;
  cursor: pointer;
  transition: color 0.18s ease, border-color 0.18s ease, background-color 0.18s ease;
}

.admin-users-table__menu-trigger:hover {
  color: #0060a9;
  border-color: rgba(0, 96, 169, 0.24);
  background: #f8f9ff;
}

.admin-users-table-shell__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid rgba(192, 199, 212, 0.16);
  background: rgba(241, 243, 250, 0.4);
  flex-wrap: wrap;
}

.admin-users-table-shell__count {
  color: #667085;
  font-size: 13px;
  font-weight: 600;
}

@keyframes admin-users-spin {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1100px) {
  .admin-users-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .admin-users-toolbar__search {
    max-width: none;
  }
}

@media (max-width: 768px) {
  .admin-users-table thead th,
  .admin-users-table tbody td,
  .admin-users-table-shell__footer {
    padding-left: 16px;
    padding-right: 16px;
  }
}
</style>
