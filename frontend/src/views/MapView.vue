<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Crosshair, MapPin } from 'lucide-vue-next'
import http from '../api/http'
import EmptyState from '../components/EmptyState.vue'
import { coordinateLabel, getCurrentDevicePosition, type DevicePosition } from '../utils/geolocation'

const places = ref<any[]>([])
const current = ref<DevicePosition | null>(null)
const locating = ref(false)
const message = ref('')

onMounted(async () => { places.value = (await http.get('/map')).data })

const points = computed(() => [
  ...places.value.map(place => ({ ...place, latitude: Number(place.latitude), longitude: Number(place.longitude), kind: 'memory' })),
  ...(current.value ? [{ id: 'current', title: '我现在的位置', ...current.value, kind: 'current' }] : []),
])

const plotted = computed(() => {
  if (!points.value.length) return []
  const latitudes = points.value.map(point => point.latitude)
  const longitudes = points.value.map(point => point.longitude)
  const minLat = Math.min(...latitudes); const maxLat = Math.max(...latitudes)
  const minLng = Math.min(...longitudes); const maxLng = Math.max(...longitudes)
  return points.value.map(point => ({
    ...point,
    x: maxLng === minLng ? 50 : 8 + ((point.longitude - minLng) / (maxLng - minLng)) * 84,
    y: maxLat === minLat ? 50 : 8 + ((maxLat - point.latitude) / (maxLat - minLat)) * 84,
  }))
})

const locate = async () => {
  locating.value = true
  message.value = ''
  try {
    current.value = await getCurrentDevicePosition()
    message.value = `当前位置：${coordinateLabel(current.value)}（精度约 ${Math.round(current.value.accuracy)} 米）`
  } catch (error) {
    message.value = error instanceof Error ? error.message : '暂时无法获取当前位置'
  } finally { locating.value = false }
}
</script>

<template>
  <header class="page-heading">
    <div><span class="eyebrow">MEMORY ATLAS</span><h1>我的足迹</h1><p>那些故事发生过的地方，慢慢连成一张生活地图。</p></div>
    <button type="button" class="button" :disabled="locating" @click="locate"><Crosshair :size="16" />{{ locating ? '正在定位…' : '定位到我' }}</button>
  </header>
  <p v-if="message" class="map-message">{{ message }}</p>
  <div v-if="plotted.length" class="map-canvas">
    <template v-for="place in plotted" :key="place.id">
      <router-link v-if="place.kind==='memory'" :to="`/memory/${place.id}`" class="map-pin" :style="{left:place.x+'%',top:place.y+'%'}" :title="place.title"><MapPin :size="15" /></router-link>
      <span v-else class="map-current" :style="{left:place.x+'%',top:place.y+'%'}" title="我现在的位置"><Crosshair :size="18" /></span>
    </template>
    <div class="map-legend"><span class="eyebrow">PLACES REMEMBERED</span><b>{{ places.length }} 个被记住的地点</b><small v-if="current">蓝色圆点是当前位置</small></div>
  </div>
  <EmptyState v-else title="地图上还没有足迹" text="创建地点记忆，或点击“定位到我”查看当前坐标。" />
</template>

<style scoped>
.page-heading .button{display:inline-flex;align-items:center;gap:7px}.map-message{margin:-18px 0 20px;padding:12px 15px;border:1px solid var(--line);border-radius:14px;color:var(--muted);background:var(--surface);font-size:12px}.map-current{position:absolute;z-index:4;transform:translate(-50%,-50%);width:42px;height:42px;display:grid;place-items:center;border:4px solid rgba(255,255,255,.9);border-radius:50%;color:white;background:#4e7894;box-shadow:0 0 0 8px rgba(78,120,148,.18),0 10px 25px rgba(45,69,84,.28)}.map-legend small{display:block;margin-top:5px;color:var(--muted);font-size:10px}@media(max-width:620px){.page-heading{align-items:flex-start}.page-heading .button{margin-top:10px}}
</style>
