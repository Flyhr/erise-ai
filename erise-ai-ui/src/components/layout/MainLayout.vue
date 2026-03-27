<template>
  <div class="shell">
    <div class="glass-card shell-frame">
      <aside class="shell-aside">
        <div class="shell-brand">
          <div class="shell-brand__eyebrow">ERISE-AI</div>
          <div class="shell-brand__title">智能知识工作台</div>
          <div class="shell-brand__copy">
            项目、文档、文件和 AI 助手在同一个工作界面里协同运行。
          </div>
          <div v-if="authStore.isAdmin" class="shell-brand__pill">管理员模式</div>
        </div>

        <el-menu :default-active="$route.path" router class="shell-menu">
          <el-menu-item index="/workspace">工作台</el-menu-item>
          <el-menu-item index="/projects">项目</el-menu-item>
          <el-menu-item index="/ai">AI 助手</el-menu-item>
        </el-menu>
      </aside>

      <section class="shell-main">
        <header class="shell-header">
          <div class="shell-search panel-surface">
            <el-input
              v-model="searchKeyword"
              clearable
              placeholder="搜索项目、文档、文件或内容"
              @keyup.enter="runSearch"
            >
              <template #append>
                <el-button @click="runSearch">搜索</el-button>
              </template>
            </el-input>
          </div>

          <div class="shell-actions">
            <el-button v-if="authStore.isAdmin" plain @click="$router.push('/admin')">管理后台</el-button>
            <el-dropdown trigger="click" @command="handleCommand">
              <div class="avatar-chip shell-user-trigger">
                <el-avatar :src="authStore.user?.avatarUrl" :size="42">{{ userInitials }}</el-avatar>
                <div class="avatar-chip__meta">
                  <span class="avatar-chip__name">{{ authStore.user?.displayName || authStore.user?.username }}</span>
                  <span class="avatar-chip__desc">个人设置、主题外观与会话入口</span>
                </div>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">个人设置</el-dropdown-item>
                  <el-dropdown-item command="theme">主题外观</el-dropdown-item>
                  <el-dropdown-item v-if="authStore.isAdmin" command="admin">管理后台</el-dropdown-item>
                  <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </header>

        <main class="shell-content">
          <router-view />
        </main>
      </section>
    </div>

    <el-drawer v-model="themeDrawerVisible" title="主题外观" size="460px">
      <div class="section-stack">
        <div>
          <div class="theme-section__title">预设主题</div>
          <div class="page-subtitle">选择白天、夜间、护眼或自定义配色，界面会立即生效。</div>
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

        <div class="section-stack">
          <div>
            <div class="theme-section__title">自定义配色</div>
            <div class="page-subtitle">自定义强调色、页面底色和卡片底色。</div>
          </div>

          <div class="theme-custom-grid">
            <div class="theme-color-field">
              <span>强调色</span>
              <el-color-picker v-model="customTheme.accent" />
            </div>
            <div class="theme-color-field">
              <span>页面底色</span>
              <el-color-picker v-model="customTheme.canvas" />
            </div>
            <div class="theme-color-field">
              <span>卡片底色</span>
              <el-color-picker v-model="customTheme.surface" />
            </div>
          </div>

          <div class="theme-custom-preview panel-surface">
            <div class="theme-custom-preview__title">预览</div>
            <div class="theme-custom-preview__swatches">
              <span :style="{ background: customTheme.accent }" />
              <span :style="{ background: customTheme.surface }" />
              <span :style="{ background: customTheme.canvas }" />
            </div>
          </div>

          <div class="table-actions">
            <el-button @click="resetCustomTheme">重置</el-button>
            <el-button type="primary" @click="applyCustomTheme">应用自定义主题</el-button>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const searchKeyword = ref('')
const themeDrawerVisible = ref(false)
const currentTheme = ref<ThemeName>(getStoredTheme())
const customTheme = reactive(getCustomThemeColors())

watch(
  () => route.query.q,
  (value) => {
    searchKeyword.value = typeof value === 'string' ? value : ''
  },
  { immediate: true },
)

const userInitials = computed(() => {
  const raw = authStore.user?.displayName || authStore.user?.username || 'U'
  return raw.trim().slice(0, 1).toUpperCase()
})

const previewStyle = (themeName: ThemeName, index: number) => ({
  background: getThemePreview(themeName, { ...customTheme })[index],
})

const runSearch = () => {
  const keyword = searchKeyword.value.trim()
  router.push({
    path: '/search',
    query: keyword ? { q: keyword } : {},
  })
}

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
.shell-frame {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  min-height: calc(100vh - 48px);
  overflow: hidden;
}

.shell-aside {
  padding: 28px 24px;
  border-right: 1px solid var(--line);
  background: linear-gradient(180deg, var(--surface-strong), var(--panel));
}

.shell-brand {
  margin-bottom: 30px;
}

.shell-brand__eyebrow,
.theme-section__title {
  font-size: 12px;
  letter-spacing: 0.2em;
  color: var(--muted);
  text-transform: uppercase;
}

.shell-brand__title {
  margin-top: 8px;
  font-size: 30px;
  font-weight: 800;
  letter-spacing: -0.04em;
}

.shell-brand__copy {
  margin-top: 10px;
  color: var(--muted);
  line-height: 1.7;
}

.shell-brand__pill {
  display: inline-flex;
  margin-top: 14px;
  padding: 8px 12px;
  border-radius: 999px;
  background: var(--panel);
  color: var(--accent);
  font-size: 12px;
  font-weight: 700;
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
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 18px;
  align-items: center;
  padding: 18px 24px;
  border-bottom: 1px solid var(--line);
  background: var(--header);
  backdrop-filter: blur(20px);
}

.shell-search {
  padding: 8px;
  border-radius: 20px;
}

.shell-search :deep(.el-input__wrapper) {
  box-shadow: none;
  background: transparent;
}

.shell-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.shell-user-trigger {
  cursor: pointer;
}

.shell-content {
  padding: 24px;
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

.theme-custom-preview {
  padding: 16px;
}

.theme-custom-preview__title {
  font-weight: 700;
}

.theme-custom-preview__swatches {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.theme-custom-preview__swatches span {
  height: 52px;
  border-radius: 16px;
}

@media (max-width: 1100px) {
  .shell-frame {
    grid-template-columns: 1fr;
  }

  .shell-aside {
    border-right: none;
    border-bottom: 1px solid var(--line);
  }

  .shell-header {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .theme-custom-grid {
    grid-template-columns: 1fr;
  }
}
</style>
