const TOKEN_KEY = 'knowflow.token'

export class ApiError extends Error {
  constructor(status, code, message) {
    super(message || '请求失败')
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

export async function request(path, options = {}) {
  const { method = 'GET', body, headers = {}, skipAuthRedirect = false } = options
  const token = getToken()
  const nextHeaders = { ...headers }
  if (token) {
    nextHeaders.Authorization = `Bearer ${token}`
  }
  const isForm = body instanceof FormData
  if (body != null && !isForm && !nextHeaders['Content-Type']) {
    nextHeaders['Content-Type'] = 'application/json; charset=utf-8'
  }

  const response = await fetch(path, {
    method,
    headers: nextHeaders,
    body: isForm ? body : body != null ? JSON.stringify(body) : undefined,
  })

  if (response.status === 204) {
    return null
  }

  const text = await response.text()
  let data = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = { message: text }
    }
  }

  if (!response.ok) {
    if (response.status === 401 && !skipAuthRedirect && !path.startsWith('/api/auth/')) {
      clearToken()
      if (!location.hash.startsWith('#/login')) {
        location.hash = '#/login'
      }
    }
    throw new ApiError(response.status, data?.code, data?.message || `HTTP ${response.status}`)
  }
  return data
}

export const http = {
  get(path, options) {
    return request(path, { ...options, method: 'GET' })
  },
  post(path, body, options) {
    return request(path, { ...options, method: 'POST', body })
  },
  put(path, body, options) {
    return request(path, { ...options, method: 'PUT', body })
  },
  delete(path, options) {
    return request(path, { ...options, method: 'DELETE' })
  },
}
