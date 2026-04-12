import http from './http'
import type { UserView } from '@/types/models'

export const getMe = () => http.get<never, UserView>('/v1/users/me')

export const updateMe = (payload: {
  displayName: string
  email: string
  avatarUrl?: string
  bio?: string
}) => http.put<never, UserView>('/v1/users/me', payload)

export const updatePassword = (payload: { oldPassword: string; newPassword: string }) =>
  http.put('/v1/users/password', payload)

export const deleteMe = (payload: { password: string }) =>
  http.delete('/v1/users/me', { data: payload })
