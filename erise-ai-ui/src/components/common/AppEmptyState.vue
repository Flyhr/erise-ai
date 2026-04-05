<template>
  <div class="app-empty-state">
    <div v-if="eyebrow" class="app-eyebrow">{{ eyebrow }}</div>
    <div class="app-empty-state__title">{{ title }}</div>
    <p v-if="description" class="app-empty-state__description">{{ description }}</p>
    <div v-if="$slots.default" class="app-empty-state__actions">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  eyebrow?: string
}>()
</script>
