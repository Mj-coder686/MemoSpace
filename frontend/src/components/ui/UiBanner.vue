<script setup lang="ts">
import { AlertCircle, CircleCheck, Info, TriangleAlert } from 'lucide-vue-next'
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  tone?: 'info' | 'success' | 'warning' | 'danger'
  title: string
  description?: string
}>(), { tone: 'info' })

const icon = computed(() => ({ info: Info, success: CircleCheck, warning: TriangleAlert, danger: AlertCircle })[props.tone])
</script>

<template>
  <section class="ui-banner" :class="`ui-banner--${tone}`" :role="tone === 'danger' ? 'alert' : 'status'">
    <span class="ui-banner__icon" aria-hidden="true"><component :is="icon" :size="20" /></span>
    <div class="ui-banner__copy">
      <strong class="ui-banner__title">{{ title }}</strong>
      <span v-if="description" class="ui-banner__description">{{ description }}</span>
    </div>
    <div v-if="$slots.actions" class="ui-banner__actions"><slot name="actions" /></div>
  </section>
</template>
