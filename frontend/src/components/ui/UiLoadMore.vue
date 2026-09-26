<script setup lang="ts">
import UiButton from './UiButton.vue'

withDefaults(defineProps<{ loading?: boolean; done?: boolean; label?: string; doneText?: string }>(), {
  loading: false,
  done: false,
  label: '加载更多',
  doneText: '已经到底了',
})
defineEmits<{ load: [] }>()
</script>

<template>
  <div class="ui-load-more">
    <span v-if="done" class="ui-load-more__done">{{ doneText }}</span>
    <UiButton v-else :loading="loading" loading-text="正在加载" @click="$emit('load')">{{ label }}</UiButton>
  </div>
</template>
