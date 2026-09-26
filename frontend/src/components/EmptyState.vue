<script setup lang="ts">
import { Archive, Feather, LockKeyhole, SearchX, WifiOff } from 'lucide-vue-next'
import { computed } from 'vue'
import UiButton from './ui/UiButton.vue'

const props = withDefaults(defineProps<{
  kind?: 'empty' | 'search' | 'permission' | 'error' | 'archived'
  title?: string
  text?: string
  actionLabel?: string
}>(), { kind: 'empty', title: '这里还很安静', text: '从第一条值得收藏的记忆开始吧。' })
defineEmits<{ action: [] }>()
const icon = computed(() => ({ empty: Feather, search: SearchX, permission: LockKeyhole, error: WifiOff, archived: Archive })[props.kind])
</script>

<template>
  <section class="ui-empty-state">
    <span class="ui-empty-state__icon" aria-hidden="true"><component :is="icon" :size="24" /></span>
    <h3>{{ title }}</h3>
    <p>{{ text }}</p>
    <div v-if="actionLabel || $slots.actions" class="ui-empty-state__actions">
      <UiButton v-if="actionLabel" variant="primary" @click="$emit('action')">{{ actionLabel }}</UiButton>
      <slot name="actions" />
    </div>
  </section>
</template>
