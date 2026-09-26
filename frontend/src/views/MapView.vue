<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import { ArrowRight, Crosshair, LocateFixed, MapPin, Navigation } from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import EmptyState from '../components/EmptyState.vue'
import { UiButton, UiSkeleton } from '../components/ui'
import { coordinateLabel, getCurrentDevicePosition, type DevicePosition } from '../utils/geolocation'

const places = ref<any[]>([])
const current = ref<DevicePosition | null>(null)
const selectedId = ref<number | null>(null)
const locating = ref(false)
const loading = ref(true)
const pageError = ref('')
const locationMessage = ref('')
const locationFailed = ref(false)

const validPlaces = computed(() => places.value
  .map((place) => ({ ...place, latitude: Number(place.latitude), longitude: Number(place.longitude), kind: 'memory' as const }))
  .filter((place) => Number.isFinite(place.latitude) && Number.isFinite(place.longitude)))
const selected = computed(() => places.value.find((place) => Number(place.id) === selectedId.value) || null)
const points = computed(() => [
  ...validPlaces.value,
  ...(current.value ? [{ id: 'current', title: '我现在的位置', ...current.value, kind: 'current' as const }] : []),
])

const plotted = computed(() => {
  if (!points.value.length) return []
  const latitudes = points.value.map((point) => point.latitude)
  const longitudes = points.value.map((point) => point.longitude)
  const minLat = Math.min(...latitudes); const maxLat = Math.max(...latitudes)
  const minLng = Math.min(...longitudes); const maxLng = Math.max(...longitudes)
  return points.value.map((point) => ({
    ...point,
    x: maxLng === minLng ? 50 : 10 + ((point.longitude - minLng) / (maxLng - minLng)) * 80,
    y: maxLat === minLat ? 50 : 10 + ((maxLat - point.latitude) / (maxLat - minLat)) * 80,
  }))
})

const load = async () => {
  loading.value = true
  pageError.value = ''
  try {
    places.value = (await http.get('/map')).data
  } catch (error) {
    pageError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

const locate = async () => {
  locating.value = true
  locationMessage.value = ''
  locationFailed.value = false
  try {
    current.value = await getCurrentDevicePosition()
    locationMessage.value = `当前位置已显示：${coordinateLabel(current.value)}，精度约 ${Math.round(current.value.accuracy)} 米。`
  } catch (error) {
    locationFailed.value = true
    locationMessage.value = error instanceof Error ? error.message : '暂时无法获取当前位置'
  } finally {
    locating.value = false
  }
}

const createLocation = () => window.dispatchEvent(new CustomEvent('memospace-open-create', { detail: { memoryType: 'LOCATION' } }))
onMounted(load)
</script>

<template>
  <div class="memory-map-page">
    <header class="companion-page-header">
      <div><span class="memory-kicker">MEMORY ATLAS</span><h1>我的足迹</h1><p>把地点当作记忆索引，而不是持续追踪你的位置。</p></div>
      <UiButton variant="primary" size="lg" :loading="locating" loading-text="正在定位" @click="locate"><Crosshair :size="18" />显示当前位置</UiButton>
    </header>

    <div class="map-permission-note" :class="{ 'is-error': locationFailed, 'is-success': current }" role="status">
      <LocateFixed :size="18" aria-hidden="true" />
      <span><strong>{{ locationFailed ? '定位没有成功' : current ? '当前位置仅在本次查看中显示' : '定位权限尚未请求' }}</strong><small>{{ locationMessage || '只有主动点击“显示当前位置”后才读取一次坐标，MemoSpace 不会在后台持续跟踪。' }}</small></span>
    </div>

    <div v-if="loading" class="memory-map-loading" aria-label="正在读取足迹"><UiSkeleton height="520px" radius="var(--radius-lg)" /><UiSkeleton height="520px" radius="var(--radius-lg)" /></div>
    <EmptyState v-else-if="pageError" kind="error" title="足迹暂时无法读取" :text="pageError" action-label="重新加载" @action="load" />

    <div v-else-if="places.length || current" class="memory-map-layout">
      <aside class="map-place-panel" aria-labelledby="place-list-title">
        <header><span class="memory-kicker">PLACES REMEMBERED</span><h2 id="place-list-title">被记住的地点</h2><p>{{ places.length }} 条带有坐标的记忆</p></header>
        <div v-if="places.length" class="map-place-list">
          <button v-for="place in places" :key="place.id" type="button" :class="{ 'is-selected': selectedId === Number(place.id) }" @click="selectedId = Number(place.id)">
            <span class="map-place-list__icon"><MapPin :size="17" /></span>
            <span><strong>{{ place.title }}</strong><small>{{ place.location || '已记录坐标' }} · {{ dayjs(place.occurred_at).format('YYYY.M.D') }}</small></span>
            <ArrowRight :size="16" aria-hidden="true" />
          </button>
        </div>
        <p v-else class="map-place-panel__empty">还没有带坐标的记忆；当前位置仍可临时显示在右侧画布中。</p>
      </aside>

      <section class="memory-map-canvas" aria-label="记忆地点画布">
        <div class="memory-map-canvas__axis" aria-hidden="true"><span>N</span><span>E</span><span>S</span><span>W</span></div>
        <template v-for="point in plotted" :key="point.id">
          <button
            v-if="point.kind === 'memory'"
            type="button"
            class="memory-map-marker"
            :class="{ 'is-selected': selectedId === Number(point.id) }"
            :style="{ left: `${point.x}%`, top: `${point.y}%` }"
            :aria-label="`选择地点记忆：${point.title}`"
            @click="selectedId = Number(point.id)"
          ><MapPin :size="19" fill="currentColor" /></button>
          <span v-else class="memory-map-current" :style="{ left: `${point.x}%`, top: `${point.y}%` }" aria-label="我现在的位置"><Navigation :size="18" fill="currentColor" /></span>
        </template>

        <article v-if="selected" class="memory-map-selection">
          <span class="memory-kicker">SELECTED PLACE</span>
          <h3>{{ selected.title }}</h3>
          <p><MapPin :size="14" />{{ selected.location || coordinateLabel({ latitude: Number(selected.latitude), longitude: Number(selected.longitude) }) }}</p>
          <time :datetime="selected.occurred_at">{{ dayjs(selected.occurred_at).format('YYYY年M月D日') }}</time>
          <router-link :to="`/memory/${selected.id}`">查看这段记忆 <ArrowRight :size="15" /></router-link>
        </article>

        <div class="memory-map-legend"><span><MapPin :size="15" fill="currentColor" />记忆地点</span><span><Navigation :size="15" fill="currentColor" />当前位置</span></div>
      </section>
    </div>

    <EmptyState v-else title="地图上还没有足迹" text="创建一条地点记忆，坐标会成为回到那一刻的入口。" action-label="创建地点记忆" @action="createLocation" />
  </div>
</template>
