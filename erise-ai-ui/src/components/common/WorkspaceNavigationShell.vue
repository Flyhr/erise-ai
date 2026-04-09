<template>
  <div class="workspace-page">
    <aside class="workspace-side-panel">
      <div class="workspace-brand-card">
        <div class="workspace-brand-mark">
          <span class="material-symbols-outlined">auto_awesome</span>
        </div>
        <div>
          <div class="workspace-brand-title">{{ brandTitle }}</div>
          <div class="workspace-brand-subtitle">{{ brandSubtitle }}</div>
        </div>
      </div>
      <nav class="workspace-side-nav">
        <button type="button" :class="['workspace-side-link', { 'is-active': activeNav === 'dashboard' }]"
          @click="$emit('navigate-dashboard')">
          <span class="material-symbols-outlined">dashboard</span>
          <span>工作台</span>
        </button>
        <button type="button" :class="['workspace-side-link', { 'is-active': activeNav === 'projects' }]"
          @click="$emit('navigate-projects')">
          <span class="material-symbols-outlined">folder_copy</span>
          <span>项目</span>
        </button>
        <button type="button" :class="['workspace-side-link', { 'is-active': activeNav === 'knowledge' }]"
          @click="$emit('navigate-knowledge')">
          <span class="material-symbols-outlined">menu_book</span>
          <span>知识库</span>
        </button>
        <button type="button" :class="['workspace-side-link', { 'is-active': activeNav === 'ai' }]"
          @click="$emit('navigate-ai')">
          <span class="material-symbols-outlined">smart_toy</span>
          <span>AI 助理</span>
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

    <section class="workspace-main-panel">
      <!-- <header class="workspace-topbar">
        <div class="workspace-search-box">
          <span class="material-symbols-outlined">search</span>
          <input :value="modelValue" type="text" :placeholder="searchPlaceholder" @input="onInput"
            @keyup.enter="$emit('search')" />
        </div>

        <div class="workspace-topbar-actions">
          <button type="button" class="workspace-icon-btn" @click="$emit('notify')">
            <span class="material-symbols-outlined">notifications</span>
          </button>
          <button type="button" class="workspace-icon-btn" @click="$emit('settings')">
            <span class="material-symbols-outlined">settings</span>
          </button>
          <button type="button" class="workspace-user-btn" @click="$emit('profile')">
            <div class="workspace-user-meta">
              <div class="workspace-user-name">{{ userName }}</div>
              <div class="workspace-user-role">{{ userRole }}</div>
            </div>
            <div class="workspace-user-avatar">{{ userAvatar }}</div>
          </button>
        </div>
      </header> -->

      <div class="workspace-shell-content">
        <slot />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
interface Props {
  modelValue?: string
  activeNav?: 'dashboard' | 'projects' | 'knowledge' | 'ai'
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
  brandTitle: 'Erise Ai 知识库',
  brandSubtitle: 'The Digital Curator',
  createText: 'New Entry',
  footerTitle: 'Erise AI 知识库V1.0',
  footerCopy: 'Premium Account',
  footerAvatar: 'ER',
  userName: '个人资料',
  userRole: '账号与主题偏好',
  userAvatar: 'U',
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

const onInput = (event: Event) => {
  emit('update:modelValue', (event.target as HTMLInputElement).value.trim())
}
</script>

<style scoped>
.workspace-page {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 24px;
  min-height: calc(100vh - 80px);
  padding: 8px 0 24px;
  color: #181c20;
}

.workspace-main-panel {
  position: relative;
  min-width: 0;
}

.workspace-shell-content {
  min-width: 0;
}

.workspace-side-panel {
  border-radius: 24px;
  background: linear-gradient(180deg, #f3f6fd 0%, #eef2f8 100%);
  border: 1px solid #d8e0ed;
  padding: 22px 18px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.04);
}

.workspace-brand-card,
.workspace-side-footer,
.workspace-user-btn,
.workspace-topbar,
.workspace-topbar-actions {
  display: flex;
  align-items: center;
}

.workspace-brand-card,
.workspace-side-footer,
.workspace-user-btn {
  gap: 12px;
}

.workspace-brand-mark,
.workspace-avatar-placeholder,
.workspace-user-avatar {
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
  letter-spacing: -0.03em;
}

.workspace-brand-subtitle,
.workspace-footer-copy,
.workspace-user-role {
  color: #667085;
}

.workspace-brand-subtitle,
.workspace-footer-copy {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.workspace-create-btn,
.workspace-side-link,
.workspace-icon-btn {
  border: 0;
  cursor: pointer;
  transition: all 0.22s ease;
}

.workspace-create-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 14px;
  border-radius: 16px;
  color: #fff;
  font-weight: 700;
  background: linear-gradient(135deg, #409eff 0%, #0060a9 100%);
  box-shadow: 0 16px 30px rgba(0, 96, 169, 0.18);
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
  background: transparent;
  border-radius: 16px;
  color: #5d6676;
  font-size: 14px;
  font-weight: 600;
  text-align: left;
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

.workspace-avatar-placeholder,
.workspace-user-avatar {
  width: 40px;
  height: 40px;
  font-size: 14px;
  font-weight: 800;
}

.workspace-footer-title,
.workspace-user-name {
  font-weight: 800;
  letter-spacing: -0.02em;
  color: #1f2937;
}

.workspace-topbar {
  justify-content: space-between;
  gap: 16px;
  padding: 8px 4px 18px;
}

.workspace-search-box {
  flex: 1;
  max-width: 460px;
  position: relative;
}

.workspace-search-box .material-symbols-outlined {
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  color: #7b8698;
  font-size: 20px;
}

.workspace-search-box input {
  width: 100%;
  height: 46px;
  border: 1px solid #d8e0ed;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.9);
  padding: 0 16px 0 44px;
  font-size: 14px;
  outline: none;
}

.workspace-search-box input:focus {
  border-color: #409eff;
  box-shadow: 0 0 0 4px rgba(64, 158, 255, 0.14);
}

.workspace-topbar-actions {
  gap: 10px;
  justify-content: flex-end;
}

.workspace-icon-btn {
  width: 42px;
  height: 42px;
  border-radius: 999px;
  background: #fff;
  color: #667085;
  border: 1px solid #d8e0ed;
}

.workspace-icon-btn:hover {
  background: #edf5ff;
  color: #0060a9;
}

.workspace-user-btn {
  padding: 7px 8px 7px 14px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid #d8e0ed;
}

.workspace-user-btn:hover,
.workspace-create-btn:hover {
  transform: translateY(-1px);
}

.workspace-user-meta {
  text-align: right;
}

@media (max-width: 1200px) {
  .workspace-page {
    grid-template-columns: 1fr;
  }

  .workspace-side-panel {
    order: 2;
  }
}

@media (max-width: 960px) {
  .workspace-topbar {
    flex-direction: column;
    align-items: stretch;
  }

  .workspace-topbar-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 720px) {
  .workspace-side-panel {
    border-radius: 18px;
  }
}
</style>
