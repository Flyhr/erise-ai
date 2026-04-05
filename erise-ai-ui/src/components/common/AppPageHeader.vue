<template>
  <header class="app-page-header" :class="{ 'is-compact': compact }">
    <div class="app-page-header__main">
      <div v-if="eyebrow" class="app-eyebrow">{{ eyebrow }}</div>
      <div class="app-page-header__title-row">
        <el-button v-if="showBack" text class="app-page-header__back" @click="handleBack">
          <el-icon><ArrowLeft /></el-icon>
          <span>{{ backLabel }}</span>
        </el-button>
        <h1 class="app-page-title">{{ title }}</h1>
        <slot name="meta" />
      </div>
      <p v-if="subtitle" class="page-subtitle app-page-header__subtitle">{{ subtitle }}</p>
      <slot />
    </div>
    <div v-if="$slots.actions" class="app-page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup lang="ts">
import type { RouteLocationRaw } from 'vue-router'
import { useRouter } from 'vue-router'

const props = withDefaults(defineProps<{
  title: string
  subtitle?: string
  eyebrow?: string
  compact?: boolean
  showBack?: boolean
  backLabel?: string
  backTo?: RouteLocationRaw
}>(), {
  compact: false,
  showBack: false,
  backLabel: '返回上一级',
})

const router = useRouter()

const handleBack = () => {
  if (props.backTo) {
    void router.push(props.backTo)
    return
  }
  router.back()
}
</script>

<style scoped>
.app-page-header__back {
  gap: 6px;
  padding: 0;
  font-weight: 600;
  color: var(--muted);
}

.app-page-header__back:hover {
  color: var(--accent);
}
</style>
