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
  <label class="ui-choice" :class="{ 'ui-choice--disabled': disabled }">
    <input
      class="ui-choice__control"
      type="checkbox"
      :name="name"
      :checked="modelValue"
      :disabled="disabled"
      @change="emit('update:modelValue', ($event.target as HTMLInputElement).checked)"
    />
    <span class="ui-choice__copy">
      <span class="ui-choice__label">{{ label }}</span>
      <span v-if="description" class="ui-choice__description">{{ description }}</span>
    </span>
  </label>
</template>
