<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { ArrowRight, BellRing, CircleCheck, Clock3, Flag, RefreshCw } from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import EmptyState from '../components/EmptyState.vue'
import { useAuthStore } from '../stores/auth'
import { useRealtimeStore } from '../stores/realtime'
import UserAvatar from '../components/UserAvatar.vue'
import { UiBanner, UiButton, UiSkeleton } from '../components/ui'

const router = useRouter()
const auth = useAuthStore()
const realtime = useRealtimeStore()
const notifications = ref<any[]>([])
const invitations = ref<any[]>([])
const reports = ref<any[]>([])
const activeView = ref<'notifications' | 'reports'>('notifications')
const message = ref('')
const pageError = ref('')
const loading = ref(true)
let unsubscribe: (() => void) | undefined

const reasonLabels: Record<string, string> = {
  ILLEGAL: '违法或违禁', HARASSMENT: '骚扰辱骂', PORNOGRAPHY: '色情低俗',
  VIOLENCE: '暴力危险', FRAUD: '诈骗虚假', PRIVACY: '隐私泄露', OTHER: '其他问题',
}
const reportStatus = (status: string) => status === 'PENDING' ? '等待核查' : status === 'RESOLVED' ? '已确认违规' : '未发现违规'
const reportResult = (action?: string) => {
  if (!action) return '管理员尚未处理'
  const labels: Record<string, string> = { NONE: '已记录违规', WARNING: '已警告', MUTE_7_DAYS: '已禁言 7 天', BAN: '已封号', DISMISS: '举报已驳回' }
  const removed = action.startsWith('DELETE+')
  const penalty = action.replace('DELETE+', '')
  return `${removed ? '内容已删除 · ' : ''}${labels[penalty] || penalty}`
}
const unreadCount = computed(() => notifications.value.filter(item => !item.is_read).length)
const todayNotifications = computed(() => notifications.value.filter(item => dayjs(item.created_at).isSame(dayjs(), 'day')))
const earlierNotifications = computed(() => notifications.value.filter(item => !dayjs(item.created_at).isSame(dayjs(), 'day')))
const notificationGroups = computed(() => [
  { label: '今天', items: todayNotifications.value },
  { label: '更早', items: earlierNotifications.value },
].filter(group => group.items.length))
const notificationTarget = (item: any) => {
  const reference = Number(item.reference_id)
  if (['COMMENT', 'SPACE_MEMORY'].includes(item.notification_type) && reference) return `/memory/${reference}`
  if (item.notification_type === 'RELATIONSHIP_ACCEPT' && reference) return `/space/${reference}`
  if (['FRIEND_REQUEST', 'FRIEND_ACCEPT'].includes(item.notification_type)) return '/friends'
  if (item.notification_type === 'REMINDER_DUE') return '/reminders'
  if (item.notification_type === 'RELATIONSHIP_INVITE') return '/notifications'
  return ''
}
const openNotification = (item: any) => {
  const target = notificationTarget(item)
  if (target && target !== '/notifications') void router.push(target)
}

const load = async () => {
  loading.value = true
  pageError.value = ''
  try {
    if (!auth.user) await auth.loadMe()
    const [notificationResponse, invitationResponse, reportResponse] = await Promise.all([
      http.get('/notifications'), http.get('/relationships/invitations'), http.get('/reports/mine')
    ])
    notifications.value = notificationResponse.data
    invitations.value = invitationResponse.data.filter((item: any) =>
      Number(item.receiver_id) === Number(auth.user?.id) && item.status === 'PENDING')
    reports.value = reportResponse.data
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
  <main class="notifications-page"><header class="relationship-domain-header">
    <div><span class="memory-kicker">MESSAGES & NOTIFICATIONS</span><h1>消息与通知</h1><p>需要回应的邀请放在最前面；其余动态按发生时间安静归档。</p></div>
    <div class="notification-summary"><span><strong>{{ invitations.length }}</strong>待回应</span><span><strong>{{ unreadCount }}</strong>新通知</span></div>
  </header>
  <UiBanner v-if="message" tone="success" title="操作已完成" :description="message" />
  <div v-if="loading" class="notification-loading" aria-label="正在载入消息"><UiSkeleton v-for="index in 5" :key="index" height="86px" radius="var(--radius-md)" /></div>
  <EmptyState v-else-if="pageError" kind="error" title="消息暂时没有载入" :text="pageError"><template #actions><UiButton variant="primary" @click="load"><RefreshCw :size="15" />重新载入</UiButton></template></EmptyState>
  <template v-else>
    <nav class="notification-tabs" aria-label="消息分类" role="tablist">
      <button role="tab" :aria-selected="activeView === 'notifications'" :class="{ active: activeView === 'notifications' }" @click="activeView='notifications'">通知与邀请 <span v-if="invitations.length + unreadCount">{{ invitations.length + unreadCount }}</span></button>
      <button role="tab" :aria-selected="activeView === 'reports'" :class="{ active: activeView === 'reports' }" @click="activeView='reports'">我的举报 <span v-if="reports.length">{{ reports.length }}</span></button>
    </nav>

    <template v-if="activeView === 'notifications'">
      <section v-if="invitations.length">
        <div class="relationship-section-heading"><div><span class="memory-kicker">NEEDS YOUR ANSWER</span><h2>等待你的回应</h2><p>只有接受关系邀请后，才会创建或关联双方唯一的共同空间。</p></div></div>
        <div class="notification-invitation-list">
          <article v-for="item in invitations" :key="item.id">
            <UserAvatar class="notification-avatar" :src="item.sender_avatar" :name="item.sender_nickname" />
            <div><h3>{{ item.sender_nickname }} 邀请你绑定为「{{ item.category_name || (item.relationship_type === 'COUPLE' ? '恋人' : item.relationship_type === 'FAMILY' ? '家人' : '死党') }}」</h3><p>{{ item.message || '一起收藏共同故事。' }} · 接受后会创建或关联双方唯一的共同空间。</p></div>
            <div class="invitation-actions"><UiButton variant="ghost" size="sm" @click="respond(item.id,'reject')">婉拒</UiButton><UiButton variant="primary" size="sm" @click="respond(item.id,'accept')">接受</UiButton></div>
          </article>
        </div>
      </section>
      <section class="notification-history" aria-labelledby="notification-history-title"><div class="relationship-section-heading"><div><span class="memory-kicker">RECENT ACTIVITY</span><h2 id="notification-history-title">最近发生</h2></div></div>
      <div v-if="notifications.length" class="notification-groups">
        <section v-for="group in notificationGroups" :key="group.label"><h3>{{ group.label }}</h3><div class="notification-list"><article v-for="item in group.items" :key="item.id" :class="{ unread: !item.is_read, actionable: notificationTarget(item) && notificationTarget(item) !== '/notifications' }" :tabindex="notificationTarget(item) && notificationTarget(item) !== '/notifications' ? 0 : undefined" @click="openNotification(item)" @keydown.enter="openNotification(item)"><UserAvatar v-if="item.actor_avatar" class="notification-avatar" :src="item.actor_avatar" :name="item.actor_nickname" /><span v-else class="notification-avatar"><BellRing :size="17" /></span><div><h4>{{ item.title }}</h4><p>{{ item.content }}</p></div><time :datetime="item.created_at">{{ dayjs(item.created_at).format('MM.DD HH:mm') }}</time><ArrowRight v-if="notificationTarget(item) && notificationTarget(item) !== '/notifications'" :size="17" /></article></div></section>
      </div>
      <EmptyState v-else title="暂时没有新消息" text="好友申请、关系邀请和提醒到来后，会清楚地显示在这里。" />
      </section>
    </template>

    <template v-else>
      <div class="relationship-section-heading report-history-heading"><div><span class="memory-kicker">REPORT HISTORY</span><h2>我提交的举报</h2><p>这里只显示处理进度；你的举报身份不会向被举报人公开。</p></div></div>
      <div v-if="reports.length" class="report-history-list">
        <article v-for="item in reports" :key="item.id" class="report-history-item">
          <span class="report-history-icon" :class="item.status.toLowerCase()"><Clock3 v-if="item.status==='PENDING'" :size="18" /><CircleCheck v-else-if="item.status==='RESOLVED'" :size="18" /><Flag v-else :size="18" /></span>
          <div><div class="report-history-title"><h3>{{ reasonLabels[item.reason_category] || item.reason_category }}</h3><span :class="item.status.toLowerCase()">{{ reportStatus(item.status) }}</span></div><p>{{ item.target_type === 'MEMORY' ? '记忆' : '评论' }} #{{ item.target_id }}<template v-if="item.description"> · {{ item.description }}</template></p><small>{{ reportResult(item.resolution_action) }}</small></div>
          <time>{{ dayjs(item.reviewed_at || item.created_at).format('YYYY.MM.DD HH:mm') }}</time>
        </article>
      </div>
      <EmptyState v-else title="还没有提交过举报" text="查看他人的 Memory 或评论时，如发现违法违规内容，可以从详情页提交举报。" />
    </template>
  </template>
  </main>
</template>
