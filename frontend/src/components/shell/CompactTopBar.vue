<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowLeft, Bell, Search, X } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import { compactBackFallback, pageTitleFor } from '../../config/navigation'
import { UiButton, UiIconButton } from '../ui'
import UserMenu from './UserMenu.vue'

const props = withDefaults(defineProps<{ unreadNotifications?: number }>(), { unreadNotifications: 0 })
const emit = defineEmits<{ notificationsOpened: [] }>()
const route = useRoute()
const router = useRouter()
const searching = ref(false)
const search = ref('')
const title = computed(() => pageTitleFor(route))
const fallback = computed(() => compactBackFallback(route.path))
const goBack = () => {
  if (router.options.history.state.back) router.back()
  else if (fallback.value) void router.push(fallback.value)
}
const runSearch = () => {
  const query = search.value.trim()
  if (!query) return
  searching.value = false
  void router.push({ path: '/memories', query: { q: query } })
}
const openNotifications = () => { emit('notificationsOpened'); void router.push('/notifications') }
</script>

<template>
  <header class="shell-compact-topbar" :class="{ 'is-searching': searching }">
    <div class="shell-compact-topbar__row">
      <div class="shell-compact-title">
        <UiIconButton v-if="fallback" label="返回" size="sm" variant="ghost" @click="goBack"><ArrowLeft :size="20" /></UiIconButton>
        <h1>{{ title }}</h1>
      </div>
      <div class="shell-compact-actions">
        <UiIconButton :label="searching ? '关闭搜索' : '搜索记忆'" size="sm" variant="ghost" @click="searching = !searching"><X v-if="searching" :size="19" /><Search v-else :size="19" /></UiIconButton>
        <UiIconButton label="通知" size="sm" variant="ghost" :badge="props.unreadNotifications" @click="openNotifications"><Bell :size="19" /></UiIconButton>
        <UserMenu compact />
      </div>
    </div>
    <form v-if="searching" class="shell-compact-search" role="search" @submit.prevent="runSearch">
      <label class="shell-search"><Search :size="17" aria-hidden="true" /><input v-model="search" aria-label="搜索记忆" placeholder="标题、内容或地点" autofocus /></label>
      <UiButton type="submit" variant="primary">搜索</UiButton>
    </form>
  </header>
</template>
