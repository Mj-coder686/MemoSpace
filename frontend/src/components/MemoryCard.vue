<script setup lang="ts">
import dayjs from 'dayjs'
import { MessageCircle, MapPin, Sparkles } from 'lucide-vue-next'
import PrivateMedia from './PrivateMedia.vue'

defineProps<{ memory: any; compact?: boolean }>()
const typeLabel: Record<string, string> = { PHOTO: '照片', VIDEO: '影像', TEXT: '文字', EVENT: '事件', LOCATION: '地点', MIXED: '图文' }
</script>

<template>
  <article class="memory-card" :class="[`type-${memory.memory_type?.toLowerCase()}`, { compact }]">
    <router-link :to="`/memory/${memory.id}`" class="memory-link">
      <div v-if="memory.cover_file_id" class="memory-visual memory-cover">
        <PrivateMedia :file-id="memory.cover_file_id" :mime-type="memory.cover_mime_type" :alt="memory.title" preview />
        <span class="visual-date cover-date">{{ dayjs(memory.occurred_at).format('MM / DD') }}</span>
      </div>
      <div v-else-if="memory.memory_type !== 'TEXT'" class="memory-visual">
        <span class="visual-date">{{ dayjs(memory.occurred_at).format('MM / DD') }}</span>
        <Sparkles :size="28" />
        <small>{{ memory.media_count ? '媒体记录暂时无法读取' : (memory.location || '这段记忆还没有添加媒体') }}</small>
      </div>
      <div class="memory-body">
        <div class="memory-meta"><span>{{ typeLabel[memory.memory_type] || '记忆' }}</span><time>{{ dayjs(memory.occurred_at).format('YYYY.MM.DD') }}</time></div>
        <h3>{{ memory.title }}</h3>
        <p>{{ memory.content || '有些时刻，不需要太多文字。' }}</p>
        <div class="memory-foot">
          <span class="creator-dot">{{ memory.creator_nickname?.slice(0, 1) || '拾' }}</span><span>{{ memory.creator_nickname }}</span>
          <span v-if="memory.location"><MapPin :size="14" />{{ memory.location }}</span>
          <span v-if="memory.comment_count"><MessageCircle :size="14" />{{ memory.comment_count }}</span>
        </div>
      </div>
    </router-link>
  </article>
</template>

<style scoped>
.memory-cover { position:relative; padding:0; background:#292b2d; }
.memory-cover :deep(.private-media) { width:100%; height:150px; min-height:150px; border-radius:0; }
.memory-cover :deep(img), .memory-cover :deep(video) { width:100%; height:100%; max-height:none; object-fit:cover; }
.cover-date { position:absolute; top:14px; left:14px; z-index:1; box-shadow:0 4px 14px rgba(20,18,16,.2); }
.memory-card.compact .memory-cover :deep(.private-media) { height:115px; min-height:115px; }
</style>
