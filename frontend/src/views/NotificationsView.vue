<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import { BellRing, RefreshCw } from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import EmptyState from '../components/EmptyState.vue'
import { useAuthStore } from '../stores/auth'
import { useRealtimeStore } from '../stores/realtime'
import UserAvatar from '../components/UserAvatar.vue'

const auth = useAuthStore()
const realtime = useRealtimeStore()
const notifications = ref<any[]>([])
const invitations = ref<any[]>([])
const message = ref('')
const pageError = ref('')
const loading = ref(true)
let unsubscribe: (() => void) | undefined

const load = async () => {
  loading.value = true
  pageError.value = ''
  try {
    if (!auth.user) await auth.loadMe()
    const [notificationResponse, invitationResponse] = await Promise.all([
      http.get('/notifications'), http.get('/relationships/invitations')
    ])
    notifications.value = notificationResponse.data
    invitations.value = invitationResponse.data.filter((item: any) =>
      Number(item.receiver_id) === Number(auth.user?.id) && item.status === 'PENDING')
    await http.put('/notifications/read')
    window.dispatchEvent(new Event('memospace-notifications-read'))
  } catch (error) {
    pageError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

const respond = async (id: number, action: 'accept' | 'reject') => {
  try {
    const { data } = await http.post(`/relationships/invitations/${id}/${action}`)
    message.value = action === 'accept'
      ? (data.reusedSpace ? '已加入现有共同空间，同一段关系不会重复建空间' : '共同空间已经准备好了')
      : '已婉拒邀请'
    await load()
  } catch (error) { message.value = errorMessage(error) }
}

onMounted(async () => {
  await load()
  unsubscribe = realtime.subscribe(event => {
    if (event.type === 'NOTIFICATION' || event.type === 'REMINDER_DUE') void load()
  })
})
onBeforeUnmount(() => unsubscribe?.())
</script>

<template>
  <header class="page-heading">
    <div><span class="eyebrow">MESSAGES & NOTIFICATIONS</span><h1>消息与通知</h1><p>好友申请、关系邀请和共同空间的新动静都在这里。</p></div>
  </header>
  <p v-if="message" class="panel notification-message" role="status">{{ message }}</p>
  <div v-if="loading" class="panel notification-state">正在载入消息…</div>
  <div v-else-if="pageError" class="panel notification-state error-state" role="alert">
    <b>消息暂时没有载入</b><p>{{ pageError }}</p><button class="button primary" @click="load"><RefreshCw :size="15" /> 重新载入</button>
  </div>
  <template v-else>
    <section v-if="invitations.length">
      <div class="section-heading"><h2>等待你的回应</h2></div>
      <div class="notification-list">
        <article v-for="item in invitations" :key="item.id" class="notification-item unread">
          <UserAvatar class="notification-avatar" :src="item.sender_avatar" :name="item.sender_nickname" />
          <div><h3>{{ item.sender_nickname }} 邀请你绑定为「{{ item.category_name || (item.relationship_type === 'COUPLE' ? '恋人' : item.relationship_type === 'FAMILY' ? '家人' : '死党') }}」</h3><p>{{ item.message || '一起收藏共同故事。' }} · 接受后会创建或关联双方唯一的共同空间。</p></div>
          <div class="invitation-actions"><button class="button" @click="respond(item.id,'reject')">婉拒</button><button class="button primary" @click="respond(item.id,'accept')">接受</button></div>
        </article>
      </div>
    </section>
    <div class="section-heading"><h2>最近发生</h2></div>
    <div v-if="notifications.length" class="notification-list">
      <article v-for="item in notifications" :key="item.id" class="notification-item" :class="{ unread: !item.is_read }">
        <UserAvatar v-if="item.actor_avatar" class="notification-avatar" :src="item.actor_avatar" :name="item.actor_nickname" />
        <span v-else class="notification-avatar"><BellRing :size="17" /></span>
        <div><h3>{{ item.title }}</h3><p>{{ item.content }}</p></div><time>{{ dayjs(item.created_at).format('MM.DD HH:mm') }}</time>
      </article>
    </div>
    <EmptyState v-else title="暂时没有新消息" text="好友申请、关系邀请和提醒到来后，会清楚地显示在这里。" />
  </template>
</template>

<style scoped>
.notification-message{padding:13px 18px}.notification-state{padding:32px;text-align:center}.notification-state b{display:block;margin-bottom:7px}.notification-state p{margin-bottom:15px;color:var(--muted)}.notification-state .button,.invitation-actions{display:inline-flex;align-items:center;gap:7px}.invitation-actions{flex-wrap:wrap}@media(max-width:620px){.notification-item{grid-template-columns:auto 1fr}.notification-item time,.invitation-actions{grid-column:2}.invitation-actions .button{flex:1}}
</style>
