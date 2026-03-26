<template>
  <div class="shell">
    <div class="glass-card" style="display: grid; grid-template-columns: 260px 1fr; overflow: hidden">
      <aside style="padding: 24px; border-right: 1px solid var(--line); background: rgba(22, 101, 52, 0.04)">
        <div style="margin-bottom: 28px">
          <div style="font-size: 13px; letter-spacing: 0.2em; color: var(--muted)">ERISE-AI</div>
          <div style="font-size: 28px; font-weight: 700; margin-top: 6px">知识工作台</div>
        </div>
        <el-menu :default-active="$route.path" router style="border-right: none; background: transparent">
          <el-menu-item index="/workspace">工作台</el-menu-item>
          <el-menu-item index="/projects">项目</el-menu-item>
          <el-menu-item index="/search">搜索</el-menu-item>
          <el-menu-item index="/ai">AI 助手</el-menu-item>
          <el-menu-item index="/settings/profile">个人设置</el-menu-item>
        </el-menu>
      </aside>
      <section style="min-width: 0">
        <header style="display: flex; justify-content: space-between; align-items: center; padding: 18px 24px; border-bottom: 1px solid var(--line)">
          <div>
            <div style="font-size: 13px; color: var(--muted)">当前用户</div>
            <div style="font-size: 18px; font-weight: 600">{{ authStore.user?.displayName || authStore.user?.username }}</div>
          </div>
          <div style="display: flex; gap: 12px; align-items: center">
            <el-button v-if="authStore.isAdmin" plain @click="$router.push('/admin')">管理台</el-button>
            <el-button type="danger" plain @click="handleLogout">退出登录</el-button>
          </div>
        </header>
        <main style="padding: 24px">
          <router-view />
        </main>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const handleLogout = async () => {
  await authStore.logout()
  router.push('/login')
}
</script>
