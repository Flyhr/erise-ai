<template>
  <div class="app-filter-bar">
    <div class="app-filter-bar__fields">
      <slot />
    </div>
    <div v-if="$slots.actions" class="app-filter-bar__actions">
      <slot name="actions" />
    </div>
  </div>
</template>
