import http from './http'
import type { NotificationUnreadCountView, PageResponse, UserNotificationView } from '@/types/models'

export interface NotificationQuery {
  pageNum?: number
  pageSize?: number
  unreadOnly?: boolean
}

export interface AdminNotificationPayload {
  title: string
  content: string
  sendToAll: boolean
  userIds?: number[]
}

export const getMyNotifications = (params: NotificationQuery) =>
  http.get<never, PageResponse<UserNotificationView>>('/v1/notifications', { params })

export const getNotificationUnreadCount = () =>
  http.get<never, NotificationUnreadCountView>('/v1/notifications/unread-count')

export const markNotificationRead = (id: number) => http.post(`/v1/notifications/${id}/read`)

export const markAllNotificationsRead = () => http.post('/v1/notifications/read-all')

export const sendAdminNotification = (payload: AdminNotificationPayload) =>
  http.post('/v1/admin/notifications', payload)
