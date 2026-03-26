import http from './http'
import type { AuthTokenResponse } from '@/types/models'

export interface CaptchaResponse {
  captchaId: string
  captchaImage: string
}

export const getCaptcha = () => http.get<never, CaptchaResponse>('/v1/auth/captcha')

export const login = (payload: {
  username: string
  password: string
  captchaId: string
  captchaCode: string
}) => http.post<never, AuthTokenResponse>('/v1/auth/login', payload)

export const register = (payload: {
  username: string
  email: string
  password: string
  displayName?: string
  captchaId: string
  captchaCode: string
}) => http.post<never, AuthTokenResponse>('/v1/auth/register', payload)

export const refresh = (refreshToken: string) =>
  http.post<never, AuthTokenResponse>('/v1/auth/refresh', { refreshToken })

export const logout = (refreshToken: string) =>
  http.post('/v1/auth/logout', { refreshToken })
