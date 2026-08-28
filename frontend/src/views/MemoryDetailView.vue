<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import dayjs from 'dayjs'
import { Bookmark, MapPin, MessageCircle, Send, Users } from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import PrivateMedia from '../components/PrivateMedia.vue'

const id=Number(useRoute().params.id);const memory=ref<any>({});const comment=ref('');const message=ref('')
const load=async()=>{memory.value=(await http.get(`/memories/${id}`)).data}
onMounted(load)
const react=async(reaction:string)=>{await http.post(`/memories/${id}/reactions`,{reaction});await load()}
const send=async()=>{if(!comment.value.trim())return;try{await http.post(`/memories/${id}/comments`,{content:comment.value});comment.value='';await load()}catch(e){message.value=errorMessage(e)}}
const favorite=async()=>{try{await http.post(`/memories/${id}/favorite`);message.value='已更新收藏'}catch(e){message.value=errorMessage(e)}}
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
      <div v-if="memory.spaces?.length" style="margin-top:35px"><span class="eyebrow">BELONGS TO</span><div style="display:flex;gap:8px;flex-wrap:wrap"><router-link v-for="space in memory.spaces" :key="space.id" :to="`/space/${space.id}`" class="chip"><Users :size="13" />{{ space.name }}</router-link></div></div>
    </article>
    <aside class="detail-side">
      <section class="panel"><span class="eyebrow">A SMALL RESPONSE</span><h2 style="font-size:19px">给这段记忆一个回应</h2><div class="reaction-row"><button v-for="emoji in ['❤️','😂','🥹','👍','😭']" :key="emoji" @click="react(emoji)">{{ emoji }}</button></div><div v-if="memory.reactions?.length" style="margin-top:12px;color:var(--muted);font-size:12px"><span v-for="item in memory.reactions" :key="item.reaction_type" style="margin-right:10px">{{ item.reaction_type }} {{ item.count }}</span></div><button v-if="memory.visibility==='PUBLIC'" class="button ghost" style="margin-top:15px" @click="favorite"><Bookmark :size="14" /> 收藏公开动态</button></section>
      <section class="panel"><span class="eyebrow">CONVERSATION</span><h2 style="font-size:19px"><MessageCircle :size="18" /> 关于这一刻</h2><div><div v-for="item in memory.comments" :key="item.id" class="comment"><b>{{ item.nickname }}</b><p>{{ item.content }}</p><small>{{ dayjs(item.created_at).format('MM.DD HH:mm') }}</small></div></div><div style="display:flex;gap:7px;margin-top:14px"><input v-model="comment" style="min-width:0;flex:1;padding:10px;border:1px solid var(--line);border-radius:12px" placeholder="写下想说的话…" @keyup.enter="send" /><button class="icon-button" @click="send"><Send :size="16" /></button></div><p v-if="message" style="margin:10px 0 0;color:var(--muted);font-size:12px">{{ message }}</p></section>
    </aside>
  </div>
</template>
