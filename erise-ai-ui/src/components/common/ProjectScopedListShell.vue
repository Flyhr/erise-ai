<template>
  <div class="page-shell project-scoped-list-shell">
    <AppPageHeader
      :title="title"
      :subtitle="subtitle"
      show-back
      back-label="返回项目中心"
      back-to="/projects"
    />

    <ProjectSubnav :project-id="projectId" />

    <AppFilterBar>
      <el-input
        :model-value="keyword"
        style="grid-column: span 5"
        clearable
        :placeholder="searchPlaceholder"
        @update:model-value="emit('update:keyword', $event)"
        @keyup.enter="emit('search')"
      />
      <div v-if="hint" class="project-scoped-list-shell__hint">{{ hint }}</div>
      <slot name="filters" />
      <template #actions>
        <slot name="actions" />
      </template>
    </AppFilterBar>

    <slot />
  </div>
</template>

<script setup lang="ts">
import AppFilterBar from '@/components/common/AppFilterBar.vue'
import AppPageHeader from '@/components/common/AppPageHeader.vue'
import ProjectSubnav from '@/components/common/ProjectSubnav.vue'

defineProps<{
  projectId: number
  title: string
  subtitle?: string
  keyword: string
  searchPlaceholder?: string
  hint?: string
}>()

const emit = defineEmits<{
  (e: 'update:keyword', value: string): void
  (e: 'search'): void
}>()
</script>

<style scoped>
.project-scoped-list-shell {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.project-scoped-list-shell__hint {
  grid-column: span 7;
  display: flex;
  align-items: center;
  color: var(--muted);
  font-size: 13px;
}
</style>
