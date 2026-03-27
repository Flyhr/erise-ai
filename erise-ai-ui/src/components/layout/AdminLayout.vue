<template>
  <div class="shell">
    <div class="glass-card admin-shell">
      <aside class="admin-aside">
        <div class="admin-brand">
          <div class="admin-brand__eyebrow">ADMIN</div>
          <div class="admin-brand__title">运营控制台</div>
          <div class="admin-brand__copy">集中查看用户、任务、模型配置、访问趋势和 AI 工作链路。</div>
        </div>

        <el-menu :default-active="$route.path" router class="shell-menu">
          <el-menu-item index="/admin">仪表盘</el-menu-item>
          <el-menu-item index="/admin/users">用户管理</el-menu-item>
          <el-menu-item index="/admin/tasks">任务监控</el-menu-item>
          <el-menu-item index="/admin/ai-models">AI 配置</el-menu-item>
          <el-menu-item index="/admin/audit-logs">审计日志</el-menu-item>
        </el-menu>
      </aside>

      <section class="admin-main">
        <header class="admin-header">
          <div class="avatar-chip">
            <el-avatar :src="authStore.user?.avatarUrl" :size="42">{{ userInitials }}</el-avatar>
            <div class="avatar-chip__meta">
              <span class="avatar-chip__name">{{ authStore.user?.displayName || authStore.user?.username }}</span>
              <span class="avatar-chip__desc">管理员控制台</span>
            </div>
          </div>

          <div class="admin-actions">
            <el-button plain @click="$router.push('/workspace')">进入普通工作台</el-button>
            <el-button plain @click="$router.push('/settings/profile')">个人设置</el-button>
            <el-button plain @click="themeDrawerVisible = true">主题外观</el-button>
            <el-button type="danger" plain @click="handleLogout">退出登录</el-button>
          </div>
        </header>

        <main class="shell-content">
          <router-view />
        </main>
      </section>
    </div>

    <el-drawer v-model="themeDrawerVisible" title="主题与外观" size="460px">
      <div class="section-stack">
        <div>
          <div class="theme-section__title">预设主题</div>
          <div class="page-subtitle">后台也支持白天、夜间、护眼和自定义配色。</div>
        </div>

        <div class="theme-grid">
          <button
            v-for="theme in themeOptions"
            :key="theme.name"
            type="button"
            class="theme-card"
            :class="{ 'is-active': theme.name === currentTheme }"
            @click="selectTheme(theme.name)"
          >
            <div class="theme-card__title">{{ theme.label }}</div>
            <div class="theme-card__desc">{{ theme.description }}</div>
            <div class="theme-card__preview">
              <span :style="previewStyle(theme.name, 0)" />
              <span :style="previewStyle(theme.name, 1)" />
              <span :style="previewStyle(theme.name, 2)" />
            </div>
          </button>
        </div>

        <el-divider />

        <div class="theme-custom-grid">
          <div class="theme-color-field">
            <span>主色</span>
            <el-color-picker v-model="customTheme.accent" />
          </div>
          <div class="theme-color-field">
            <span>页面背景</span>
            <el-color-picker v-model="customTheme.canvas" />
          </div>
          <div class="theme-color-field">
            <span>卡片背景</span>
            <el-color-picker v-model="customTheme.surface" />
          </div>
        </div>

        <div class="table-actions">
          <el-button @click="resetCustomTheme">重置默认</el-button>
          <el-button type="primary" @click="applyCustomTheme">应用自定义主题</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import {
  applyTheme,
  defaultCustomThemeColors,
  getCustomThemeColors,
  getStoredTheme,
  getThemePreview,
  themeOptions,
  updateCustomThemeColors,
  type ThemeName,
} from '@/theme'

const router = useRouter()
const authStore = useAuthStore()
const themeDrawerVisible = ref(false)
const currentTheme = ref<ThemeName>(getStoredTheme())
const customTheme = reactive(getCustomThemeColors())

const userInitials = computed(() => {
  const raw = authStore.user?.displayName || authStore.user?.username || 'A'
  return raw.trim().slice(0, 1).toUpperCase()
})

const previewStyle = (themeName: ThemeName, index: number) => ({
  background: getThemePreview(themeName, { ...customTheme })[index],
})

const selectTheme = (themeName: ThemeName) => {
  if (themeName === 'custom') {
    applyCustomTheme()
    return
  }
  currentTheme.value = themeName
  applyTheme(themeName)
}

const applyCustomTheme = () => {
  Object.assign(customTheme, updateCustomThemeColors({ ...customTheme }))
  currentTheme.value = 'custom'
  applyTheme('custom')
}

const resetCustomTheme = () => {
  Object.assign(customTheme, defaultCustomThemeColors)
}

const handleLogout = async () => {
  await authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.admin-shell {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  overflow: hidden;
}

.admin-aside {
  padding: 28px 24px;
  border-right: 1px solid var(--line);
  background: linear-gradient(180deg, var(--surface-strong), var(--panel));
}

.admin-brand__eyebrow,
.theme-section__title {
  font-size: 12px;
  letter-spacing: 0.24em;
  color: var(--muted);
  text-transform: uppercase;
}

.admin-brand__title {
  margin-top: 8px;
  font-size: 30px;
  font-weight: 800;
  letter-spacing: -0.04em;
}

.admin-brand__copy {
  margin-top: 10px;
  color: var(--muted);
  line-height: 1.7;
}

.admin-main {
  min-width: 0;
}

.admin-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 18px 24px;
  border-bottom: 1px solid var(--line);
  background: var(--header);
}

.admin-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.theme-custom-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.theme-color-field {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  border-radius: 18px;
  border: 1px solid var(--line);
  background: var(--surface-strong);
}

@media (max-width: 1100px) {
  .admin-shell {
    grid-template-columns: 1fr;
  }

  .admin-aside {
    border-right: none;
    border-bottom: 1px solid var(--line);
  }

  .admin-header {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 900px) {
  .theme-custom-grid {
    grid-template-columns: 1fr;
  }
}
</style>