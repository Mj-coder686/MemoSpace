<script setup lang="ts" generic="T extends string | number">
defineProps<{
  modelValue?: T
  value: T
  label: string
  description?: string
  name: string
  disabled?: boolean
}>()

const emit = defineEmits<{ 'update:modelValue': [value: T] }>()
</script>

<template>
  <label class="ui-choice" :class="{ 'ui-choice--disabled': disabled }">
    <input
      class="ui-choice__control"
      type="radio"
      :name="name"
      :value="value"
      :checked="modelValue === value"
      :disabled="disabled"
      @change="emit('update:modelValue', value)"
    />
    <span class="ui-choice__copy">
      <span class="ui-choice__label">{{ label }}</span>
      <span v-if="description" class="ui-choice__description">{{ description }}</span>
    </span>
  </label>
</template>
