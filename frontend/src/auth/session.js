import { computed, reactive } from 'vue'
import { fetchCurrentUser, logout as clearSession } from '../api/auth.js'
import { getToken } from '../api/http.js'

const state = reactive({
  user: null,
  loading: false,
  error: '',
  ready: false,
})

export const currentUser = computed(() => state.user)
export const isAuthenticated = computed(() => Boolean(state.user))
export const sessionLoading = computed(() => state.loading)
export const userRole = computed(() => state.user?.role || 'CUSTOMER')

export const isCustomer = computed(() => userRole.value === 'CUSTOMER')
export const isAgent = computed(() => userRole.value === 'AGENT')
export const isAdmin = computed(() => userRole.value === 'ADMIN')

export async function bootstrapSession() {
  if (!getToken()) {
    state.user = null
    state.ready = true
    return
  }
  state.loading = true
  state.error = ''
  try {
    state.user = await fetchCurrentUser()
  } catch (error) {
    state.user = null
    state.error = error.message
  } finally {
    state.loading = false
    state.ready = true
  }
}

export async function refreshSession() {
  state.user = await fetchCurrentUser()
}

export function setSessionUser(user) {
  state.user = user
}

export function logout() {
  clearSession()
  state.user = null
  location.hash = '#/login'
}

export const session = state
