<script setup lang="ts">
import { computed, toRef, useId } from 'vue'
import { X } from 'lucide-vue-next'
import { useOverlayFocus } from '../../composables/useOverlayFocus'
import UiIconButton from './UiIconButton.vue'

const props = withDefaults(defineProps<{ open: boolean; title: string; description?: string; width?: 'default' | 'wide'; busy?: boolean }>(), { width: 'default', busy: false })
const emit = defineEmits<{ close: [] }>()
const uid = useId()
const titleId = `ui-drawer-title-${uid}`
const descriptionId = `ui-drawer-description-${uid}`
const canClose = computed(() => !props.busy)
const requestClose = () => { if (canClose.value) emit('close') }
const { surfaceRef } = useOverlayFocus(toRef(props, 'open'), requestClose, () => canClose.value)
</script>

<template>
  <Teleport to="body">
    <Transition name="ui-overlay">
      <div v-if="open" class="ui-overlay-backdrop ui-drawer-backdrop" @click.self="requestClose">
        <aside ref="surfaceRef" class="ui-drawer" :class="{ 'ui-drawer--wide': width === 'wide' }" role="dialog" aria-modal="true" :aria-labelledby="titleId" :aria-describedby="description ? descriptionId : undefined" tabindex="-1">
          <header class="ui-overlay__header">
            <div class="ui-overlay__header-copy">
              <h2 :id="titleId" class="ui-overlay__title">{{ title }}</h2>
              <p v-if="description" :id="descriptionId" class="ui-overlay__description">{{ description }}</p>
            </div>
            <UiIconButton label="关闭" size="sm" variant="ghost" :disabled="busy" @click="requestClose"><X :size="19" /></UiIconButton>
          </header>
          <div class="ui-overlay__body"><slot /></div>
          <footer v-if="$slots.actions" class="ui-overlay__actions"><slot name="actions" /></footer>
        </aside>
      </div>
    </Transition>
  </Teleport>
</template>
