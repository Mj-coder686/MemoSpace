<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import {
  BellOff, BellRing, Check, Clock3, Copy, HeartHandshake, MessageCircle, Search, Settings2,
  ShieldBan, UserMinus, UserPlus, Users, X
} from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import { useAuthStore } from '../stores/auth'
import { useRealtimeStore } from '../stores/realtime'
import type { RealtimeEvent } from '../stores/realtime'
import UserAvatar from '../components/UserAvatar.vue'

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
const categories = ref<any[]>([])
const relationFriend = ref<Friend|null>(null)
const relationForm = ref({ categoryId:'', message:'想和你建立一段共同记录的关系。' })
const relationBusy = ref(false)
let unsubscribeRealtime:(()=>void)|undefined

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
    const [friendResponse, requestResponse, categoryResponse] = await Promise.all([
      http.get('/friends'),
      http.get('/friends/requests'),
      http.get('/relationship-categories')
    ])
    friends.value = friendResponse.data
    requests.value = requestResponse.data
    categories.value = categoryResponse.data
  } catch (error) {
    pageError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

const normalizeSearch = () => {
  query.value = query.value.trimStart().slice(0, 60)
  searchResults.value = []
}

const searchUsers = async () => {
  const keyword = query.value.trim()
  if (keyword.length < 2) {
    pageError.value = '请输入至少 2 位 Memo ID、昵称或用户名。'
    return
  }
  searching.value = true
  pageError.value = ''
  pageMessage.value = ''
  try {
    const { data } = await http.get('/users/search', { params: { q: keyword } })
    searchResults.value = data.filter((person: any) => Number(person.id) !== Number(auth.user?.id))
    if (!searchResults.value.length) pageMessage.value = `没有找到“${keyword}”，可以换完整 Memo ID、昵称或用户名再试。`
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

const openRelationship = (friend:Friend) => {
  relationFriend.value=friend
  relationForm.value={categoryId:categories.value.length?String(categories.value[0].id):'',message:'想和你建立一段共同记录的关系。'}
}

const sendRelationship = async () => {
  if(!relationFriend.value||!relationForm.value.categoryId)return
  relationBusy.value=true;pageError.value=''
  try{const category=categories.value.find(item=>String(item.id)===relationForm.value.categoryId);await http.post('/relationships/invitations',{receiverId:relationFriend.value.friend_id,categoryId:Number(relationForm.value.categoryId),message:relationForm.value.message.trim()});pageMessage.value=`已向 ${displayName(relationFriend.value)} 发出「${category?.name||'关系'}」申请，对方会立即收到通知。`;relationFriend.value=null}
  catch(error){pageError.value=errorMessage(error)}finally{relationBusy.value=false}
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
  unsubscribeRealtime=realtime.subscribe((event:RealtimeEvent)=>{
    if(event.type==='NOTIFICATION'&&['FRIEND_REQUEST','FRIEND_ACCEPT'].includes(String(event.notificationType)))load()
  })
  if (!memoId.value) await auth.loadMe()
  await load()
})
onBeforeUnmount(()=>unsubscribeRealtime?.())
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
      <span class="eyebrow">FIND A FRIEND</span>
      <h2>添加好友</h2>
      <p>支持完整或部分 Memo ID，也可以输入昵称、用户名；数字 ID 不会因为昵称改变而变化。</p>
      <form class="memo-id-search" @submit.prevent="searchUsers">
        <Search :size="18" />
        <input v-model="query" maxlength="60" aria-label="搜索 Memo ID、昵称或用户名" placeholder="Memo ID、昵称或用户名" autocomplete="off" @input="normalizeSearch" />
        <button type="submit" :disabled="searching">{{ searching ? '查找中' : '查找' }}</button>
      </form>
      <label class="field compact-field">
        <span>申请留言</span>
        <input v-model="requestMessage" maxlength="200" />
      </label>
      <div v-if="searchResults.length" class="friend-search-results">
        <article v-for="person in searchResults" :key="person.id">
          <UserAvatar class="friend-avatar" :src="person.avatar" :name="person.nickname||person.username" />
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
          <UserAvatar class="friend-avatar small" :src="requestPerson(item).avatar" :name="requestPerson(item).nickname||'友'" />
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
          <UserAvatar class="friend-avatar large" :src="friend.avatar" :name="displayName(friend)" />
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
        <div class="friend-card-actions"><button class="button" @click="openRelationship(friend)"><HeartHandshake :size="16" /> 申请关系</button><button class="button primary" @click="router.push(`/chat/${friend.friend_id}`)"><MessageCircle :size="17" /> 聊天</button></div>
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

  <div v-if="relationFriend" class="modal-backdrop" @click.self="relationFriend=null">
    <section class="create-modal friend-settings-modal" role="dialog" aria-modal="true" aria-labelledby="relationship-request-title">
      <header><div><span class="eyebrow">RELATIONSHIP REQUEST</span><h2 id="relationship-request-title">和 {{displayName(relationFriend)}} 建立关系</h2></div><button class="icon-button" aria-label="关闭" @click="relationFriend=null"><X :size="18" /></button></header>
      <p class="relationship-helper">好友和关系是两层独立连接。对方接受后，你们会拥有唯一的共同空间。</p>
      <label class="field"><span>关系分类</span><select v-model="relationForm.categoryId"><option value="">请选择</option><option v-for="category in categories" :key="category.id" :value="String(category.id)">{{category.icon}} {{category.name}}</option></select></label>
      <label class="field"><span>申请留言</span><textarea v-model="relationForm.message" maxlength="200" rows="3"></textarea></label>
      <footer><button class="button" @click="relationFriend=null">取消</button><button class="button primary" :disabled="relationBusy||!relationForm.categoryId" @click="sendRelationship">{{relationBusy?'正在发送…':'发送关系申请'}}</button></footer>
    </section>
  </div>
</template>

<style scoped>
.friend-card-actions{display:grid;grid-template-columns:1fr 1fr;gap:7px}.friend-card-actions .button{padding:0 10px;display:flex;align-items:center;justify-content:center;gap:5px}.relationship-helper{margin:-4px 0 16px;color:var(--muted);font-size:12px;line-height:1.7}@media(max-width:420px){.friend-card-actions{grid-template-columns:1fr}}
</style>
