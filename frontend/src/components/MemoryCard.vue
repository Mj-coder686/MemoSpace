<script setup lang="ts">
import { computed } from 'vue'
import dayjs from 'dayjs'
import { Globe2, LockKeyhole, MapPin, MessageCircle, UsersRound } from 'lucide-vue-next'
import PrivateMedia from './PrivateMedia.vue'

const props = defineProps<{ memory: any; compact?: boolean }>()
const typeLabel: Record<string, string> = { PHOTO: '照片', VIDEO: '影像', TEXT: '文字', EVENT: '事件', LOCATION: '地点', MIXED: '图文' }
const visibility = computed(() => ({
  PRIVATE: { label: '仅自己', icon: LockKeyhole },
  RELATIONSHIP: { label: '关系成员', icon: UsersRound },
  PUBLIC: { label: '公开', icon: Globe2 },
  CUSTOM: { label: '指定的人', icon: UsersRound },
})[props.memory.visibility as 'PRIVATE' | 'RELATIONSHIP' | 'PUBLIC' | 'CUSTOM'] || { label: '受保护', icon: LockKeyhole })
const mediaRich = computed(() => Boolean(props.memory.cover_file_id) || ['PHOTO', 'VIDEO', 'MIXED'].includes(props.memory.memory_type))
</script>

<template>
  <article class="archive-memory-card" :class="[{ 'is-compact': compact, 'is-media-rich': mediaRich }, `is-${memory.memory_type?.toLowerCase()}`]">
    <router-link :to="`/memory/${memory.id}`" :aria-label="`打开记忆：${memory.title}`">
      <div v-if="memory.cover_file_id" class="archive-memory-card__visual">
        <PrivateMedia :file-id="memory.cover_file_id" :mime-type="memory.cover_mime_type" :alt="memory.title" preview />
        <span class="archive-memory-card__type">{{ typeLabel[memory.memory_type] || '记忆' }}</span>
      </div>
      <div class="archive-memory-card__body">
        <div class="archive-memory-card__meta">
          <time :datetime="memory.occurred_at">{{ dayjs(memory.occurred_at).format('YYYY · MM · DD') }}</time>
          <span><component :is="visibility.icon" :size="13" aria-hidden="true" />{{ visibility.label }}</span>
        </div>
        <h3>{{ memory.title }}</h3>
        <p>{{ memory.content || '有些时刻，不需要太多文字。' }}</p>
        <div class="archive-memory-card__foot">
          <span v-if="memory.creator_nickname" class="archive-memory-card__creator">{{ memory.creator_nickname.slice(0, 1) }}</span>
          <span v-if="memory.creator_nickname">{{ memory.creator_nickname }}</span>
          <span v-if="memory.location"><MapPin :size="13" aria-hidden="true" />{{ memory.location }}</span>
          <span v-if="memory.comment_count"><MessageCircle :size="13" aria-hidden="true" />{{ memory.comment_count }}</span>
        </div>
      </div>
    </router-link>
  </article>
</template>
