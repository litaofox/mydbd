import http from './http'

export interface NotifyMessage {
  id: string
  title: string
  content: string
  level: number
  eventType: string
  bizType: string
  bizId: string
  isRead: number
  createDate: string
  readDate: string | null
}

export interface NotifySettings {
  popupMinLevel: number
  soundEnabled: boolean
}

export interface PageResult<T> {
  total: number
  page: number
  size: number
  records: T[]
}

export function listMessages(params: {
  page?: number
  size?: number
  unreadOnly?: boolean
}): Promise<PageResult<NotifyMessage>> {
  return http.get('/api/notify/messages', { params })
}

export function latestMessages(limit = 10): Promise<NotifyMessage[]> {
  return http.get('/api/notify/latest', { params: { limit } })
}

export function unreadCount(): Promise<{ count: number }> {
  return http.get('/api/notify/unread-count')
}

export function markRead(id: string): Promise<void> {
  return http.post(`/api/notify/messages/${id}/read`)
}

export function readAll(): Promise<{ updated: number }> {
  return http.post('/api/notify/messages/read-all')
}

export function deleteMessage(id: string): Promise<void> {
  return http.delete(`/api/notify/messages/${id}`)
}

export function getNotifySettings(): Promise<NotifySettings> {
  return http.get('/api/notify/settings')
}
