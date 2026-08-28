<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { MapPin } from 'lucide-vue-next'
import http from '../api/http'
import EmptyState from '../components/EmptyState.vue'
const places=ref<any[]>([]);onMounted(async()=>{places.value=(await http.get('/map')).data})
const plotted=computed(()=>places.value.map((p,i)=>({...p,x:15+((Math.abs(p.longitude)*13+i*17)%70),y:18+((Math.abs(p.latitude)*19+i*23)%65)})))
</script>
<template><header class="page-heading"><div><span class="eyebrow">MEMORY ATLAS</span><h1>我的足迹</h1><p>那些故事发生过的地方，慢慢连成一张生活地图。</p></div></header><div v-if="places.length" class="map-canvas"><router-link v-for="place in plotted" :key="place.id" :to="`/memory/${place.id}`" class="map-pin" :style="{left:place.x+'%',top:place.y+'%'}" :title="place.title"><MapPin :size="15" /></router-link><div class="map-legend"><span class="eyebrow">PLACES REMEMBERED</span><b>{{places.length}} 个被记住的地点</b></div></div><EmptyState v-else title="地图上还没有足迹" text="创建带有经纬度的地点记忆，它会出现在这里。" /></template>
