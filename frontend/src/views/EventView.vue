<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import dayjs from 'dayjs'
import http from '../api/http'
import MemoryCard from '../components/MemoryCard.vue'
import EmptyState from '../components/EmptyState.vue'
const event=ref<any>({});onMounted(async()=>{event.value=(await http.get(`/events/${useRoute().params.id}`)).data})
</script>
<template><section class="hero" style="display:block;min-height:270px"><span class="eyebrow">SHARED EVENT</span><h1>{{event.name}}</h1><p>{{dayjs(event.start_at).format('YYYY.MM.DD')}} <template v-if="event.end_at">— {{dayjs(event.end_at).format('YYYY.MM.DD')}}</template> · {{event.location||'共同空间'}}</p><p>{{event.description}}</p></section><div class="section-heading"><div><span class="eyebrow">STORIES INSIDE</span><h2>事件里的记忆</h2></div></div><div v-if="event.memories?.length" class="memory-grid"><MemoryCard v-for="item in event.memories" :key="item.id" :memory="item" /></div><EmptyState v-else title="事件故事还没有开始" text="把属于这段行程或纪念日的 Memory 挂到事件中。" /></template>
