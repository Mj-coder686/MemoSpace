import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { websocketUrl } from '../utils/serverConnection'

export type RealtimeEvent = {
  type: string
  userId?: number
  friendId?: number
  online?: boolean
  onlineFriendIds?: number[]
  [key: string]: unknown
}

type Listener = (event: RealtimeEvent) => void

export const useRealtimeStore = defineStore('realtime', () => {
  const socket = ref<WebSocket | null>(null)
  const transportConnected = ref(false)
  const authenticated = ref(false)
  const onlineFriendIds = ref<number[]>([])
  const lastEvent = ref<RealtimeEvent | null>(null)
  const listeners = new Set<Listener>()
  let reconnectTimer: number | undefined
  let heartbeatTimer: number | undefined
  let reconnectAttempt = 0
  let intentionallyClosed = false

  const connected = computed(() => transportConnected.value && authenticated.value)

  const notify = (event: RealtimeEvent) => {
    lastEvent.value = event
    for (const listener of listeners) listener(event)
  }

  const updatePresence = (event: RealtimeEvent) => {
    if (event.type === 'AUTH_OK' && Array.isArray(event.onlineFriendIds)) {
      onlineFriendIds.value = event.onlineFriendIds.map(Number)
      return
    }
    if (event.type !== 'PRESENCE') return
    const id = Number(event.userId ?? event.friendId)
    if (!id) return
    const next = new Set(onlineFriendIds.value)
    event.online ? next.add(id) : next.delete(id)
    onlineFriendIds.value = [...next]
  }

  const scheduleReconnect = () => {
    if (intentionallyClosed || !localStorage.getItem('memospace_token')) return
    window.clearTimeout(reconnectTimer)
    const delay = Math.min(12_000, 800 * 2 ** reconnectAttempt++)
    reconnectTimer = window.setTimeout(connect, delay)
  }

  const startHeartbeat = () => {
    window.clearInterval(heartbeatTimer)
    heartbeatTimer = window.setInterval(() => send('PING', { nonce: Date.now() }), 25_000)
  }

  const connect = () => {
    const token = localStorage.getItem('memospace_token')
    if (!token || socket.value?.readyState === WebSocket.OPEN || socket.value?.readyState === WebSocket.CONNECTING) return
    const endpoint = websocketUrl()
    if (!endpoint) {
      notify({ type: 'CONNECTION_ERROR', message: '请先在登录页设置服务器地址' })
      return
    }
    intentionallyClosed = false
    const ws = new WebSocket(endpoint)
    socket.value = ws
    ws.onopen = () => {
      transportConnected.value = true
      reconnectAttempt = 0
      ws.send(JSON.stringify({ type: 'AUTH', token }))
    }
    ws.onmessage = message => {
      try {
        const event = JSON.parse(String(message.data)) as RealtimeEvent
        if (event.type === 'AUTH_OK') {
          authenticated.value = true
          startHeartbeat()
        }
        updatePresence(event)
        notify(event)
      } catch {
        notify({ type: 'PROTOCOL_ERROR', message: '无法解析实时消息' })
      }
    }
    ws.onerror = () => notify({ type: 'CONNECTION_ERROR' })
    ws.onclose = () => {
      if (socket.value === ws) socket.value = null
      transportConnected.value = false
      authenticated.value = false
      onlineFriendIds.value = []
      window.clearInterval(heartbeatTimer)
      scheduleReconnect()
    }
  }

  const disconnect = () => {
    intentionallyClosed = true
    window.clearTimeout(reconnectTimer)
    window.clearInterval(heartbeatTimer)
    socket.value?.close()
    socket.value = null
    transportConnected.value = false
    authenticated.value = false
    onlineFriendIds.value = []
  }

  const send = (type: string, payload: Record<string, unknown> = {}) => {
    if (!connected.value || socket.value?.readyState !== WebSocket.OPEN) return false
    socket.value.send(JSON.stringify({ type, ...payload }))
    return true
  }

  const subscribe = (listener: Listener) => {
    listeners.add(listener)
    return () => listeners.delete(listener)
  }

  const isOnline = (userId: number) => onlineFriendIds.value.includes(Number(userId))

  return { connected, authenticated, onlineFriendIds, lastEvent, connect, disconnect, send, subscribe, isOnline }
})
