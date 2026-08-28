<script setup lang="ts">
import { Feather } from 'lucide-vue-next'
withDefaults(defineProps<{ title?: string; text?: string }>(), { title: '这里还很安静', text: '从第一条值得收藏的记忆开始吧。' })
</script>
<template><div class="empty-state"><span><Feather :size="25" /></span><h3>{{ title }}</h3><p>{{ text }}</p></div></template>
