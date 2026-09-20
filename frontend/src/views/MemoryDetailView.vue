<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import dayjs from 'dayjs'
import { Bookmark, Flag, MapPin, MessageCircle, Send, Users } from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import PrivateMedia from '../components/PrivateMedia.vue'
import ReportModal from '../components/ReportModal.vue'
import { useAuthStore } from '../stores/auth'

const auth=useAuthStore();const id=Number(useRoute().params.id);const memory=ref<any>({});const comment=ref('');const message=ref('')
const reportTarget=ref<{type:'MEMORY'|'COMMENT';id:number;title:string}|null>(null)
const load=async()=>{memory.value=(await http.get(`/memories/${id}`)).data}
onMounted(load)
const react=async(reaction:string)=>{await http.post(`/memories/${id}/reactions`,{reaction});await load()}
const send=async()=>{if(!comment.value.trim())return;try{await http.post(`/memories/${id}/comments`,{content:comment.value});comment.value='';await load()}catch(e){message.value=errorMessage(e)}}
const favorite=async()=>{try{await http.post(`/memories/${id}/favorite`);message.value='已更新收藏'}catch(e){message.value=errorMessage(e)}}
const reported=()=>{reportTarget.value=null;message.value='举报已提交，管理员会在后台核查。'}
</script>

<template>
  <header class="page-heading"><router-link to="/memories" class="text-link">← 返回记忆库</router-link></header>
  <div class="detail-layout">
    <article class="detail-story">
      <span class="eyebrow">{{ memory.memory_type }} · {{ memory.visibility }}</span>
      <h1>{{ memory.title }}</h1>
      <div style="display:flex;flex-wrap:wrap;gap:9px;margin-bottom:30px"><span class="chip">{{ dayjs(memory.occurred_at).format('YYYY年MM月DD日 HH:mm') }}</span><span v-if="memory.location" class="chip"><MapPin :size="13" />{{ memory.location }}</span><span class="chip">{{ memory.creator_nickname }}</span></div>
      <div v-if="memory.media?.length" class="media-gallery"><PrivateMedia v-for="media in memory.media" :key="media.id" :file-id="media.file_id" :mime-type="media.mime_type" :alt="memory.title" /></div>
      <div v-else-if="memory.memory_type!=='TEXT'" class="memory-visual" style="height:330px;border-radius:23px;margin-bottom:30px"><span class="visual-date">MEMORY NO. {{ memory.id }}</span><span style="align-self:center;font:600 28px 'Noto Serif SC',serif">{{ memory.location || '此刻有光' }}</span><small>私密媒体会通过短期授权访问</small></div>
      <p class="detail-content">{{ memory.content || '有些时刻，照片已经说完了一切。' }}</p>
      <button v-if="memory.creator_id!==auth.user?.id" class="button report-entry" @click="reportTarget={type:'MEMORY',id,title:memory.title}"><Flag :size="14" />举报这条记忆</button>
      <div v-if="memory.spaces?.length" style="margin-top:35px"><span class="eyebrow">BELONGS TO</span><div style="display:flex;gap:8px;flex-wrap:wrap"><router-link v-for="space in memory.spaces" :key="space.id" :to="`/space/${space.id}`" class="chip"><Users :size="13" />{{ space.name }}</router-link></div></div>
    </article>
    <aside class="detail-side">
      <section class="panel"><span class="eyebrow">A SMALL RESPONSE</span><h2 style="font-size:19px">给这段记忆一个回应</h2><div class="reaction-row"><button v-for="emoji in ['❤️','😂','🥹','👍','😭']" :key="emoji" @click="react(emoji)">{{ emoji }}</button></div><div v-if="memory.reactions?.length" style="margin-top:12px;color:var(--muted);font-size:12px"><span v-for="item in memory.reactions" :key="item.reaction_type" style="margin-right:10px">{{ item.reaction_type }} {{ item.count }}</span></div><button v-if="memory.visibility==='PUBLIC'" class="button ghost" style="margin-top:15px" @click="favorite"><Bookmark :size="14" /> 收藏公开动态</button></section>
      <section class="panel"><span class="eyebrow">CONVERSATION</span><h2 style="font-size:19px"><MessageCircle :size="18" /> 关于这一刻</h2><div><div v-for="item in memory.comments" :key="item.id" class="comment"><b>{{ item.nickname }}</b><p>{{ item.content }}</p><small>{{ dayjs(item.created_at).format('MM.DD HH:mm') }}</small><button v-if="item.user_id!==auth.user?.id" class="comment-report" @click="reportTarget={type:'COMMENT',id:item.id,title:`${item.nickname}：${item.content.slice(0,40)}`}"><Flag :size="12" />举报</button></div></div><div style="display:flex;gap:7px;margin-top:14px"><input v-model="comment" style="min-width:0;flex:1;padding:10px;border:1px solid var(--line);border-radius:12px" placeholder="写下想说的话…" @keyup.enter="send" /><button class="icon-button" @click="send"><Send :size="16" /></button></div><p v-if="message" style="margin:10px 0 0;color:var(--muted);font-size:12px">{{ message }}</p></section>
    </aside>
  </div>
  <ReportModal v-if="reportTarget" :target-type="reportTarget.type" :target-id="reportTarget.id" :target-title="reportTarget.title" @close="reportTarget=null" @reported="reported" />
</template>

<style scoped>
.report-entry{margin-top:25px;display:inline-flex;align-items:center;gap:6px;color:#80545a}.comment{position:relative}.comment-report{position:absolute;right:0;top:0;display:flex;align-items:center;gap:3px;padding:4px;border:0;color:var(--muted);background:transparent;font-size:9px}.comment-report:hover{color:#8d4f55}
</style>
