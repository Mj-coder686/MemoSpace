<script setup lang="ts">
import { computed, ref, toRef, useId } from 'vue'
import { X } from 'lucide-vue-next'
import { useOverlayFocus } from '../../composables/useOverlayFocus'
import UiIconButton from './UiIconButton.vue'

const props = withDefaults(defineProps<{ open: boolean; title: string; description?: string; busy?: boolean; dismissible?: boolean }>(), { busy: false, dismissible: true })
const emit = defineEmits<{ close: [] }>()
const uid = useId()
const titleId = `ui-sheet-title-${uid}`
const descriptionId = `ui-sheet-description-${uid}`
const startY = ref<number | null>(null)
const canClose = computed(() => props.dismissible && !props.busy)
const requestClose = () => { if (canClose.value) emit('close') }
const { surfaceRef } = useOverlayFocus(toRef(props, 'open'), requestClose, () => canClose.value)
const onPointerDown = (event: PointerEvent) => {
  startY.value = event.clientY
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
}
const onPointerUp = (event: PointerEvent) => {
  if (startY.value != null && event.clientY - startY.value > 80) requestClose()
  startY.value = null
}
</script>

<template>
  <Teleport to="body">
    <Transition name="ui-overlay">
      <div v-if="open" class="ui-overlay-backdrop ui-sheet-backdrop" @click.self="requestClose">
        <section ref="surfaceRef" class="ui-bottom-sheet" role="dialog" aria-modal="true" :aria-labelledby="titleId" :aria-describedby="description ? descriptionId : undefined" tabindex="-1">
          <div class="ui-bottom-sheet__handle" aria-hidden="true" @pointerdown="onPointerDown" @pointerup="onPointerUp" />
          <header class="ui-overlay__header">
            <div class="ui-overlay__header-copy">
              <h2 :id="titleId" class="ui-overlay__title">{{ title }}</h2>
              <p v-if="description" :id="descriptionId" class="ui-overlay__description">{{ description }}</p>
            </div>
            <UiIconButton v-if="dismissible" label="关闭" size="sm" variant="ghost" :disabled="busy" @click="requestClose"><X :size="19" /></UiIconButton>
          </header>
          <div class="ui-overlay__body"><slot /></div>
          <footer v-if="$slots.actions" class="ui-overlay__actions"><slot name="actions" /></footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>
