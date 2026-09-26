<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Capacitor } from '@capacitor/core'
import { useRoute } from 'vue-router'
import dayjs from 'dayjs'
import {
  AlarmClock, Bell, CalendarHeart, Check, CheckCircle2, Clock3, Gift, ImagePlus,
  ListTodo, Plane, Plus, RefreshCw, Trash2, UserRound, Users, X
} from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import { useAuthStore } from '../stores/auth'
import EmptyState from '../components/EmptyState.vue'
import PrivateMedia from '../components/PrivateMedia.vue'
import { UiBanner, UiButton, UiDialog, UiInput, UiSelect, UiSkeleton, UiTextarea } from '../components/ui'
import { chooseNativeImage } from '../utils/nativeImagePicker'

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
const nativeApp = Capacitor.isNativePlatform()
const imagePickerBusy = ref(false)
const removeTarget = ref<Reminder | null>(null)
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
const triggerAt = (item: Reminder) => dayjs(item.next_trigger_at || item.remind_at)
const isOverdue = (item: Reminder) => item.status !== 'COMPLETED' && item.status !== 'CANCELLED' && triggerAt(item).isBefore(dayjs())
const timeRelation = (item: Reminder) => {
  const trigger = triggerAt(item)
  if (item.status === 'COMPLETED') return '已经完成'
  if (isOverdue(item)) return `已过期 · ${trigger.format('MM月DD日 HH:mm')}`
  if (trigger.isSame(dayjs(), 'day')) return `今天 ${trigger.format('HH:mm')}`
  if (trigger.isSame(dayjs().add(1, 'day'), 'day')) return `明天 ${trigger.format('HH:mm')}`
  return trigger.format('YYYY年MM月DD日 HH:mm')
}

const load = async () => {
  loading.value = true
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

const selectNativeImage = async () => {
  imagePickerBusy.value = true
  pageError.value = ''
  try {
    const file = await chooseNativeImage('reminder')
    if (file) imageFile.value = file
  } catch (error) { pageError.value = errorMessage(error) }
  finally { imagePickerBusy.value = false }
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
  actionBusy.value = item.id
  pageError.value = ''
  try {
    await http.delete(`/reminders/${item.id}`)
    removeTarget.value = null
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
    return
  }
  const relationship = Number(route.query.relationship)
  if (relationship && relationships.value.some(item => Number(item.relationship_id || item.id) === relationship)) {
    openCreate('RELATIONSHIP')
    form.value.relationshipId = String(relationship)
    form.value.title = String(route.query.title || '')
    form.value.reminderKind = String(route.query.kind || 'ANNIVERSARY')
    form.value.scheduleType = 'YEARLY'
    const source = dayjs(String(route.query.date || dayjs().format('YYYY-MM-DD')))
    let next = source.year(dayjs().year()).hour(9).minute(0).second(0)
    if (next.isBefore(dayjs())) next = next.add(1, 'year')
    form.value.remindAt = next.format('YYYY-MM-DDTHH:mm')
  }
})
</script>

<template>
  <main class="reminders-page">
    <header class="relationship-domain-header reminder-heading">
      <div><h1>重要提醒</h1><p>时间不是任务清单，而是一些值得按时回来的约定。</p></div>
      <div class="reminder-summary" aria-label="提醒摘要"><span><strong>{{ upcomingCount }}</strong>进行中</span><span><strong>{{ pendingCount }}</strong>待确认</span></div>
      <UiButton variant="primary" size="lg" @click="openCreate()"><Plus :size="18" />新建提醒</UiButton>
    </header>

    <UiBanner v-if="pageMessage" tone="success" title="提醒已更新" :description="pageMessage" />
    <UiBanner v-if="pageError && !creating" tone="danger" title="提醒操作没有完成" :description="pageError"><template #actions><UiButton variant="ghost" size="sm" @click="load"><RefreshCw :size="15" />重新载入</UiButton></template></UiBanner>

    <section class="reminder-principle" aria-label="好友提醒权限说明">
      <AlarmClock :size="21" />
      <div><strong>熟人之间，也保留舒服的边界</strong><p>关于好友的私人提醒只属于你；好友发来的提醒是否直接生效，由你为对方设置的权限决定。</p></div>
    </section>

    <div class="reminder-toolbar">
      <div class="reminder-filters" role="tablist" aria-label="筛选提醒">
        <button v-for="item in [{v:'UPCOMING',l:'即将到来'},{v:'PENDING',l:'待确认'},{v:'COMPLETED',l:'已完成'},{v:'ALL',l:'全部'}]" :key="item.v" role="tab" :aria-selected="activeFilter===item.v" :class="{active:activeFilter===item.v}" @click="activeFilter=item.v as any">{{ item.l }}<span v-if="item.v==='PENDING' && pendingCount">{{ pendingCount }}</span></button>
      </div>
      <div class="reminder-quick-create" aria-label="快捷创建">
        <UiButton variant="ghost" size="sm" @click="openCreate('ABOUT_FRIEND')"><Gift :size="15" />记好友生日</UiButton>
        <UiButton variant="ghost" size="sm" @click="openCreate('ASSIGN_FRIEND')"><UserRound :size="15" />提醒好友</UiButton>
        <UiButton variant="ghost" size="sm" @click="openCreate('RELATIONSHIP')"><Users :size="15" />共同计划</UiButton>
      </div>
    </div>

    <div v-if="loading" class="reminder-loading" aria-label="正在整理提醒"><UiSkeleton v-for="index in 4" :key="index" height="112px" radius="var(--radius-md)" /></div>
    <ol v-else-if="filtered.length" class="reminder-timeline">
      <li v-for="item in filtered" :key="item.id" :class="{ 'is-pending': item.acceptance_status === 'PENDING', 'is-completed': item.status === 'COMPLETED', 'is-overdue': isOverdue(item) }">
        <div class="reminder-date-block" aria-hidden="true"><b>{{ triggerAt(item).format('DD') }}</b><span>{{ triggerAt(item).format('MM月') }}</span><small>{{ triggerAt(item).format('HH:mm') }}</small></div>
        <div class="reminder-marker"><component :is="kindIcon(item.reminder_kind)" :size="19" /></div>
        <PrivateMedia v-if="item.image_file_id" class="reminder-image" :file-id="Number(item.image_file_id)" mime-type="image/*" :alt="item.title" preview />
        <div class="reminder-copy">
          <div class="reminder-meta"><span>{{ kindLabel(item.reminder_kind) }}</span><span>{{ scheduleLabel(item.schedule_type) }}</span><span v-if="item.acceptance_status === 'PENDING'" class="is-emphasis">等待你确认</span><span v-else-if="isOverdue(item)" class="is-warning">已经错过时间</span></div>
          <h2>{{ item.title }}</h2>
          <p v-if="item.note">{{ item.note }}</p>
          <small><Clock3 :size="13" />{{ timeRelation(item) }}<template v-if="item.related_user_nickname"> · 与 {{ item.related_user_nickname }} 有关</template><template v-else-if="Number(item.creator_id) !== Number(auth.user?.id)"> · 由 {{ item.creator_nickname || '好友' }} 创建</template></small>
        </div>
        <div class="reminder-actions">
          <template v-if="item.acceptance_status === 'PENDING'">
            <UiButton variant="ghost" size="sm" :disabled="actionBusy===item.id" @click="action(item,'reject')"><X :size="15" />拒绝</UiButton>
            <UiButton variant="primary" size="sm" :loading="actionBusy===item.id" @click="action(item,'accept')"><Check :size="15" />接受</UiButton>
          </template>
          <template v-else-if="item.status !== 'COMPLETED' && item.status !== 'CANCELLED'">
            <UiButton variant="ghost" size="sm" :disabled="actionBusy===item.id" @click="action(item,'snooze')"><RefreshCw :size="15" />稍后 30 分钟</UiButton>
            <UiButton variant="tonal" size="sm" :loading="actionBusy===item.id" @click="action(item,'complete')"><CheckCircle2 :size="15" />完成</UiButton>
          </template>
          <span v-else class="completed-label"><CheckCircle2 :size="15" />已完成</span>
          <UiButton v-if="Number(item.creator_id) === Number(auth.user?.id)" variant="ghost" size="sm" :disabled="actionBusy===item.id" :aria-label="`删除提醒${item.title}`" @click="removeTarget=item"><Trash2 :size="15" /></UiButton>
        </div>
      </li>
    </ol>
    <EmptyState v-else title="这里暂时没有提醒" text="从一件想记住的小事开始，到了时间它会回来找你。" action-label="创建第一条提醒" @action="openCreate()" />

    <UiDialog :open="creating" title="新建提醒" description="先决定这件事属于谁，再安排它回来的时间。" width="wide" compact-fullscreen :busy="saving" @close="creating=false">
      <form id="create-reminder-form" class="reminder-form" @submit.prevent="createReminder">
        <fieldset class="reminder-mode-picker"><legend>这是一条怎样的提醒？</legend><div><button v-for="mode in modeOptions" :key="mode.value" type="button" :class="{active:form.mode===mode.value}" :aria-pressed="form.mode===mode.value" @click="form.mode=mode.value as FormMode"><b>{{ mode.label }}</b><small>{{ mode.description }}</small></button></div></fieldset>
        <div class="reminder-form-grid">
          <UiInput v-model="form.title" label="提醒标题" name="reminder-title" :maxlength="120" placeholder="例如：妈妈的生日" required />
          <UiInput v-model="form.remindAt" label="首次提醒时间" name="reminder-time" type="datetime-local" required />
          <UiTextarea v-model="form.note" class="reminder-note-field" label="补充说明" name="reminder-note" :maxlength="1000" :rows="3" placeholder="写下要准备的东西，或一句想说的话" />
          <UiSelect v-model="form.reminderKind" label="类型" name="reminder-kind"><option v-for="item in kindOptions" :key="item.value" :value="item.value">{{ item.label }}</option></UiSelect>
          <UiSelect v-model="form.scheduleType" label="重复周期" name="reminder-schedule"><option v-for="item in scheduleOptions" :key="item.value" :value="item.value">{{ item.label }}</option></UiSelect>
          <UiSelect v-if="form.mode==='ABOUT_FRIEND' || form.mode==='ASSIGN_FRIEND'" v-model="form.friendId" :label="form.mode==='ABOUT_FRIEND' ? '这条提醒关于谁' : '把提醒发给谁'" name="reminder-friend" required><option value="">请选择好友</option><option v-for="friend in friends" :key="friend.friend_id" :value="String(friend.friend_id)">{{ friend.remark_name || friend.nickname }} · {{ friend.public_id }}</option></UiSelect>
          <UiSelect v-if="form.mode==='RELATIONSHIP'" v-model="form.relationshipId" label="选择共同关系" name="reminder-relationship" required><option value="">请选择关系</option><option v-for="relationship in relationships" :key="relationship.relationship_id || relationship.id" :value="String(relationship.relationship_id || relationship.id)">{{ relationship.other_nickname || relationship.space_name || relationship.relationship_type }}</option></UiSelect>
        </div>
        <button v-if="nativeApp" type="button" class="reminder-image-picker" :disabled="imagePickerBusy" @click="selectNativeImage"><ImagePlus :size="20" /><span><b>{{ imagePickerBusy ? '正在打开手机相册…' : imageFile?.name || '从手机相册添加图片' }}</b><small>只读取你主动选择的这一张图片</small></span></button>
        <label v-else class="reminder-image-picker"><ImagePlus :size="20" /><span><b>{{ imageFile ? imageFile.name : '添加一张提醒图片' }}</b><small>到期时，可以从画面里更快想起这件事</small></span><input type="file" accept="image/*" @change="selectImage" /></label>
        <p class="privacy-note">提醒保存在你的 MemoSpace 中；好友提醒仍受双方设置与后端权限控制。</p>
        <UiBanner v-if="pageError" tone="danger" title="提醒还没有创建" :description="pageError" />
      </form>
      <template #actions><UiButton variant="ghost" :disabled="saving" @click="creating=false">取消</UiButton><UiButton type="submit" form="create-reminder-form" variant="primary" :loading="saving" loading-text="正在创建" :disabled="!form.title.trim() || !form.remindAt">创建提醒</UiButton></template>
    </UiDialog>

    <UiDialog :open="Boolean(removeTarget)" title="删除这条提醒？" :description="removeTarget ? `“${removeTarget.title}”将从提醒列表中移除，这个操作无法撤销。` : ''" :busy="Boolean(removeTarget && actionBusy===removeTarget.id)" @close="removeTarget=null"><template #actions><UiButton variant="ghost" :disabled="Boolean(removeTarget && actionBusy===removeTarget.id)" @click="removeTarget=null">保留提醒</UiButton><UiButton variant="danger" :loading="Boolean(removeTarget && actionBusy===removeTarget.id)" loading-text="正在删除" @click="removeTarget && remove(removeTarget)">确认删除</UiButton></template></UiDialog>
  </main>
</template>
