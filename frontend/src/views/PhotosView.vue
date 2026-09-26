<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import { ImagePlus, MapPin, Play } from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import EmptyState from '../components/EmptyState.vue'
import PrivateMedia from '../components/PrivateMedia.vue'
import { UiButton, UiSkeleton } from '../components/ui'

const photos = ref<any[]>([])
const loading = ref(true)
const pageError = ref('')

const groups = computed(() => {
  const grouped = new Map<string, any[]>()
  photos.value.forEach((item) => {
    const key = dayjs(item.occurred_at).format('YYYY年 M月')
    grouped.set(key, [...(grouped.get(key) || []), item])
  })
  return [...grouped.entries()].map(([label, items]) => ({ label, items }))
})

const load = async () => {
  loading.value = true
  pageError.value = ''
  try {
    const { data } = await http.get('/memories')
    photos.value = data.filter((item: any) => ['PHOTO', 'VIDEO', 'MIXED'].includes(item.memory_type) && item.cover_file_id)
  } catch (error) {
    pageError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

const createPhoto = () => window.dispatchEvent(new CustomEvent('memospace-open-create', { detail: { memoryType: 'PHOTO' } }))
onMounted(load)
</script>

<template>
  <div class="photo-archive-page">
    <header class="companion-page-header">
      <div><span class="memory-kicker">VISUAL ARCHIVE</span><h1>记忆相册</h1><p>照片不是装饰，它们是被认真保存的生活证据。</p></div>
      <UiButton variant="primary" size="lg" @click="createPhoto"><ImagePlus :size="18" />添加照片记忆</UiButton>
    </header>

    <div v-if="loading" class="photo-archive-loading" aria-label="正在读取相册">
      <UiSkeleton v-for="height in ['320px','240px','360px','280px','330px','250px']" :key="height" :height="height" radius="var(--radius-md)" />
    </div>

    <EmptyState v-else-if="pageError" kind="error" title="相册暂时没有打开" :text="pageError" action-label="重新加载" @action="load" />

    <div v-else-if="groups.length" class="photo-archive-groups">
      <section v-for="group in groups" :key="group.label" class="photo-month" :aria-labelledby="`photo-month-${group.label}`">
        <div class="photo-month__heading"><h2 :id="`photo-month-${group.label}`">{{ group.label }}</h2><span>{{ group.items.length }} 段影像</span></div>
        <div class="photo-month__grid">
          <router-link v-for="item in group.items" :key="item.id" :to="`/memory/${item.id}`" class="photo-tile" :aria-label="`打开记忆：${item.title}`">
            <PrivateMedia :file-id="item.cover_file_id" :mime-type="item.cover_mime_type" :alt="item.title" preview />
            <span v-if="item.memory_type === 'VIDEO'" class="photo-tile__play" aria-hidden="true"><Play :size="20" fill="currentColor" /></span>
            <span class="photo-tile__caption"><strong>{{ item.title }}</strong><span><time :datetime="item.occurred_at">{{ dayjs(item.occurred_at).format('M月D日') }}</time><span v-if="item.location"><MapPin :size="13" />{{ item.location }}</span></span></span>
          </router-link>
        </div>
      </section>
    </div>

    <EmptyState v-else title="相册还是空的" text="添加第一张真实照片，让这里慢慢长成你的生活影像档案。" action-label="添加照片记忆" @action="createPhoto" />
  </div>
</template>
