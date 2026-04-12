<template>
  <div class="app-shell">
    <aside class="app-sidebar">
      <div class="app-sidebar__brand">
        <div class="admin-brand-mark">
          <span class="material-symbols-outlined">auto_awesome</span>
        </div>

        <div>
          <div class="app-brand-title">管理员控制台</div>
        </div>
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
          <NotificationCenterDrawer admin-mode show-label button-label="通知" />
          <el-button plain @click="router.push('/admin/profile')">个人资料</el-button>
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
import { Cpu, DataAnalysis, DocumentCopy, Files, Menu, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import AppDrawerPanel from '@/components/common/AppDrawerPanel.vue'
import NotificationCenterDrawer from '@/components/common/NotificationCenterDrawer.vue'
import ThemePanel from '@/components/common/ThemePanel.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const sidebarVisible = ref(false)
const themeDrawerVisible = ref(false)

const navItems = [
  { index: '/admin', label: '仪表盘', icon: DataAnalysis },
  { index: '/admin/users', label: '用户管理', icon: User },
  { index: '/admin/project-files', label: '项目文件管理', icon: Files },
  { index: '/admin/models', label: '模型管理', icon: Cpu },
  { index: '/admin/logs', label: '日志管理', icon: DocumentCopy },
]

const activeNavIndex = computed(() => {
  if (route.path.startsWith('/admin/users')) return '/admin/users'
  if (
    route.path.startsWith('/admin/project-files') ||
    route.path.startsWith('/admin/files') ||
    route.path.startsWith('/admin/documents') ||
    route.path.startsWith('/admin/contents')
  ) return '/admin/project-files'
  if (route.path.startsWith('/admin/models') || route.path.startsWith('/admin/ai-models')) return '/admin/models'
  if (route.path.startsWith('/admin/logs') || route.path.startsWith('/admin/audit-logs')) return '/admin/logs'
  return '/admin'
})

const pageTitle = computed(() => (route.meta.title as string) || '运营控制台')
const pageDescription = computed(() => (route.meta.description as string) || '')

const handleLogout = async () => {
  await authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.admin-brand-mark {
  width: 42px;
  height: 42px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 16px;
  color: #ffffff;
  background: linear-gradient(135deg, #409eff 0%, #0060a9 100%);
  box-shadow: 0 14px 30px rgba(64, 158, 255, 0.28);
}
</style>
