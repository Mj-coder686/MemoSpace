<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import dayjs from 'dayjs'
import {
  ArrowLeft, Bookmark, Flag, Globe2, LockKeyhole, MapPin, MessageCircle, RefreshCw, Send, Trash2, UsersRound,
} from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import PrivateMedia from '../components/PrivateMedia.vue'
import ReportModal from '../components/ReportModal.vue'
import MemoryDeleteDialog from '../components/MemoryDeleteDialog.vue'
import EmptyState from '../components/EmptyState.vue'
import { UiBanner, UiButton, UiDialog, UiSkeleton } from '../components/ui'
import { useAuthStore } from '../stores/auth'

type PageState = 'loading' | 'ready' | 'forbidden' | 'not-found' | 'error'

const auth = useAuthStore()
const router = useRouter()
const id = Number(useRoute().params.id)
const memory = ref<any>(null)
const comment = ref('')
const statusMessage = ref('')
const actionError = ref('')
const commentError = ref('')
const pageState = ref<PageState>('loading')
const pageError = ref('')
const actionBusy = ref(false)
const reportTarget = ref<{ type: 'MEMORY' | 'COMMENT'; id: number; title: string } | null>(null)
const previewMedia = ref<any | null>(null)
const deleteTarget = ref<any | null>(null)
const isOwner = computed(() => Number(memory.value?.creator_id) === Number(auth.user?.id))

const visibility = computed(() => ({
  PRIVATE: { label: '仅自己可见', description: '只保存在创建者的私人空间', icon: LockKeyhole },
  RELATIONSHIP: { label: '关系成员可见', description: '仅所属共同空间的合法成员可见', icon: UsersRound },
  PUBLIC: { label: '公开记忆', description: '可能出现在关注者的动态中', icon: Globe2 },
  CUSTOM: { label: '指定的人可见', description: '只向创建者选择的人开放', icon: UsersRound },
})[memory.value?.visibility as 'PRIVATE' | 'RELATIONSHIP' | 'PUBLIC' | 'CUSTOM'] || { label: '受保护的记忆', description: '访问范围由创建者决定', icon: LockKeyhole })

const load = async (silent = false) => {
  if (!silent) pageState.value = 'loading'
  pageError.value = ''
  try {
    memory.value = (await http.get(`/memories/${id}`)).data
    pageState.value = 'ready'
  } catch (error: any) {
    if (silent) throw error
    const status = error?.response?.status
    pageState.value = status === 403 ? 'forbidden' : status === 404 ? 'not-found' : 'error'
    pageError.value = status === 403 ? '你没有查看这段内容的权限。' : status === 404 ? '这段记忆不存在或已经被删除。' : errorMessage(error)
  }
}

onMounted(() => { void load() })

const react = async (reaction: string) => {
  if (actionBusy.value) return
  actionBusy.value = true
  actionError.value = ''
  statusMessage.value = ''
  try {
    await http.post(`/memories/${id}/reactions`, { reaction })
    await load(true)
  } catch (error) {
    actionError.value = errorMessage(error)
  } finally {
    actionBusy.value = false
  }
}

const send = async () => {
  if (!comment.value.trim() || actionBusy.value) return
  actionBusy.value = true
  commentError.value = ''
  try {
    await http.post(`/memories/${id}/comments`, { content: comment.value.trim() })
    comment.value = ''
    await load(true)
  } catch (error) {
    commentError.value = errorMessage(error)
  } finally {
    actionBusy.value = false
  }
}

const favorite = async () => {
  if (actionBusy.value) return
  actionBusy.value = true
  actionError.value = ''
  statusMessage.value = ''
  try {
    await http.post(`/memories/${id}/favorite`)
    statusMessage.value = '已更新收藏。'
  } catch (error) {
    actionError.value = errorMessage(error)
  } finally {
    actionBusy.value = false
  }
}

const reported = () => {
  reportTarget.value = null
  statusMessage.value = '举报已提交。你可以在“通知 → 我的举报”中查看处理进度。'
}

const deleted = async () => {
  deleteTarget.value = null
  await router.replace('/memories')
}
</script>

<template>
  <div class="memory-detail-page">
    <router-link class="memory-detail-back" to="/memories"><ArrowLeft :size="17" />返回记忆库</router-link>

    <div v-if="pageState === 'loading'" class="memory-detail-loading" aria-label="正在打开这段记忆">
      <div><UiSkeleton width="120px" /><UiSkeleton height="54px" /><UiSkeleton height="380px" radius="var(--radius-lg)" /><UiSkeleton height="120px" /></div>
      <div><UiSkeleton height="180px" radius="var(--radius-lg)" /><UiSkeleton height="300px" radius="var(--radius-lg)" /></div>
    </div>

    <EmptyState
      v-else-if="pageState === 'forbidden'"
      kind="permission"
      title="无法访问这段记忆"
      text="你当前没有查看权限。为保护隐私，这里不会显示更多内容信息。"
    >
      <template #actions><UiButton variant="secondary" @click="$router.push('/memories')">返回记忆库</UiButton></template>
    </EmptyState>

    <EmptyState
      v-else-if="pageState === 'not-found'"
      kind="archived"
      title="这段记忆已经不在这里"
      text="它可能已经被删除，或者原来的链接已经失效。"
    >
      <template #actions><UiButton variant="secondary" @click="$router.push('/memories')">返回记忆库</UiButton></template>
    </EmptyState>

    <EmptyState
      v-else-if="pageState === 'error'"
      kind="error"
      title="这段记忆暂时无法打开"
      :text="pageError"
      action-label="重新载入"
      @action="load()"
    />

    <template v-else-if="memory">
      <UiBanner v-if="statusMessage" class="memory-detail-banner" tone="success" title="操作已完成" :description="statusMessage" />
      <UiBanner v-if="actionError" class="memory-detail-banner" tone="danger" title="操作没有完成" :description="actionError" />

      <div class="memory-detail-layout">
        <article class="memory-detail-story">
          <header class="memory-detail-title">
            <span class="memory-kicker">MEMORY NO. {{ memory.id }}</span>
            <h1>{{ memory.title }}</h1>
            <div class="memory-detail-meta">
              <time :datetime="memory.occurred_at">{{ dayjs(memory.occurred_at).format('YYYY年M月D日 HH:mm') }}</time>
              <span v-if="memory.location"><MapPin :size="14" />{{ memory.location }}</span>
              <span>{{ memory.creator_nickname }}</span>
            </div>
            <div class="memory-visibility-note">
              <component :is="visibility.icon" :size="18" aria-hidden="true" />
              <span><strong>{{ visibility.label }}</strong><small>{{ visibility.description }}</small></span>
            </div>
          </header>

          <div v-if="memory.media?.length" class="memory-detail-media" :class="{ 'is-single': memory.media.length === 1 }">
            <button v-for="media in memory.media" :key="media.id" type="button" :aria-label="`预览媒体：${memory.title}`" @click="previewMedia = media">
              <PrivateMedia :file-id="media.file_id" :mime-type="media.mime_type" :alt="memory.title" preview />
            </button>
          </div>

          <section class="memory-detail-content" aria-label="记忆正文">
            <p>{{ memory.content || '有些时刻，照片已经说完了一切。' }}</p>
          </section>

          <section v-if="memory.spaces?.length" class="memory-detail-spaces" aria-labelledby="memory-spaces-title">
            <span class="memory-kicker">BELONGS TO</span>
            <h2 id="memory-spaces-title">所属空间</h2>
            <div><router-link v-for="space in memory.spaces" :key="space.id" :to="`/space/${space.id}`"><UsersRound :size="16" />{{ space.name }}</router-link></div>
          </section>

          <button v-if="memory.creator_id !== auth.user?.id" class="memory-report-entry" type="button" @click="reportTarget = { type: 'MEMORY', id, title: memory.title }"><Flag :size="14" />举报这条记忆</button>
          <div v-if="isOwner" class="memory-owner-actions">
            <UiButton variant="danger" size="sm" @click="deleteTarget = memory"><Trash2 :size="16" />删除这条记忆</UiButton>
          </div>
        </article>

        <aside class="memory-detail-aside">
          <section class="memory-response-panel">
            <span class="memory-kicker">A SMALL RESPONSE</span>
            <h2>给这段记忆一个回应</h2>
            <div class="memory-reaction-row" aria-label="选择回应">
              <button v-for="emoji in ['❤️', '😂', '🥹', '👍', '😭']" :key="emoji" type="button" :disabled="actionBusy" :aria-label="`回应 ${emoji}`" @click="react(emoji)">{{ emoji }}</button>
            </div>
            <div v-if="memory.reactions?.length" class="memory-reaction-counts"><span v-for="item in memory.reactions" :key="item.reaction_type">{{ item.reaction_type }} {{ item.count }}</span></div>
            <UiButton v-if="memory.visibility === 'PUBLIC'" variant="ghost" :disabled="actionBusy" @click="favorite"><Bookmark :size="16" />收藏公开动态</UiButton>
          </section>

          <section class="memory-comments" aria-labelledby="memory-comments-title">
            <div class="memory-comments__heading"><span class="memory-kicker">CONVERSATION</span><h2 id="memory-comments-title"><MessageCircle :size="19" />关于这一刻</h2></div>
            <div v-if="memory.comments?.length" class="memory-comment-list">
              <article v-for="item in memory.comments" :key="item.id" class="memory-comment">
                <span class="memory-comment__avatar">{{ item.nickname?.slice(0, 1) || '拾' }}</span>
                <div><strong>{{ item.nickname }}</strong><p>{{ item.content }}</p><time :datetime="item.created_at">{{ dayjs(item.created_at).format('M.D HH:mm') }}</time></div>
                <button v-if="item.user_id !== auth.user?.id" type="button" :aria-label="`举报 ${item.nickname} 的评论`" @click="reportTarget = { type: 'COMMENT', id: item.id, title: `${item.nickname}：${item.content.slice(0, 40)}` }"><Flag :size="13" /></button>
              </article>
            </div>
            <p v-else class="memory-comments__empty">还没有人留言。你可以从一句很轻的话开始。</p>
            <div class="memory-comment-composer">
              <label for="memory-comment-input" class="sr-only">写下评论</label>
              <textarea id="memory-comment-input" v-model="comment" :disabled="actionBusy" rows="2" placeholder="写下想说的话……" @keydown.ctrl.enter.prevent="send" />
              <UiButton :disabled="actionBusy || !comment.trim()" variant="primary" aria-label="发送评论" @click="send"><Send :size="17" /></UiButton>
            </div>
            <p v-if="commentError" class="memory-comment-error" role="alert">{{ commentError }} 输入内容已保留，可以重试。</p>
          </section>
        </aside>
      </div>
    </template>

    <UiDialog
      :open="Boolean(previewMedia)"
      title="媒体预览"
      width="wide"
      compact-fullscreen
      @close="previewMedia = null"
    >
      <div v-if="previewMedia" class="memory-media-preview">
        <PrivateMedia :file-id="previewMedia.file_id" :mime-type="previewMedia.mime_type" :alt="memory?.title" />
      </div>
    </UiDialog>

    <ReportModal v-if="reportTarget" :target-type="reportTarget.type" :target-id="reportTarget.id" :target-title="reportTarget.title" @close="reportTarget = null" @reported="reported" />
    <MemoryDeleteDialog :memory="deleteTarget" @close="deleteTarget = null" @deleted="deleted" />
  </div>
</template>
