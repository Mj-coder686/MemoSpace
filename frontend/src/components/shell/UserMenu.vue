<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref } from 'vue'
import { AlarmClock, LogOut, Settings, UserRound } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import UserAvatar from '../UserAvatar.vue'
import { UiBottomSheet, UiButton } from '../ui'

const props = withDefaults(defineProps<{ compact?: boolean }>(), { compact: false })
const auth = useAuthStore()
const router = useRouter()
const open = ref(false)
const root = ref<HTMLElement | null>(null)

const close = () => { open.value = false }
const toggle = async () => {
  open.value = !open.value
  if (open.value) await nextTick()
}
const navigate = (path: string) => { close(); void router.push(path) }
const logout = () => { close(); auth.logout(); void router.replace('/login') }
const onDocumentPointer = (event: PointerEvent) => {
  if (props.compact) return
  if (open.value && root.value && !root.value.contains(event.target as Node)) close()
}
const onDocumentKey = (event: KeyboardEvent) => { if (event.key === 'Escape' && open.value) close() }
document.addEventListener('pointerdown', onDocumentPointer)
document.addEventListener('keydown', onDocumentKey)
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocumentPointer)
  document.removeEventListener('keydown', onDocumentKey)
})
</script>

<template>
  <div ref="root" class="shell-user-menu">
    <button class="shell-user-trigger" type="button" aria-label="打开个人菜单" aria-haspopup="menu" :aria-expanded="open" @click="toggle">
      <UserAvatar :src="auth.user?.avatar" :name="auth.user?.nickname" />
    </button>
    <div v-if="open && !compact" class="shell-user-popover" role="menu">
      <div class="shell-user-summary"><strong>{{ auth.user?.nickname || '拾光用户' }}</strong><small>{{ auth.user?.publicId ? `Memo ID ${auth.user.publicId}` : '个人空间' }}</small></div>
      <div class="shell-user-links">
        <button role="menuitem" @click="navigate(auth.user?.id ? `/user/${auth.user.id}` : '/settings')"><UserRound :size="18" />我的主页</button>
        <button role="menuitem" @click="navigate('/reminders')"><AlarmClock :size="18" />重要提醒</button>
        <button role="menuitem" @click="navigate('/settings')"><Settings :size="18" />设置</button>
        <button class="is-danger" role="menuitem" @click="logout"><LogOut :size="18" />退出登录</button>
      </div>
    </div>
    <UiBottomSheet v-if="compact" :open="open" title="个人" :description="auth.user?.nickname || '拾光用户'" @close="close">
      <div class="shell-user-links">
        <button @click="navigate(auth.user?.id ? `/user/${auth.user.id}` : '/settings')"><UserRound :size="18" />我的主页</button>
        <button @click="navigate('/reminders')"><AlarmClock :size="18" />重要提醒</button>
        <button @click="navigate('/settings')"><Settings :size="18" />设置</button>
      </div>
      <template #actions><UiButton variant="danger" block @click="logout"><LogOut :size="18" />退出登录</UiButton></template>
    </UiBottomSheet>
  </div>
</template>
