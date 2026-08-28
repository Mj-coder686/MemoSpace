<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { Archive, CalendarHeart, Send } from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import EmptyState from '../components/EmptyState.vue'
import PrivateMedia from '../components/PrivateMedia.vue'

const route = useRoute(); const router = useRouter(); const id = Number(route.params.id)
const space = ref<any>({}); const timeline = ref<any[]>([]); const messages = ref<any[]>([]); const events = ref<any[]>([])
const note = ref(''); const feedback = ref('')
const style = computed(() => ({ '--space-primary':space.value.primary_color || '#7f879e','--space-background':space.value.background_color || '#f7f6f4','--space-text':space.value.text_color || '#373a45','--space-muted':space.value.muted_color || '#848691' }))
const days = computed(() => Math.max(1, dayjs().diff(dayjs(space.value.created_at), 'day')))

const load = async () => {
  const [a,b,c,d] = await Promise.all([http.get(`/spaces/${id}`),http.get(`/spaces/${id}/timeline`),http.get(`/spaces/${id}/messages`),http.get(`/spaces/${id}/events`)])
  space.value=a.data; timeline.value=b.data; messages.value=c.data; events.value=d.data
}
onMounted(load)
const leave = async () => { if(!note.value.trim())return; await http.post(`/spaces/${id}/messages`,{content:note.value});note.value='';messages.value=(await http.get(`/spaces/${id}/messages`)).data }
const archive = async () => {
  if (!space.value.relationship_id || !confirm('封存后将不能继续添加共同记忆，但历史会被保留。确认吗？')) return
  try { await http.delete(`/relationships/${space.value.relationship_id}`); await load() } catch(e){ feedback.value=errorMessage(e) }
}
</script>

<template>
  <div :style="style">
    <section class="space-hero" :class="{archived:space.status==='ARCHIVED'}">
      <span class="eyebrow">{{ space.status==='ARCHIVED' ? 'A STORY SAFELY ARCHIVED' : space.space_type==='PERSONAL' ? 'MY PRIVATE UNIVERSE' : 'OUR SHARED DAYS' }}</span>
      <h1>{{ space.name }}</h1>
      <p v-if="space.status==='ARCHIVED'">此空间已于 {{ dayjs(space.archived_at).format('YYYY-MM-DD') }} 封存，历史记忆仍然被好好保存。</p>
      <p v-else-if="space.space_type==='RELATIONSHIP'">我们已经一起记录了 {{ days }} 天，每一次回望都有迹可循。</p>
      <p v-else>你的私人数字档案，只有你可以决定谁能走进这里。</p>
      <div class="space-stats"><div><b>{{ space.memoryCount || 0 }}</b><small>共同记忆</small></div><div><b>{{ space.photoCount || 0 }}</b><small>照片故事</small></div><div><b>{{ space.placeCount || 0 }}</b><small>一起去过</small></div></div>
    </section>

    <div class="two-column">
      <section>
        <div class="section-heading"><div><span class="eyebrow">MEMORY TIMELINE</span><h2>我们的时间轴</h2></div></div>
        <div v-if="timeline.length" class="timeline">
          <article v-for="item in timeline" :key="item.id" class="timeline-item">
            <div class="timeline-date">{{ dayjs(item.occurred_at).format('YYYY · MMMM · DD') }}</div>
            <router-link :to="`/memory/${item.id}`" class="timeline-card">
              <PrivateMedia v-if="item.cover_file_id" class="timeline-cover" :file-id="item.cover_file_id" :mime-type="item.cover_mime_type" :alt="item.title" preview />
              <h3>{{ item.title }}</h3><p>{{ item.content || '一段安静的记录' }}</p>
            </router-link>
          </article>
        </div>
        <EmptyState v-else title="这里还没有属于你们的故事" text="从第一张照片、第一句话或第一次旅行开始吧。" />
      </section>
      <aside style="display:grid;gap:18px">
        <section class="panel"><span class="eyebrow">PEOPLE HERE</span><h2 style="font-size:19px">空间成员</h2><div class="space-members"><span v-for="member in space.members" :key="member.id" :title="member.nickname">{{ member.nickname?.slice(0,1) }}</span></div></section>
        <section v-if="space.anniversaries?.length" class="panel"><span class="eyebrow">ANNIVERSARIES</span><h2 style="font-size:19px"><CalendarHeart :size="18" /> 重要的日子</h2><div v-for="day in space.anniversaries" :key="day.id" class="message-note"><b>{{ day.title }}</b><p>{{ dayjs(day.anniversary_date).format('MM 月 DD 日') }}</p></div></section>
        <section v-if="events.length" class="panel"><span class="eyebrow">SHARED EVENTS</span><h2 style="font-size:19px">共同事件</h2><router-link v-for="event in events" :key="event.id" :to="`/event/${event.id}`" class="message-note" style="display:block"><b>{{ event.name }}</b><p>{{ dayjs(event.start_at).format('YYYY.MM.DD') }} · {{ event.location || '共同故事' }}</p></router-link></section>
        <section class="panel"><span class="eyebrow">MESSAGE WALL</span><h2 style="font-size:19px">空间留言</h2><div class="message-list"><div v-for="item in messages.slice(0,5)" :key="item.id" class="message-note"><b>{{ item.nickname }} · {{ dayjs(item.created_at).format('MM.DD') }}</b><p>{{ item.content }}</p></div></div><div v-if="space.status==='ACTIVE'" style="display:flex;gap:7px;margin-top:13px"><input v-model="note" style="min-width:0;flex:1;padding:10px;border:1px solid var(--line);border-radius:12px" placeholder="留一句话…" @keyup.enter="leave" /><button class="icon-button" @click="leave"><Send :size="16" /></button></div></section>
        <button v-if="space.relationship_id && space.status==='ACTIVE'" class="button ghost" @click="archive"><Archive :size="15" /> 封存这段关系</button><p v-if="feedback" class="form-error">{{ feedback }}</p>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.timeline-cover { width:100%; height:230px; min-height:230px; margin:-20px -20px 18px; width:calc(100% + 40px); border-radius:21px 21px 0 0; }
.timeline-cover :deep(img), .timeline-cover :deep(video) { width:100%; height:100%; max-height:none; object-fit:cover; }
</style>
