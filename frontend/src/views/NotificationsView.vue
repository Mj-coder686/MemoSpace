<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import { BellRing, CircleCheck, Clock3, Flag, RefreshCw } from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import EmptyState from '../components/EmptyState.vue'
import { useAuthStore } from '../stores/auth'
import { useRealtimeStore } from '../stores/realtime'
import UserAvatar from '../components/UserAvatar.vue'

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
  <header class="page-heading">
    <div><span class="eyebrow">MESSAGES & NOTIFICATIONS</span><h1>消息与通知</h1><p>好友申请、关系邀请和共同空间的新动静都在这里。</p></div>
  </header>
  <p v-if="message" class="panel notification-message" role="status">{{ message }}</p>
  <div v-if="loading" class="panel notification-state">正在载入消息…</div>
  <div v-else-if="pageError" class="panel notification-state error-state" role="alert">
    <b>消息暂时没有载入</b><p>{{ pageError }}</p><button class="button primary" @click="load"><RefreshCw :size="15" /> 重新载入</button>
  </div>
  <template v-else>
    <nav class="notification-tabs" aria-label="消息分类">
      <button :class="{ active: activeView === 'notifications' }" @click="activeView='notifications'">通知与邀请</button>
      <button :class="{ active: activeView === 'reports' }" @click="activeView='reports'">我的举报 <span v-if="reports.length">{{ reports.length }}</span></button>
    </nav>

    <template v-if="activeView === 'notifications'">
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

    <template v-else>
      <div class="section-heading report-history-heading"><div><h2>我提交的举报</h2><p>这里仅显示处理进度，不会公开你的举报身份。</p></div></div>
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
</template>

<style scoped>
.notification-message{padding:13px 18px}.notification-state{padding:32px;text-align:center}.notification-state b{display:block;margin-bottom:7px}.notification-state p{margin-bottom:15px;color:var(--muted)}.notification-state .button,.invitation-actions{display:inline-flex;align-items:center;gap:7px}.invitation-actions{flex-wrap:wrap}.notification-tabs{display:flex;gap:7px;margin:-12px 0 28px}.notification-tabs button{padding:10px 16px;border:1px solid var(--line);border-radius:13px;color:var(--muted);background:var(--surface)}.notification-tabs button.active{color:var(--ink);border-color:var(--accent);box-shadow:inset 0 -2px var(--accent)}.notification-tabs span{margin-left:5px;padding:1px 6px;border-radius:999px;color:white;background:var(--accent);font-size:9px}.report-history-heading{align-items:end}.report-history-heading p{margin:5px 0 0;color:var(--muted);font-size:12px}.report-history-list{display:grid;gap:11px}.report-history-item{padding:17px 20px;display:grid;grid-template-columns:42px minmax(0,1fr) auto;align-items:center;gap:13px;border:1px solid var(--line);border-radius:19px;background:var(--surface)}.report-history-icon{width:42px;height:42px;display:grid;place-items:center;border-radius:14px;color:#80545a;background:#f4e1e1}.report-history-icon.resolved{color:#47705c;background:#dfece4}.report-history-icon.dismissed{color:#6f717a;background:#e8e7e4}.report-history-title{display:flex;align-items:center;gap:8px}.report-history-title h3{margin:0;font:600 14px 'Noto Serif SC',serif}.report-history-title span{padding:3px 7px;border-radius:999px;color:#7d5c20;background:#f6ebca;font-size:9px}.report-history-title span.resolved{color:#47705c;background:#dfece4}.report-history-title span.dismissed{color:#666a73;background:#e8e7e4}.report-history-item p{margin:5px 0;color:var(--muted);font-size:11px;line-height:1.6}.report-history-item small{color:var(--ink);font-size:10px}.report-history-item time{color:var(--muted);font-size:10px}@media(max-width:620px){.notification-item{grid-template-columns:auto 1fr}.notification-item time,.invitation-actions{grid-column:2}.invitation-actions .button{flex:1}.notification-tabs{margin-top:0}.notification-tabs button{flex:1}.report-history-item{grid-template-columns:38px 1fr;padding:14px}.report-history-item time{grid-column:2}.report-history-title{align-items:flex-start;justify-content:space-between}}
</style>
