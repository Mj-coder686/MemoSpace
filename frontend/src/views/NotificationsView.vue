<script setup lang="ts">
import { onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import { BellRing } from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import EmptyState from '../components/EmptyState.vue'
import { useAuthStore } from '../stores/auth'
const auth=useAuthStore();const notifications=ref<any[]>([]);const invitations=ref<any[]>([]);const message=ref('')
const load=async()=>{const[a,b]=await Promise.all([http.get('/notifications'),http.get('/relationships/invitations')]);notifications.value=a.data;invitations.value=b.data.filter((x:any)=>x.receiver_id===auth.user?.id&&x.status==='PENDING');await http.put('/notifications/read')}
const respond=async(id:number,action:'accept'|'reject')=>{try{const {data}=await http.post(`/relationships/invitations/${id}/${action}`);message.value=action==='accept'?(data.reusedSpace?'已加入现有共同空间，同一段关系不会重复建空间':'共同空间已经准备好了'):'已婉拒邀请';await load()}catch(e){message.value=errorMessage(e)}}
onMounted(load)
</script>
<template><header class="page-heading"><div><span class="eyebrow">A GENTLE REMINDER</span><h1>消息</h1><p>关系邀请、评论和共同空间的新动静都在这里。</p></div></header><p v-if="message" class="panel" style="padding:13px 18px">{{message}}</p><section v-if="invitations.length"><div class="section-heading"><h2>等待你的回应</h2></div><div class="notification-list"><article v-for="item in invitations" :key="item.id" class="notification-item unread"><span class="notification-avatar">{{item.sender_nickname?.slice(0,1)}}</span><div><h3>{{item.sender_nickname}} 邀请你绑定为「{{item.category_name||(item.relationship_type==='COUPLE'?'恋人':item.relationship_type==='FAMILY'?'家人':'死党')}}」</h3><p>{{item.message||'一起收藏共同故事。'}} · 接受后会创建或关联双方唯一的共同空间。</p></div><div style="display:flex;gap:6px"><button class="button" @click="respond(item.id,'reject')">婉拒</button><button class="button primary" @click="respond(item.id,'accept')">接受</button></div></article></div></section><div class="section-heading"><h2>最近发生</h2></div><div v-if="notifications.length" class="notification-list"><article v-for="item in notifications" :key="item.id" class="notification-item" :class="{unread:!item.is_read}"><span class="notification-avatar"><BellRing :size="17" /></span><div><h3>{{item.title}}</h3><p>{{item.content}}</p></div><time>{{dayjs(item.created_at).format('MM.DD HH:mm')}}</time></article></div><EmptyState v-else title="暂时没有新消息" text="安静也是空间的一部分。" /></template>
