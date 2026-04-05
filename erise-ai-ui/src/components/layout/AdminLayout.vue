<template>
  <div class="app-shell">
    <aside class="app-sidebar">
      <div class="app-sidebar__brand">
        <span class="app-brand-mark">A</span>
        <div>
          <div class="app-eyebrow">管理后台</div>
          <div class="app-brand-title">运营控制台</div>
          <div class="app-brand-copy">面向用户、任务、模型与审计信号的高密度后台工作区。</div>
        </div>
        <AppStatusTag label="管理模式" tone="primary" />
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
          <div class="app-topbar__copy">{{ pageDescription }}</div>
        </div>

        <div class="app-topbar__actions">
          <el-button plain @click="router.push('/workspace')">返回工作台</el-button>
          <el-button plain @click="router.push('/settings/profile')">个人资料</el-button>
          <!-- <el-button plain @click="themeDrawerVisible = true">主题</el-button> -->
          <el-button type="danger" plain @click="handleLogout">退出登录</el-button>
        </div>
      </header>

      <main class="app-page">
        <router-view />
      </main>
    </section>

    <el-drawer v-model="sidebarVisible" direction="ltr" size="280px" title="后台导航">
      <AppDrawerPanel>
        <el-menu :default-active="activeNavIndex" router class="shell-menu">
          <el-menu-item v-for="item in navItems" :key="item.index" :index="item.index" @click="sidebarVisible = false">
            <el-icon>
              <component :is="item.icon" />
            </el-icon>
            <span>{{ item.label }}</span>
          </el-menu-item>
        </el-menu>
      </AppDrawerPanel>
    </el-drawer>

    <el-drawer v-model="themeDrawerVisible" title="主题" size="420px">
      <AppDrawerPanel>
        <ThemePanel description="后台与主工作台共用同一套主题变量和视觉规则。" />
      </AppDrawerPanel>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Menu } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import AppDrawerPanel from '@/components/common/AppDrawerPanel.vue'
import AppStatusTag from '@/components/common/AppStatusTag.vue'
import ThemePanel from '@/components/common/ThemePanel.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const sidebarVisible = ref(false)
const themeDrawerVisible = ref(false)

const navItems = [
  { index: '/admin', label: '总览', icon: 'DataAnalysis' },
  { index: '/admin/users', label: '用户', icon: 'User' },
  { index: '/admin/tasks', label: '任务', icon: 'List' },
  { index: '/admin/ai-models', label: 'AI 模型', icon: 'Cpu' },
  { index: '/admin/audit-logs', label: '审计日志', icon: 'DocumentCopy' },
]

const activeNavIndex = computed(() => {
  if (route.path.startsWith('/admin/users')) return '/admin/users'
  if (route.path.startsWith('/admin/tasks')) return '/admin/tasks'
  if (route.path.startsWith('/admin/ai-models')) return '/admin/ai-models'
  if (route.path.startsWith('/admin/audit-logs')) return '/admin/audit-logs'
  return '/admin'
})

const pageTitle = computed(() => (route.meta.title as string) || '运营控制台')
const pageDescription = computed(() => (route.meta.description as string) || '追踪平台信号、后台任务与关键控制点。')

const handleLogout = async () => {
  await authStore.logout()
  router.push('/login')
}
</script>
