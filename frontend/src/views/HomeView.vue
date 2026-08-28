<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ArrowRight, Clock3 } from 'lucide-vue-next'
import dayjs from 'dayjs'
import http from '../api/http'
import { useAuthStore } from '../stores/auth'
import MemoryCard from '../components/MemoryCard.vue'
import EmptyState from '../components/EmptyState.vue'

const auth = useAuthStore()
const dashboard = ref<any>({ stats: {}, recent: [], today: [], feed: [] })
const spaces = ref<any[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    const [homeResponse, spaceResponse] = await Promise.all([http.get('/home'), http.get('/spaces')])
    dashboard.value = homeResponse.data
    spaces.value = spaceResponse.data
  } finally { loading.value = false }
})
</script>

<template>
  <div v-if="!loading">
    <section class="hero">
      <div class="hero-copy"><span class="eyebrow">{{ dayjs().format('YYYY · MM · DD') }}</span><h1>早安，{{ auth.user?.nickname }}。<br />今天也有<em>值得记住</em>的事。</h1><p>记忆不需要宏大，只要它在某个瞬间真实地打动过你。</p></div>
      <div class="hero-stats">
        <div class="hero-stat"><b>{{ dashboard.stats.memories || 0 }}</b><span>珍藏记忆</span></div>
        <div class="hero-stat"><b>{{ dashboard.stats.spaces || 0 }}</b><span>记忆空间</span></div>
        <div class="hero-stat"><b>{{ dashboard.stats.places || 0 }}</b><span>留下足迹</span></div>
      </div>
    </section>

    <section v-if="dashboard.today?.length">
      <div class="section-heading"><div><span class="eyebrow">ON THIS DAY</span><h2>往年今日</h2></div><router-link to="/calendar">翻开日历 <ArrowRight :size="14" style="vertical-align:middle" /></router-link></div>
      <div class="memory-grid"><MemoryCard v-for="item in dashboard.today" :key="item.id" :memory="item" /></div>
    </section>

    <section>
      <div class="section-heading"><div><span class="eyebrow">YOUR SPACES</span><h2>最近的空间</h2></div><router-link to="/spaces">查看全部</router-link></div>
      <div v-if="spaces.length" class="space-grid">
        <router-link v-for="space in spaces.slice(0,2)" :key="space.id" :to="`/space/${space.id}`" class="space-card"
          :style="{'--space-primary':space.primary_color,'--space-background':space.background_color,'--space-text':space.text_color,'--space-muted':space.muted_color}">
          <div><span class="eyebrow">{{ space.space_type === 'PERSONAL' ? 'PRIVATE ARCHIVE' : 'SHARED STORY' }}</span><h3>{{ space.name }}</h3><p>{{ space.status === 'ARCHIVED' ? '这段空间已经温柔封存' : '继续写下属于这里的故事' }}</p></div>
          <div class="space-numbers"><div><b>{{ space.memoryCount }}</b><small>共同记忆</small></div><div><b>{{ space.photoCount }}</b><small>照片故事</small></div><div><b>{{ space.placeCount }}</b><small>去过地点</small></div></div>
        </router-link>
      </div>
    </section>

    <section>
      <div class="section-heading"><div><span class="eyebrow">RECENTLY</span><h2>刚刚留下的记忆</h2></div><router-link to="/memories">进入时间轴</router-link></div>
      <div v-if="dashboard.recent?.length" class="memory-grid"><MemoryCard v-for="item in dashboard.recent" :key="item.id" :memory="item" /></div>
      <EmptyState v-else />
    </section>

    <section v-if="dashboard.feed?.length">
      <div class="section-heading"><div><span class="eyebrow">FROM PEOPLE YOU CARE</span><h2>好友的新故事</h2></div><router-link to="/explore">去公共空间</router-link></div>
      <div class="memory-grid"><MemoryCard v-for="item in dashboard.feed.slice(0,3)" :key="item.id" :memory="item" compact /></div>
    </section>
  </div>
  <div v-else class="panel"><Clock3 :size="20" /> 正在整理今天的记忆…</div>
</template>
