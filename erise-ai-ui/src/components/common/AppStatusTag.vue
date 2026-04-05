<template>
  <span class="app-status-tag" :class="`is-${tone}`">{{ label }}</span>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    label: string
    tone?: 'primary' | 'success' | 'warning' | 'danger' | 'info'
  }>(),
  {
    tone: 'info',
  },
)
</script>
