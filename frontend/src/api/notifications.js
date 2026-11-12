import { http } from './http.js'

export function listMyNotifications(page = 1, size = 10) {
  const query = new URLSearchParams({
    page: String(page),
    size: String(size),
  })
  return http.get(`/api/notifications/me?${query.toString()}`)
}
