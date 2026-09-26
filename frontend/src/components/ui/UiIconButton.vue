<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  label: string
  size?: 'sm' | 'md' | 'lg'
  variant?: 'secondary' | 'ghost' | 'tonal'
  badge?: number
  disabled?: boolean
  type?: 'button' | 'submit' | 'reset'
}>(), {
  size: 'md',
  variant: 'secondary',
  disabled: false,
  type: 'button',
})

const badgeLabel = computed(() => props.badge == null ? '' : props.badge > 99 ? '99+' : String(props.badge))
</script>

<template>
  <button
    class="ui-icon-button"
    :class="[`ui-icon-button--${size}`, `ui-icon-button--${variant}`]"
    :type="type"
    :aria-label="label"
    :disabled="disabled"
  >
    <slot />
    <span v-if="badge != null && badge > 0" class="ui-icon-button__badge" aria-hidden="true">{{ badgeLabel }}</span>
    <span v-if="badge != null && badge > 0" class="visually-hidden">，{{ badgeLabel }} 条未读</span>
  </button>
</template>
