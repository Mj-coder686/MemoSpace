<script setup lang="ts">
withDefaults(defineProps<{
  modelValue?: boolean
  label: string
  description?: string
  name?: string
  disabled?: boolean
}>(), { modelValue: false, disabled: false })

const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
</script>

<template>
  <label class="ui-switch" :class="{ 'ui-switch--disabled': disabled }">
    <span class="ui-switch__copy">
      <span class="ui-switch__label">{{ label }}</span>
      <span v-if="description" class="ui-switch__description">{{ description }}</span>
    </span>
    <input
      class="ui-switch__control"
      type="checkbox"
      role="switch"
      :name="name"
      :checked="modelValue"
      :disabled="disabled"
      @change="emit('update:modelValue', ($event.target as HTMLInputElement).checked)"
    />
  </label>
</template>
