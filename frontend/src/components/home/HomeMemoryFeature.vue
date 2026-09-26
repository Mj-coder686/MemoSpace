<script setup lang="ts">
import dayjs from 'dayjs'
import { ArrowUpRight, BookOpenText, MapPin } from 'lucide-vue-next'
import PrivateMedia from '../PrivateMedia.vue'

defineProps<{ memory: any }>()

const typeLabel: Record<string, string> = {
  PHOTO: '照片', VIDEO: '影像', TEXT: '文字', EVENT: '事件', LOCATION: '地点', MIXED: '图文',
}
</script>

<template>
  <article class="home-memory-feature">
    <router-link :to="`/memory/${memory.id}`" :aria-label="`打开记忆：${memory.title}`">
      <div class="home-memory-feature__visual" :class="{ 'is-text': !memory.cover_file_id }">
        <PrivateMedia
          v-if="memory.cover_file_id"
          :file-id="memory.cover_file_id"
          :mime-type="memory.cover_mime_type"
          :alt="memory.title"
          preview
        />
        <BookOpenText v-else :size="36" aria-hidden="true" />
        <span>{{ typeLabel[memory.memory_type] || '记忆' }}</span>
      </div>
      <div class="home-memory-feature__copy">
        <div class="home-memory-feature__meta">
          <time :datetime="memory.occurred_at">{{ dayjs(memory.occurred_at).format('YYYY年M月D日') }}</time>
          <span v-if="memory.location"><MapPin :size="13" aria-hidden="true" />{{ memory.location }}</span>
        </div>
        <h3>{{ memory.title }}</h3>
        <p>{{ memory.content || '有些时刻，不需要太多文字。' }}</p>
        <span class="home-memory-feature__open">继续翻阅 <ArrowUpRight :size="16" aria-hidden="true" /></span>
      </div>
    </router-link>
  </article>
</template>
