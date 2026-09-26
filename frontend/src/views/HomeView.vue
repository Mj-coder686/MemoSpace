<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import {
  AlarmClock, ArrowRight, Bell, CalendarHeart, Gift, ListTodo, Plane, Plus, RefreshCw, UserPlus,
} from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import { useAuthStore } from '../stores/auth'
import EmptyState from '../components/EmptyState.vue'
import HomeMemoryFeature from '../components/home/HomeMemoryFeature.vue'
import HomeMemoryRow from '../components/home/HomeMemoryRow.vue'
import HomeSpaceCard from '../components/home/HomeSpaceCard.vue'
import { UiButton, UiSkeleton } from '../components/ui'

type Dashboard = {
  stats: { memories?: number; spaces?: number; places?: number }
  recent: any[]
  today: any[]
  feed: any[]
}

type Reminder = {
  id: number
  title: string
  note?: string
  reminder_kind: string
  remind_at: string
  next_trigger_at?: string
  status: string
  acceptance_status?: string
}

const auth = useAuthStore()
const dashboard = ref<Dashboard>({ stats: {}, recent: [], today: [], feed: [] })
const spaces = ref<any[]>([])
const reminders = ref<Reminder[]>([])
const dashboardLoading = ref(true)
const spacesLoading = ref(true)
const remindersLoading = ref(true)
const dashboardError = ref('')
const spacesError = ref('')
const remindersError = ref('')

const now = dayjs()
const weekdayNames = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
const localDate = `${now.format('M月D日')} · ${weekdayNames[now.day()]}`
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 11) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})
const upcomingReminders = computed(() => reminders.value.filter((item) =>
  item.status !== 'COMPLETED' && item.status !== 'CANCELLED' && item.acceptance_status !== 'REJECTED',
).slice(0, 3))
const newUser = computed(() => !dashboardLoading.value && !dashboardError.value && dashboard.value.recent.length === 0)
const hasSharedSpace = computed(() => spaces.value.some((space) => space.space_type !== 'PERSONAL'))

const reminderIcons: Record<string, any> = {
  TASK: ListTodo,
  BIRTHDAY: Gift,
  ANNIVERSARY: CalendarHeart,
  PLAN: Plane,
  CUSTOM: Bell,
}

const reminderTime = (item: Reminder) => {
  const value = dayjs(item.next_trigger_at || item.remind_at)
  if (!value.isValid()) return '时间待确认'
  if (value.isSame(dayjs(), 'day')) return `今天 ${value.format('HH:mm')}`
  if (value.isSame(dayjs().add(1, 'day'), 'day')) return `明天 ${value.format('HH:mm')}`
  return value.format('M月D日 HH:mm')
}

const loadDashboard = async () => {
  dashboardLoading.value = true
  dashboardError.value = ''
  try {
    dashboard.value = (await http.get('/home')).data
  } catch (error) {
    dashboardError.value = errorMessage(error)
  } finally {
    dashboardLoading.value = false
  }
}

const loadSpaces = async () => {
  spacesLoading.value = true
  spacesError.value = ''
  try {
    spaces.value = (await http.get('/spaces')).data
  } catch (error) {
    spacesError.value = errorMessage(error)
  } finally {
    spacesLoading.value = false
  }
}

const loadReminders = async () => {
  remindersLoading.value = true
  remindersError.value = ''
  try {
    reminders.value = (await http.get('/reminders')).data
  } catch (error) {
    remindersError.value = errorMessage(error)
  } finally {
    remindersLoading.value = false
  }
}

const openCreate = () => window.dispatchEvent(new CustomEvent('memospace-open-create'))

onMounted(() => {
  void Promise.allSettled([loadDashboard(), loadSpaces(), loadReminders()])
})
</script>

<template>
  <div class="home-page">
    <header class="home-welcome">
      <div>
        <span class="home-kicker">{{ localDate }}</span>
        <h1>{{ greeting }}，{{ auth.user?.nickname || '拾光的人' }}。</h1>
        <p v-if="dashboard.stats.memories">你已经为 {{ dashboard.stats.memories }} 个时刻留下了名字，今天也可以从很小的事开始。</p>
        <p v-else>记忆不需要宏大，只要它在某个瞬间真实地打动过你。</p>
      </div>
      <UiButton variant="primary" size="lg" @click="openCreate"><Plus :size="19" />记录此刻</UiButton>
    </header>

    <section v-if="newUser" class="home-onboarding" aria-labelledby="home-first-step-title">
      <div class="home-onboarding__index" aria-hidden="true">01</div>
      <div class="home-onboarding__copy">
        <span class="home-kicker">YOUR FIRST MEMORY</span>
        <h2 id="home-first-step-title">先留下一件今天不想忘记的事。</h2>
        <p>一张照片、一句话、一个地点都可以。完成第一条记忆后，我们再陪你邀请重要的人。</p>
        <UiButton variant="primary" size="lg" @click="openCreate"><Plus :size="18" />记录第一条记忆</UiButton>
      </div>
      <div class="home-onboarding__next"><span>下一步</span><strong>邀请重要的人</strong><small>完成第一条记忆后开启</small></div>
    </section>

    <template v-else>
      <div class="home-primary-grid">
        <section class="home-section home-recent" aria-labelledby="home-recent-title">
          <div class="home-section__heading">
            <div><span class="home-kicker">RECENT MEMORY</span><h2 id="home-recent-title">最近记忆</h2></div>
            <router-link to="/memories">进入时间轴 <ArrowRight :size="15" /></router-link>
          </div>

          <div v-if="dashboardLoading" class="home-memory-loading">
            <UiSkeleton height="260px" radius="var(--radius-lg)" />
            <UiSkeleton height="64px" /><UiSkeleton height="64px" />
          </div>
          <EmptyState
            v-else-if="dashboardError"
            kind="error"
            title="最近记忆暂时没有打开"
            :text="dashboardError"
            action-label="重新加载"
            @action="loadDashboard"
          />
          <div v-else-if="dashboard.recent.length" class="home-memory-stack">
            <HomeMemoryFeature :memory="dashboard.recent[0]" />
            <div v-if="dashboard.recent.length > 1" class="home-memory-rows">
              <HomeMemoryRow v-for="item in dashboard.recent.slice(1, 3)" :key="item.id" :memory="item" />
            </div>
          </div>
        </section>

        <aside class="home-section home-reminders" aria-labelledby="home-reminders-title">
          <div class="home-section__heading">
            <div><span class="home-kicker">NEXT UP</span><h2 id="home-reminders-title">接下来</h2></div>
            <router-link to="/reminders">全部提醒</router-link>
          </div>

          <div v-if="remindersLoading" class="home-reminder-loading">
            <UiSkeleton v-for="index in 3" :key="index" height="76px" />
          </div>
          <EmptyState
            v-else-if="remindersError"
            kind="error"
            title="提醒暂时不可用"
            :text="remindersError"
            action-label="重试"
            @action="loadReminders"
          />
          <div v-else-if="upcomingReminders.length" class="home-reminder-list">
            <router-link v-for="item in upcomingReminders" :key="item.id" to="/reminders" class="home-reminder-item">
              <span class="home-reminder-item__icon"><component :is="reminderIcons[item.reminder_kind] || AlarmClock" :size="19" /></span>
              <span><strong>{{ item.title }}</strong><time :datetime="item.next_trigger_at || item.remind_at">{{ reminderTime(item) }}</time></span>
            </router-link>
          </div>
          <div v-else class="home-reminder-empty">
            <AlarmClock :size="24" aria-hidden="true" />
            <strong>接下来的时间很从容</strong>
            <p>生日、纪念日和想做的事，都可以在这里提前记住。</p>
            <router-link to="/reminders">添加提醒</router-link>
          </div>
        </aside>
      </div>

      <section class="home-section home-spaces" aria-labelledby="home-spaces-title">
        <div class="home-section__heading">
          <div><span class="home-kicker">RECENT SPACES</span><h2 id="home-spaces-title">最近空间</h2></div>
          <router-link to="/spaces">查看全部 <ArrowRight :size="15" /></router-link>
        </div>

        <div v-if="spacesLoading" class="home-space-grid"><UiSkeleton v-for="index in 3" :key="index" height="132px" radius="var(--radius-lg)" /></div>
        <EmptyState
          v-else-if="spacesError"
          kind="error"
          title="空间暂时没有打开"
          :text="spacesError"
          action-label="重新加载"
          @action="loadSpaces"
        />
        <div v-else>
          <router-link v-if="!hasSharedSpace" to="/friends" class="home-invite-step">
            <span class="home-invite-step__icon"><UserPlus :size="20" /></span>
            <span><small>第二步</small><strong>邀请一位重要的人，共同建立空间</strong></span>
            <ArrowRight :size="18" aria-hidden="true" />
          </router-link>
          <div v-if="spaces.length" class="home-space-grid">
            <HomeSpaceCard v-for="space in spaces.slice(0, 3)" :key="space.id" :space="space" />
          </div>
        </div>
      </section>

      <section v-if="dashboard.today?.length" class="home-section home-anniversary" aria-labelledby="home-today-title">
        <div class="home-section__heading">
          <div><span class="home-kicker">ON THIS DAY</span><h2 id="home-today-title">往年今日</h2></div>
          <router-link to="/calendar">翻开日历 <ArrowRight :size="15" /></router-link>
        </div>
        <div class="home-summary-grid"><HomeMemoryRow v-for="item in dashboard.today.slice(0, 3)" :key="item.id" :memory="item" /></div>
      </section>

      <section v-if="dashboard.feed?.length" class="home-section home-feed" aria-labelledby="home-feed-title">
        <div class="home-section__heading">
          <div><span class="home-kicker">PEOPLE YOU CARE ABOUT</span><h2 id="home-feed-title">重要的人有新故事</h2></div>
          <router-link to="/explore">看看动态 <ArrowRight :size="15" /></router-link>
        </div>
        <div class="home-summary-grid"><HomeMemoryRow v-for="item in dashboard.feed.slice(0, 3)" :key="item.id" :memory="item" /></div>
      </section>
    </template>

    <section v-if="dashboardError && !dashboardLoading" class="home-dashboard-recovery" aria-live="polite">
      <RefreshCw :size="18" />
      <span>首页的记忆摘要加载失败，但空间和提醒仍可继续使用。</span>
      <UiButton variant="link" @click="loadDashboard">重试摘要</UiButton>
    </section>
  </div>
</template>
