<script setup lang="ts">
import { BookHeart, Compass, Home, Plus, Users } from 'lucide-vue-next'
import { useRoute } from 'vue-router'
import { compactPrimaryNavigation, isCompactNavigationActive } from '../../config/navigation'

defineEmits<{ create: [] }>()
const route = useRoute()
const icons = { home: Home, memory: BookHeart, relationship: Users, feed: Compass }
</script>

<template>
  <nav class="shell-bottom-nav" aria-label="底部导航">
    <template v-for="(item, index) in compactPrimaryNavigation" :key="item.to">
      <button v-if="index === 2" class="shell-bottom-nav__create" type="button" aria-label="记录此刻" @click="$emit('create')"><span class="shell-bottom-nav__create-icon"><Plus :size="25" /></span><span>记录</span></button>
      <router-link :to="item.to" :class="{ 'is-active': isCompactNavigationActive(route.path, item.domain) }" :aria-current="isCompactNavigationActive(route.path, item.domain) ? 'page' : undefined">
        <component :is="icons[item.domain as keyof typeof icons]" :size="21" /><span>{{ item.label }}</span>
      </router-link>
    </template>
  </nav>
</template>
