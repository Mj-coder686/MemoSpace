<script setup lang="ts">
import { onMounted, ref } from 'vue'
import http from '../api/http'
import MemoryCard from '../components/MemoryCard.vue'
import EmptyState from '../components/EmptyState.vue'
const photos=ref<any[]>([])
onMounted(async()=>{const data=(await http.get('/memories')).data;photos.value=data.filter((x:any)=>['PHOTO','VIDEO','MIXED'].includes(x.memory_type))})
</script>
<template><header class="page-heading"><div><span class="eyebrow">VISUAL ARCHIVE</span><h1>记忆相册</h1><p>按时间铺开的光影、地点和人与人之间的故事。</p></div></header><div v-if="photos.length" class="memory-grid"><MemoryCard v-for="item in photos" :key="item.id" :memory="item" /></div><EmptyState v-else title="相册还是空的" text="上传第一张照片，让这里慢慢长成你的生活。" /></template>
