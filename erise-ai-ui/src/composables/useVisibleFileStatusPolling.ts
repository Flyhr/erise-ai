import { computed, onBeforeUnmount, unref, watch, type MaybeRefOrGetter, type Ref } from 'vue'
import { watchFileStatuses } from '@/api/file'
import type { FileView } from '@/types/models'

interface UseVisibleFileStatusPollingOptions<T> {
  rows: Ref<T[]>
  enabled?: MaybeRefOrGetter<boolean>
  intervalMs?: number
  maxDurationMs?: number
  getFileId: (row: T) => number | undefined
  isFileActive: (row: T) => boolean
  applyDetail: (row: T, detail: FileView) => void
  onDetails?: (details: FileView[]) => void
  onTimeout?: (fileIds: number[]) => void
}

export const useVisibleFileStatusPolling = <T>(options: UseVisibleFileStatusPollingOptions<T>) => {
  const enabled = computed(() => unref(options.enabled ?? true))
  const intervalMs = options.intervalMs ?? 3000
  const maxDurationMs = options.maxDurationMs ?? 120000
  const requestTimeoutMs = Math.max(intervalMs, Math.min(maxDurationMs, 25000))
  const activeFileIds = computed(() =>
    [...new Set(
      options.rows.value
        .filter((row) => options.isFileActive(row))
        .map((row) => options.getFileId(row))
        .filter((id): id is number => typeof id === 'number'),
      )],
  )
  const activeFileIdSignature = computed(() => activeFileIds.value.join(','))

  let pollTimer: number | undefined
  let polling = false
  let pollStartedAt: number | undefined
  let timeoutNotified = false
  let statusCursor = ''

  const clearTimer = () => {
    if (pollTimer) {
      window.clearTimeout(pollTimer)
      pollTimer = undefined
    }
  }

  const stop = () => {
    clearTimer()
    pollStartedAt = undefined
    timeoutNotified = false
    statusCursor = ''
  }

  const canPoll = () => enabled.value && activeFileIds.value.length > 0

  const isTimedOut = () =>
    Boolean(pollStartedAt && Date.now() - pollStartedAt >= maxDurationMs)

  const schedule = () => {
    if (!canPoll()) {
      stop()
      return
    }
    if (!pollStartedAt) {
      pollStartedAt = Date.now()
    }
    if (isTimedOut()) {
      if (!timeoutNotified) {
        timeoutNotified = true
        options.onTimeout?.([...activeFileIds.value])
      }
    }
    clearTimer()
    pollTimer = window.setTimeout(() => {
      void refreshNow()
    }, 0)
  }

  const refreshNow = async () => {
    if (polling || !canPoll()) {
      stop()
      return
    }
    if (!pollStartedAt) {
      pollStartedAt = Date.now()
    }
    if (isTimedOut()) {
      if (!timeoutNotified) {
        timeoutNotified = true
        options.onTimeout?.([...activeFileIds.value])
      }
    }

    polling = true
    const fileIds = [...activeFileIds.value]
    if (!fileIds.length) {
      polling = false
      stop()
      return
    }

    try {
      const payload = await watchFileStatuses(
        {
          fileIds,
          cursor: statusCursor || undefined,
          timeoutMs: requestTimeoutMs,
        },
        { background: true },
      )
      statusCursor = payload.cursor || ''
      const details = (payload.details || []).filter((detail): detail is FileView => Boolean(detail))

      if (!details.length) {
        return
      }

      const detailMap = new Map(details.map((detail) => [detail.id, detail]))
      options.rows.value.forEach((row) => {
        const fileId = options.getFileId(row)
        if (fileId == null) {
          return
        }
        const detail = detailMap.get(fileId)
        if (!detail) {
          return
        }
        options.applyDetail(row, detail)
      })

      options.onDetails?.(details)
    } finally {
      polling = false
      schedule()
    }
  }

  watch(
    activeFileIdSignature,
    (current, previous) => {
      if (current === previous) {
        return
      }
      pollStartedAt = undefined
      timeoutNotified = false
      statusCursor = ''
    },
  )

  watch(
    [options.rows, enabled, activeFileIdSignature],
    () => {
      if (!canPoll()) {
        stop()
        return
      }
      if (!pollTimer && !polling) {
        pollStartedAt = pollStartedAt || Date.now()
        void refreshNow()
      }
    },
    { deep: true, immediate: true },
  )

  onBeforeUnmount(() => {
    stop()
  })

  return {
    refreshNow,
    stop,
  }
}
