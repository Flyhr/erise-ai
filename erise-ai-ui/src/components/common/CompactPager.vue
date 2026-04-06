<template>
  <div class="compact-pager">
    <button
      type="button"
      class="compact-pager__button"
      :disabled="currentPage <= 1"
      @click="emitChange(currentPage - 1)"
    >
      上一页
    </button>
    <div class="compact-pager__meta">
      <span>第</span>
      <input
        v-model="draftPage"
        class="compact-pager__input"
        inputmode="numeric"
        @keydown.enter.prevent="submitDraft"
        @blur="submitDraft"
      />
      <span>页 / 共 {{ totalPages }} 页</span>
    </div>
    <button
      type="button"
      class="compact-pager__button"
      :disabled="currentPage >= totalPages"
      @click="emitChange(currentPage + 1)"
    >
      下一页
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  pageNum: number
  pageSize: number
  total: number
}>(), {
  pageNum: 1,
  pageSize: 10,
  total: 0,
})

const emit = defineEmits<{
  (e: 'change', page: number): void
}>()

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / Math.max(props.pageSize, 1))))
const currentPage = computed(() => Math.min(Math.max(props.pageNum, 1), totalPages.value))
const draftPage = ref(String(currentPage.value))

watch(
  () => currentPage.value,
  (value) => {
    draftPage.value = String(value)
  },
  { immediate: true },
)

const emitChange = (value: number) => {
  const nextPage = Math.min(Math.max(value, 1), totalPages.value)
  if (nextPage !== currentPage.value) {
    emit('change', nextPage)
  } else {
    draftPage.value = String(nextPage)
  }
}

const submitDraft = () => {
  const nextPage = Number(draftPage.value)
  if (!Number.isFinite(nextPage)) {
    draftPage.value = String(currentPage.value)
    return
  }
  emitChange(nextPage)
}
</script>

<style scoped>
.compact-pager {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.compact-pager__button {
  min-width: 88px;
  height: 36px;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface-strong);
  color: var(--text);
  font-size: 13px;
  cursor: pointer;
  transition: 0.2s ease;
}

.compact-pager__button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.compact-pager__button:not(:disabled):hover {
  border-color: var(--brand);
  color: var(--brand);
}

.compact-pager__meta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--muted);
  font-size: 13px;
}

.compact-pager__input {
  width: 56px;
  height: 34px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface-strong);
  color: var(--text);
  text-align: center;
  outline: none;
}

.compact-pager__input:focus {
  border-color: var(--brand);
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.12);
}
</style>
