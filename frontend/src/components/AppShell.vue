<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { AlarmClock, Bell, BookHeart, CalendarDays, Compass, Home, Images, MapPin, MessageCircle, Plus, Search, Settings, Sparkles, Users } from 'lucide-vue-next'
import { useAuthStore } from '../stores/auth'
import { useRealtimeStore } from '../stores/realtime'
import CreateMemoryModal from './CreateMemoryModal.vue'

const auth = useAuthStore()
const realtime = useRealtimeStore()
const router = useRouter()
const creating = ref(false)
const search = ref('')

const runSearch = () => {
  if (search.value.trim()) router.push({ path: '/memories', query: { q: search.value.trim() } })
}

onMounted(() => realtime.connect())
onBeforeUnmount(() => realtime.disconnect())
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
        <button class="icon-button" aria-label="通知" @click="router.push('/notifications')"><Bell :size="19" /></button>
        <button class="avatar-button" @click="router.push(`/user/${auth.user?.id}`)">
          <span>{{ auth.user?.nickname?.slice(0, 1) || '拾' }}</span>
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
  </div>
</template>
