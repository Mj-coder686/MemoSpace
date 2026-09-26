<script setup lang="ts">
import { ref } from 'vue'
import { AlarmClock, Bell, MessageCircle, Search } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import { isDomainActive, webPrimaryNavigation } from '../../config/navigation'
import { UiIconButton } from '../ui'
import UserMenu from './UserMenu.vue'

const props = withDefaults(defineProps<{ unreadNotifications?: number; realtimeConnected?: boolean }>(), { unreadNotifications: 0, realtimeConnected: false })
const emit = defineEmits<{ notificationsOpened: [] }>()
const route = useRoute()
const router = useRouter()
const search = ref('')
const runSearch = () => {
  const query = search.value.trim()
  if (query) void router.push({ path: '/memories', query: { q: query } })
}
const openNotifications = () => { emit('notificationsOpened'); void router.push('/notifications') }
</script>

<template>
  <header class="shell-web-topbar">
    <router-link to="/home" class="shell-brand" aria-label="拾光空间首页">
      <span class="shell-brand__mark" aria-hidden="true">拾</span>
      <span class="shell-brand__copy"><strong>拾光空间</strong><small>MEMOSPACE</small></span>
    </router-link>
    <nav class="shell-primary-nav" aria-label="主导航">
      <router-link v-for="item in webPrimaryNavigation" :key="item.to" :to="item.to" :class="{ 'is-active': isDomainActive(route.path, item.domain) }" :aria-current="isDomainActive(route.path, item.domain) ? 'page' : undefined">{{ item.label }}</router-link>
    </nav>
    <div class="shell-topbar-actions">
      <form class="shell-search" role="search" @submit.prevent="runSearch">
        <Search :size="17" aria-hidden="true" />
        <input v-model="search" aria-label="搜索记忆" placeholder="搜索记忆" />
      </form>
      <UiIconButton label="重要提醒" variant="ghost" @click="router.push('/reminders')"><AlarmClock :size="19" /></UiIconButton>
      <UiIconButton label="好友与消息" variant="ghost" @click="router.push('/friends')">
        <MessageCircle :size="19" /><span v-if="realtimeConnected" class="shell-realtime-indicator" aria-hidden="true" /><span class="visually-hidden">{{ realtimeConnected ? '实时连接正常' : '实时连接未建立' }}</span>
      </UiIconButton>
      <UiIconButton label="通知" variant="ghost" :badge="props.unreadNotifications" @click="openNotifications"><Bell :size="19" /></UiIconButton>
      <UserMenu />
    </div>
  </header>
</template>
