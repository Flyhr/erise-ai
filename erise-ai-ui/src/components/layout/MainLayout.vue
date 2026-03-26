<template>
  <div class="shell">
    <div class="glass-card shell-frame">
      <aside class="shell-aside">
        <div class="shell-brand">
          <div class="shell-brand__eyebrow">ERISE-AI</div>
          <div class="shell-brand__title">知识工作台</div>
        </div>
        <el-menu :default-active="$route.path" router class="shell-menu">
          <el-menu-item index="/workspace">工作台</el-menu-item>
          <el-menu-item index="/projects">项目</el-menu-item>
          <el-menu-item index="/ai">AI 助手</el-menu-item>
          <el-menu-item index="/settings/profile">个人设置</el-menu-item>
        </el-menu>
      </aside>
      <section class="shell-main">
        <header class="shell-header">
          <div class="shell-user">
            <div class="shell-user__label">当前用户</div>
            <div class="shell-user__name">{{ authStore.user?.displayName || authStore.user?.username }}</div>
          </div>

          <div class="shell-search">
            <el-input
              v-model="searchKeyword"
              clearable
              placeholder="搜索项目中的文档、文件与知识内容"
              @keyup.enter="runSearch"
            />
            <el-button type="primary" @click="runSearch">搜索</el-button>
          </div>

          <div class="shell-actions">
            <el-button v-if="authStore.isAdmin" plain @click="$router.push('/admin')">管理台</el-button>
            <el-button type="danger" plain @click="handleLogout">退出登录</el-button>
          </div>
        </header>
        <main class="shell-content">
          <router-view />
        </main>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const searchKeyword = ref('')

watch(
  () => route.query.q,
  (value) => {
    searchKeyword.value = typeof value === 'string' ? value : ''
  },
  { immediate: true },
)

const runSearch = () => {
  const keyword = searchKeyword.value.trim()
  router.push({
    path: '/search',
    query: keyword ? { q: keyword } : {},
  })
}

const handleLogout = async () => {
  await authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.shell-frame {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  overflow: hidden;
}

.shell-aside {
  padding: 24px;
  border-right: 1px solid var(--line);
  background: rgba(22, 101, 52, 0.04);
}

.shell-brand {
  margin-bottom: 28px;
}

.shell-brand__eyebrow {
  font-size: 13px;
  letter-spacing: 0.2em;
  color: var(--muted);
}

.shell-brand__title {
  font-size: 28px;
  font-weight: 700;
  margin-top: 6px;
}

.shell-menu {
  border-right: none;
  background: transparent;
}

.shell-main {
  min-width: 0;
}

.shell-header {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 18px 24px;
  border-bottom: 1px solid var(--line);
}

.shell-user__label {
  font-size: 13px;
  color: var(--muted);
}

.shell-user__name {
  font-size: 18px;
  font-weight: 600;
}

.shell-search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
}

.shell-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.shell-content {
  padding: 24px;
}

@media (max-width: 1200px) {
  .shell-header {
    grid-template-columns: 1fr;
  }

  .shell-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 900px) {
  .shell-frame {
    grid-template-columns: 1fr;
  }

  .shell-aside {
    border-right: none;
    border-bottom: 1px solid var(--line);
  }

  .shell-content {
    padding: 16px;
  }
}
</style>
