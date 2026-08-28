<script setup lang="ts">
import { onMounted, ref } from 'vue'
import http from '../api/http'
import EmptyState from '../components/EmptyState.vue'
const spaces = ref<any[]>([])
onMounted(async () => { spaces.value = (await http.get('/spaces')).data })
</script>

<template>
  <header class="page-heading"><div><span class="eyebrow">PLACES WE BELONG</span><h1>记忆空间</h1><p>自己的安静角落，和重要的人共同写下的故事。</p></div></header>
  <div v-if="spaces.length" class="space-grid">
    <router-link v-for="space in spaces" :key="space.id" :to="`/space/${space.id}`" class="space-card" :class="{archived:space.status==='ARCHIVED'}"
      :style="{'--space-primary':space.primary_color,'--space-background':space.background_color,'--space-text':space.text_color,'--space-muted':space.muted_color}">
      <div><span class="eyebrow">{{ space.status === 'ARCHIVED' ? 'ARCHIVED SPACE' : space.space_type === 'PERSONAL' ? 'JUST FOR ME' : 'BETWEEN US' }}</span><h3>{{ space.name }}</h3><p>{{ space.status === 'ARCHIVED' ? '历史仍在，只是不再增加新内容。' : space.preset_name + ' · 低饱和主题' }}</p></div>
      <div class="space-numbers"><div><b>{{ space.memoryCount }}</b><small>记忆</small></div><div><b>{{ space.photoCount }}</b><small>照片</small></div><div><b>{{ space.placeCount }}</b><small>地点</small></div></div>
    </router-link>
  </div>
  <EmptyState v-else title="还没有共同空间" text="向重要的人发出邀请，从第一段共同记忆开始。" />
</template>
