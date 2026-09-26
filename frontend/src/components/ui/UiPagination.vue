<script setup lang="ts">
import { ArrowLeft, ArrowRight } from 'lucide-vue-next'
import UiIconButton from './UiIconButton.vue'

const props = withDefaults(defineProps<{ page?: number; totalPages: number; label?: string }>(), { page: 1, label: '分页导航' })
const emit = defineEmits<{ 'update:page': [page: number] }>()
const go = (page: number) => emit('update:page', Math.min(props.totalPages, Math.max(1, page)))
</script>

<template>
  <nav class="ui-pagination" :aria-label="label">
    <UiIconButton label="上一页" size="sm" variant="ghost" :disabled="page <= 1" @click="go(page - 1)"><ArrowLeft :size="18" /></UiIconButton>
    <span class="ui-pagination__status" aria-live="polite">第 {{ page }} / {{ totalPages }} 页</span>
    <UiIconButton label="下一页" size="sm" variant="ghost" :disabled="page >= totalPages" @click="go(page + 1)"><ArrowRight :size="18" /></UiIconButton>
  </nav>
</template>
