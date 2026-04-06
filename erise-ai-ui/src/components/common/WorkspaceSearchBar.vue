<template>
  <header class="workspace-topbar">
    <div class="workspace-search-box">
      <span class="material-symbols-outlined">search</span>
      <input v-model="searchKeyword" type="text" :placeholder="searchPlaceholder"
        @keydown.enter.prevent="handleSearch" />
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
      <button type="button" class="workspace-user-btn" @click="handleProfile">
        <div class="workspace-user-meta">
          <div class="workspace-user-name">{{ userName }}</div>
          <div class="workspace-user-role">{{ userRole }}</div>
        </div>
        <div class="workspace-user-avatar">{{ userAvatar }}</div>
      </button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'

interface Props {
  userName?: string
  userRole?: string
  userAvatar?: string
  searchPlaceholder?: string
}

withDefaults(defineProps<Props>(), {
  userName: '个人中心',
  userRole: '账号设置',
  userAvatar: 'U',
  searchPlaceholder: '搜索文件、文档名称进行搜索',
})

const router = useRouter()
const route = useRoute()
const searchKeyword = ref(typeof route.query.q === 'string' ? route.query.q : '')

watch(
  () => route.query.q,
  (keyword) => {
    searchKeyword.value = typeof keyword === 'string' ? keyword : ''
  },
)

const handleSearch = () => {
  const keyword = searchKeyword.value.trim()
  router.push({
    path: '/workspace/search',
    query: keyword ? { q: keyword } : {},
  })
}

const handleNotify = () => {
  ElMessageBox.alert('通知中心 暂未开放更多入口', '提示', {
    confirmButtonText: '确定',
    type: 'info',
  })
}

const handleSettings = () => {
  router.push('/settings/profile')
}

const handleProfile = () => {
  router.push('/settings/profile')
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
  padding: 6px;
  border-radius: 16px;
  background: transparent;
  cursor: pointer;
}

.workspace-user-meta {
  text-align: right;
}

.workspace-user-name {
  font-weight: 700;
}

.workspace-user-role {
  color: #667085;
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

@media (max-width: 720px) {
  .workspace-topbar {
    flex-direction: column;
    align-items: stretch;
  }

  .workspace-topbar-actions {
    justify-content: flex-end;
  }
}
</style>
