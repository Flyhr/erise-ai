<template>
  <button type="button" class="search-suffix-button" @click.stop="$emit('click')">
    搜索
  </button>
</template>

<script setup lang="ts">
defineEmits<{
  (e: 'click'): void
}>()
</script>

<style scoped>
.search-suffix-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 32px;
  padding: 0 12px;
  border: 0;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.86);
  color: #005b9f;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.18s ease, color 0.18s ease, transform 0.18s ease;
}

.search-suffix-button:hover {
  background: #d9ecff;
  color: #004f8a;
  transform: translateY(-1px);
}
</style>
