<script setup lang="ts">
import { computed, toRef, useId } from 'vue'
import { X } from 'lucide-vue-next'
import { useOverlayFocus } from '../../composables/useOverlayFocus'
import UiIconButton from './UiIconButton.vue'

const props = withDefaults(defineProps<{
  open: boolean
  title: string
  description?: string
  width?: 'default' | 'wide'
  busy?: boolean
  closeOnBackdrop?: boolean
  closeOnEscape?: boolean
  hideClose?: boolean
  compactFullscreen?: boolean
}>(), {
  width: 'default',
  busy: false,
  closeOnBackdrop: true,
  closeOnEscape: true,
  hideClose: false,
  compactFullscreen: false,
})
const emit = defineEmits<{ close: [] }>()
const uid = useId()
const titleId = `ui-dialog-title-${uid}`
const descriptionId = `ui-dialog-description-${uid}`
const canClose = computed(() => !props.busy)
const requestClose = () => { if (canClose.value) emit('close') }
const { surfaceRef } = useOverlayFocus(toRef(props, 'open'), requestClose, () => props.closeOnEscape && canClose.value)
</script>

<template>
  <Teleport to="body">
    <Transition name="ui-overlay">
      <div v-if="open" class="ui-overlay-backdrop" :class="{ 'ui-overlay-backdrop--fullscreen': compactFullscreen }" @click.self="closeOnBackdrop && requestClose()">
        <section ref="surfaceRef" class="ui-dialog" :class="{ 'ui-dialog--wide': width === 'wide', 'ui-dialog--compact-fullscreen': compactFullscreen }" role="dialog" aria-modal="true" :aria-labelledby="titleId" :aria-describedby="description ? descriptionId : undefined" tabindex="-1">
          <header class="ui-overlay__header">
            <div class="ui-overlay__header-copy">
              <h2 :id="titleId" class="ui-overlay__title">{{ title }}</h2>
              <p v-if="description" :id="descriptionId" class="ui-overlay__description">{{ description }}</p>
            </div>
            <UiIconButton v-if="!hideClose" label="关闭" size="sm" variant="ghost" :disabled="busy" @click="requestClose"><X :size="19" /></UiIconButton>
          </header>
          <div class="ui-overlay__body"><slot /></div>
          <footer v-if="$slots.actions" class="ui-overlay__actions"><slot name="actions" /></footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>
