import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getMe, updateMe } from '@/api/user'
import { login as loginApi, logout as logoutApi, refresh as refreshApi } from '@/api/auth'
import type { AuthTokenResponse, UserView } from '@/types/models'

const ACCESS_TOKEN_KEY = 'erise-access-token'
const REFRESH_TOKEN_KEY = 'erise-refresh-token'
const USER_KEY = 'erise-user'

function persistSession(payload: AuthTokenResponse) {
  localStorage.setItem(ACCESS_TOKEN_KEY, payload.accessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, payload.refreshToken)
  localStorage.setItem(USER_KEY, JSON.stringify(payload.user))
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserView | null>(JSON.parse(localStorage.getItem(USER_KEY) || 'null'))
  const accessToken = ref<string | null>(localStorage.getItem(ACCESS_TOKEN_KEY))
  const refreshToken = ref<string | null>(localStorage.getItem(REFRESH_TOKEN_KEY))

  const isAuthenticated = computed(() => Boolean(accessToken.value))
  const isAdmin = computed(() => user.value?.roleCode === 'ADMIN')

  async function login(payload: { username: string; password: string; captchaId: string; captchaCode: string }) {
    const session = await loginApi(payload)
    applySession(session)
  }

  function applySession(session: AuthTokenResponse) {
    accessToken.value = session.accessToken
    refreshToken.value = session.refreshToken
    user.value = session.user
    persistSession(session)
  }

  async function hydrate() {
    if (!accessToken.value) {
      return
    }
    try {
      user.value = await getMe()
      localStorage.setItem(USER_KEY, JSON.stringify(user.value))
    } catch {
      if (refreshToken.value) {
        const session = await refreshApi(refreshToken.value)
        applySession(session)
      } else {
        clear()
      }
    }
  }

  async function updateProfile(payload: { displayName: string; email: string; avatarUrl?: string; bio?: string }) {
    user.value = await updateMe(payload)
    localStorage.setItem(USER_KEY, JSON.stringify(user.value))
  }

  async function logout() {
    if (refreshToken.value) {
      await logoutApi(refreshToken.value)
    }
    clear()
  }

  function clear() {
    user.value = null
    accessToken.value = null
    refreshToken.value = null
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return {
    user,
    accessToken,
    refreshToken,
    isAuthenticated,
    isAdmin,
    login,
    applySession,
    hydrate,
    updateProfile,
    logout,
    clear,
  }
})
