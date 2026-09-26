<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Copy, Images, LibraryBig, MapPin, Settings2, UserPlus, UsersRound } from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import { useAuthStore } from '../stores/auth'
import EmptyState from '../components/EmptyState.vue'
import MemoryCard from '../components/MemoryCard.vue'
import UserAvatar from '../components/UserAvatar.vue'
import { UiBanner, UiButton, UiSkeleton } from '../components/ui'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)
const profile = ref<any>(null)
const memories = ref<any[]>([])
const loading = ref(true)
const pageError = ref('')
const message = ref('')
const followBusy = ref(false)

const isSelf = computed(() => Number(id) === Number(auth.user?.id))
const memoId = computed(() => profile.value?.public_id || profile.value?.publicId || '')
const followerCount = computed(() => Number(profile.value?.followers || 0))
const followingCount = computed(() => Number(profile.value?.following || 0))
const publicMemoryCount = computed(() => Number(profile.value?.public_memories || memories.value.filter(item => item.visibility === 'PUBLIC').length || 0))

const load = async () => {
  loading.value = true
  pageError.value = ''
  try {
    if (!auth.user) await auth.loadMe()
    if (Number(id) === Number(auth.user?.id)) {
      profile.value = auth.user
      memories.value = (await http.get('/memories')).data.slice(0, 6)
    } else {
      const [profileResponse, feedResponse] = await Promise.all([
        http.get(`/users/${id}`),
        http.get('/feed', { params: { scope: 'latest' } }),
      ])
      profile.value = profileResponse.data
      memories.value = feedResponse.data.filter((item: any) => Number(item.creator_id) === id).slice(0, 6)
    }
  } catch (error) {
    pageError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

const follow = async () => {
  if (!profile.value) return
  followBusy.value = true
  pageError.value = ''
  try {
    const wasFollowing = Boolean(profile.value.is_following)
    const { data } = await http.post(`/users/${id}/follow`)
    profile.value.is_following = data.following
    profile.value.followers = Math.max(0, followerCount.value + (data.following && !wasFollowing ? 1 : !data.following && wasFollowing ? -1 : 0))
    message.value = data.following ? `已关注 ${profile.value.nickname}。` : `已取消关注 ${profile.value.nickname}。`
  } catch (error) {
    pageError.value = errorMessage(error)
  } finally {
    followBusy.value = false
  }
}

const copyMemoId = async () => {
  if (!memoId.value) return
  try {
    await navigator.clipboard.writeText(memoId.value)
    message.value = 'Memo ID 已复制。'
  } catch {
    pageError.value = '无法复制 Memo ID，请手动选择并复制。'
  }
}

const invite = () => router.push({ path: '/relationships', query: { inviteUser: String(id), inviteName: profile.value?.nickname || '' } })

onMounted(load)
</script>

<template>
  <main class="user-profile-page">
    <div v-if="loading" class="user-profile-loading"><UiSkeleton height="260px" radius="var(--radius-lg)" /><UiSkeleton height="48px" /><div><UiSkeleton v-for="index in 3" :key="index" height="320px" radius="var(--radius-lg)" /></div></div>
    <EmptyState v-else-if="pageError && !profile" kind="error" title="没有打开这个主页" :text="pageError"><template #actions><UiButton variant="primary" @click="load">重新载入</UiButton><UiButton variant="ghost" @click="router.push('/explore')">返回动态</UiButton></template></EmptyState>
    <template v-else-if="profile">
      <section class="user-profile-hero">
        <div class="user-profile-portrait"><UserAvatar :src="profile.avatar" :name="profile.nickname" /></div>
        <div class="user-profile-identity"><span class="memory-kicker">{{ isSelf ? 'MY MEMOSPACE' : 'PUBLIC PORTRAIT' }}</span><h1>{{ profile.nickname }}</h1><p class="user-profile-handle">@{{ profile.username }}</p><p class="user-profile-bio">{{ profile.bio || (isSelf ? '可以在资料设置中写下一句自我介绍。' : '这个人还没有写下自我介绍。') }}</p><p v-if="profile.location" class="user-profile-location"><MapPin :size="15" />{{ profile.location }}</p><button v-if="memoId" class="user-profile-memo-id" @click="copyMemoId"><span>Memo ID</span><strong>{{ memoId }}</strong><Copy :size="14" /></button></div>
        <div class="user-profile-actions" v-if="isSelf"><UiButton variant="primary" size="lg" @click="router.push('/settings')"><Settings2 :size="17" />编辑资料</UiButton></div>
        <div class="user-profile-actions" v-else><UiButton variant="primary" size="lg" :loading="followBusy" :loading-text="profile.is_following ? '取消中' : '关注中'" @click="follow">{{ profile.is_following ? '取消关注' : '关注 TA' }}</UiButton><UiButton variant="secondary" size="lg" @click="invite"><UserPlus :size="17" />建立关系</UiButton></div>
        <dl class="user-profile-stats"><div><dt>{{ publicMemoryCount }}</dt><dd>公开记忆</dd></div><div><dt>{{ followerCount }}</dt><dd>关注者</dd></div><div><dt>{{ followingCount }}</dt><dd>正在关注</dd></div></dl>
      </section>

      <UiBanner v-if="message" tone="success" title="操作已完成" :description="message" />
      <UiBanner v-if="pageError" tone="danger" title="操作没有完成" :description="pageError" />

      <nav v-if="isSelf" class="user-profile-shortcuts" aria-label="我的空间入口">
        <button @click="router.push('/memories')"><LibraryBig :size="20" /><span><strong>记忆库</strong><small>翻阅全部私人档案</small></span></button>
        <button @click="router.push('/photos')"><Images :size="20" /><span><strong>相册</strong><small>按时间查看影像</small></span></button>
        <button @click="router.push('/spaces')"><UsersRound :size="20" /><span><strong>空间</strong><small>回到个人与共同空间</small></span></button>
      </nav>

      <section class="user-profile-memories" aria-labelledby="profile-memories-title">
        <div class="relationship-section-heading"><div><span class="memory-kicker">{{ isSelf ? 'RECENT ARCHIVE' : 'SHARED PUBLICLY' }}</span><h2 id="profile-memories-title">{{ isSelf ? '最近的记忆' : 'TA 愿意公开的生活' }}</h2><p v-if="!isSelf">这里只呈现后端允许公开访问的内容，不暗示任何私人记忆数量。</p></div><UiButton v-if="isSelf" variant="link" @click="router.push('/memories')">查看全部记忆</UiButton></div>
        <div v-if="memories.length" class="user-profile-memory-grid"><MemoryCard v-for="item in memories" :key="item.id" :memory="item" /></div>
        <EmptyState v-else :title="isSelf ? '还没有留下第一条记忆' : 'TA 暂时没有公开记忆'" :text="isSelf ? '从今天的一件小事开始，建立属于自己的生活档案。' : '私人内容不会以空白卡片或数量提示的方式暴露。'"><template v-if="isSelf" #actions><UiButton variant="primary" @click="router.push('/memories')">前往记忆库</UiButton></template></EmptyState>
      </section>
    </template>
  </main>
</template>
