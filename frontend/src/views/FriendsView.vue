<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import {
  BellOff, BellRing, Check, Clock3, Copy, MessageCircle, Search, Settings2,
  ShieldBan, UserMinus, UserPlus, Users, X
} from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import { useAuthStore } from '../stores/auth'
import { useRealtimeStore } from '../stores/realtime'

type Friend = {
  friendship_id: number
  friend_id: number
  public_id: string
  username?: string
  nickname: string
  avatar?: string
  bio?: string
  remark_name?: string
  allow_direct_reminders: boolean | number
  mute_chat: boolean | number
  created_at?: string
}

type FriendRequest = {
  id: number
  direction: 'INCOMING' | 'OUTGOING'
  status: string
  message?: string
  created_at?: string
  sender_id?: number
  sender_public_id?: string
  sender_nickname?: string
  sender_avatar?: string
  receiver_id?: number
  receiver_public_id?: string
  receiver_nickname?: string
  receiver_avatar?: string
}

const router = useRouter()
const auth = useAuthStore()
const realtime = useRealtimeStore()
const friends = ref<Friend[]>([])
const requests = ref<FriendRequest[]>([])
const loading = ref(true)
const pageError = ref('')
const pageMessage = ref('')
const query = ref('')
const searching = ref(false)
const searchResults = ref<any[]>([])
const requestMessage = ref('很高兴认识你，想和你成为好友。')
const requestBusy = ref<number | null>(null)
const requestTab = ref<'INCOMING' | 'OUTGOING'>('INCOMING')
const editingFriend = ref<Friend | null>(null)
const settingForm = ref({ remarkName: '', allowDirectReminders: true, muteChat: false })
const savingSettings = ref(false)

const memoId = computed(() => auth.user?.publicId || auth.user?.public_id || '')
const pendingIncoming = computed(() => requests.value.filter(item => item.direction === 'INCOMING' && item.status === 'PENDING'))
const visibleRequests = computed(() => requests.value.filter(item => item.direction === requestTab.value))
const truthy = (value: boolean | number) => value === true || Number(value) === 1
const displayName = (friend: Friend) => friend.remark_name || friend.nickname
const formatTime = (value?: string) => value ? dayjs(value).format('MM月DD日 HH:mm') : ''

const requestPerson = (item: FriendRequest) => item.direction === 'INCOMING'
  ? { id: item.sender_id, publicId: item.sender_public_id, nickname: item.sender_nickname, avatar: item.sender_avatar }
  : { id: item.receiver_id, publicId: item.receiver_public_id, nickname: item.receiver_nickname, avatar: item.receiver_avatar }

const load = async () => {
  pageError.value = ''
  try {
    const [friendResponse, requestResponse] = await Promise.all([
      http.get('/friends'),
      http.get('/friends/requests')
    ])
    friends.value = friendResponse.data
    requests.value = requestResponse.data
  } catch (error) {
    pageError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

const normalizeMemoId = () => {
  query.value = query.value.replace(/\D/g, '').slice(0, 12)
  searchResults.value = []
}

const searchUsers = async () => {
  if (query.value.length !== 12) {
    pageError.value = '请输入完整的 12 位 Memo ID。'
    return
  }
  searching.value = true
  pageError.value = ''
  pageMessage.value = ''
  try {
    const { data } = await http.get('/users/search', { params: { q: query.value } })
    searchResults.value = data.filter((person: any) => Number(person.id) !== Number(auth.user?.id))
    if (!searchResults.value.length) pageMessage.value = '没有找到这个 Memo ID，请确认数字是否完整。'
  } catch (error) {
    pageError.value = errorMessage(error)
  } finally {
    searching.value = false
  }
}

const sendRequest = async (person: any) => {
  requestBusy.value = Number(person.id)
  pageError.value = ''
  pageMessage.value = ''
  try {
    await http.post('/friends/requests', { receiverId: Number(person.id), message: requestMessage.value.trim() })
    pageMessage.value = `好友申请已发送给 ${person.nickname || person.username}。`
    searchResults.value = []
    query.value = ''
    await load()
  } catch (error) {
    pageError.value = errorMessage(error)
  } finally {
    requestBusy.value = null
  }
}

const respond = async (request: FriendRequest, action: 'accept' | 'reject') => {
  requestBusy.value = request.id
  pageError.value = ''
  try {
    await http.post(`/friends/requests/${request.id}/${action}`)
    pageMessage.value = action === 'accept' ? '已成为好友，现在可以开始聊天。' : '已拒绝这条好友申请。'
    await load()
  } catch (error) {
    pageError.value = errorMessage(error)
  } finally {
    requestBusy.value = null
  }
}

const openSettings = (friend: Friend) => {
  editingFriend.value = friend
  settingForm.value = {
    remarkName: friend.remark_name || '',
    allowDirectReminders: truthy(friend.allow_direct_reminders),
    muteChat: truthy(friend.mute_chat)
  }
}

const saveSettings = async () => {
  if (!editingFriend.value) return
  savingSettings.value = true
  pageError.value = ''
  try {
    await http.put(`/friends/${editingFriend.value.friend_id}/settings`, settingForm.value)
    editingFriend.value = null
    pageMessage.value = '好友设置已保存。'
    await load()
  } catch (error) {
    pageError.value = errorMessage(error)
  } finally {
    savingSettings.value = false
  }
}

const removeFriend = async (friend: Friend) => {
  if (!window.confirm(`确定删除好友「${displayName(friend)}」吗？关系绑定和共同空间不会因此删除。`)) return
  try {
    await http.delete(`/friends/${friend.friend_id}`)
    editingFriend.value = null
    pageMessage.value = '好友已删除；已有关系绑定仍然保留。'
    await load()
  } catch (error) { pageError.value = errorMessage(error) }
}

const blockFriend = async (friend: Friend) => {
  if (!window.confirm(`拉黑「${displayName(friend)}」后会同时解除好友并停止聊天，是否继续？`)) return
  try {
    await http.post(`/users/${friend.friend_id}/block`)
    editingFriend.value = null
    pageMessage.value = '已拉黑并解除好友。关系空间仍作为独立数据保留。'
    await load()
  } catch (error) { pageError.value = errorMessage(error) }
}

const copyMemoId = async () => {
  if (!memoId.value) return
  await navigator.clipboard.writeText(memoId.value)
  pageMessage.value = 'Memo ID 已复制。'
}

onMounted(async () => {
  realtime.connect()
  if (!memoId.value) await auth.loadMe()
  await load()
})
</script>

<template>
  <header class="page-heading friends-heading">
    <div>
      <span class="eyebrow">FRIENDS & CONVERSATIONS</span>
      <h1>好友中心</h1>
      <p>好友是聊天和日常提醒的基础；关系绑定仍然是独立的共同记忆关系。</p>
    </div>
    <button class="button" @click="router.push('/relationships')"><Users :size="17" /> 关系分类</button>
  </header>

  <p v-if="pageMessage" class="relationship-notice success" role="status">{{ pageMessage }}</p>
  <p v-if="pageError" class="relationship-notice error" role="alert">{{ pageError }}</p>

  <section class="memo-id-card" aria-label="我的 Memo ID">
    <div>
      <span class="eyebrow">MY MEMO ID</span>
      <strong>{{ memoId || '正在获取…' }}</strong>
      <p>这是系统自动分配且不会变化的 12 位数字代号，分享它就能让朋友找到你。</p>
    </div>
    <button class="button" :disabled="!memoId" @click="copyMemoId"><Copy :size="16" /> 复制 ID</button>
  </section>

  <div class="friend-workbench">
    <section class="panel friend-add-panel">
      <span class="eyebrow">ADD BY MEMO ID</span>
      <h2>添加好友</h2>
      <p>输入对方完整的 Memo ID。数字 ID 不会因为昵称改变而变化。</p>
      <form class="memo-id-search" @submit.prevent="searchUsers">
        <Search :size="18" />
        <input v-model="query" inputmode="numeric" maxlength="12" aria-label="12 位 Memo ID" placeholder="请输入 12 位 Memo ID" @input="normalizeMemoId" />
        <button type="submit" :disabled="searching">{{ searching ? '查找中' : '查找' }}</button>
      </form>
      <label class="field compact-field">
        <span>申请留言</span>
        <input v-model="requestMessage" maxlength="200" />
      </label>
      <div v-if="searchResults.length" class="friend-search-results">
        <article v-for="person in searchResults" :key="person.id">
          <span class="friend-avatar">{{ (person.nickname || person.username)?.slice(0, 1) }}</span>
          <div><b>{{ person.nickname || person.username }}</b><small>Memo ID {{ person.public_id }}</small></div>
          <button class="button primary" :disabled="requestBusy === Number(person.id)" @click="sendRequest(person)"><UserPlus :size="16" /> 申请好友</button>
        </article>
      </div>
    </section>

    <section class="panel request-panel">
      <div class="friend-panel-title">
        <div><span class="eyebrow">REQUESTS</span><h2>好友申请</h2></div>
        <span v-if="pendingIncoming.length" class="request-count">{{ pendingIncoming.length }}</span>
      </div>
      <div class="request-tabs" role="tablist">
        <button :class="{ active: requestTab === 'INCOMING' }" @click="requestTab = 'INCOMING'">收到的</button>
        <button :class="{ active: requestTab === 'OUTGOING' }" @click="requestTab = 'OUTGOING'">发出的</button>
      </div>
      <div v-if="visibleRequests.length" class="request-list">
        <article v-for="item in visibleRequests" :key="item.id">
          <span class="friend-avatar small">{{ requestPerson(item).nickname?.slice(0, 1) || '友' }}</span>
          <div class="request-copy">
            <b>{{ requestPerson(item).nickname || '一位用户' }}</b>
            <small>Memo ID {{ requestPerson(item).publicId || '—' }} · {{ formatTime(item.created_at) }}</small>
            <p v-if="item.message">{{ item.message }}</p>
          </div>
          <div v-if="item.direction === 'INCOMING' && item.status === 'PENDING'" class="request-actions">
            <button class="tiny-action accept" :disabled="requestBusy === item.id" aria-label="接受好友申请" @click="respond(item, 'accept')"><Check :size="16" /></button>
            <button class="tiny-action" :disabled="requestBusy === item.id" aria-label="拒绝好友申请" @click="respond(item, 'reject')"><X :size="16" /></button>
          </div>
          <span v-else class="request-status" :class="item.status.toLowerCase()">{{ item.status === 'PENDING' ? '等待回应' : item.status === 'ACCEPTED' ? '已接受' : '已结束' }}</span>
        </article>
      </div>
      <div v-else class="friend-mini-empty"><Clock3 :size="20" /><span>这里暂时没有申请记录</span></div>
    </section>
  </div>

  <section>
    <div class="section-heading">
      <div><span class="eyebrow">MY FRIENDS</span><h2>我的好友</h2></div>
      <span class="section-help">{{ friends.length }} 位好友 · {{ realtime.connected ? '实时连接正常' : '正在连接实时服务' }}</span>
    </div>
    <div v-if="friends.length" class="friend-grid">
      <article v-for="friend in friends" :key="friend.friend_id" class="friend-card">
        <div class="friend-card-head">
          <span class="friend-avatar large">{{ displayName(friend).slice(0, 1) }}</span>
          <i :class="{ online: realtime.isOnline(friend.friend_id) }" :title="realtime.isOnline(friend.friend_id) ? '在线' : '离线'" />
          <button class="friend-settings-button" :aria-label="`设置${displayName(friend)}`" @click="openSettings(friend)"><Settings2 :size="17" /></button>
        </div>
        <h3>{{ displayName(friend) }}</h3>
        <p v-if="friend.remark_name" class="original-name">昵称：{{ friend.nickname }}</p>
        <p class="friend-memo-id">Memo ID {{ friend.public_id }}</p>
        <p class="friend-bio">{{ friend.bio || '还没有写个人签名。' }}</p>
        <div class="friend-permissions">
          <span :class="{ enabled: truthy(friend.allow_direct_reminders) }"><BellRing v-if="truthy(friend.allow_direct_reminders)" :size="13" /><BellOff v-else :size="13" />好友提醒</span>
          <span v-if="truthy(friend.mute_chat)">聊天已静音</span>
        </div>
        <button class="button primary full-button" @click="router.push(`/chat/${friend.friend_id}`)"><MessageCircle :size="17" /> 开始聊天</button>
      </article>
    </div>
    <div v-else-if="!loading" class="empty-state"><span><Users :size="23" /></span><h3>好友列表还是空的</h3><p>使用上方的 12 位 Memo ID 找到重要的人。</p></div>
  </section>

  <div v-if="editingFriend" class="modal-backdrop" @click.self="editingFriend = null">
    <section class="create-modal friend-settings-modal" role="dialog" aria-modal="true" aria-labelledby="friend-setting-title">
      <header><div><span class="eyebrow">FRIEND SETTINGS</span><h2 id="friend-setting-title">{{ displayName(editingFriend) }}</h2></div><button class="icon-button" aria-label="关闭" @click="editingFriend = null"><X :size="18" /></button></header>
      <label class="field"><span>仅自己可见的好友备注</span><input v-model="settingForm.remarkName" maxlength="60" placeholder="留空则显示对方昵称" /></label>
      <label class="setting-toggle"><span><b>允许对方直接创建提醒</b><small>关闭后，对方发来的提醒需要你确认。</small></span><input v-model="settingForm.allowDirectReminders" type="checkbox" /></label>
      <label class="setting-toggle"><span><b>聊天静音</b><small>保留消息，但不突出显示聊天通知。</small></span><input v-model="settingForm.muteChat" type="checkbox" /></label>
      <footer class="friend-setting-footer">
        <div><button class="danger-link" @click="removeFriend(editingFriend)"><UserMinus :size="15" /> 删除好友</button><button class="danger-link" @click="blockFriend(editingFriend)"><ShieldBan :size="15" /> 拉黑</button></div>
        <button class="button primary" :disabled="savingSettings" @click="saveSettings">{{ savingSettings ? '保存中…' : '保存设置' }}</button>
      </footer>
    </section>
  </div>
</template>
