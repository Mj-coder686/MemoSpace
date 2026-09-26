<script setup lang="ts">
withDefaults(defineProps<{ width?: string; height?: string; radius?: string }>(), {
  width: '100%',
  height: '1rem',
  radius: 'var(--radius-sm)',
})
</script>

<template><span class="ui-skeleton" :style="{ width, height, borderRadius: radius }" aria-hidden="true" /></template>
