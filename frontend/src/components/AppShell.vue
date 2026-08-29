<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { AlarmClock, Bell, BookHeart, CalendarDays, Compass, Home, Images, MapPin, MessageCircle, Plus, Search, Settings, Sparkles, Users } from 'lucide-vue-next'
import { useAuthStore } from '../stores/auth'
import { useRealtimeStore } from '../stores/realtime'
import type { RealtimeEvent } from '../stores/realtime'
import http from '../api/http'
import CreateMemoryModal from './CreateMemoryModal.vue'
import UserAvatar from './UserAvatar.vue'
import { loadAppearance } from '../utils/appearance'

const auth = useAuthStore()
const realtime = useRealtimeStore()
const router = useRouter()
const creating = ref(false)
const search = ref('')
const unreadNotifications = ref(0)
const liveNotice = ref<{ title:string; content:string; path:string } | null>(null)
let unsubscribeRealtime: (() => void) | undefined
let noticeTimer: number | undefined
const clearUnread = () => { unreadNotifications.value=0 }

const runSearch = () => {
  if (search.value.trim()) router.push({ path: '/memories', query: { q: search.value.trim() } })
}

const showLiveNotice = (event:RealtimeEvent) => {
  if (event.type !== 'NOTIFICATION' && event.type !== 'REMINDER_DUE') return
  unreadNotifications.value += 1
  const reminder = event.type === 'REMINDER_DUE'
  liveNotice.value = {
    title:String(event.title || (reminder ? '提醒时间到了' : '收到新消息')),
    content:String(event.content || event.note || (reminder ? '点开查看这条重要提醒' : '共同空间有了新动静')),
    path:reminder ? '/reminders'
      : event.notificationType === 'FRIEND_REQUEST' || event.notificationType === 'FRIEND_ACCEPT' ? '/friends'
      : event.notificationType === 'SPACE_MEMORY' && event.referenceId ? `/memory/${event.referenceId}` : '/notifications'
  }
  window.clearTimeout(noticeTimer)
  noticeTimer=window.setTimeout(()=>{liveNotice.value=null},6000)
}
const openNotice = () => { if(liveNotice.value) router.push(liveNotice.value.path); liveNotice.value=null }
onMounted(async () => {
  if (!auth.token) return
  realtime.connect()
  unsubscribeRealtime=realtime.subscribe(showLiveNotice)
  try { await loadAppearance() } catch { /* keep the locally cached appearance */ }
  try { unreadNotifications.value=(await http.get('/notifications')).data.filter((item:any)=>!item.is_read).length } catch { unreadNotifications.value=0 }
  window.addEventListener('memospace-notifications-read',clearUnread)
})
onBeforeUnmount(() => { unsubscribeRealtime?.();window.clearTimeout(noticeTimer);window.removeEventListener('memospace-notifications-read',clearUnread);realtime.disconnect() })
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <router-link to="/home" class="brand">
        <span class="brand-mark"><Sparkles :size="18" /></span>
        <span><b>拾光空间</b><small>MEMOSPACE</small></span>
      </router-link>
      <nav class="desktop-nav">
        <router-link to="/home">首页</router-link>
        <router-link to="/memories">记忆</router-link>
        <router-link to="/relationships">关系分类</router-link>
        <router-link to="/friends">好友</router-link>
        <router-link to="/reminders">提醒</router-link>
        <router-link to="/explore">动态</router-link>
      </nav>
      <div class="top-actions">
        <form class="search-box" @submit.prevent="runSearch">
          <Search :size="17" /><input v-model="search" placeholder="搜索一段记忆…" />
        </form>
        <button class="icon-button realtime-button" :class="{ connected: realtime.connected }" aria-label="好友聊天" @click="router.push('/friends')"><MessageCircle :size="19" /></button>
        <button class="icon-button notification-button" aria-label="通知" @click="unreadNotifications=0;router.push('/notifications')"><Bell :size="19" /><span v-if="unreadNotifications" class="notification-badge">{{ unreadNotifications>99?'99+':unreadNotifications }}</span></button>
        <button class="avatar-button" @click="router.push(`/user/${auth.user?.id}`)">
          <UserAvatar :src="auth.user?.avatar" :name="auth.user?.nickname" />
        </button>
      </div>
    </header>

    <main class="page-wrap"><slot /></main>

    <button class="create-fab desktop-fab" @click="creating = true"><Plus :size="21" />记录此刻</button>

    <nav class="mobile-nav">
      <router-link to="/home"><Home :size="21" /><span>首页</span></router-link>
      <router-link to="/explore"><Compass :size="21" /><span>动态</span></router-link>
      <button class="mobile-create" @click="creating = true"><Plus :size="25" /></button>
      <router-link to="/friends"><Users :size="21" /><span>好友</span></router-link>
      <router-link :to="`/user/${auth.user?.id}`"><BookHeart :size="21" /><span>我的</span></router-link>
    </nav>

    <aside class="quick-dock" aria-label="快捷入口">
      <router-link to="/photos" title="相册"><Images :size="18" /></router-link>
      <router-link to="/calendar" title="日历"><CalendarDays :size="18" /></router-link>
      <router-link to="/map" title="地图"><MapPin :size="18" /></router-link>
      <router-link to="/relationships" title="关系分类"><Users :size="18" /></router-link>
      <router-link to="/reminders" title="重要提醒"><AlarmClock :size="18" /></router-link>
      <router-link to="/settings" title="设置"><Settings :size="18" /></router-link>
    </aside>

    <CreateMemoryModal v-if="creating" @close="creating = false" />
    <button v-if="liveNotice" class="live-notice" role="status" @click="openNotice"><span><Bell :size="18" /></span><div><b>{{ liveNotice.title }}</b><p>{{ liveNotice.content }}</p></div></button>
  </div>
</template>
