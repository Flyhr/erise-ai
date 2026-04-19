<template>
  <div class="compact-pager" :class="`compact-pager--${variant}`">
    <template v-if="variant === 'project'">
      <button
        type="button"
        class="compact-pager__icon-button"
        :disabled="currentPage <= 1"
        aria-label="上一页"
        @click="emitChange(currentPage - 1)"
      >
        <span class="material-symbols-outlined">chevron_left</span>
      </button>

      <div class="compact-pager__pages">
        <template v-for="(page, index) in visiblePages" :key="`${page}-${index}`">
          <span v-if="isEllipsis(page)" class="compact-pager__ellipsis">...</span>
          <button
            v-else
            type="button"
            class="compact-pager__page-chip"
            :class="{ 'is-active': page === currentPage }"
            @click="emitChange(page)"
          >
            {{ page }}
          </button>
        </template>
      </div>

      <button
        type="button"
        class="compact-pager__icon-button"
        :disabled="currentPage >= totalPages"
        aria-label="下一页"
        @click="emitChange(currentPage + 1)"
      >
        <span class="material-symbols-outlined">chevron_right</span>
      </button>

      <div class="compact-pager__goto">
        <span>跳转至</span>
        <input
          v-model="draftPage"
          class="compact-pager__goto-input"
          inputmode="numeric"
          @keydown.enter.prevent="submitDraft"
          @blur="submitDraft"
        />
        <button type="button" class="compact-pager__goto-button" @click="submitDraft">确认</button>
      </div>
    </template>

    <template v-else>
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
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  pageNum: number
  pageSize: number
  total: number
  variant?: 'default' | 'project'
}>(), {
  pageNum: 1,
  pageSize: 10,
  total: 0,
  variant: 'default',
})

const emit = defineEmits<{
  (e: 'change', page: number): void
}>()

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / Math.max(props.pageSize, 1))))
const currentPage = computed(() => Math.min(Math.max(props.pageNum, 1), totalPages.value))
const draftPage = ref(String(currentPage.value))
type VisiblePage = number | 'ellipsis-left' | 'ellipsis-right'

const visiblePages = computed<VisiblePage[]>(() => {
  if (totalPages.value <= 5) {
    return Array.from({ length: totalPages.value }, (_, index) => index + 1)
  }
  if (currentPage.value <= 3) {
    return [1, 2, 3, 'ellipsis-right', totalPages.value]
  }
  if (currentPage.value >= totalPages.value - 2) {
    return [1, 'ellipsis-left', totalPages.value - 2, totalPages.value - 1, totalPages.value]
  }
  return [
    1,
    'ellipsis-left',
    currentPage.value - 1,
    currentPage.value,
    currentPage.value + 1,
    'ellipsis-right',
    totalPages.value,
  ]
})

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

const isEllipsis = (value: VisiblePage): value is 'ellipsis-left' | 'ellipsis-right' => typeof value === 'string'
</script>

<style scoped>
.compact-pager {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.compact-pager--project {
  gap: 10px;
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

.compact-pager__pages {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.compact-pager__icon-button,
.compact-pager__page-chip,
.compact-pager__goto-input,
.compact-pager__goto-button {
  height: 34px;
  border: 1px solid rgba(192, 199, 212, 0.3);
  background: #ffffff;
  color: #475467;
}

.compact-pager__icon-button,
.compact-pager__page-chip,
.compact-pager__goto-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  cursor: pointer;
  transition:
    color 0.18s ease,
    border-color 0.18s ease,
    background-color 0.18s ease,
    transform 0.18s ease;
}

.compact-pager__icon-button {
  width: 34px;
  padding: 0;
}

.compact-pager__icon-button:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.compact-pager__icon-button:not(:disabled):hover,
.compact-pager__page-chip:hover,
.compact-pager__goto-button:hover {
  color: #0060a9;
  border-color: rgba(0, 96, 169, 0.26);
}

.compact-pager__page-chip {
  min-width: 34px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 800;
}

.compact-pager__page-chip.is-active {
  border-color: transparent;
  background: #ffffff;
  color: #0060a9;
  box-shadow: 0 10px 20px rgba(148, 163, 184, 0.18);
}

.compact-pager__ellipsis {
  padding: 0 2px;
  color: #667085;
  font-size: 12px;
  font-weight: 800;
}

.compact-pager__goto {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-left: 6px;
  padding-left: 14px;
  border-left: 1px solid rgba(192, 199, 212, 0.3);
  color: #667085;
  font-size: 12px;
}

.compact-pager__goto-input {
  width: 42px;
  padding: 0 8px;
  border-radius: 10px;
  text-align: center;
  outline: none;
}

.compact-pager__goto-input:focus {
  border-color: rgba(0, 96, 169, 0.26);
  box-shadow: 0 0 0 3px rgba(0, 96, 169, 0.1);
}

.compact-pager__goto-button {
  padding: 0 10px;
  font-size: 12px;
  font-weight: 800;
}

@media (max-width: 768px) {
  .compact-pager--project {
    width: 100%;
    justify-content: flex-start;
  }

  .compact-pager__goto {
    margin-left: 0;
    padding-left: 0;
    border-left: 0;
  }
}
</style>
