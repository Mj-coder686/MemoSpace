import { Capacitor } from '@capacitor/core'

const SERVER_ORIGIN_KEY = 'memospace_server_origin'

const trimTrailingSlashes = (value: string) => value.replace(/\/+$/, '')

export const isNativeApp = () => Capacitor.isNativePlatform()

export const normalizeServerOrigin = (value: string) => {
  const raw = trimTrailingSlashes(value.trim().replace(/\/api$/i, ''))
  if (!raw) return ''
  const parsed = new URL(raw)
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') throw new Error('服务器地址必须以 http:// 或 https:// 开头')
  return trimTrailingSlashes(parsed.origin + parsed.pathname.replace(/\/$/, ''))
}

export const savedServerOrigin = () => {
  const saved = localStorage.getItem(SERVER_ORIGIN_KEY)
  if (saved) return saved
  const configured = import.meta.env.VITE_NATIVE_SERVER_URL as string | undefined
  return configured ? normalizeServerOrigin(configured) : ''
}

export const saveServerOrigin = (value: string) => {
  const normalized = normalizeServerOrigin(value)
  if (!normalized) localStorage.removeItem(SERVER_ORIGIN_KEY)
  else localStorage.setItem(SERVER_ORIGIN_KEY, normalized)
  window.dispatchEvent(new CustomEvent('memospace-server-changed'))
  return normalized
}

export const apiBaseUrl = () => {
  if (isNativeApp()) {
    const origin = savedServerOrigin()
    return origin ? `${origin}/api` : '/api'
  }
  return (import.meta.env.VITE_API_BASE as string | undefined) || '/api'
}

export const websocketUrl = () => {
  const configured = import.meta.env.VITE_WS_URL as string | undefined
  if (configured && !isNativeApp()) return configured
  if (isNativeApp()) {
    const origin = savedServerOrigin()
    if (!origin) return ''
    return `${origin.replace(/^http:/, 'ws:').replace(/^https:/, 'wss:')}/ws/chat`
  }
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${location.host}/ws/chat`
}

export const requiresServerConfiguration = () => isNativeApp() && !savedServerOrigin()
