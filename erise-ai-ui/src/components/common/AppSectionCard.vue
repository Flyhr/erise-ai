<template>
  <section class="app-card" :class="{ 'is-compact': compact }">
    <header v-if="title || description || $slots.header || $slots.actions" class="app-card__header">
      <div class="app-card__header-main">
        <slot name="header">
          <div v-if="eyebrow" class="app-eyebrow">{{ eyebrow }}</div>
          <h2 v-if="title" class="app-card__title">{{ title }}</h2>
          <p v-if="description" class="app-card__description">{{ description }}</p>
        </slot>
      </div>
      <div v-if="$slots.actions" class="app-card__header-actions">
        <slot name="actions" />
      </div>
    </header>
    <div class="app-card__body" :class="{ 'is-unpadded': unpadded }">
      <slot />
    </div>
    <footer v-if="$slots.footer" class="app-card__footer">
      <slot name="footer" />
    </footer>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  title?: string
  description?: string
  eyebrow?: string
  compact?: boolean
  unpadded?: boolean
}>()
</script>
