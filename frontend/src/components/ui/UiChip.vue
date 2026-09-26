<script setup lang="ts">
withDefaults(defineProps<{
  interactive?: boolean
  selected?: boolean
  disabled?: boolean
  type?: 'button' | 'submit' | 'reset'
}>(), {
  interactive: false,
  selected: false,
  disabled: false,
  type: 'button',
})
</script>

<template>
  <button
    v-if="interactive"
    class="ui-chip"
    :class="{ 'ui-chip--selected': selected, 'ui-chip--disabled': disabled }"
    :type="type"
    :disabled="disabled"
    :aria-pressed="selected"
  ><slot /></button>
  <span v-else class="ui-chip" :class="{ 'ui-chip--selected': selected }"><slot /></span>
</template>
