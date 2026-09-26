<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Plus, Search, X } from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import MemoryCard from '../components/MemoryCard.vue'
import MemoryDeleteDialog from '../components/MemoryDeleteDialog.vue'
import EmptyState from '../components/EmptyState.vue'
import { UiBanner, UiButton, UiChip, UiSkeleton } from '../components/ui'

const route = useRoute()
const router = useRouter()
const memories = ref<any[]>([])
const query = ref(String(route.query.q || ''))
const activeQuery = ref(String(route.query.q || ''))
const type = ref('ALL')
const loading = ref(true)
const pageError = ref('')
const statusMessage = ref('')
const deleteTarget = ref<any | null>(null)

const typeOptions = [
  { value: 'ALL', label: '全部' },
  { value: 'TEXT', label: '文字' },
  { value: 'PHOTO', label: '照片' },
  { value: 'VIDEO', label: '视频' },
  { value: 'LOCATION', label: '地点' },
  { value: 'MIXED', label: '图文' },
]

const filtered = computed(() => type.value === 'ALL' ? memories.value : memories.value.filter((item) => item.memory_type === type.value))
const isInitialEmpty = computed(() => !activeQuery.value && type.value === 'ALL' && memories.value.length === 0)
const resultTitle = computed(() => activeQuery.value ? `“${activeQuery.value}”的搜索结果` : '全部记忆')

const load = async () => {
  loading.value = true
  pageError.value = ''
  try {
    memories.value = (await http.get('/memories', { params: { q: activeQuery.value } })).data
  } catch (error) {
    pageError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

const search = async () => {
  const next = query.value.trim()
  if (next === activeQuery.value) return load()
  await router.push({ path: '/memories', query: next ? { q: next } : {} })
}

const clearSearch = async () => {
  query.value = ''
  if (!activeQuery.value) return
  await router.push('/memories')
}

const openCreate = () => window.dispatchEvent(new CustomEvent('memospace-open-create'))
const deleted = (memory: any) => {
  memories.value = memories.value.filter((item) => Number(item.id) !== Number(memory.id))
  deleteTarget.value = null
  statusMessage.value = `“${memory.title}”已删除。`
}

watch(() => route.query.q, (value) => {
  query.value = String(value || '')
  activeQuery.value = query.value
  void load()
})
onMounted(load)
</script>

<template>
  <div class="memory-archive-page">
    <header class="memory-archive-header">
      <div>
        <span class="memory-kicker">MY MEMORY ARCHIVE</span>
        <h1>我的记忆库</h1>
        <p>每一个被认真记录的瞬间，都在这里拥有自己的位置。</p>
      </div>
      <UiButton variant="primary" size="lg" @click="openCreate"><Plus :size="18" />记录此刻</UiButton>
    </header>

    <UiBanner v-if="statusMessage" tone="success" title="记忆已删除" :description="statusMessage" />

    <section class="memory-archive-tools" aria-label="搜索与筛选">
      <form class="memory-search" role="search" @submit.prevent="search">
        <Search :size="18" aria-hidden="true" />
        <input v-model="query" type="search" aria-label="搜索记忆" placeholder="搜索标题、内容或地点" autocomplete="off" />
        <button v-if="query" type="button" aria-label="清除搜索" @click="clearSearch"><X :size="17" /></button>
        <UiButton type="submit" size="sm" variant="tonal">搜索</UiButton>
      </form>
      <div class="memory-type-filters" aria-label="记忆类型">
        <UiChip v-for="item in typeOptions" :key="item.value" interactive :selected="type === item.value" @click="type = item.value">{{ item.label }}</UiChip>
      </div>
    </section>

    <div class="memory-result-heading" aria-live="polite">
      <div><h2>{{ resultTitle }}</h2><span v-if="!loading && !pageError">{{ filtered.length }} 条</span></div>
      <button v-if="activeQuery" type="button" @click="clearSearch">清除搜索</button>
    </div>

    <div v-if="loading" class="memory-archive-loading" aria-label="正在读取记忆">
      <UiSkeleton v-for="(height, index) in ['340px','430px','300px','390px','320px','410px']" :key="index" :height="height" radius="var(--radius-lg)" />
    </div>

    <EmptyState
      v-else-if="pageError"
      kind="error"
      title="记忆库暂时没有打开"
      :text="pageError"
      action-label="重新加载"
      @action="load"
    />

    <div v-else-if="filtered.length" class="memory-archive-grid">
      <MemoryCard v-for="item in filtered" :key="item.id" :memory="item" can-delete @request-delete="deleteTarget = $event" />
    </div>

    <EmptyState
      v-else-if="isInitialEmpty"
      title="从第一条记忆开始"
      text="一张照片、一句话、一个地点，都可以成为生活档案的第一页。"
      action-label="记录第一条记忆"
      @action="openCreate"
    />

    <EmptyState
      v-else-if="activeQuery"
      kind="search"
      title="没有找到这段记忆"
      :text="`没有与“${activeQuery}”匹配的标题、内容或地点。`"
      action-label="清除搜索"
      @action="clearSearch"
    />

    <EmptyState
      v-else
      kind="search"
      title="这个类型还没有记忆"
      text="切换到其他类型，或记录一条新的故事。"
      action-label="查看全部"
      @action="type = 'ALL'"
    />

    <MemoryDeleteDialog :memory="deleteTarget" @close="deleteTarget = null" @deleted="deleted" />
  </div>
</template>
