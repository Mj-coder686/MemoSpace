<script setup lang="ts">
import { computed, useId } from 'vue'

withDefaults(defineProps<{
  modelValue?: string | number
  label: string
  name?: string
  helper?: string
  error?: string
  required?: boolean
  disabled?: boolean
}>(), {
  modelValue: '',
  required: false,
  disabled: false,
})

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const uid = useId()
const inputId = computed(() => `ui-select-${uid}`)
const messageId = computed(() => `ui-select-message-${uid}`)
</script>

<template>
  <label class="ui-field" :for="inputId">
    <span class="ui-field__label">
      {{ label }}<span v-if="required" class="ui-field__required" aria-hidden="true">*</span>
    </span>
    <select
      :id="inputId"
      class="ui-field__control ui-field__select"
      :value="modelValue"
      :name="name"
      :required="required"
      :disabled="disabled"
      :aria-invalid="error ? 'true' : undefined"
      :aria-describedby="helper || error ? messageId : undefined"
      @change="emit('update:modelValue', ($event.target as HTMLSelectElement).value)"
    >
      <slot />
    </select>
    <span :id="messageId" class="ui-field__message" :class="{ 'ui-field__message--error': error }">
      {{ error || helper || '' }}
    </span>
  </label>
</template>
