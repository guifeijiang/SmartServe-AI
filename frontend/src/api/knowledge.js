import { http, request } from './http.js'

export function listKnowledgeBases() {
  return http.get('/api/knowledge-bases')
}

export function getKnowledgeBase(id) {
  return http.get(`/api/knowledge-bases/${id}`)
}

export function createKnowledgeBase(payload) {
  return http.post('/api/knowledge-bases', payload)
}

export function updateKnowledgeBase(id, payload) {
  return http.put(`/api/knowledge-bases/${id}`, payload)
}

export function deleteKnowledgeBase(id) {
  return http.delete(`/api/knowledge-bases/${id}`)
}

export function listDocuments(knowledgeBaseId) {
  return http.get(`/api/knowledge-bases/${knowledgeBaseId}/documents`)
}

export function uploadDocument(knowledgeBaseId, file) {
  const body = new FormData()
  body.append('file', file)
  return request(`/api/knowledge-bases/${knowledgeBaseId}/documents`, {
    method: 'POST',
    body,
  })
}

export function processDocument(knowledgeBaseId, documentId) {
  return http.post(`/api/knowledge-bases/${knowledgeBaseId}/documents/${documentId}/process`)
}

export function deleteDocument(knowledgeBaseId, documentId) {
  return http.delete(`/api/knowledge-bases/${knowledgeBaseId}/documents/${documentId}`)
}

export function createChatSession(knowledgeBaseId, title) {
  return http.post(`/api/knowledge-bases/${knowledgeBaseId}/sessions`, title ? { title } : {})
}

export function listChatSessions(knowledgeBaseId) {
  return http.get(`/api/knowledge-bases/${knowledgeBaseId}/sessions`)
}

export function listChatMessages(sessionId) {
  return http.get(`/api/chat-sessions/${sessionId}/messages`)
}

export function askKnowledgeBase(knowledgeBaseId, message, sessionId) {
  return http.post(`/api/knowledge-bases/${knowledgeBaseId}/ask`, { message, sessionId })
}
