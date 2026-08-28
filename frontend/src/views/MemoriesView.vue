<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { Search } from 'lucide-vue-next'
import { useRoute } from 'vue-router'
import http from '../api/http'
import MemoryCard from '../components/MemoryCard.vue'
import EmptyState from '../components/EmptyState.vue'

const route = useRoute()
const memories = ref<any[]>([])
const query = ref(String(route.query.q || ''))
const type = ref('ALL')
const load = async () => { memories.value = (await http.get('/memories', { params: { q: query.value } })).data }
onMounted(load)
watch(() => route.query.q, (q) => { query.value = String(q || ''); load() })
const filtered = () => type.value === 'ALL' ? memories.value : memories.value.filter(item => item.memory_type === type.value)
</script>

<template>
  <header class="page-heading"><div><span class="eyebrow">MY MEMORY ARCHIVE</span><h1>我的记忆库</h1><p>每一个被认真记录的瞬间，都在这里拥有自己的位置。</p></div><form class="search-wide" @submit.prevent="load"><Search :size="18" /><input v-model="query" placeholder="搜索标题、内容或地点" /></form></header>
  <div class="filters"><button v-for="item in [{v:'ALL',l:'全部'},{v:'TEXT',l:'文字'},{v:'PHOTO',l:'照片'},{v:'VIDEO',l:'视频'},{v:'LOCATION',l:'地点'},{v:'MIXED',l:'图文'}]" :key="item.v" :class="{active:type===item.v}" @click="type=item.v">{{ item.l }}</button></div>
  <div v-if="filtered().length" class="memory-grid"><MemoryCard v-for="item in filtered()" :key="item.id" :memory="item" /></div>
  <EmptyState v-else title="没有找到这段记忆" text="换一个关键词，或从今天开始写下新的故事。" />
</template>
