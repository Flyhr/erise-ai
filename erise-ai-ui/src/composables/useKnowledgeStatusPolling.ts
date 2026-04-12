import { computed, onBeforeUnmount, ref, toValue, unref, watch, type MaybeRefOrGetter, type Ref } from 'vue'

interface KnowledgeStatusRecord {
  parseStatus?: string
  indexStatus?: string
}

const trackedKnowledgeRecordKeys = ref<string[]>([])
const ACTIVE_STATUSES = new Set(['INIT', 'UPLOADING', 'PENDING', 'PROCESSING'])

const normalizeStatus = (value?: string) => (value || '').trim().toUpperCase()

const hasActiveKnowledgeStatus = (record: KnowledgeStatusRecord) =>
  ACTIVE_STATUSES.has(normalizeStatus(record.parseStatus)) || ACTIVE_STATUSES.has(normalizeStatus(record.indexStatus))

const normalizeRecordKey = (value: string | number) => String(value)

export const trackKnowledgeStatusRecord = (key: string | number) => {
  const normalizedKey = normalizeRecordKey(key)
  if (!trackedKnowledgeRecordKeys.value.includes(normalizedKey)) {
    trackedKnowledgeRecordKeys.value = [...trackedKnowledgeRecordKeys.value, normalizedKey]
  }
}

export const untrackKnowledgeStatusRecord = (key: string | number) => {
  const normalizedKey = normalizeRecordKey(key)
  trackedKnowledgeRecordKeys.value = trackedKnowledgeRecordKeys.value.filter((item) => item !== normalizedKey)
}

export const useKnowledgeStatusPolling = <T extends KnowledgeStatusRecord>(options: {
  records: Ref<T[]>
  reload: () => Promise<void>
  refreshTracked?: (records: T[]) => Promise<void>
  enabled?: MaybeRefOrGetter<boolean>
  intervalMs?: number
  trackedKeys?: MaybeRefOrGetter<Array<string | number>>
  includeUntrackedActive?: MaybeRefOrGetter<boolean>
  getRecordKey?: (record: T) => string | number | undefined
}) => {
  const intervalMs = options.intervalMs ?? 5000
  const enabled = computed(() => unref(options.enabled ?? true))
  const trackedKeySource = options.trackedKeys ?? trackedKnowledgeRecordKeys
  const trackedKeys = computed(() => (toValue(trackedKeySource) || []).map(normalizeRecordKey))
  const includeUntrackedActive = computed(() => unref(options.includeUntrackedActive ?? false))
  let pollHandle: number | undefined
  let loading = false

  const stop = () => {
    if (pollHandle) {
      window.clearTimeout(pollHandle)
      pollHandle = undefined
    }
  }

  const hasTrackedRecords = () => trackedKeys.value.length > 0

  const isTrackedRecord = (record: T) => {
    if (!options.getRecordKey) {
      return true
    }
    const key = options.getRecordKey?.(record)
    if (key == null) {
      return false
    }
    if (trackedKeys.value.includes(normalizeRecordKey(key))) {
      return true
    }
    return includeUntrackedActive.value && hasActiveKnowledgeStatus(record)
  }

  const pruneSettledTrackedRecords = () => {
    if (!hasTrackedRecords() || !options.getRecordKey) {
      return
    }
    options.records.value.forEach((record) => {
      const key = options.getRecordKey?.(record)
      if (key == null) {
        return
      }
      if (!hasActiveKnowledgeStatus(record)) {
        untrackKnowledgeStatusRecord(key)
      }
    })
  }

  const activeTrackedRecords = () =>
    options.records.value.filter((record) => hasActiveKnowledgeStatus(record) && isTrackedRecord(record))

  const hasActiveRecords = () => activeTrackedRecords().length > 0

  const shouldPoll = () => enabled.value && hasActiveRecords()

  const scheduleNext = () => {
    if (!shouldPoll()) {
      stop()
      return
    }
    stop()
    pollHandle = window.setTimeout(() => {
      void tick()
    }, intervalMs)
  }

  const tick = async () => {
    if (loading || !shouldPoll()) {
      stop()
      return
    }
    loading = true
    try {
      const trackedRecords = activeTrackedRecords()
      if (options.refreshTracked && trackedRecords.length) {
        await options.refreshTracked(trackedRecords)
      } else {
        await options.reload()
      }
    } finally {
      loading = false
      pruneSettledTrackedRecords()
      scheduleNext()
    }
  }

  const sync = () => {
    if (!shouldPoll()) {
      stop()
      return
    }
    if (!pollHandle && !loading) {
      void tick()
    }
  }

  watch(
    [options.records, enabled, trackedKeys, includeUntrackedActive],
    () => {
      pruneSettledTrackedRecords()
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
