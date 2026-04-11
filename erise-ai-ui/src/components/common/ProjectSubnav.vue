<template>
  <nav class="project-subnav" aria-label="项目二级导航">
    <button
      v-for="item in resolvedItems"
      :key="item.key"
      type="button"
      class="project-subnav__item"
      :class="{ 'is-active': isActive(item) }"
      @click="handleSelect(item)"
    >
      {{ item.label }}
    </button>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

interface ProjectSubnavItem {
  key: string
  label: string
  to?: string
}

const props = withDefaults(defineProps<{
  projectId: number
  mode?: 'route' | 'value'
  modelValue?: string
  items?: ProjectSubnavItem[]
}>(), {
  mode: 'route',
  modelValue: undefined,
  items: undefined,
})

const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void
}>()

const route = useRoute()
const router = useRouter()

const defaultItems = computed<ProjectSubnavItem[]>(() => [
  { key: 'overview', label: '概览', to: `/projects/${props.projectId}` },
  { key: 'files', label: '文件', to: `/projects/${props.projectId}/files` },
  { key: 'documents', label: '文档', to: `/projects/${props.projectId}/documents` },
  { key: 'content', label: '表格', to: `/projects/${props.projectId}/contents/sheet` },
])

const resolvedItems = computed(() => props.items?.length ? props.items : defaultItems.value)

const handleSelect = (item: ProjectSubnavItem) => {
  if (props.mode === 'value') {
    emit('update:modelValue', item.key)
    return
  }
  if (item.to) {
    router.push(item.to)
  }
}

const isActive = (item: ProjectSubnavItem) => {
  if (props.mode === 'value') {
    return props.modelValue === item.key
  }
  if (!item.to) {
    return false
  }
  if (item.key === 'content') {
    return route.path.startsWith(`/projects/${props.projectId}/contents/`)
  }
  if (item.key === 'overview') {
    return route.path === item.to
  }
  return route.path.startsWith(item.to)
}
</script>

<style scoped>
.project-subnav {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  border-radius: 16px;
  background: #f1f3fa;
  border: 1px solid rgba(192, 199, 212, 0.2);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.78);
}

.project-subnav__item {
  min-height: 36px;
  padding: 0 16px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: #5f6775;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  cursor: pointer;
  transition:
    background-color 0.18s ease,
    color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
}

.project-subnav__item:hover {
  color: #1f2937;
  background: rgba(255, 255, 255, 0.58);
}

.project-subnav__item.is-active {
  color: #0060a9;
  background: #ffffff;
  box-shadow:
    0 1px 2px rgba(15, 23, 42, 0.08),
    0 8px 18px rgba(148, 163, 184, 0.14);
}

.project-subnav__item:active {
  transform: translateY(1px);
}

@media (max-width: 768px) {
  .project-subnav {
    width: 100%;
    justify-content: space-between;
    overflow-x: auto;
  }

  .project-subnav__item {
    flex: 1 0 auto;
    white-space: nowrap;
  }
}
</style>
