<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import dayjs from 'dayjs'
import { Globe2, LockKeyhole, MapPin, MessageCircle, Trash2, UsersRound } from 'lucide-vue-next'
import PrivateMedia from './PrivateMedia.vue'
import UiIconButton from './ui/UiIconButton.vue'

const props = withDefaults(defineProps<{ memory: any; compact?: boolean; canDelete?: boolean }>(), { compact: false, canDelete: false })
const emit = defineEmits<{ requestDelete: [memory: any] }>()
const typeLabel: Record<string, string> = { PHOTO: '照片', VIDEO: '影像', TEXT: '文字', EVENT: '事件', LOCATION: '地点', MIXED: '图文' }
const visibility = computed(() => ({
  PRIVATE: { label: '仅自己', icon: LockKeyhole },
  RELATIONSHIP: { label: '关系成员', icon: UsersRound },
  PUBLIC: { label: '公开', icon: Globe2 },
  CUSTOM: { label: '指定的人', icon: UsersRound },
})[props.memory.visibility as 'PRIVATE' | 'RELATIONSHIP' | 'PUBLIC' | 'CUSTOM'] || { label: '受保护', icon: LockKeyhole })
const mediaRich = computed(() => Boolean(props.memory.cover_file_id) || ['PHOTO', 'VIDEO', 'MIXED'].includes(props.memory.memory_type))
const pressStart = ref<{ x: number; y: number } | null>(null)
const suppressOpen = ref(false)
let pressTimer: ReturnType<typeof setTimeout> | undefined

const clearPress = () => {
  if (pressTimer) clearTimeout(pressTimer)
  pressTimer = undefined
  pressStart.value = null
}

const requestDelete = () => {
  if (props.canDelete) emit('requestDelete', props.memory)
}

const onPointerDown = (event: PointerEvent) => {
  if (!props.canDelete || event.pointerType === 'mouse') return
  clearPress()
  pressStart.value = { x: event.clientX, y: event.clientY }
  pressTimer = setTimeout(() => {
    suppressOpen.value = true
    requestDelete()
    if ('vibrate' in navigator) navigator.vibrate?.(18)
  }, 550)
}

const onPointerMove = (event: PointerEvent) => {
  if (!pressStart.value) return
  if (Math.hypot(event.clientX - pressStart.value.x, event.clientY - pressStart.value.y) > 10) clearPress()
}

const onOpen = (event: MouseEvent) => {
  if (!suppressOpen.value) return
  event.preventDefault()
  event.stopPropagation()
  suppressOpen.value = false
}

const onContextMenu = (event: MouseEvent) => {
  if (!props.canDelete) return
  event.preventDefault()
  if (!suppressOpen.value) requestDelete()
  suppressOpen.value = true
  window.setTimeout(() => { suppressOpen.value = false }, 350)
}

onBeforeUnmount(clearPress)
</script>

<template>
  <article
    class="archive-memory-card"
    :class="[{ 'is-compact': compact, 'is-media-rich': mediaRich, 'is-deletable': canDelete }, `is-${memory.memory_type?.toLowerCase()}`]"
    @pointerdown="onPointerDown"
    @pointermove="onPointerMove"
    @pointerup="clearPress"
    @pointercancel="clearPress"
    @contextmenu="onContextMenu"
  >
    <UiIconButton
      v-if="canDelete"
      class="archive-memory-card__delete"
      variant="secondary"
      size="md"
      :label="`删除记忆：${memory.title}`"
      @pointerdown.stop
      @click.stop="requestDelete"
    ><Trash2 :size="18" aria-hidden="true" /></UiIconButton>
    <router-link :to="`/memory/${memory.id}`" :aria-label="`打开记忆：${memory.title}`" @click="onOpen">
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
