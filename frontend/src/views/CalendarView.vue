<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import dayjs from 'dayjs'
import http from '../api/http'
const cursor=ref(dayjs());const entries=ref<any[]>([])
const load=async()=>{entries.value=(await http.get('/calendar',{params:{year:cursor.value.year(),month:cursor.value.month()+1}})).data}
onMounted(load);watch(cursor,load)
const cells=computed(()=>{const start=cursor.value.startOf('month');const prefix=(start.day()+6)%7;const count=cursor.value.daysInMonth();const map=new Map(entries.value.map(x=>[dayjs(x.memory_date).date(),x]));return [...Array(prefix).fill(null),...Array.from({length:count},(_,i)=>({day:i+1,entry:map.get(i+1)}))]})
</script>
<template><header class="page-heading"><div><span class="eyebrow">BACK TO A DAY</span><h1>回到某一天</h1><p>有记忆的日期会亮起一个小小的标记。</p></div><div style="display:flex;gap:8px"><button class="button" @click="cursor=cursor.subtract(1,'month')">←</button><span class="chip">{{ cursor.format('YYYY 年 MM 月') }}</span><button class="button" @click="cursor=cursor.add(1,'month')">→</button></div></header><section class="calendar-card"><div class="calendar-head"><span v-for="w in ['一','二','三','四','五','六','日']" :key="w">周{{w}}</span></div><div class="calendar-grid"><div v-for="(cell,i) in cells" :key="i" class="calendar-day" :class="{empty:!cell,'has-memory':cell?.entry}"><template v-if="cell"><b>{{cell.day}}</b><i v-if="cell.entry" :title="cell.entry.preview"></i></template></div></div></section></template>
