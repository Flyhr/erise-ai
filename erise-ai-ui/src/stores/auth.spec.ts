import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from './auth'

describe('auth store', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('applies session into storage', () => {
    const store = useAuthStore()
    store.applySession({
      accessToken: 'a',
      refreshToken: 'r',
      user: {
        id: 1,
        username: 'admin',
        displayName: 'Admin',
        email: 'admin@example.com',
        roleCode: 'ADMIN',
      },
    })

    expect(store.isAuthenticated).toBe(true)
    expect(localStorage.getItem('erise-access-token')).toBe('a')
  })
})
