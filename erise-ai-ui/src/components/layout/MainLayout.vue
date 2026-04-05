<template>
  <div class="app-shell">
    <aside class="app-sidebar">
      <div class="app-sidebar__brand">
        <span class="app-brand-mark">E</span>
        <!-- <div> -->
        <!-- <div class="app-eyebrow">Erise 知识中枢</div> -->
        <div class="app-brand-title">Erise知识库</div>
        <!-- <div class="app-brand-copy">项目、文件、文档、搜索与 AI 协作的统一入口。</div> -->
        <!-- </div> -->
        <AppStatusTag v-if="authStore.isAdmin" label="管理员" tone="primary" />
      </div>

      <el-menu :default-active="activeNavIndex" router class="shell-menu">
        <el-menu-item v-for="item in navItems" :key="item.index" :index="item.index">
          <el-icon>
            <component :is="item.icon" />
          </el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <section class="app-main">
      <header class="app-topbar">
        <el-button class="app-topbar__mobile-toggle" plain @click="sidebarVisible = true">
          <el-icon>
            <Menu />
          </el-icon>
        </el-button>

        <div class="app-topbar__context">
          <div class="app-topbar__title">{{ pageTitle }}</div>
          <!-- <div class="app-topbar__copy">{{ pageDescription }}</div> -->
        </div>

        <div class="app-topbar__search panel-surface">
          <el-input v-model="searchKeyword" clearable placeholder="搜索项目、文档、文件或知识内容" @keyup.enter="runSearch">
            <template #append>
              <el-button @click="runSearch">搜索</el-button>
            </template>
          </el-input>
        </div>

        <div class="app-topbar__actions">
          <el-button v-if="authStore.isAdmin" plain @click="router.push('/admin')">管理后台</el-button>
          <el-button plain @click="themeDrawerVisible = true">主题</el-button>
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="avatar-chip shell-user-trigger">
              <el-avatar :src="authStore.user?.avatarUrl" :size="40">{{ userInitials }}</el-avatar>
              <div class="avatar-chip__meta">
                <span class="avatar-chip__name">{{ authStore.user?.displayName || authStore.user?.username }}</span>
                <span class="avatar-chip__desc">个人资料、主题与账号操作</span>
              </div>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人资料</el-dropdown-item>
                <el-dropdown-item command="theme">主题</el-dropdown-item>
                <el-dropdown-item v-if="authStore.isAdmin" command="admin">管理后台</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="app-page">
        <router-view />
      </main>
    </section>

    <el-drawer v-model="sidebarVisible" direction="ltr" size="280px" title="导航">
      <AppDrawerPanel>
        <div class="section-stack">
          <div>
            <div class="app-eyebrow">导航</div>
            <div class="page-subtitle">主工作台的一级入口。</div>
          </div>
          <el-menu :default-active="activeNavIndex" router class="shell-menu">
            <el-menu-item v-for="item in navItems" :key="item.index" :index="item.index"
              @click="sidebarVisible = false">
              <el-icon>
                <component :is="item.icon" />
              </el-icon>
              <span>{{ item.label }}</span>
            </el-menu-item>
          </el-menu>
        </div>
      </AppDrawerPanel>
    </el-drawer>

    <el-drawer v-model="themeDrawerVisible" title="主题" size="420px">
      <AppDrawerPanel>
        <ThemePanel description="默认主题更克制稳重，其余主题仍然可选。" />
      </AppDrawerPanel>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Menu } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import AppDrawerPanel from '@/components/common/AppDrawerPanel.vue'
import AppStatusTag from '@/components/common/AppStatusTag.vue'
import ThemePanel from '@/components/common/ThemePanel.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const searchKeyword = ref('')
const sidebarVisible = ref(false)
const themeDrawerVisible = ref(false)

const navItems = computed(() => {
  const base = [
    { index: '/workspace', label: '工作台', icon: 'House' },
    { index: '/projects', label: '项目', icon: 'FolderOpened' },
    { index: '/files', label: '文件', icon: 'Files' },
    { index: '/documents', label: '文档', icon: 'Document' },
    { index: '/search', label: '搜索', icon: 'Search' },
    { index: '/ai', label: 'AI 助理', icon: 'ChatLineRound' },
  ]
  if (authStore.isAdmin) {
    base.push({ index: '/admin', label: '管理后台', icon: 'Setting' })
  }
  return base
})

const activeNavIndex = computed(() => {
  if (route.path.startsWith('/admin')) return '/admin'
  if (route.path.startsWith('/projects')) return '/projects'
  if (route.path.startsWith('/files')) return '/files'
  if (route.path.startsWith('/documents')) return '/documents'
  if (route.path.startsWith('/search')) return '/search'
  if (route.path.startsWith('/ai')) return '/ai'
  return '/workspace'
})

const pageTitle = computed(() => (route.meta.title as string) || 'Erise')
// const pageDescription = computed(() => (route.meta.description as string) || '在项目、搜索、文件、文档与 AI 对话之间组织知识工作。')
const userInitials = computed(() => {
  const raw = authStore.user?.displayName || authStore.user?.username || 'U'
  return raw.trim().slice(0, 1).toUpperCase()
})

watch(
  () => route.query.q,
  (value) => {
    searchKeyword.value = typeof value === 'string' ? value : ''
  },
  { immediate: true },
)

const runSearch = () => {
  const keyword = searchKeyword.value.trim()
  router.push({ path: '/search', query: keyword ? { q: keyword } : {} })
}

const handleCommand = async (command: string | number | object) => {
  if (command === 'profile') {
    router.push('/settings/profile')
    return
  }
  if (command === 'theme') {
    themeDrawerVisible.value = true
    return
  }
  if (command === 'admin') {
    router.push('/admin')
    return
  }
  if (command === 'logout') {
    await authStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.shell-user-trigger {
  cursor: pointer;
}
</style>
