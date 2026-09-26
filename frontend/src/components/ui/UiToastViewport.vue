<script setup lang="ts">
import { X } from 'lucide-vue-next'
import { dismissToast, useToast, type ToastItem } from '../../composables/useToast'
import UiButton from './UiButton.vue'
import UiIconButton from './UiIconButton.vue'

const { toasts } = useToast()
const runAction = async (toast: ToastItem) => {
  await toast.onAction?.()
  dismissToast(toast.id)
}
</script>

<template>
  <Teleport to="body">
    <div class="ui-toast-viewport" aria-live="polite" aria-atomic="false">
      <TransitionGroup name="ui-toast-list">
        <article v-for="toast in toasts" :key="toast.id" class="ui-toast" :class="`ui-toast--${toast.tone}`">
          <div class="ui-toast__copy">
            <strong class="ui-toast__title">{{ toast.title }}</strong>
            <span v-if="toast.message" class="ui-toast__message">{{ toast.message }}</span>
          </div>
          <UiIconButton label="关闭提示" size="sm" variant="ghost" @click="dismissToast(toast.id)"><X :size="17" /></UiIconButton>
          <div v-if="toast.actionLabel" class="ui-toast__actions">
            <UiButton variant="link" size="sm" @click="runAction(toast)">{{ toast.actionLabel }}</UiButton>
          </div>
        </article>
      </TransitionGroup>
    </div>
  </Teleport>
</template>
