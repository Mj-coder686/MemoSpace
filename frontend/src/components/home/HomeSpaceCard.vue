<script setup lang="ts">
import { ArrowUpRight, LockKeyhole, UsersRound } from 'lucide-vue-next'

defineProps<{ space: any }>()
</script>

<template>
  <article
    class="home-space-card"
    :class="{ 'is-archived': space.status === 'ARCHIVED' }"
    :style="{ '--home-space-accent': space.primary_color || 'var(--color-brand-600)' }"
  >
    <router-link :to="`/space/${space.id}`">
      <span class="home-space-card__icon" aria-hidden="true">
        <LockKeyhole v-if="space.space_type === 'PERSONAL'" :size="20" />
        <UsersRound v-else :size="20" />
      </span>
      <span class="home-space-card__copy">
        <small>{{ space.space_type === 'PERSONAL' ? '私人空间' : '共同空间' }}</small>
        <strong>{{ space.name }}</strong>
        <span>{{ space.status === 'ARCHIVED' ? '这段空间已经封存' : `${space.memoryCount || 0} 条记忆 · ${space.photoCount || 0} 张照片` }}</span>
      </span>
      <ArrowUpRight :size="18" aria-hidden="true" />
    </router-link>
  </article>
</template>
