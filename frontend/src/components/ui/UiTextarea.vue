<script setup lang="ts">
import { computed, nextTick, onMounted, ref, useId, watch } from 'vue'

const props = withDefaults(defineProps<{
  modelValue?: string
  label: string
  name?: string
  placeholder?: string
  helper?: string
  error?: string
  rows?: number
  maxlength?: number
  required?: boolean
  disabled?: boolean
  autogrow?: boolean
}>(), {
  modelValue: '',
  rows: 4,
  required: false,
  disabled: false,
  autogrow: true,
})

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const uid = useId()
const inputId = computed(() => `ui-textarea-${uid}`)
const messageId = computed(() => `ui-textarea-message-${uid}`)
const counter = computed(() => props.maxlength ? `${props.modelValue.length}/${props.maxlength}` : '')
const controlRef = ref<HTMLTextAreaElement | null>(null)
const resize = () => {
  if (!props.autogrow || !controlRef.value) return
  const control = controlRef.value
  control.style.height = 'auto'
  const maxHeight = Number.parseFloat(getComputedStyle(control).maxHeight)
  const nextHeight = Number.isFinite(maxHeight) ? Math.min(control.scrollHeight, maxHeight) : control.scrollHeight
  control.style.height = `${nextHeight}px`
  control.style.overflowY = Number.isFinite(maxHeight) && control.scrollHeight > maxHeight ? 'auto' : 'hidden'
}
const onInput = (event: Event) => {
  emit('update:modelValue', (event.target as HTMLTextAreaElement).value)
  resize()
}
watch(() => props.modelValue, () => nextTick(resize))
onMounted(resize)
</script>

<template>
  <label class="ui-field" :for="inputId">
    <span class="ui-field__label">
      {{ label }}<span v-if="required" class="ui-field__required" aria-hidden="true">*</span>
    </span>
    <textarea
      ref="controlRef"
      :id="inputId"
      class="ui-field__control ui-field__textarea"
      :value="modelValue"
      :name="name"
      :placeholder="placeholder"
      :rows="rows"
      :maxlength="maxlength"
      :required="required"
      :disabled="disabled"
      :aria-invalid="error ? 'true' : undefined"
      :aria-describedby="helper || error || maxlength ? messageId : undefined"
      @input="onInput"
    />
    <span :id="messageId" class="ui-field__message" :class="{ 'ui-field__message--error': error }">
      {{ error || helper || '' }}<span v-if="counter">{{ error || helper ? ' · ' : '' }}{{ counter }}</span>
    </span>
  </label>
</template>
