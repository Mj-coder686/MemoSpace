<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Copy, MapPin } from 'lucide-vue-next'
import http from '../api/http'
import { useAuthStore } from '../stores/auth'
import UserAvatar from '../components/UserAvatar.vue'
const auth=useAuthStore();const id=Number(useRoute().params.id);const profile=ref<any>({})
const memoId=()=>profile.value.public_id||profile.value.publicId||''
onMounted(async()=>{profile.value=id===auth.user?.id?auth.user:(await http.get(`/users/${id}`)).data})
const follow=async()=>{const{data}=await http.post(`/users/${id}/follow`);profile.value.is_following=data.following}
const copyMemoId=async()=>{if(!memoId())return;await navigator.clipboard.writeText(memoId())}
</script>
<template><section class="profile-hero"><UserAvatar class="profile-avatar" :src="profile.avatar" :name="profile.nickname" /><div><span class="eyebrow">@{{profile.username}}</span><h1>{{profile.nickname}}</h1><p>{{profile.bio||'这个人还没有写下自我介绍。'}}</p><p v-if="profile.location" style="margin-top:8px"><MapPin :size="13" style="vertical-align:middle" /> {{profile.location}}</p><p v-if="memoId()" class="profile-memo-id">Memo ID {{memoId()}} <button class="icon-button" aria-label="复制 Memo ID" @click="copyMemoId"><Copy :size="14" /></button></p></div><button v-if="id!==auth.user?.id" class="button primary" @click="follow">{{profile.is_following?'取消关注':'关注 TA'}}</button><router-link v-else class="button" to="/settings">编辑资料</router-link><div v-if="id!==auth.user?.id" class="profile-stats"><div><b>{{profile.public_memories||0}}</b><span>公开记忆</span></div><div><b>{{profile.followers||0}}</b><span>关注者</span></div><div><b>{{profile.following||0}}</b><span>正在关注</span></div></div></section><div class="section-heading"><div><span class="eyebrow">A SMALL PORTRAIT</span><h2>{{id===auth.user?.id?'我的空间入口':'TA 愿意公开的生活'}}</h2></div></div><div class="panel" style="min-height:180px;display:grid;place-items:center;text-align:center"><div><h2>{{id===auth.user?.id?'这里是你的数字档案首页':'尊重每一段记忆的边界'}}</h2><p style="color:var(--muted)">{{id===auth.user?.id?'前往记忆库、相册或共同空间继续翻阅。':'只有被设为公开的故事才会出现在公共动态中。'}}</p><router-link class="button primary" :to="id===auth.user?.id?'/memories':'/explore'">继续浏览</router-link></div></div></template>
