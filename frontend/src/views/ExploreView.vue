<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search, UserPlus } from 'lucide-vue-next'
import http from '../api/http'
import MemoryCard from '../components/MemoryCard.vue'
import EmptyState from '../components/EmptyState.vue'

const router=useRouter();const tab=ref<'latest'|'following'|'people'>('latest');const feed=ref<any[]>([]);const people=ref<any[]>([]);const q=ref('');const message=ref('')
const loadFeed=async()=>{if(tab.value!=='people')feed.value=(await http.get('/feed',{params:{scope:tab.value}})).data}
const search=async()=>{people.value=(await http.get('/users/search',{params:{q:q.value}})).data;tab.value='people'}
const follow=async(person:any)=>{const {data}=await http.post(`/users/${person.id}/follow`);person.following=data.following}
const invite=(person:any)=>router.push({path:'/relationships',query:{inviteUser:String(person.id),inviteName:person.nickname}})
onMounted(loadFeed)
</script>

<template>
  <header class="page-heading"><div><span class="eyebrow">PUBLIC MOMENTS</span><h1>公共动态</h1><p>偶尔走出私人空间，看看朋友们愿意分享的生活切片。</p></div><form class="search-wide" @submit.prevent="search"><Search :size="18" /><input v-model="q" placeholder="按昵称寻找一个人" /></form></header>
  <div class="filters"><button :class="{active:tab==='latest'}" @click="tab='latest';loadFeed()">最新</button><button :class="{active:tab==='following'}" @click="tab='following';loadFeed()">关注</button><button :class="{active:tab==='people'}" @click="search">找人</button></div>
  <p v-if="message" class="panel" style="padding:13px 18px">{{message}}</p>
  <div v-if="tab!=='people' && feed.length" class="memory-grid"><MemoryCard v-for="item in feed" :key="item.id" :memory="item" /></div>
  <EmptyState v-else-if="tab!=='people'" title="这一页还很安静" text="关注的人发布公开记忆后，会出现在这里。" />
  <div v-else class="space-grid">
    <article v-for="person in people" :key="person.id" class="panel profile-hero" style="grid-template-columns:auto 1fr">
      <router-link :to="`/user/${person.id}`" class="profile-avatar" style="width:58px;height:58px;font-size:21px">{{person.nickname?.slice(0,1)}}</router-link>
      <div><router-link :to="`/user/${person.id}`"><h2 style="margin:0;font-size:19px">{{person.nickname}}</h2></router-link><p style="margin:4px 0 12px;color:var(--muted);font-size:12px">@{{person.username}} · {{person.location||'某个温柔的地方'}}</p><div style="display:flex;gap:7px"><button class="button" @click="follow(person)">{{person.following?'取消关注':'关注'}}</button><button class="button primary" @click="invite(person)"><UserPlus :size="14" /> 选择关系分类</button></div></div>
    </article>
  </div>
</template>
