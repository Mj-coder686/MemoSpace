import { Capacitor } from '@capacitor/core'

const SERVER_ORIGIN_KEY = 'memospace_server_origin'
export const PRODUCTION_SERVER_ORIGIN = 'https://memospace.fun'

const trimTrailingSlashes = (value: string) => value.replace(/\/+$/, '')

export const isNativeApp = () => Capacitor.isNativePlatform()

export const normalizeServerOrigin = (value: string) => {
  const raw = trimTrailingSlashes(value.trim().replace(/\/api$/i, ''))
  if (!raw) return ''
  const parsed = new URL(raw)
  if (parsed.protocol !== 'https:') throw new Error('服务器地址必须使用 https:// 安全连接')
  return trimTrailingSlashes(parsed.origin + parsed.pathname.replace(/\/$/, ''))
}

export const savedServerOrigin = () => {
  const saved = localStorage.getItem(SERVER_ORIGIN_KEY)
  const configured = import.meta.env.VITE_NATIVE_SERVER_URL as string | undefined
  const fallback = normalizeServerOrigin(configured || PRODUCTION_SERVER_ORIGIN)
  if (!saved) return fallback
  try {
    return normalizeServerOrigin(saved)
  } catch {
    localStorage.removeItem(SERVER_ORIGIN_KEY)
    return fallback
  }
}

export const saveServerOrigin = (value: string) => {
  const normalized = normalizeServerOrigin(value)
  if (!normalized || normalized === PRODUCTION_SERVER_ORIGIN) localStorage.removeItem(SERVER_ORIGIN_KEY)
  else localStorage.setItem(SERVER_ORIGIN_KEY, normalized)
  window.dispatchEvent(new CustomEvent('memospace-server-changed'))
  return normalized || PRODUCTION_SERVER_ORIGIN
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

export const requiresServerConfiguration = () => false
