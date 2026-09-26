<script setup lang="ts">
withDefaults(defineProps<{
  variant?: 'primary' | 'secondary' | 'tonal' | 'ghost' | 'danger' | 'link'
  size?: 'sm' | 'md' | 'lg'
  type?: 'button' | 'submit' | 'reset'
  loading?: boolean
  loadingText?: string
  disabled?: boolean
  block?: boolean
}>(), {
  variant: 'secondary',
  size: 'md',
  type: 'button',
  loading: false,
  loadingText: '处理中',
  disabled: false,
  block: false,
})
</script>

<template>
  <button
    class="ui-button"
    :class="[`ui-button--${variant}`, `ui-button--${size}`, { 'ui-button--loading': loading, 'ui-button--block': block }]"
    :type="type"
    :disabled="disabled || loading"
    :aria-busy="loading || undefined"
  >
    <span class="ui-button__content"><slot /></span>
    <span v-if="loading" class="ui-button__loading">
      <span class="ui-spinner" aria-hidden="true" />
      <span>{{ loadingText }}</span>
    </span>
  </button>
</template>
