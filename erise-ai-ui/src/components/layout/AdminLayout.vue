<template>
  <div class="shell">
    <div class="glass-card" style="display: grid; grid-template-columns: 260px 1fr; overflow: hidden">
      <aside style="padding: 24px; border-right: 1px solid var(--line); background: rgba(20, 83, 45, 0.08)">
        <div style="margin-bottom: 28px">
          <div style="font-size: 13px; letter-spacing: 0.2em; color: var(--muted)">ADMIN</div>
          <div style="font-size: 28px; font-weight: 700; margin-top: 6px">运营与审计</div>
        </div>
        <el-menu :default-active="$route.path" router style="border-right: none; background: transparent">
          <el-menu-item index="/admin">仪表盘</el-menu-item>
          <el-menu-item index="/admin/users">用户管理</el-menu-item>
          <el-menu-item index="/admin/tasks">任务监控</el-menu-item>
          <el-menu-item index="/admin/ai-models">AI 配置</el-menu-item>
          <el-menu-item index="/admin/audit-logs">审计日志</el-menu-item>
        </el-menu>
      </aside>
      <section style="min-width: 0">
        <header style="display: flex; justify-content: space-between; align-items: center; padding: 18px 24px; border-bottom: 1px solid var(--line)">
          <div>
            <div style="font-size: 13px; color: var(--muted)">管理员控制台</div>
            <div style="font-size: 18px; font-weight: 600">{{ authStore.user?.displayName }}</div>
          </div>
          <div style="display: flex; gap: 12px">
            <el-button plain @click="$router.push('/workspace')">返回用户端</el-button>
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
