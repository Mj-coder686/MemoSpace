<script setup lang="ts">
defineProps<{ items: Array<{ label: string; to: string }> }>()
</script>

<template>
  <nav v-if="items.length" class="shell-secondary-nav" aria-label="当前分区导航">
    <router-link v-for="item in items" :key="item.to" :to="item.to">{{ item.label }}</router-link>
  </nav>
</template>
