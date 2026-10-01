import { Capacitor } from '@capacitor/core'
import { PushNotifications } from '@capacitor/push-notifications'
import type { Router } from 'vue-router'
import http from '../api/http'

const TOKEN_KEY = 'memospace_push_token'
const STATUS_KEY = 'memospace_push_status'
let initialized = false
let activeRouter: Router | null = null

const setStatus = (status: string) => {
  localStorage.setItem(STATUS_KEY, status)
  window.dispatchEvent(new CustomEvent('memospace:push-status', { detail: status }))
}

const destination = (type?: string, referenceId?: string) => {
  if (type === 'COMMENT' || type === 'SPACE_MEMORY') return referenceId ? `/memory/${referenceId}` : '/notifications'
  if (type === 'REMINDER_DUE') return '/reminders'
  if (type?.startsWith('FRIEND')) return '/friends'
  if (type?.startsWith('RELATIONSHIP') || type === 'ANNIVERSARY') return '/relationships'
  return '/notifications'
}

export const nativePushStatus = () => localStorage.getItem(STATUS_KEY) || 'idle'

export const enableNativePush = async (askPermission = true) => {
  if (!Capacitor.isNativePlatform()) return false
  let permission = await PushNotifications.checkPermissions()
  if (permission.receive !== 'granted' && askPermission) permission = await PushNotifications.requestPermissions()
  if (permission.receive !== 'granted') {
    setStatus('denied')
    return false
  }
  setStatus('registering')
  await PushNotifications.register()
  return true
}

export const initializeNativePush = async (router: Router) => {
  if (!Capacitor.isNativePlatform()) return
  activeRouter = router
  if (!initialized) {
    initialized = true
    await PushNotifications.addListener('registration', async ({ value }) => {
      localStorage.setItem(TOKEN_KEY, value)
      try {
        if (localStorage.getItem('memospace_token')) {
          const { data } = await http.post('/push/devices', { token: value, platform: 'ANDROID', deviceName: 'Android' })
          setStatus(data.providerConfigured ? 'ready' : 'device-ready')
        }
      } catch {
        setStatus('server-error')
      }
    })
    await PushNotifications.addListener('registrationError', () => setStatus('registration-error'))
    await PushNotifications.addListener('pushNotificationReceived', () => {
      window.dispatchEvent(new CustomEvent('memospace:notification-received'))
    })
    await PushNotifications.addListener('pushNotificationActionPerformed', ({ notification }) => {
      const data = notification.data || {}
      void activeRouter?.push(destination(String(data.notificationType || ''), String(data.referenceId || '')))
    })
    await PushNotifications.createChannel({
      id: 'memospace_updates',
      name: '拾光提醒',
      description: '好友、关系、评论和重要日期提醒',
      importance: 4,
      visibility: 0,
      vibration: true,
      lights: true,
      lightColor: '#8A7899',
    })
  }
  if (localStorage.getItem('memospace_token')) await enableNativePush(true)
}

export const refreshNativePushRegistration = async () => {
  if (!Capacitor.isNativePlatform() || !localStorage.getItem('memospace_token')) return
  await enableNativePush(true)
}

export const unregisterNativePush = async () => {
  if (!Capacitor.isNativePlatform()) return
  const token = localStorage.getItem(TOKEN_KEY)
  try {
    if (token && localStorage.getItem('memospace_token')) await http.delete('/push/devices', { data: { token } })
    await PushNotifications.unregister()
  } catch { /* Signing out must continue even if the server is offline. */ }
  localStorage.removeItem(TOKEN_KEY)
  setStatus('idle')
}
