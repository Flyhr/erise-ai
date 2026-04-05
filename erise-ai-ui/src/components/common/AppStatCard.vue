<template>
  <article class="app-stat-card">
    <div class="app-stat-card__label">{{ label }}</div>
    <div class="app-stat-card__value">{{ value }}</div>
    <div v-if="hint" class="app-stat-card__hint">{{ hint }}</div>
    <slot />
  </article>
</template>

<script setup lang="ts">
defineProps<{
  label: string
  value: string | number
  hint?: string
}>()
</script>
