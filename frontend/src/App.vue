<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from './components/AppShell.vue'

const route = useRoute()
const publicPage = computed(() => Boolean(route.meta.public))
</script>

<template>
  <router-view v-if="publicPage" />
  <AppShell v-else><router-view /></AppShell>
</template>
