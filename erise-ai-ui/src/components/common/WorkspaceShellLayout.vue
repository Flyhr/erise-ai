<template>
  <WorkspaceNavigationShell
    v-model="searchKeyword"
    :active-nav="activeNav"
    @create="showComingSoon('新建内容')"
    @navigate-dashboard="goDashboard"
    @navigate-projects="router.push('/projects')"
    @navigate-knowledge="goKnowledge"
    @navigate-ai="router.push('/ai')"
    @search="openSearch"
    @notify="showComingSoon('通知中心')"
    @settings="showComingSoon('工作台设置')"
    @profile="router.push('/settings/profile')"
  >
    <RouterView />
  </WorkspaceNavigationShell>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { RouterView, useRoute, useRouter } from 'vue-router'
import WorkspaceNavigationShell from '@/components/common/WorkspaceNavigationShell.vue'

const router = useRouter()
const route = useRoute()
const searchKeyword = ref(typeof route.query.keyword === 'string' ? route.query.keyword : '')

watch(
  () => route.query.keyword,
  (keyword) => {
    searchKeyword.value = typeof keyword === 'string' ? keyword : ''
  },
)

const activeNav = computed<'dashboard' | 'projects' | 'knowledge' | 'ai'>(() => {
  const path = route.path
  if (path.startsWith('/ai')) return 'ai'
  if (path.startsWith('/projects')) return 'projects'
  if (path.startsWith('/workspace')) return 'dashboard'
  return 'dashboard'
})

const showComingSoon = (feature: string) => {
  ElMessageBox.alert(`${feature} 当前功能还未开发`, '提示', {
    confirmButtonText: '确定',
    type: 'info',
  })
}

const openSearch = () => {
  router.push({
    path: '/search',
    query: searchKeyword.value ? { keyword: searchKeyword.value } : {},
  })
}

const goDashboard = async () => {
  if (!route.path.startsWith('/workspace')) {
    await router.push('/workspace')
  }
}

const goKnowledge = async () => {
  if (!route.path.startsWith('/workspace')) {
    await router.push('/workspace')
  }

  await nextTick()
  window.setTimeout(() => {
    document.getElementById('knowledge-base')?.scrollIntoView({
      behavior: 'smooth',
      block: 'start',
    })
  }, 50)
}
</script>
