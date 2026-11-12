import { http } from './http.js'

export function listTickets(page = 1, size = 10) {
  const query = new URLSearchParams({
    page: String(page),
    size: String(size),
  })
  return http.get(`/api/tickets?${query.toString()}`)
}

export function getTicket(id) {
  return http.get(`/api/tickets/${id}`)
}

export function createTicket(payload) {
  return http.post('/api/tickets', payload)
}

export function assignTicket(id, agentUserId) {
  return http.post(`/api/tickets/${id}/assign`, { agentUserId })
}

export function claimTicket(id) {
  return http.post(`/api/tickets/${id}/claim`)
}

export function handleTicket(id, remark) {
  return http.post(`/api/tickets/${id}/handle`, { remark })
}

export function resolveTicket(id, remark) {
  return http.post(`/api/tickets/${id}/resolve`, { remark })
}

export function closeTicket(id, remark) {
  return http.post(`/api/tickets/${id}/close`, { remark })
}

export function listTicketOperations(id) {
  return http.get(`/api/tickets/${id}/operations`)
}
