<template>
  <header class="workspace-topbar">
    <div class="workspace-search-box">
      <span class="material-symbols-outlined">search</span>
      <input
        v-model="searchKeyword"
        type="text"
        :placeholder="searchPlaceholder"
        @keydown.enter.prevent="handleSearch"
      />
      <button type="button" class="workspace-search-submit" @click="handleSearch">
        搜索
      </button>
    </div>

    <div class="workspace-topbar-actions">
      <button type="button" class="workspace-icon-btn" @click="handleNotify">
        <span class="material-symbols-outlined">notifications</span>
      </button>
      <button type="button" class="workspace-icon-btn" @click="handleSettings">
        <span class="material-symbols-outlined">settings</span>
      </button>

      <el-dropdown trigger="click" placement="bottom-end" @command="handleCommand">
        <button type="button" class="workspace-user-btn">
          <div class="workspace-user-meta">
            <div class="workspace-user-name">{{ displayUserName }}</div>
            <div class="workspace-user-role">{{ displayUserRole }}</div>
          </div>
          <div class="workspace-user-avatar">{{ displayUserAvatar }}</div>
          <span class="material-symbols-outlined workspace-user-arrow">expand_more</span>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">个人资料</el-dropdown-item>
            <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { resolveErrorMessage } from '@/utils/formatters'

interface Props {
  userName?: string
  userRole?: string
  userAvatar?: string
  searchPlaceholder?: string
}

const props = withDefaults(defineProps<Props>(), {
  userName: '',
  userRole: '',
  userAvatar: '',
  searchPlaceholder: '搜索项目、文件和文档',
})

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const searchKeyword = ref(typeof route.query.q === 'string' ? route.query.q : '')
const loggingOut = ref(false)

const displayUserName = computed(
  () => props.userName || authStore.user?.displayName || authStore.user?.username || '个人中心',
)

const displayUserRole = computed(() => {
  if (props.userRole) {
    return props.userRole
  }
  if (authStore.isAdmin) {
    return '管理员账号'
  }
  return '账号设置'
})

const displayUserAvatar = computed(() => {
  if (props.userAvatar) {
    return props.userAvatar
  }
  const raw = authStore.user?.displayName || authStore.user?.username || 'U'
  return raw.trim().slice(0, 1).toUpperCase()
})

watch(
  () => route.query.q,
  (keyword) => {
    searchKeyword.value = typeof keyword === 'string' ? keyword : ''
  },
)

const handleSearch = () => {
  const keyword = searchKeyword.value.trim()
  router.push({
    path: '/search',
    query: keyword ? { q: keyword } : {},
  })
}

const handleNotify = () => {
  ElMessageBox.alert('通知中心正在建设中，后续会补充更多消息入口。', '提示', {
    confirmButtonText: '知道了',
    type: 'info',
  })
}

const handleSettings = () => {
  router.push('/settings/profile')
}

const handleLogout = async () => {
  if (loggingOut.value) {
    return
  }

  await ElMessageBox.confirm('确认退出当前账号吗？退出后需要重新登录。', '退出登录', {
    confirmButtonText: '退出登录',
    cancelButtonText: '取消',
    type: 'warning',
  })

  loggingOut.value = true
  try {
    await authStore.logout()
    ElMessage.success('已退出登录')
  } catch (error) {
    authStore.clear()
    ElMessage.warning(resolveErrorMessage(error, '登录状态已清除，请重新登录'))
  } finally {
    loggingOut.value = false
    router.replace('/login')
  }
}

const handleCommand = async (command: string | number | object) => {
  if (command === 'profile') {
    router.push('/settings/profile')
    return
  }
  if (command === 'logout') {
    try {
      await handleLogout()
    } catch {
      // User canceled the confirmation dialog.
    }
  }
}
</script>

<style scoped>
.workspace-topbar,
.workspace-topbar-actions,
.workspace-user-btn {
  display: flex;
  align-items: center;
}

.workspace-topbar {
  position: sticky;
  top: 0;
  z-index: 6;
  gap: 16px;
  justify-content: space-between;
  min-height: 72px;
  margin-bottom: 18px;
  padding: 14px 18px;
  border-radius: 22px;
  border: 1px solid rgba(216, 224, 237, 0.85);
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(18px);
  box-shadow: 0 14px 32px rgba(15, 23, 42, 0.05);
}

.workspace-search-box {
  min-width: 0;
  flex: 1;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  border-radius: 16px;
  border: 1px solid #d7deeb;
  background: #f9fbff;
}

.workspace-search-box input {
  width: 100%;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  padding: 14px 0;
  font-size: 14px;
}

.workspace-search-submit,
.workspace-icon-btn {
  border: 0;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.22s ease;
}

.workspace-search-submit {
  padding: 10px 14px;
  background: #e9f3ff;
  color: #005b9f;
  font-weight: 700;
}

.workspace-search-submit:hover,
.workspace-icon-btn:hover {
  background: #d9ecff;
}

.workspace-topbar-actions {
  gap: 10px;
}

.workspace-icon-btn {
  width: 40px;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #f5f8fc;
  color: #344054;
}

.workspace-user-btn {
  gap: 12px;
  border: 0;
  padding: 8px 10px;
  border-radius: 16px;
  background: transparent;
  cursor: pointer;
  transition: background 0.22s ease, box-shadow 0.22s ease;
}

.workspace-user-btn:hover {
  background: #f5f8fc;
}

.workspace-user-btn:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.18);
}

.workspace-user-meta {
  text-align: right;
}

.workspace-user-name {
  font-weight: 700;
  color: #111827;
}

.workspace-user-role {
  color: #667085;
  font-size: 13px;
}

.workspace-user-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 16px;
  color: #fff;
  background: linear-gradient(135deg, #409eff 0%, #0060a9 100%);
  font-size: 14px;
  font-weight: 800;
}

.workspace-user-arrow {
  color: #667085;
  font-size: 20px;
}

@media (max-width: 720px) {
  .workspace-topbar {
    flex-direction: column;
    align-items: stretch;
  }

  .workspace-topbar-actions {
    justify-content: flex-end;
  }

  .workspace-user-meta {
    display: none;
  }
}
</style>
