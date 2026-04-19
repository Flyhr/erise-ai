<template>
  <div class="workspace-page">
    <aside class="workspace-nav-rail" aria-label="一级导航">
      <button type="button" class="workspace-nav-rail__brand" title="打开导航" @click="navDrawerVisible = true">
        <span class="material-symbols-outlined">auto_awesome</span>
      </button>
      <button v-for="item in navItems" :key="item.key" type="button"
        :class="['workspace-nav-rail__item', { 'is-active': activeNav === item.key }]" :title="item.label"
        @click="handleNavigate(item.event)">
        <span class="material-symbols-outlined">{{ item.icon }}</span>
      </button>
      <button type="button" class="workspace-nav-rail__menu" title="展开导航" @click="navDrawerVisible = true">
        <span class="material-symbols-outlined">menu</span>
      </button>
    </aside>

    <section class="workspace-main-panel">
      <div class="workspace-shell-content">
        <slot />
      </div>
    </section>

    <el-drawer v-model="navDrawerVisible" direction="ltr" size="292px" :with-header="false" append-to-body
      class="workspace-nav-drawer">
      <aside class="workspace-side-panel">
        <div class="workspace-brand-card">
          <div class="workspace-brand-mark">
            <span class="material-symbols-outlined">auto_awesome</span>
          </div>
          <div>
            <div class="workspace-brand-title">{{ brandTitle }}</div>
            <div class="workspace-brand-subtitle">{{ brandSubtitle || 'Knowledge workspace' }}</div>
          </div>
        </div>

        <nav class="workspace-side-nav" aria-label="工作区导航">
          <button v-for="item in navItems" :key="item.key" type="button"
            :class="['workspace-side-link', { 'is-active': activeNav === item.key }]"
            @click="handleNavigate(item.event)">
            <span class="material-symbols-outlined">{{ item.icon }}</span>
            <span>{{ item.label }}</span>
          </button>
        </nav>

        <div class="workspace-side-footer">
          <div class="workspace-avatar-placeholder">{{ footerAvatar }}</div>
          <div>
            <div class="workspace-footer-title">{{ footerTitle }}</div>
            <div class="workspace-footer-copy">{{ footerCopy }}</div>
          </div>
        </div>
      </aside>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

type ActiveNav = 'dashboard' | 'projects' | 'knowledge' | 'ai'
type NavigationEvent = 'navigate-dashboard' | 'navigate-projects' | 'navigate-knowledge' | 'navigate-ai'

interface Props {
  modelValue?: string
  activeNav?: ActiveNav
  brandTitle?: string
  brandSubtitle?: string
  createText?: string
  footerTitle?: string
  footerCopy?: string
  footerAvatar?: string
  userName?: string
  userRole?: string
  userAvatar?: string
  searchPlaceholder?: string
}

withDefaults(defineProps<Props>(), {
  modelValue: '',
  activeNav: 'dashboard',
  brandTitle: 'Erise AI 知识库',
  brandSubtitle: '',
  createText: '新建内容',
  footerTitle: 'Erise AI 知识库 V1.0',
  // footerCopy: '企业版账号',
  footerAvatar: 'E',
  userName: '个人资料',
  userRole: '账号与偏好设置',
  userAvatar: '我',
  searchPlaceholder: '搜索项目、知识库、文件或 AI 会话...',
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'create'): void
  (e: 'navigate-dashboard'): void
  (e: 'navigate-projects'): void
  (e: 'navigate-knowledge'): void
  (e: 'navigate-ai'): void
  (e: 'search'): void
  (e: 'notify'): void
  (e: 'settings'): void
  (e: 'profile'): void
}>()

const navDrawerVisible = ref(false)

const navItems: Array<{ key: ActiveNav; label: string; icon: string; event: NavigationEvent }> = [
  { key: 'dashboard', label: '工作台', icon: 'dashboard', event: 'navigate-dashboard' },
  { key: 'projects', label: '项目', icon: 'folder_copy', event: 'navigate-projects' },
  { key: 'knowledge', label: '知识库', icon: 'menu_book', event: 'navigate-knowledge' },
  { key: 'ai', label: 'AI 助理', icon: 'smart_toy', event: 'navigate-ai' },
]

const handleNavigate = (event: NavigationEvent) => {
  navDrawerVisible.value = false
  if (event === 'navigate-dashboard') {
    emit('navigate-dashboard')
  } else if (event === 'navigate-projects') {
    emit('navigate-projects')
  } else if (event === 'navigate-knowledge') {
    emit('navigate-knowledge')
  } else {
    emit('navigate-ai')
  }
}
</script>

<style scoped>
.workspace-page {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 18px;
  min-height: calc(100vh - 80px);
  padding: 8px 0 24px;
  color: #181c20;
}

.workspace-main-panel,
.workspace-shell-content {
  min-width: 0;
}

.workspace-nav-rail {
  position: sticky;
  top: 12px;
  height: calc(100dvh - 104px);
  min-height: 420px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 14px 10px;
  border-radius: 22px;
  border: 1px solid #d8e0ed;
  background: linear-gradient(180deg, #f3f6fd 0%, #eef2f8 100%);
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.04);
}

.workspace-nav-rail__brand,
.workspace-nav-rail__item,
.workspace-nav-rail__menu {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 15px;
  color: #5d6676;
  background: transparent;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;
}

.workspace-nav-rail__brand {
  color: #fff;
  background: linear-gradient(135deg, #409eff 0%, #0060a9 100%);
  box-shadow: 0 14px 30px rgba(64, 158, 255, 0.22);
}

.workspace-nav-rail__item:hover,
.workspace-nav-rail__item.is-active,
.workspace-nav-rail__menu:hover {
  background: #ffffff;
  color: #0060a9;
  transform: translateY(-1px);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
}

.workspace-nav-rail__menu {
  margin-top: auto;
}

.workspace-nav-drawer :deep(.el-drawer) {
  background: #f3f6fd;
}

.workspace-nav-drawer :deep(.el-drawer__body) {
  padding: 0;
}

.workspace-side-panel {
  height: 100%;
  min-height: 0;
  background: linear-gradient(180deg, #f3f6fd 0%, #eef2f8 100%);
  padding: 22px 18px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.workspace-brand-card,
.workspace-side-footer {
  display: flex;
  align-items: center;
  gap: 12px;
}

.workspace-brand-mark,
.workspace-avatar-placeholder {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 16px;
  color: #fff;
  background: linear-gradient(135deg, #409eff 0%, #0060a9 100%);
}

.workspace-brand-mark {
  width: 42px;
  height: 42px;
  box-shadow: 0 14px 30px rgba(64, 158, 255, 0.28);
}

.workspace-brand-title {
  font-size: 18px;
  font-weight: 800;
}

.workspace-brand-subtitle,
.workspace-footer-copy {
  color: #667085;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.workspace-side-nav {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.workspace-side-link {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 0;
  background: transparent;
  border-radius: 16px;
  color: #5d6676;
  font-size: 14px;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
  transition: all 0.22s ease;
}

.workspace-side-link:hover,
.workspace-side-link.is-active {
  background: #fff;
  color: #0060a9;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
}

.workspace-side-footer {
  margin-top: auto;
  padding-top: 18px;
  border-top: 1px solid #dbe4f0;
}

.workspace-avatar-placeholder {
  width: 40px;
  height: 40px;
  font-size: 14px;
  font-weight: 800;
}

.workspace-footer-title {
  color: #1f2937;
  font-weight: 800;
}

@media (max-width: 1200px) {
  .workspace-page {
    grid-template-columns: 64px minmax(0, 1fr);
    gap: 14px;
  }
}

@media (max-width: 720px) {
  .workspace-page {
    display: block;
    padding-top: 64px;
  }

  .workspace-nav-rail {
    position: fixed;
    inset: 10px auto auto 12px;
    z-index: 10;
    width: auto;
    height: 48px;
    min-height: 0;
    flex-direction: row;
    padding: 4px;
    border-radius: 16px;
  }

  .workspace-nav-rail__brand,
  .workspace-nav-rail__item {
    display: none;
  }

  .workspace-nav-rail__menu {
    margin-top: 0;
  }
}
</style>
