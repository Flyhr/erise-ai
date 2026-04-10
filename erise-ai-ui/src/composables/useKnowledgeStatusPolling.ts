import { computed, onBeforeUnmount, unref, watch, type MaybeRefOrGetter, type Ref } from 'vue'

interface KnowledgeStatusRecord {
  parseStatus?: string
  indexStatus?: string
}

const ACTIVE_STATUSES = new Set(['INIT', 'UPLOADING', 'PENDING', 'PROCESSING'])

const normalizeStatus = (value?: string) => (value || '').trim().toUpperCase()

const hasActiveKnowledgeStatus = (record: KnowledgeStatusRecord) =>
  ACTIVE_STATUSES.has(normalizeStatus(record.parseStatus)) || ACTIVE_STATUSES.has(normalizeStatus(record.indexStatus))

export const useKnowledgeStatusPolling = <T extends KnowledgeStatusRecord>(options: {
  records: Ref<T[]>
  reload: () => Promise<void>
  enabled?: MaybeRefOrGetter<boolean>
  intervalMs?: number
}) => {
  const intervalMs = options.intervalMs ?? 5000
  const enabled = computed(() => unref(options.enabled ?? true))
  let pollHandle: number | undefined
  let loading = false

  const stop = () => {
    if (pollHandle) {
      window.clearInterval(pollHandle)
      pollHandle = undefined
    }
  }

  const tick = async () => {
    if (loading || !enabled.value) {
      return
    }
    loading = true
    try {
      await options.reload()
    } finally {
      loading = false
    }
  }

  const sync = () => {
    if (!enabled.value || !options.records.value.some(hasActiveKnowledgeStatus)) {
      stop()
      return
    }
    if (!pollHandle) {
      pollHandle = window.setInterval(() => {
        void tick()
      }, intervalMs)
    }
  }

  watch(
    [options.records, enabled],
    () => {
      sync()
    },
    { deep: true, immediate: true },
  )

  onBeforeUnmount(() => {
    stop()
  })

  return {
    refreshNow: tick,
    stop,
  }
}
