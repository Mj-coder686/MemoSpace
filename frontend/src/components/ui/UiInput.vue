<script setup lang="ts">
import { Eye, EyeOff } from 'lucide-vue-next'
import { computed, ref, useId, useSlots } from 'vue'

const props = withDefaults(defineProps<{
  modelValue?: string | number
  label: string
  type?: string
  name?: string
  placeholder?: string
  helper?: string
  error?: string
  autocomplete?: string
  required?: boolean
  disabled?: boolean
  readonly?: boolean
  revealable?: boolean
  inputmode?: 'none' | 'text' | 'decimal' | 'numeric' | 'tel' | 'search' | 'email' | 'url'
  maxlength?: number
  min?: string
}>(), {
  modelValue: '',
  type: 'text',
  required: false,
  disabled: false,
  readonly: false,
  revealable: false,
})

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const uid = useId()
const inputId = computed(() => `ui-input-${uid}`)
const messageId = computed(() => `ui-input-message-${uid}`)
const revealed = ref(false)
const slots = useSlots()
const hasTrailing = computed(() => props.revealable && props.type === 'password' || Boolean(slots.trailing))
const resolvedType = computed(() => props.revealable && props.type === 'password' && revealed.value ? 'text' : props.type)
</script>

<template>
  <div class="ui-field">
    <label class="ui-field__label" :for="inputId">
      {{ label }}<span v-if="required" class="ui-field__required" aria-hidden="true">*</span>
    </label>
    <span class="ui-field__control-wrap">
      <input
        :id="inputId"
        class="ui-field__control"
        :class="{ 'ui-field__control--trailing': hasTrailing }"
        :value="modelValue"
        :type="resolvedType"
        :name="name"
        :placeholder="placeholder"
        :autocomplete="autocomplete"
        :required="required"
        :disabled="disabled"
        :readonly="readonly"
        :inputmode="inputmode"
        :maxlength="maxlength"
        :min="min"
        :aria-invalid="error ? 'true' : undefined"
        :aria-describedby="helper || error ? messageId : undefined"
        @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
      />
      <button
        v-if="revealable && type === 'password'"
        class="ui-field__trailing"
        type="button"
        :aria-label="revealed ? '隐藏密码' : '显示密码'"
        :aria-pressed="revealed"
        @click="revealed = !revealed"
      ><EyeOff v-if="revealed" :size="18" /><Eye v-else :size="18" /></button>
      <span v-else-if="$slots.trailing" class="ui-field__trailing"><slot name="trailing" /></span>
    </span>
    <span :id="messageId" class="ui-field__message" :class="{ 'ui-field__message--error': error }">
      {{ error || helper || '' }}
    </span>
  </div>
</template>
