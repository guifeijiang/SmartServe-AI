import { reactive } from 'vue'

const routes = [
  { path: '/login', name: 'login', public: true },
  { path: '/register', name: 'register', public: true },
  { path: '/notifications', name: 'notifications' },
  { path: '/tickets', name: 'tickets' },
  { path: '/tickets/new', name: 'ticket-create' },
  { path: '/tickets/:id', name: 'ticket-detail' },
  { path: '/knowledge', name: 'knowledge' },
  { path: '/knowledge/:id/chat', name: 'knowledge-chat' },
  { path: '/knowledge/:id', name: 'knowledge-detail' },
]

export const route = reactive({
  name: 'tickets',
  path: '/tickets',
  params: {},
  public: false,
})

function matchRoute(pathname) {
  const clean = pathname.replace(/\/+$/, '') || '/'
  for (const item of routes) {
    const keys = []
    const pattern = item.path.replace(/:([A-Za-z]+)/g, (_, key) => {
      keys.push(key)
      return '([^/]+)'
    })
    const regex = new RegExp(`^${pattern}$`)
    const found = clean.match(regex)
    if (found) {
      const params = {}
      keys.forEach((key, index) => {
        params[key] = decodeURIComponent(found[index + 1])
      })
      return { ...item, path: clean, params }
    }
  }
  return null
}

export function parseHash() {
  const raw = (location.hash || '#/tickets').replace(/^#/, '')
  const pathname = raw.split('?')[0] || '/tickets'
  const matched = matchRoute(pathname)
  if (!matched) {
    route.name = 'tickets'
    route.path = '/tickets'
    route.params = {}
    route.public = false
    if (location.hash !== '#/tickets') {
      location.hash = '#/tickets'
    }
    return
  }
  route.name = matched.name
  route.path = matched.path
  route.params = matched.params
  route.public = Boolean(matched.public)
}

export function push(path) {
  const next = path.startsWith('#') ? path : `#${path}`
  if (location.hash === next) {
    parseHash()
    return
  }
  location.hash = next
}

window.addEventListener('hashchange', parseHash)
parseHash()
