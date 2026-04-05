<template>
  <nav class="project-subnav" aria-label="项目二级导航">
    <button v-for="item in items" :key="item.key" type="button" class="project-subnav__item"
      :class="{ 'is-active': isActive(item) }" @click="router.push(item.to)">
      {{ item.label }}
    </button>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const props = defineProps<{
  projectId: number
}>()

const route = useRoute()
const router = useRouter()

const items = computed(() => [
  { key: 'overview', label: '概览', to: `/projects/${props.projectId}` },
  { key: 'files', label: '文件', to: `/projects/${props.projectId}/files` },
  { key: 'documents', label: '文档', to: `/projects/${props.projectId}/documents` },
  { key: 'content', label: '表格', to: `/projects/${props.projectId}/contents/sheet` },
  // { key: 'ai', label: 'AI 助理', to: `/projects/${props.projectId}/ai` },
])

const isActive = (item: { key: string; to: string }) => {
  if (item.key === 'content') {
    return route.path.startsWith(`/projects/${props.projectId}/contents/`)
  }
  if (item.key === 'overview') {
    return route.path === item.to
  }
  return route.path.startsWith(item.to)
}
</script>
