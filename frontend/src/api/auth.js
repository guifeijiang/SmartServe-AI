import { http, setToken, clearToken } from './http.js'

export function register(payload) {
  return http.post('/api/auth/register', payload)
}

export async function login(payload) {
  const data = await http.post('/api/auth/login', payload, { skipAuthRedirect: true })
  setToken(data.token)
  return data
}

export function logout() {
  clearToken()
}

export function fetchCurrentUser() {
  return http.get('/api/users/me')
}
