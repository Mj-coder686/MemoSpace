<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { ArrowLeft, CalendarDays, MapPin } from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import MemoryCard from '../components/MemoryCard.vue'
import EmptyState from '../components/EmptyState.vue'
import { UiSkeleton } from '../components/ui'

const route = useRoute()
const router = useRouter()
const event = ref<any | null>(null)
const loading = ref(true)
const pageError = ref('')

const load = async () => {
  loading.value = true
  pageError.value = ''
  try { event.value = (await http.get(`/events/${route.params.id}`)).data }
  catch (error) { pageError.value = errorMessage(error) }
  finally { loading.value = false }
}

onMounted(load)
</script>

<template>
  <main class="event-detail-page">
    <button class="relationship-back-link" type="button" @click="router.back()"><ArrowLeft :size="16" />返回上一页</button>
    <div v-if="loading" class="event-detail-loading" aria-label="正在打开共同事件"><UiSkeleton height="290px" radius="var(--radius-lg)" /><UiSkeleton height="380px" radius="var(--radius-lg)" /></div>
    <EmptyState v-else-if="pageError || !event" kind="error" title="这个事件暂时无法打开" :text="pageError" action-label="重新加载" @action="load" />
    <template v-else>
      <header class="event-detail-hero">
        <span class="event-detail-mark"><CalendarDays :size="28" /></span>
        <div><span class="memory-kicker">SHARED EVENT</span><h1>{{ event.name }}</h1><p v-if="event.description">{{ event.description }}</p></div>
        <dl><div><dt>时间</dt><dd>{{ dayjs(event.start_at).format('YYYY 年 M 月 D 日') }}<template v-if="event.end_at"> — {{ dayjs(event.end_at).format('M 月 D 日') }}</template></dd></div><div><dt>地点</dt><dd><MapPin :size="15" />{{ event.location || '共同空间' }}</dd></div></dl>
      </header>

      <section class="event-memory-section" aria-labelledby="event-memory-title">
        <div class="relationship-section-heading"><div><span class="memory-kicker">STORIES INSIDE</span><h2 id="event-memory-title">事件里的记忆</h2><p>{{ event.memories?.length || 0 }} 段 Memory 共同组成了这次经历。</p></div></div>
        <div v-if="event.memories?.length" class="memory-grid"><MemoryCard v-for="item in event.memories" :key="item.id" :memory="item" /></div>
        <EmptyState v-else title="事件故事还没有开始" text="把属于这段行程或纪念日的 Memory 挂到事件中。" />
      </section>
    </template>
  </main>
</template>
