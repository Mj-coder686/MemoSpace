<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { X } from 'lucide-vue-next'
import http from '../api/http'
import EmptyState from '../components/EmptyState.vue'
import MemoryCard from '../components/MemoryCard.vue'

const route=useRoute();const router=useRouter()
const initial=dayjs(String(route.query.date||''));const cursor=ref(initial.isValid()?initial:dayjs())
const entries=ref<any[]>([]);const selectedDate=ref(String(route.query.date||''));const dayMemories=ref<any[]>([]);const loadingDay=ref(false)
const load=async()=>{entries.value=(await http.get('/calendar',{params:{year:cursor.value.year(),month:cursor.value.month()+1}})).data}
const openDay=async(day:number)=>{
  selectedDate.value=cursor.value.date(day).format('YYYY-MM-DD');loadingDay.value=true
  await router.replace({query:{...route.query,date:selectedDate.value}})
  try{dayMemories.value=(await http.get('/calendar/day',{params:{date:selectedDate.value}})).data}finally{loadingDay.value=false}
}
const closeDay=async()=>{selectedDate.value='';dayMemories.value=[];const query={...route.query};delete query.date;await router.replace({query})}
onMounted(async()=>{await load();if(selectedDate.value)await openDay(dayjs(selectedDate.value).date())});watch(cursor,async()=>{await load();if(selectedDate.value&&!dayjs(selectedDate.value).isSame(cursor.value,'month'))await closeDay()})
const cells=computed(()=>{const start=cursor.value.startOf('month');const prefix=(start.day()+6)%7;const count=cursor.value.daysInMonth();const map=new Map(entries.value.map(x=>[dayjs(x.memory_date).date(),x]));return [...Array(prefix).fill(null),...Array.from({length:count},(_,i)=>({day:i+1,entry:map.get(i+1)}))]})
</script>
<template>
  <header class="page-heading"><div><span class="eyebrow">BACK TO A DAY</span><h1>回到某一天</h1><p>点开亮起的日期，重新走进当天的文字、照片和地点。</p></div><div class="calendar-switch"><button class="button" @click="cursor=cursor.subtract(1,'month')">←</button><span class="chip">{{ cursor.format('YYYY 年 MM 月') }}</span><button class="button" @click="cursor=cursor.add(1,'month')">→</button></div></header>
  <section class="calendar-card"><div class="calendar-head"><span v-for="w in ['一','二','三','四','五','六','日']" :key="w">周{{w}}</span></div><div class="calendar-grid"><button v-for="(cell,i) in cells" :key="i" class="calendar-day" :class="{empty:!cell,'has-memory':cell?.entry,selected:cell&&selectedDate===cursor.date(cell.day).format('YYYY-MM-DD')}" :disabled="!cell" :aria-label="cell?`${cursor.month()+1}月${cell.day}日${cell.entry?`，${cell.entry.count}条记忆`:'，没有记忆'}`:undefined" @click="cell&&openDay(cell.day)"><template v-if="cell"><b>{{cell.day}}</b><i v-if="cell.entry" :title="cell.entry.preview"></i><small v-if="cell.entry">{{cell.entry.count}} 条</small></template></button></div></section>
  <section v-if="selectedDate" class="calendar-day-panel">
    <div class="section-heading"><div><span class="eyebrow">MEMORIES OF THE DAY</span><h2>{{ dayjs(selectedDate).format('YYYY 年 MM 月 DD 日') }}</h2></div><button class="icon-button" aria-label="关闭日期详情" @click="closeDay"><X :size="18" /></button></div>
    <p v-if="loadingDay" class="calendar-loading">正在翻开这一天…</p>
    <div v-else-if="dayMemories.length" class="memory-grid"><MemoryCard v-for="item in dayMemories" :key="item.id" :memory="item" /></div>
    <EmptyState v-else title="这一天还没有记录" text="你仍然可以从今天补写这段故事。" />
  </section>
</template>

<style scoped>
.calendar-switch{display:flex;gap:8px}.calendar-day{font:inherit;color:var(--ink);text-align:left;cursor:pointer}.calendar-day:disabled{cursor:default}.calendar-day small{display:block;margin-top:6px;color:var(--muted);font-size:9px}.calendar-day.selected{outline:2px solid var(--accent);outline-offset:1px}.calendar-day-panel{margin-top:32px}.calendar-day-panel .section-heading{align-items:center}.calendar-loading{padding:28px;text-align:center;color:var(--muted)}
@media(max-width:650px){.calendar-switch{width:100%;justify-content:space-between}.calendar-day small{display:none}}
</style>
