<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { ArrowLeft, BellPlus, CheckCheck, MessageCircle, Send, Settings2, Users } from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import { useAuthStore } from '../stores/auth'
import { type RealtimeEvent, useRealtimeStore } from '../stores/realtime'

type Message = {
  id: number
  friendshipId?: number
  senderId: number
  receiverId: number
  clientMessageId: string
  content: string
  sentAt: string
  deliveredAt?: string
  readAt?: string
  pending?: boolean
  failed?: boolean
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const realtime = useRealtimeStore()
const friendId = computed(() => Number(route.params.friendId))
const friend = ref<any | null>(null)
const messages = ref<Message[]>([])
const loading = ref(true)
const loadingOlder = ref(false)
const hasMore = ref(false)
const nextBeforeId = ref<number | null>(null)
const content = ref('')
const pageError = ref('')
const messageArea = ref<HTMLElement | null>(null)
let unsubscribe: (() => void) | undefined

const normalizeMessage = (value: any): Message => ({
  id: Number(value.id),
  friendshipId: Number(value.friendshipId ?? value.friendship_id),
  senderId: Number(value.senderId ?? value.sender_id),
  receiverId: Number(value.receiverId ?? value.receiver_id),
  clientMessageId: String(value.clientMessageId ?? value.client_message_id ?? ''),
  content: String(value.content ?? ''),
  sentAt: String(value.sentAt ?? value.sent_at ?? new Date().toISOString()),
  deliveredAt: value.deliveredAt ?? value.delivered_at,
  readAt: value.readAt ?? value.read_at
})

const displayName = computed(() => friend.value?.remark_name || friend.value?.nickname || '好友')
const isMine = (message: Message) => message.senderId === Number(auth.user?.id)
const scrollToBottom = async () => {
  await nextTick()
  if (messageArea.value) messageArea.value.scrollTop = messageArea.value.scrollHeight
}

const mergeMessage = (incoming: Message) => {
  const index = messages.value.findIndex(item =>
    (incoming.id > 0 && item.id === incoming.id) ||
    (incoming.clientMessageId && item.clientMessageId === incoming.clientMessageId)
  )
  if (index >= 0) messages.value[index] = { ...messages.value[index], ...incoming, pending: false, failed: false }
  else messages.value.push(incoming)
  messages.value.sort((a, b) => Number(a.id) - Number(b.id))
}

const markRead = async () => {
  const latest = [...messages.value].reverse().find(item => !isMine(item) && item.id > 0)
  if (!latest) return
  if (!realtime.send('READ', { friendId: friendId.value, throughMessageId: latest.id })) {
    try { await http.post(`/friends/${friendId.value}/messages/read`, { throughMessageId: latest.id }) } catch { /* next open retries */ }
  }
}

const loadMessages = async (older = false) => {
  if (older) loadingOlder.value = true
  try {
    const { data } = await http.get(`/friends/${friendId.value}/messages`, {
      params: { beforeId: older ? nextBeforeId.value : undefined, limit: 50 }
    })
    const items = (data.items || data || []).map(normalizeMessage)
    messages.value = older ? [...items, ...messages.value] : items
    hasMore.value = Boolean(data.hasMore)
    nextBeforeId.value = data.nextBeforeId ? Number(data.nextBeforeId) : null
    if (!older) {
      await scrollToBottom()
      await markRead()
    }
  } catch (error) {
    pageError.value = errorMessage(error)
  } finally {
    loadingOlder.value = false
  }
}

const loadFriend = async () => {
  try {
    const { data } = await http.get('/friends')
    friend.value = data.find((item: any) => Number(item.friend_id) === friendId.value) || null
    if (!friend.value) pageError.value = '这位用户不在你的好友列表中，无法打开聊天。'
  } catch (error) { pageError.value = errorMessage(error) }
}

const onRealtimeEvent = async (event: RealtimeEvent) => {
  if (event.type === 'ACK') {
    const clientId = String(event.clientMessageId || '')
    const index = messages.value.findIndex(item => item.clientMessageId === clientId)
    if (index >= 0) messages.value[index] = {
      ...messages.value[index], id: Number(event.messageId), sentAt: String(event.sentAt), pending: false
    }
    return
  }
  if (event.type === 'MESSAGE' && event.message) {
    const incoming = normalizeMessage(event.message)
    if (incoming.senderId !== friendId.value && incoming.receiverId !== friendId.value) return
    mergeMessage(incoming)
    await scrollToBottom()
    if (!isMine(incoming)) await markRead()
    return
  }
  if (event.type === 'READ' && Number(event.userId) === friendId.value) {
    const through = Number(event.throughMessageId)
    messages.value = messages.value.map(item => isMine(item) && item.id <= through
      ? { ...item, readAt: String(event.readAt || new Date().toISOString()) }
      : item)
    return
  }
  if (event.type === 'ERROR' && event.clientMessageId) {
    const item = messages.value.find(message => message.clientMessageId === String(event.clientMessageId))
    if (item) { item.pending = false; item.failed = true }
    pageError.value = String(event.message || '消息发送失败')
  }
}

const sendMessage = async () => {
  const text = content.value.trim()
  if (!text || !friend.value) return
  const clientMessageId = crypto.randomUUID()
  const optimistic: Message = {
    id: -Date.now(), senderId: Number(auth.user?.id), receiverId: friendId.value,
    clientMessageId, content: text, sentAt: new Date().toISOString(), pending: true
  }
  messages.value.push(optimistic)
  content.value = ''
  pageError.value = ''
  await scrollToBottom()
  if (!realtime.send('CHAT_SEND', { friendId: friendId.value, content: text, clientMessageId })) {
    optimistic.pending = false
    optimistic.failed = true
    pageError.value = '实时连接尚未建立，请稍候再发送。'
    realtime.connect()
  }
}

const handleComposerKey = (event: KeyboardEvent) => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    void sendMessage()
  }
}

onMounted(async () => {
  realtime.connect()
  unsubscribe = realtime.subscribe(onRealtimeEvent)
  await loadFriend()
  if (friend.value) await loadMessages()
  loading.value = false
})

onBeforeUnmount(() => unsubscribe?.())
</script>

<template>
  <section class="chat-page" aria-label="好友聊天">
    <header class="chat-header">
      <button class="icon-button" aria-label="返回好友中心" @click="router.push('/friends')"><ArrowLeft :size="19" /></button>
      <span class="friend-avatar">{{ displayName.slice(0, 1) }}</span>
      <div class="chat-person">
        <h1>{{ displayName }}</h1>
        <span><i :class="{ online: realtime.isOnline(friendId) }" />{{ realtime.isOnline(friendId) ? '在线' : '离线，消息会保留' }}<template v-if="friend?.public_id"> · Memo ID {{ friend.public_id }}</template></span>
      </div>
      <div class="chat-header-actions">
        <button class="button" @click="router.push({ path: '/reminders', query: { recipient: friendId } })"><BellPlus :size="16" /> 创建提醒</button>
        <button class="button" @click="router.push({ path: '/relationships', query: { inviteUser: friendId, inviteName: displayName } })"><Users :size="16" /> 绑定关系</button>
        <button class="icon-button" aria-label="好友设置" @click="router.push('/friends')"><Settings2 :size="18" /></button>
      </div>
    </header>

    <p v-if="pageError" class="relationship-notice error chat-error" role="alert">{{ pageError }}</p>
    <div ref="messageArea" class="chat-messages" aria-live="polite">
      <button v-if="hasMore" class="load-older" :disabled="loadingOlder" @click="loadMessages(true)">{{ loadingOlder ? '读取中…' : '查看更早的消息' }}</button>
      <div v-if="loading" class="chat-empty">正在打开对话…</div>
      <div v-else-if="!messages.length" class="chat-empty"><MessageCircle :size="30" /><h2>从一句问候开始</h2><p>消息会保存到共享数据库，换一台设备登录后仍能继续阅读。</p></div>
      <article v-for="message in messages" :key="`${message.id}-${message.clientMessageId}`" class="chat-message" :class="{ mine: isMine(message), failed: message.failed }">
        <div class="chat-bubble"><p>{{ message.content }}</p><small>{{ dayjs(message.sentAt).format('HH:mm') }}<template v-if="message.pending"> · 发送中</template><template v-else-if="message.failed"> · 发送失败</template><CheckCheck v-else-if="isMine(message) && message.readAt" :size="13" /></small></div>
      </article>
    </div>

    <form class="chat-composer" @submit.prevent="sendMessage">
      <textarea v-model="content" maxlength="1000" rows="1" aria-label="聊天消息" placeholder="写下想说的话，Enter 发送，Shift + Enter 换行" @keydown="handleComposerKey" />
      <button type="submit" :disabled="!content.trim() || !friend"><Send :size="19" /><span>发送</span></button>
    </form>
  </section>
</template>
