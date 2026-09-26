<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Bell, Plus } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useRealtimeStore } from '../stores/realtime'
import type { RealtimeEvent } from '../stores/realtime'
import http from '../api/http'
import { secondaryNavigationFor } from '../config/navigation'
import { registerOverlay } from '../composables/useOverlayStack'
import { loadAppearance } from '../utils/appearance'
import BottomNavigation from './shell/BottomNavigation.vue'
import CompactTopBar from './shell/CompactTopBar.vue'
import SecondaryNavigation from './shell/SecondaryNavigation.vue'
import WebTopBar from './shell/WebTopBar.vue'
import CreateMemoryModal from './CreateMemoryModal.vue'
import { UiButton } from './ui'

const auth = useAuthStore()
const realtime = useRealtimeStore()
const router = useRouter()
const route = useRoute()
const creating = ref(false)
const createMemoryType = ref<'TEXT' | 'PHOTO' | 'VIDEO' | 'LOCATION'>('TEXT')
const unreadNotifications = ref(0)
const liveNotice = ref<{ title: string; content: string; path: string } | null>(null)
const fabCondensed = ref(false)
const secondaryItems = computed(() => secondaryNavigationFor(route.path))
let unsubscribeRealtime: (() => void) | undefined
let unregisterCreateOverlay: (() => void) | undefined
let noticeTimer: number | undefined

const clearUnread = () => { unreadNotifications.value = 0 }
const navigate = (path: string) => {
  liveNotice.value = null
  void router.push(path)
}
const showLiveNotice = (event: RealtimeEvent) => {
  if (event.type !== 'NOTIFICATION' && event.type !== 'REMINDER_DUE') return
  unreadNotifications.value += 1
  const reminder = event.type === 'REMINDER_DUE'
  liveNotice.value = {
    title: String(event.title || (reminder ? '提醒时间到了' : '收到新消息')),
    content: String(event.content || event.note || (reminder ? '点开查看这条重要提醒' : '共同空间有了新动静')),
    path: reminder ? '/reminders'
      : event.notificationType === 'FRIEND_REQUEST' || event.notificationType === 'FRIEND_ACCEPT' ? '/friends'
      : event.notificationType === 'SPACE_MEMORY' && event.referenceId ? `/memory/${event.referenceId}` : '/notifications',
  }
  window.clearTimeout(noticeTimer)
  noticeTimer = window.setTimeout(() => { liveNotice.value = null }, 6000)
}
const updateFab = () => { fabCondensed.value = window.scrollY > 96 }
const openCreate = (event?: Event) => {
  const requested = event instanceof CustomEvent ? event.detail?.memoryType : undefined
  createMemoryType.value = ['TEXT', 'PHOTO', 'VIDEO', 'LOCATION'].includes(requested) ? requested : 'TEXT'
  creating.value = true
}
const openDefaultCreate = () => openCreate()

watch(() => route.fullPath, () => {
  liveNotice.value = null
  const active = document.activeElement
  if (active instanceof HTMLElement) active.blur()
})
watch(creating, (open) => {
  unregisterCreateOverlay?.()
  unregisterCreateOverlay = open ? registerOverlay(() => { creating.value = false }) : undefined
})

onMounted(async () => {
  window.addEventListener('scroll', updateFab, { passive: true })
  window.addEventListener('memospace-open-create', openCreate)
  updateFab()
  if (!auth.token) return
  realtime.connect()
  unsubscribeRealtime = realtime.subscribe(showLiveNotice)
  try { await loadAppearance() } catch { /* keep the locally cached appearance */ }
  try { unreadNotifications.value = (await http.get('/notifications')).data.filter((item: any) => !item.is_read).length } catch { unreadNotifications.value = 0 }
  window.addEventListener('memospace-notifications-read', clearUnread)
})
onBeforeUnmount(() => {
  unsubscribeRealtime?.()
  unregisterCreateOverlay?.()
  window.clearTimeout(noticeTimer)
  window.removeEventListener('scroll', updateFab)
  window.removeEventListener('memospace-open-create', openCreate)
  window.removeEventListener('memospace-notifications-read', clearUnread)
  realtime.disconnect()
})
</script>

<template>
  <div class="app-shell">
    <WebTopBar :unread-notifications="unreadNotifications" :realtime-connected="realtime.connected" @notifications-opened="clearUnread" />
    <CompactTopBar :unread-notifications="unreadNotifications" @notifications-opened="clearUnread" />
    <SecondaryNavigation :items="secondaryItems" />

    <main id="main-content" class="shell-main"><slot /></main>

    <UiButton v-if="route.path !== '/home'" class="shell-create-fab" :class="{ 'is-condensed': fabCondensed }" variant="primary" aria-label="记录此刻" @click="openDefaultCreate">
      <Plus :size="21" /><span>记录此刻</span>
    </UiButton>
    <BottomNavigation @create="openDefaultCreate" />

    <CreateMemoryModal v-if="creating" :initial-type="createMemoryType" @close="creating = false" />
    <button v-if="liveNotice" class="shell-live-notice" type="button" aria-live="polite" @click="navigate(liveNotice.path)">
      <span class="shell-live-notice__icon"><Bell :size="19" /></span>
      <span><strong>{{ liveNotice.title }}</strong><span>{{ liveNotice.content }}</span></span>
    </button>
  </div>
</template>
