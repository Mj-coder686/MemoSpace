<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import dayjs, { type Dayjs } from 'dayjs'
import { ChevronLeft, ChevronRight, Clock3, Image as ImageIcon, MapPin, X } from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import EmptyState from '../components/EmptyState.vue'
import PrivateMedia from '../components/PrivateMedia.vue'
import { UiButton, UiIconButton, UiSkeleton } from '../components/ui'

const route = useRoute()
const router = useRouter()
const requestedDate = dayjs(String(route.query.date || ''), 'YYYY-MM-DD', true)
const cursor = ref((requestedDate.isValid() ? requestedDate : dayjs()).startOf('month'))
const entries = ref<any[]>([])
const selectedDate = ref(requestedDate.isValid() ? requestedDate.format('YYYY-MM-DD') : '')
const dayMemories = ref<any[]>([])
const monthLoading = ref(true)
const dayLoading = ref(false)
const monthError = ref('')
const dayError = ref('')
let monthRequest = 0

const entryMap = computed(() => new Map(entries.value.map((item) => [dayjs(item.memory_date).format('YYYY-MM-DD'), item])))
const cells = computed(() => {
  const monthStart = cursor.value.startOf('month')
  const mondayOffset = (monthStart.day() + 6) % 7
  const gridStart = monthStart.subtract(mondayOffset, 'day')
  return Array.from({ length: 42 }, (_, index) => {
    const date = gridStart.add(index, 'day')
    const key = date.format('YYYY-MM-DD')
    return { date, key, inMonth: date.isSame(cursor.value, 'month'), today: date.isSame(dayjs(), 'day'), entry: entryMap.value.get(key) }
  })
})

const loadMonth = async () => {
  const request = ++monthRequest
  monthLoading.value = true
  monthError.value = ''
  try {
    const { data } = await http.get('/calendar', { params: { year: cursor.value.year(), month: cursor.value.month() + 1 } })
    if (request === monthRequest) entries.value = data
  } catch (error) {
    if (request === monthRequest) monthError.value = errorMessage(error)
  } finally {
    if (request === monthRequest) monthLoading.value = false
  }
}

const loadDay = async (date: string) => {
  dayLoading.value = true
  dayError.value = ''
  dayMemories.value = []
  try {
    dayMemories.value = (await http.get('/calendar/day', { params: { date } })).data
  } catch (error) {
    dayError.value = errorMessage(error)
  } finally {
    dayLoading.value = false
  }
}

const openDay = async (date: Dayjs) => {
  if (!date.isSame(cursor.value, 'month')) cursor.value = date.startOf('month')
  selectedDate.value = date.format('YYYY-MM-DD')
  await router.replace({ query: { ...route.query, date: selectedDate.value } })
  await loadDay(selectedDate.value)
}

const closeDay = async () => {
  selectedDate.value = ''
  dayMemories.value = []
  dayError.value = ''
  const query = { ...route.query }
  delete query.date
  await router.replace({ query })
}

const changeMonth = (amount: number) => {
  cursor.value = cursor.value.add(amount, 'month').startOf('month')
  if (selectedDate.value && !dayjs(selectedDate.value).isSame(cursor.value, 'month')) void closeDay()
}

const goToday = () => { void openDay(dayjs()) }
const weekdayLabel = (value: string) => ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'][dayjs(value).day()]

watch(cursor, loadMonth)
onMounted(async () => {
  await loadMonth()
  if (selectedDate.value) await loadDay(selectedDate.value)
})
</script>

<template>
  <div class="calendar-page">
    <header class="companion-page-header">
      <div><span class="memory-kicker">BACK TO A DAY</span><h1>回到某一天</h1><p>日历不是任务表，而是重新走进一段生活的入口。</p></div>
      <UiButton variant="tonal" @click="goToday">今天</UiButton>
    </header>

    <div class="calendar-layout" :class="{ 'has-selection': selectedDate }">
      <section class="calendar-surface" aria-labelledby="calendar-month-title">
        <div class="calendar-toolbar">
          <UiIconButton label="上一个月" variant="ghost" @click="changeMonth(-1)"><ChevronLeft :size="20" /></UiIconButton>
          <div><span class="memory-kicker">{{ cursor.format('YYYY') }}</span><h2 id="calendar-month-title">{{ cursor.format('M 月') }}</h2></div>
          <UiIconButton label="下一个月" variant="ghost" @click="changeMonth(1)"><ChevronRight :size="20" /></UiIconButton>
        </div>

        <div class="calendar-weekdays" aria-hidden="true"><span v-for="weekday in ['一','二','三','四','五','六','日']" :key="weekday">周{{ weekday }}</span></div>
        <div v-if="monthLoading" class="calendar-grid calendar-grid--loading" aria-label="正在读取这个月"><UiSkeleton v-for="index in 42" :key="index" height="72px" radius="var(--radius-sm)" /></div>
        <EmptyState v-else-if="monthError" kind="error" title="这个月暂时无法打开" :text="monthError" action-label="重新加载" @action="loadMonth" />
        <div v-else class="calendar-grid">
          <button
            v-for="cell in cells"
            :key="cell.key"
            type="button"
            class="calendar-date"
            :class="{ 'is-outside': !cell.inMonth, 'is-today': cell.today, 'is-selected': selectedDate === cell.key, 'has-memory': cell.entry }"
            :aria-current="cell.today ? 'date' : undefined"
            :aria-pressed="selectedDate === cell.key"
            :aria-label="`${cell.date.format('YYYY年M月D日')}${cell.today ? '，今天' : ''}${cell.entry ? `，${cell.entry.count}条记忆` : '，没有记忆'}`"
            @click="openDay(cell.date)"
          >
            <span>{{ cell.date.date() }}</span>
            <span v-if="cell.entry" class="calendar-date__memory"><i aria-hidden="true" />{{ cell.entry.count }}<span class="sr-only">条记忆</span></span>
          </button>
        </div>
      </section>

      <aside v-if="selectedDate" class="calendar-day-drawer" aria-labelledby="selected-day-title">
        <header>
          <div><span class="memory-kicker">MEMORIES OF THE DAY</span><h2 id="selected-day-title">{{ dayjs(selectedDate).format('M月D日') }}</h2><p>{{ dayjs(selectedDate).format('YYYY年') }} · {{ weekdayLabel(selectedDate) }}</p></div>
          <UiIconButton label="关闭日期详情" variant="ghost" @click="closeDay"><X :size="18" /></UiIconButton>
        </header>

        <div v-if="dayLoading" class="calendar-day-loading" aria-label="正在翻开这一天"><UiSkeleton v-for="index in 3" :key="index" height="104px" radius="var(--radius-md)" /></div>
        <EmptyState v-else-if="dayError" kind="error" title="这一天暂时无法打开" :text="dayError" action-label="重试" @action="loadDay(selectedDate)" />
        <div v-else-if="dayMemories.length" class="calendar-day-list">
          <router-link v-for="item in dayMemories" :key="item.id" :to="`/memory/${item.id}`" :aria-label="`打开记忆：${item.title}`">
            <span v-if="item.cover_file_id" class="calendar-day-list__media"><PrivateMedia :file-id="item.cover_file_id" :mime-type="item.cover_mime_type" :alt="item.title" preview /></span>
            <span v-else class="calendar-day-list__symbol" aria-hidden="true"><ImageIcon :size="18" /></span>
            <span class="calendar-day-list__copy"><strong>{{ item.title }}</strong><span><Clock3 :size="13" />{{ dayjs(item.occurred_at).format('HH:mm') }}<template v-if="item.location"><MapPin :size="13" />{{ item.location }}</template></span><small>{{ item.content || '有些时刻，照片已经说完了一切。' }}</small></span>
          </router-link>
        </div>
        <EmptyState v-else title="这一天还没有记录" text="日历允许留白；如果想补写，使用“记录此刻”并调整发生时间。" />
      </aside>

      <aside v-else class="calendar-day-placeholder" aria-label="日期详情提示"><span>{{ cursor.format('MM') }}</span><p>选择一个日期<br />查看当天的记忆</p></aside>
    </div>
  </div>
</template>
