<template>
  <div class="knowledge-sync-status" :class="{ 'is-compact': compact }">
    <AppStatusTag
      :label="knowledgeProgressLabel(parseStatus, indexStatus)"
      :tone="knowledgeProgressTone(parseStatus, indexStatus)"
    />
    <span v-if="parseErrorMessage" class="knowledge-sync-status__error">{{ parseErrorMessage }}</span>
    <el-button
      v-if="canRetry"
      link
      size="small"
      class="knowledge-sync-status__retry"
      @click="$emit('retry')"
    >
      {{ retryText }}
    </el-button>
  </div>
</template>

<script setup lang="ts">
import AppStatusTag from '@/components/common/AppStatusTag.vue'
import { knowledgeProgressLabel, knowledgeProgressTone } from '@/utils/formatters'

withDefaults(defineProps<{
  parseStatus?: string
  indexStatus?: string
  parseErrorMessage?: string
  canRetry?: boolean
  retryText?: string
  compact?: boolean
}>(), {
  canRetry: false,
  retryText: '重试',
  compact: false,
})

defineEmits<{
  retry: []
}>()
</script>

<style scoped>
.knowledge-sync-status {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
}

.knowledge-sync-status.is-compact {
  gap: 4px;
}

.knowledge-sync-status__error {
  color: var(--danger, #c2410c);
  font-size: 12px;
  line-height: 1.45;
  word-break: break-word;
}

.knowledge-sync-status__retry {
  padding: 0;
  min-height: auto;
}
</style>
