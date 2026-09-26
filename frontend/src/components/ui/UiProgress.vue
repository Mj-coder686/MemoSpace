<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{ value?: number; label: string; showValue?: boolean }>(), {
  value: 0,
  showValue: true,
})
const normalized = computed(() => Math.min(100, Math.max(0, props.value)))
</script>

<template>
  <div class="ui-progress">
    <div class="ui-progress__meta">
      <span>{{ label }}</span><span v-if="showValue">{{ Math.round(normalized) }}%</span>
    </div>
    <div class="ui-progress__track" role="progressbar" :aria-label="label" aria-valuemin="0" aria-valuemax="100" :aria-valuenow="normalized">
      <div class="ui-progress__value" :style="{ transform: `scaleX(${normalized / 100})` }" />
    </div>
  </div>
</template>
