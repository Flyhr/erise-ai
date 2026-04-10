import { computed, readonly, reactive } from 'vue'

const MIN_VISIBLE_MS = 360
const DEFAULT_LOADING_TITLE = '\u6b63\u5728\u8fdb\u5165\u9875\u9762'
const DEFAULT_LOADING_DESCRIPTION = '\u754c\u9762\u5185\u5bb9\u6b63\u5728\u51c6\u5907\u4e2d\uff0c\u8bf7\u7a0d\u5019\u3002'

interface RouteLoadingState {
  visible: boolean
  pendingNavigation: boolean
  pendingRequests: number
  targetPath: string
  title: string
  description: string
  activatedAt: number
  navigationToken: number
  hideTimer: ReturnType<typeof setTimeout> | null
}

const requestCounter = new Map<number, number>()

const state = reactive<RouteLoadingState>({
  visible: false,
  pendingNavigation: false,
  pendingRequests: 0,
  targetPath: '',
  title: DEFAULT_LOADING_TITLE,
  description: DEFAULT_LOADING_DESCRIPTION,
  activatedAt: 0,
  navigationToken: 0,
  hideTimer: null,
})

const clearHideTimer = () => {
  if (state.hideTimer) {
    clearTimeout(state.hideTimer)
    state.hideTimer = null
  }
}

const syncPendingRequests = () => {
  state.pendingRequests = requestCounter.get(state.navigationToken) ?? 0
}

const finalizeLoading = () => {
  clearHideTimer()
  requestCounter.delete(state.navigationToken)
  state.visible = false
  state.pendingNavigation = false
  state.pendingRequests = 0
  state.targetPath = ''
  state.activatedAt = 0
  state.title = DEFAULT_LOADING_TITLE
  state.description = DEFAULT_LOADING_DESCRIPTION
}

const scheduleHideIfIdle = () => {
  if (!state.visible || state.pendingNavigation || state.pendingRequests > 0) {
    return
  }

  clearHideTimer()
  const delay = Math.max(0, MIN_VISIBLE_MS - (Date.now() - state.activatedAt))
  state.hideTimer = setTimeout(() => {
    if (state.pendingNavigation || state.pendingRequests > 0) {
      return
    }
    finalizeLoading()
  }, delay)
}

export const startRouteLoading = (
  targetPath: string,
  options?: {
    title?: string
    description?: string
  },
) => {
  clearHideTimer()
  state.navigationToken += 1
  state.visible = true
  state.pendingNavigation = true
  state.pendingRequests = 0
  state.targetPath = targetPath
  state.title = options?.title ?? DEFAULT_LOADING_TITLE
  state.description = options?.description ?? DEFAULT_LOADING_DESCRIPTION
  state.activatedAt = Date.now()
  requestCounter.set(state.navigationToken, 0)
}

export const resolveRouteLoading = (targetPath: string) => {
  if (!state.visible || (state.targetPath && targetPath !== state.targetPath)) {
    return
  }
  state.pendingNavigation = false
  syncPendingRequests()
  scheduleHideIfIdle()
}

export const cancelRouteLoading = () => {
  finalizeLoading()
}

export const beginRouteLoadingRequest = () => {
  if (!state.visible) {
    return null
  }

  const token = state.navigationToken
  requestCounter.set(token, (requestCounter.get(token) ?? 0) + 1)
  syncPendingRequests()
  return token
}

export const finishRouteLoadingRequest = (token: number | null | undefined) => {
  if (!token) {
    return
  }

  const count = requestCounter.get(token) ?? 0
  if (count <= 1) {
    requestCounter.delete(token)
  } else {
    requestCounter.set(token, count - 1)
  }

  if (token !== state.navigationToken) {
    return
  }

  syncPendingRequests()
  scheduleHideIfIdle()
}

export const useRouteLoading = () => ({
  state: readonly(state),
  visible: computed(() => state.visible),
  resolve: resolveRouteLoading,
  cancel: cancelRouteLoading,
})
