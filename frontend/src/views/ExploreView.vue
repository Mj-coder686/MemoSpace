<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Search, UserPlus, UsersRound, X } from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import MemoryCard from '../components/MemoryCard.vue'
import EmptyState from '../components/EmptyState.vue'
import UserAvatar from '../components/UserAvatar.vue'
import { UiBanner, UiButton, UiIconButton, UiSkeleton } from '../components/ui'

type FeedScope = 'latest' | 'following'

const router = useRouter()
const scope = ref<FeedScope>('latest')
const feed = ref<any[]>([])
const people = ref<any[]>([])
const query = ref('')
const searchedQuery = ref('')
const feedLoading = ref(true)
const searchLoading = ref(false)
const feedError = ref('')
const searchError = ref('')
const message = ref('')
const followBusy = ref<number | null>(null)

const loadFeed = async (nextScope: FeedScope = scope.value) => {
  scope.value = nextScope
  feedLoading.value = true
  feedError.value = ''
  try {
    feed.value = (await http.get('/feed', { params: { scope: nextScope } })).data
  } catch (error) {
    feedError.value = errorMessage(error)
  } finally {
    feedLoading.value = false
  }
}

const search = async () => {
  const value = query.value.trim()
  if (!value) {
    people.value = []
    searchedQuery.value = ''
    return
  }
  searchLoading.value = true
  searchError.value = ''
  searchedQuery.value = value
  try {
    people.value = (await http.get('/users/search', { params: { q: value } })).data
  } catch (error) {
    searchError.value = errorMessage(error)
  } finally {
    searchLoading.value = false
  }
}

const clearSearch = () => {
  query.value = ''
  searchedQuery.value = ''
  people.value = []
  searchError.value = ''
}

const focusSearch = () => document.getElementById('explore-person-search')?.focus()

const follow = async (person: any) => {
  followBusy.value = Number(person.id)
  message.value = ''
  try {
    const { data } = await http.post(`/users/${person.id}/follow`)
    person.following = data.following
    message.value = data.following ? `已关注 ${person.nickname}。` : `已取消关注 ${person.nickname}。`
  } catch (error) {
    searchError.value = errorMessage(error)
  } finally {
    followBusy.value = null
  }
}

const invite = (person: any) => router.push({ path: '/relationships', query: { inviteUser: String(person.id), inviteName: person.nickname } })

onMounted(() => loadFeed())
</script>

<template>
  <main class="explore-page">
    <header class="relationship-domain-header explore-heading">
      <div><span class="memory-kicker">PUBLIC MOMENTS</span><h1>动态</h1><p>看看人们愿意公开的生活切片；私人记忆和关系空间仍留在原来的边界里。</p></div>
      <form class="explore-search" role="search" @submit.prevent="search">
        <label for="explore-person-search">寻找一个人</label>
        <div><Search :size="18" /><input id="explore-person-search" v-model="query" type="search" placeholder="输入昵称、用户名或 Memo ID" /><UiIconButton v-if="query" label="清除搜索" size="sm" variant="ghost" @click="clearSearch"><X :size="16" /></UiIconButton><UiButton type="submit" variant="primary" size="sm" :loading="searchLoading" loading-text="寻找中">搜索</UiButton></div>
      </form>
    </header>

    <UiBanner v-if="message" tone="success" title="关注状态已更新" :description="message" />

    <section v-if="searchedQuery || searchLoading || searchError" class="explore-people" aria-labelledby="people-results-title">
      <div class="relationship-section-heading"><div><span class="memory-kicker">PEOPLE</span><h2 id="people-results-title">寻找「{{ searchedQuery }}」</h2><p>关注只影响公共动态；建立关系需要对方另行接受。</p></div><UiButton variant="ghost" size="sm" @click="clearSearch">收起结果</UiButton></div>
      <UiBanner v-if="searchError" tone="danger" title="没有完成搜索" :description="searchError"><template #actions><UiButton variant="ghost" size="sm" @click="search">重试</UiButton></template></UiBanner>
      <div v-else-if="searchLoading" class="explore-people-loading"><UiSkeleton v-for="index in 3" :key="index" height="88px" radius="var(--radius-md)" /></div>
      <div v-else-if="people.length" class="explore-people-list">
        <article v-for="person in people" :key="person.id">
          <button class="explore-person-identity" @click="router.push(`/user/${person.id}`)"><UserAvatar :src="person.avatar" :name="person.nickname" /><span><strong>{{ person.nickname }}</strong><small>@{{ person.username }}<template v-if="person.location"> · {{ person.location }}</template></small><em v-if="person.bio">{{ person.bio }}</em></span><ArrowRight :size="17" /></button>
          <div class="explore-person-actions"><UiButton variant="ghost" size="sm" :loading="followBusy===Number(person.id)" @click="follow(person)">{{ person.following ? '取消关注' : '关注' }}</UiButton><UiButton variant="tonal" size="sm" @click="invite(person)"><UserPlus :size="15" />选择关系分类</UiButton></div>
        </article>
      </div>
      <EmptyState v-else kind="search" title="没有找到这个人" text="试试完整的 Memo ID、昵称或用户名；搜索不会展示被拉黑或不可用的账号。" />
    </section>

    <section class="explore-feed" aria-labelledby="feed-title">
      <div class="explore-feed-heading">
        <div><span class="memory-kicker">SHARED WITH EVERYONE</span><h2 id="feed-title">公开片段</h2></div>
        <div class="explore-feed-tabs" role="tablist" aria-label="动态范围"><button role="tab" :aria-selected="scope==='latest'" :class="{active:scope==='latest'}" @click="loadFeed('latest')">最新</button><button role="tab" :aria-selected="scope==='following'" :class="{active:scope==='following'}" @click="loadFeed('following')">我关注的</button></div>
      </div>
      <UiBanner v-if="feedError" tone="danger" title="动态暂时没有载入" :description="feedError"><template #actions><UiButton variant="ghost" size="sm" @click="loadFeed()">重新载入</UiButton></template></UiBanner>
      <div v-else-if="feedLoading" class="explore-feed-loading"><UiSkeleton v-for="height in ['360px','440px','320px','390px']" :key="height" :height="height" radius="var(--radius-lg)" /></div>
      <div v-else-if="feed.length" class="explore-feed-grid"><MemoryCard v-for="item in feed" :key="item.id" :memory="item" /></div>
      <EmptyState v-else :kind="scope==='following' ? 'search' : 'empty'" :title="scope==='following' ? '关注的人还没有公开新记忆' : '公共动态还很安静'" :text="scope==='following' ? '可以先搜索一个你认识的人；关注不会自动建立好友或关系。' : '公开发布的 Memory 会依照时间出现在这里。'"><template v-if="scope==='following'" #actions><UiButton variant="primary" @click="focusSearch"><UsersRound :size="16" />寻找一个人</UiButton></template></EmptyState>
    </section>
  </main>
</template>
