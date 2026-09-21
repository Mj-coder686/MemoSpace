<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import dayjs from 'dayjs'
import { Bookmark, Flag, MapPin, MessageCircle, RefreshCw, Send, Users } from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import PrivateMedia from '../components/PrivateMedia.vue'
import ReportModal from '../components/ReportModal.vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const id = Number(useRoute().params.id)
const memory = ref<any>(null)
const comment = ref('')
const message = ref('')
const loading = ref(true)
const pageError = ref('')
const actionBusy = ref(false)
const reportTarget = ref<{ type:'MEMORY'|'COMMENT'; id:number; title:string } | null>(null)

const load = async () => {
  loading.value = true
  pageError.value = ''
  try { memory.value = (await http.get(`/memories/${id}`)).data }
  catch (error) { pageError.value = errorMessage(error) }
  finally { loading.value = false }
}

onMounted(load)

const react = async (reaction: string) => {
  if (actionBusy.value) return
  actionBusy.value = true
  message.value = ''
  try { await http.post(`/memories/${id}/reactions`, { reaction }); await load() }
  catch (error) { message.value = errorMessage(error) }
  finally { actionBusy.value = false }
}

const send = async () => {
  if (!comment.value.trim() || actionBusy.value) return
  actionBusy.value = true
  message.value = ''
  try {
    await http.post(`/memories/${id}/comments`, { content: comment.value.trim() })
    comment.value = ''
    await load()
  } catch (error) { message.value = errorMessage(error) }
  finally { actionBusy.value = false }
}

const favorite = async () => {
  if (actionBusy.value) return
  actionBusy.value = true
  message.value = ''
  try { await http.post(`/memories/${id}/favorite`); message.value = '已更新收藏' }
  catch (error) { message.value = errorMessage(error) }
  finally { actionBusy.value = false }
}

const reported = () => {
  reportTarget.value = null
  message.value = '举报已提交，管理员会在后台核查。你可以到“消息与通知 → 我的举报”查看进度。'
}
</script>

<template>
  <header class="page-heading"><router-link to="/memories" class="text-link">← 返回记忆库</router-link></header>

  <div v-if="loading" class="panel detail-state">正在打开这段记忆…</div>
  <div v-else-if="pageError" class="panel detail-state error-state" role="alert">
    <b>这段记忆暂时无法打开</b><p>{{ pageError }}</p>
    <button class="button primary" @click="load"><RefreshCw :size="15" />重新载入</button>
  </div>

  <template v-else-if="memory">
    <p v-if="message" class="panel detail-message" role="status">{{ message }}</p>
    <div class="detail-layout">
      <article class="detail-story">
        <span class="eyebrow">{{ memory.memory_type }} · {{ memory.visibility }}</span>
        <h1>{{ memory.title }}</h1>
        <div class="detail-chips">
          <span class="chip">{{ dayjs(memory.occurred_at).format('YYYY年MM月DD日 HH:mm') }}</span>
          <span v-if="memory.location" class="chip"><MapPin :size="13" />{{ memory.location }}</span>
          <span class="chip">{{ memory.creator_nickname }}</span>
        </div>
        <div v-if="memory.media?.length" class="media-gallery">
          <PrivateMedia v-for="media in memory.media" :key="media.id" :file-id="media.file_id" :mime-type="media.mime_type" :alt="memory.title" />
        </div>
        <div v-else-if="memory.memory_type!=='TEXT'" class="memory-visual detail-placeholder">
          <span class="visual-date">MEMORY NO. {{ memory.id }}</span>
          <span>{{ memory.location || '此刻有光' }}</span><small>这条记忆没有可显示的媒体</small>
        </div>
        <p class="detail-content">{{ memory.content || '有些时刻，照片已经说完了一切。' }}</p>
        <button v-if="memory.creator_id!==auth.user?.id" class="button report-entry" @click="reportTarget={type:'MEMORY',id,title:memory.title}"><Flag :size="14" />举报这条记忆</button>
        <div v-if="memory.spaces?.length" class="detail-spaces">
          <span class="eyebrow">BELONGS TO</span><div><router-link v-for="space in memory.spaces" :key="space.id" :to="`/space/${space.id}`" class="chip"><Users :size="13" />{{ space.name }}</router-link></div>
        </div>
      </article>

      <aside class="detail-side">
        <section class="panel">
          <span class="eyebrow">A SMALL RESPONSE</span><h2>给这段记忆一个回应</h2>
          <div class="reaction-row"><button v-for="emoji in ['❤️','😂','🥹','👍','😭']" :key="emoji" :disabled="actionBusy" @click="react(emoji)">{{ emoji }}</button></div>
          <div v-if="memory.reactions?.length" class="reaction-counts"><span v-for="item in memory.reactions" :key="item.reaction_type">{{ item.reaction_type }} {{ item.count }}</span></div>
          <button v-if="memory.visibility==='PUBLIC'" class="button ghost favorite-button" :disabled="actionBusy" @click="favorite"><Bookmark :size="14" />收藏公开动态</button>
        </section>
        <section class="panel">
          <span class="eyebrow">CONVERSATION</span><h2><MessageCircle :size="18" />关于这一刻</h2>
          <div><div v-for="item in memory.comments" :key="item.id" class="comment"><b>{{ item.nickname }}</b><p>{{ item.content }}</p><small>{{ dayjs(item.created_at).format('MM.DD HH:mm') }}</small><button v-if="item.user_id!==auth.user?.id" class="comment-report" @click="reportTarget={type:'COMMENT',id:item.id,title:`${item.nickname}：${item.content.slice(0,40)}`}"><Flag :size="12" />举报</button></div></div>
          <div class="comment-composer"><input v-model="comment" :disabled="actionBusy" placeholder="写下想说的话…" @keyup.enter="send" /><button class="icon-button" :disabled="actionBusy||!comment.trim()" aria-label="发送评论" @click="send"><Send :size="16" /></button></div>
        </section>
      </aside>
    </div>
  </template>

  <ReportModal v-if="reportTarget" :target-type="reportTarget.type" :target-id="reportTarget.id" :target-title="reportTarget.title" @close="reportTarget=null" @reported="reported" />
</template>

<style scoped>
.detail-state{padding:42px;text-align:center}.detail-state b{display:block;font-size:18px}.detail-state p{color:var(--muted)}.detail-state .button{display:inline-flex;align-items:center;gap:6px}.detail-message{margin-bottom:18px;padding:12px 16px;color:var(--accent);font-size:12px}.detail-chips{margin-bottom:30px;display:flex;flex-wrap:wrap;gap:9px}.detail-placeholder{height:330px;margin-bottom:30px;border-radius:23px}.detail-placeholder>span:nth-child(2){align-self:center;font:600 28px 'Noto Serif SC',serif}.report-entry{margin-top:25px;display:inline-flex;align-items:center;gap:6px;color:#80545a}.detail-spaces{margin-top:35px}.detail-spaces>div{display:flex;flex-wrap:wrap;gap:8px}.detail-side h2{font-size:19px}.reaction-counts{margin-top:12px;color:var(--muted);font-size:12px}.reaction-counts span{margin-right:10px}.favorite-button{margin-top:15px}.comment{position:relative}.comment-report{position:absolute;right:0;top:0;display:flex;align-items:center;gap:3px;padding:4px;border:0;color:var(--muted);background:transparent;font-size:9px}.comment-report:hover{color:#8d4f55}.comment-composer{margin-top:14px;display:flex;gap:7px}.comment-composer input{min-width:0;flex:1;padding:10px;border:1px solid var(--line);border-radius:12px;color:var(--ink);background:var(--surface-solid)}
</style>
