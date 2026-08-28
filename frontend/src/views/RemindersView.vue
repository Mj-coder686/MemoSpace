<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import dayjs from 'dayjs'
import {
  AlarmClock, Bell, CalendarHeart, Check, CheckCircle2, Clock3, Gift, ImagePlus,
  ListTodo, Plane, Plus, RefreshCw, Trash2, UserRound, Users, X
} from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import { useAuthStore } from '../stores/auth'
import PrivateMedia from '../components/PrivateMedia.vue'

type Reminder = {
  id: number
  creator_id: number
  related_user_id?: number
  relationship_id?: number
  image_file_id?: number
  title: string
  note?: string
  reminder_kind: string
  schedule_type: string
  remind_at: string
  next_trigger_at?: string
  timezone?: string
  status: string
  acceptance_status?: string
  participant_role?: string
  creator_nickname?: string
  related_user_nickname?: string
}

type FormMode = 'PERSONAL' | 'ABOUT_FRIEND' | 'ASSIGN_FRIEND' | 'RELATIONSHIP'

const route = useRoute()
const auth = useAuthStore()
const reminders = ref<Reminder[]>([])
const friends = ref<any[]>([])
const relationships = ref<any[]>([])
const loading = ref(true)
const pageError = ref('')
const pageMessage = ref('')
const activeFilter = ref<'UPCOMING' | 'PENDING' | 'COMPLETED' | 'ALL'>('UPCOMING')
const creating = ref(false)
const saving = ref(false)
const actionBusy = ref<number | null>(null)
const imageFile = ref<File | null>(null)
const form = ref({
  mode: 'PERSONAL' as FormMode,
  title: '', note: '', reminderKind: 'TASK', scheduleType: 'ONCE',
  remindAt: dayjs().add(1, 'hour').format('YYYY-MM-DDTHH:mm'), friendId: '', relationshipId: ''
})

const kindOptions = [
  { value: 'TASK', label: '待办', icon: ListTodo },
  { value: 'BIRTHDAY', label: '生日', icon: Gift },
  { value: 'ANNIVERSARY', label: '纪念日', icon: CalendarHeart },
  { value: 'PLAN', label: '计划 / 预约', icon: Plane },
  { value: 'CUSTOM', label: '其他', icon: Bell }
]
const scheduleOptions = [
  { value: 'ONCE', label: '仅一次' }, { value: 'DAILY', label: '每天' },
  { value: 'WEEKLY', label: '每周' }, { value: 'MONTHLY', label: '每月' }, { value: 'YEARLY', label: '每年' }
]
const modeOptions = [
  { value: 'PERSONAL', label: '提醒自己', description: '个人待办或计划' },
  { value: 'ABOUT_FRIEND', label: '关于好友', description: '生日、喜好和纪念日' },
  { value: 'ASSIGN_FRIEND', label: '发给好友', description: '由好友权限决定是否待确认' },
  { value: 'RELATIONSHIP', label: '共同提醒', description: '双方关系空间里的计划' }
]

const filtered = computed(() => reminders.value.filter(item => {
  if (activeFilter.value === 'ALL') return true
  if (activeFilter.value === 'PENDING') return item.acceptance_status === 'PENDING'
  if (activeFilter.value === 'COMPLETED') return item.status === 'COMPLETED'
  return item.status !== 'COMPLETED' && item.status !== 'CANCELLED' && item.acceptance_status !== 'REJECTED'
}))
const pendingCount = computed(() => reminders.value.filter(item => item.acceptance_status === 'PENDING').length)
const upcomingCount = computed(() => reminders.value.filter(item => item.status !== 'COMPLETED' && item.status !== 'CANCELLED').length)
const kindLabel = (kind: string) => kindOptions.find(item => item.value === kind)?.label || '提醒'
const scheduleLabel = (schedule: string) => scheduleOptions.find(item => item.value === schedule)?.label || schedule
const kindIcon = (kind: string) => kindOptions.find(item => item.value === kind)?.icon || Bell

const load = async () => {
  pageError.value = ''
  try {
    const [reminderResponse, friendResponse, relationshipResponse] = await Promise.all([
      http.get('/reminders'), http.get('/friends'), http.get('/relationships')
    ])
    reminders.value = reminderResponse.data
    friends.value = friendResponse.data
    relationships.value = relationshipResponse.data
  } catch (error) { pageError.value = errorMessage(error) }
  finally { loading.value = false }
}

const resetForm = () => {
  form.value = {
    mode: 'PERSONAL', title: '', note: '', reminderKind: 'TASK', scheduleType: 'ONCE',
    remindAt: dayjs().add(1, 'hour').format('YYYY-MM-DDTHH:mm'), friendId: '', relationshipId: ''
  }
  imageFile.value = null
}

const openCreate = (mode: FormMode = 'PERSONAL') => {
  resetForm()
  form.value.mode = mode
  creating.value = true
}

const selectImage = (event: Event) => {
  imageFile.value = (event.target as HTMLInputElement).files?.[0] || null
}

const uploadImage = async () => {
  if (!imageFile.value) return undefined
  const body = new FormData()
  body.append('file', imageFile.value)
  const { data } = await http.post('/files', body)
  return Number(data.id)
}

const createReminder = async () => {
  if (!form.value.title.trim() || !form.value.remindAt) return
  if ((form.value.mode === 'ABOUT_FRIEND' || form.value.mode === 'ASSIGN_FRIEND') && !form.value.friendId) {
    pageError.value = '请选择一位好友。'
    return
  }
  if (form.value.mode === 'RELATIONSHIP' && !form.value.relationshipId) {
    pageError.value = '请选择一段关系。'
    return
  }
  saving.value = true
  pageError.value = ''
  try {
    const imageFileId = await uploadImage()
    const payload: Record<string, unknown> = {
      title: form.value.title.trim(), note: form.value.note.trim() || undefined,
      reminderKind: form.value.reminderKind, scheduleType: form.value.scheduleType,
      remindAt: dayjs(form.value.remindAt).format('YYYY-MM-DDTHH:mm:ss'),
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Shanghai', imageFileId
    }
    if (form.value.mode === 'ABOUT_FRIEND') payload.relatedUserId = Number(form.value.friendId)
    if (form.value.mode === 'ASSIGN_FRIEND') payload.recipientUserId = Number(form.value.friendId)
    if (form.value.mode === 'RELATIONSHIP') payload.relationshipId = Number(form.value.relationshipId)
    await http.post('/reminders', payload)
    creating.value = false
    pageMessage.value = form.value.mode === 'ASSIGN_FRIEND'
      ? '提醒已发给好友；是否直接生效由对方的好友设置决定。'
      : '提醒已创建，到期后会出现在站内通知中。'
    await load()
  } catch (error) { pageError.value = errorMessage(error) }
  finally { saving.value = false }
}

const action = async (item: Reminder, name: 'accept' | 'reject' | 'complete' | 'snooze') => {
  actionBusy.value = item.id
  pageError.value = ''
  try {
    const body = name === 'snooze' ? { remindAt: dayjs().add(30, 'minute').format('YYYY-MM-DDTHH:mm:ss') } : undefined
    await http.post(`/reminders/${item.id}/${name}`, body)
    pageMessage.value = name === 'accept' ? '已接受这条提醒。' : name === 'reject' ? '已拒绝这条提醒。' : name === 'complete' ? '已标记完成。' : '已推迟 30 分钟。'
    await load()
  } catch (error) { pageError.value = errorMessage(error) }
  finally { actionBusy.value = null }
}

const remove = async (item: Reminder) => {
  if (!window.confirm(`确定删除提醒「${item.title}」吗？`)) return
  actionBusy.value = item.id
  try {
    await http.delete(`/reminders/${item.id}`)
    pageMessage.value = '提醒已删除。'
    await load()
  } catch (error) { pageError.value = errorMessage(error) }
  finally { actionBusy.value = null }
}

onMounted(async () => {
  await load()
  const recipient = Number(route.query.recipient)
  if (recipient && friends.value.some(item => Number(item.friend_id) === recipient)) {
    openCreate('ASSIGN_FRIEND')
    form.value.friendId = String(recipient)
  }
})
</script>

<template>
  <header class="page-heading reminder-heading">
    <div><span class="eyebrow">DATES, TASKS & PLANS</span><h1>重要提醒</h1><p>把生日、纪念日、每天要做的事和与好友的计划放在同一个时间轴里。</p></div>
    <button class="button primary" @click="openCreate()"><Plus :size="17" /> 新建提醒</button>
  </header>

  <p v-if="pageMessage" class="relationship-notice success" role="status">{{ pageMessage }}</p>
  <p v-if="pageError" class="relationship-notice error" role="alert">{{ pageError }}</p>

  <section class="reminder-overview">
    <div><span class="reminder-overview-icon"><AlarmClock :size="23" /></span><b>{{ upcomingCount }}</b><small>进行中的提醒</small></div>
    <div><span class="reminder-overview-icon rose"><UserRound :size="23" /></span><b>{{ pendingCount }}</b><small>等待我确认</small></div>
    <div class="reminder-principle"><strong>熟人之间，也保留舒服的边界</strong><p>关于好友的私人提醒无需对方同意；好友发给你的提醒会遵循你为 TA 设置的权限。</p></div>
  </section>

  <div class="reminder-toolbar">
    <div class="filters reminder-filters">
      <button v-for="item in [{v:'UPCOMING',l:'即将到来'},{v:'PENDING',l:`待确认 ${pendingCount || ''}`},{v:'COMPLETED',l:'已完成'},{v:'ALL',l:'全部'}]" :key="item.v" :class="{active:activeFilter===item.v}" @click="activeFilter=item.v as any">{{ item.l }}</button>
    </div>
    <div class="reminder-quick-create">
      <button @click="openCreate('ABOUT_FRIEND')"><Gift :size="15" /> 记好友生日</button>
      <button @click="openCreate('ASSIGN_FRIEND')"><UserRound :size="15" /> 提醒好友</button>
      <button @click="openCreate('RELATIONSHIP')"><Users :size="15" /> 共同计划</button>
    </div>
  </div>

  <div v-if="filtered.length" class="reminder-list">
    <article v-for="item in filtered" :key="item.id" class="reminder-card" :class="{ pending: item.acceptance_status === 'PENDING', completed: item.status === 'COMPLETED' }">
      <div class="reminder-date-block"><b>{{ dayjs(item.next_trigger_at || item.remind_at).format('DD') }}</b><span>{{ dayjs(item.next_trigger_at || item.remind_at).format('MM月') }}</span><small>{{ dayjs(item.next_trigger_at || item.remind_at).format('HH:mm') }}</small></div>
      <PrivateMedia v-if="item.image_file_id" class="reminder-image" :file-id="Number(item.image_file_id)" mime-type="image/*" :alt="item.title" preview />
      <div v-else class="reminder-kind-icon"><component :is="kindIcon(item.reminder_kind)" :size="23" /></div>
      <div class="reminder-copy">
        <div class="reminder-tags"><span>{{ kindLabel(item.reminder_kind) }}</span><span>{{ scheduleLabel(item.schedule_type) }}</span><span v-if="item.acceptance_status === 'PENDING'" class="pending-tag">等待确认</span></div>
        <h2>{{ item.title }}</h2>
        <p v-if="item.note">{{ item.note }}</p>
        <small v-if="item.related_user_nickname">与 {{ item.related_user_nickname }} 有关</small>
        <small v-else-if="Number(item.creator_id) !== Number(auth.user?.id)">由 {{ item.creator_nickname || '好友' }} 创建</small>
      </div>
      <div class="reminder-actions">
        <template v-if="item.acceptance_status === 'PENDING'">
          <button class="button primary" :disabled="actionBusy===item.id" @click="action(item,'accept')"><Check :size="15" /> 接受</button>
          <button class="button" :disabled="actionBusy===item.id" @click="action(item,'reject')"><X :size="15" /> 拒绝</button>
        </template>
        <template v-else-if="item.status !== 'COMPLETED' && item.status !== 'CANCELLED'">
          <button class="button primary" :disabled="actionBusy===item.id" @click="action(item,'complete')"><CheckCircle2 :size="15" /> 完成</button>
          <button class="button" :disabled="actionBusy===item.id" @click="action(item,'snooze')"><RefreshCw :size="15" /> 稍后 30 分钟</button>
        </template>
        <span v-else class="completed-label"><CheckCircle2 :size="15" /> 已完成</span>
        <button v-if="Number(item.creator_id) === Number(auth.user?.id)" class="reminder-delete" :disabled="actionBusy===item.id" aria-label="删除提醒" @click="remove(item)"><Trash2 :size="16" /></button>
      </div>
    </article>
  </div>
  <div v-else-if="!loading" class="empty-state"><span><Clock3 :size="24" /></span><h3>这里暂时没有提醒</h3><p>从一件想记住的小事开始，到了时间它会回来找你。</p><button class="button primary" @click="openCreate()">创建第一条提醒</button></div>

  <div v-if="creating" class="modal-backdrop" @click.self="creating=false">
    <section class="create-modal reminder-modal" role="dialog" aria-modal="true" aria-labelledby="create-reminder-title">
      <header><div><span class="eyebrow">A MOMENT TO REMEMBER</span><h2 id="create-reminder-title">新建提醒</h2></div><button class="icon-button" aria-label="关闭" @click="creating=false"><X :size="18" /></button></header>
      <div class="reminder-mode-grid">
        <button v-for="mode in modeOptions" :key="mode.value" :class="{active:form.mode===mode.value}" @click="form.mode=mode.value as FormMode"><b>{{ mode.label }}</b><small>{{ mode.description }}</small></button>
      </div>
      <label class="field"><span>提醒标题</span><input v-model="form.title" maxlength="120" placeholder="例如：妈妈的生日" /></label>
      <label class="field"><span>补充说明</span><textarea v-model="form.note" maxlength="1000" rows="3" placeholder="写下需要准备的东西或一句想说的话"></textarea></label>
      <div class="field-row">
        <label class="field"><span>类型</span><select v-model="form.reminderKind"><option v-for="item in kindOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
        <label class="field"><span>重复周期</span><select v-model="form.scheduleType"><option v-for="item in scheduleOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
      </div>
      <label class="field"><span>首次提醒时间</span><input v-model="form.remindAt" type="datetime-local" /></label>
      <label v-if="form.mode==='ABOUT_FRIEND' || form.mode==='ASSIGN_FRIEND'" class="field"><span>{{ form.mode==='ABOUT_FRIEND' ? '这条提醒关于谁' : '把提醒发给谁' }}</span><select v-model="form.friendId"><option value="">请选择好友</option><option v-for="friend in friends" :key="friend.friend_id" :value="String(friend.friend_id)">{{ friend.remark_name || friend.nickname }} · {{ friend.public_id }}</option></select></label>
      <label v-if="form.mode==='RELATIONSHIP'" class="field"><span>选择共同关系</span><select v-model="form.relationshipId"><option value="">请选择关系</option><option v-for="relationship in relationships" :key="relationship.relationship_id || relationship.id" :value="String(relationship.relationship_id || relationship.id)">{{ relationship.other_nickname || relationship.space_name || relationship.relationship_type }}</option></select></label>
      <label class="reminder-image-picker"><ImagePlus :size="20" /><span><b>{{ imageFile ? imageFile.name : '添加一张提醒图片' }}</b><small>到期时可以从图片想起这件事</small></span><input type="file" accept="image/*" @change="selectImage" /></label>
      <p class="privacy-note">站内提醒会保存在共享数据库中。以后接入 Android 或 iPhone 系统推送时，无需迁移这些数据。</p>
      <footer><button class="button" @click="creating=false">取消</button><button class="button primary" :disabled="saving || !form.title.trim() || !form.remindAt" @click="createReminder">{{ saving ? '正在保存…' : '创建提醒' }}</button></footer>
    </section>
  </div>
</template>
