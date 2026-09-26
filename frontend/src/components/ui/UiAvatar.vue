<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  src?: string
  name?: string
  alt?: string
  size?: 32 | 40 | 48 | 64 | 96
  online?: boolean
}>(), {
  name: '拾光用户',
  size: 40,
  online: false,
})

const failed = ref(false)
watch(() => props.src, () => { failed.value = false })
const initial = computed(() => Array.from(props.name.trim())[0] || '拾')
const tone = computed(() => Array.from(props.name).reduce((sum, char) => sum + (char.codePointAt(0) || 0), 0) % 6)
</script>

<template>
  <span class="ui-avatar" :class="[`ui-avatar--${size}`, `ui-avatar--tone-${tone}`]">
    <img v-if="src && !failed" :src="src" :alt="alt ?? name" @error="failed = true" />
    <span v-else aria-hidden="true">{{ initial }}</span>
    <span v-if="online" class="ui-avatar__presence" aria-hidden="true" />
    <span v-if="online" class="visually-hidden">在线</span>
  </span>
</template>
