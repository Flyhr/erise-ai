import { computed, onBeforeUnmount, unref, watch, type MaybeRefOrGetter, type Ref } from 'vue'
import { getFile } from '@/api/file'
import type { FileView } from '@/types/models'

interface UseVisibleFileStatusPollingOptions<T> {
  rows: Ref<T[]>
  enabled?: MaybeRefOrGetter<boolean>
  intervalMs?: number
  getFileId: (row: T) => number | undefined
  isFileActive: (row: T) => boolean
  applyDetail: (row: T, detail: FileView) => void
  onDetails?: (details: FileView[]) => void
}

export const useVisibleFileStatusPolling = <T>(options: UseVisibleFileStatusPollingOptions<T>) => {
  const enabled = computed(() => unref(options.enabled ?? true))
  const intervalMs = options.intervalMs ?? 1000
  const activeFileIds = computed(() =>
    [...new Set(
      options.rows.value
        .filter((row) => options.isFileActive(row))
        .map((row) => options.getFileId(row))
        .filter((id): id is number => typeof id === 'number'),
    )],
  )

  let pollTimer: number | undefined
  let polling = false

  const stop = () => {
    if (pollTimer) {
      window.clearTimeout(pollTimer)
      pollTimer = undefined
    }
  }

  const canPoll = () => enabled.value && activeFileIds.value.length > 0

  const schedule = () => {
    if (!canPoll()) {
      stop()
      return
    }
    stop()
    pollTimer = window.setTimeout(() => {
      void refreshNow()
    }, intervalMs)
  }

  const refreshNow = async () => {
    if (polling || !canPoll()) {
      stop()
      return
    }

    polling = true
    const fileIds = [...activeFileIds.value]
    if (!fileIds.length) {
      polling = false
      stop()
      return
    }

    try {
      const details = (
        await Promise.all(
          fileIds.map(async (id) => {
            try {
              return await getFile(id, { background: true })
            } catch {
              return undefined
            }
          }),
        )
      ).filter((detail): detail is FileView => Boolean(detail))

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
    [options.rows, enabled],
    () => {
      if (!canPoll()) {
        stop()
        return
      }
      if (!pollTimer && !polling) {
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
