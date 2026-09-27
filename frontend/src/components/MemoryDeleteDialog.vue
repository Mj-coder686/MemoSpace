<script setup lang="ts">
import { ref, watch } from 'vue'
import { Trash2 } from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import { UiBanner, UiButton, UiDialog } from './ui'

const props = defineProps<{ memory: any | null }>()
const emit = defineEmits<{ close: []; deleted: [memory: any] }>()
const busy = ref(false)
const failure = ref('')

watch(() => props.memory, () => { failure.value = '' })

const remove = async () => {
  if (!props.memory || busy.value) return
  busy.value = true
  failure.value = ''
  try {
    await http.delete(`/memories/${props.memory.id}`)
    emit('deleted', props.memory)
  } catch (error) {
    failure.value = errorMessage(error)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <UiDialog
    :open="Boolean(memory)"
    title="删除这条记忆？"
    description="删除后会立即隐藏，并在回收保留期结束后彻底清理。"
    :busy="busy"
    @close="emit('close')"
  >
    <div v-if="memory" class="memory-delete-confirmation">
      <span class="memory-delete-confirmation__icon" aria-hidden="true"><Trash2 :size="22" /></span>
      <div>
        <strong>{{ memory.title }}</strong>
        <p>它会立即从记忆库、公开动态和所属空间中隐藏；保留期结束后，关联媒体也会一并彻底清理。</p>
      </div>
    </div>
    <UiBanner v-if="failure" tone="danger" title="没有删除成功" :description="failure" />
    <template #actions>
      <UiButton variant="secondary" :disabled="busy" @click="emit('close')">保留这条记忆</UiButton>
      <UiButton variant="danger" :loading="busy" loading-text="正在删除" @click="remove"><Trash2 :size="16" />确认删除</UiButton>
    </template>
  </UiDialog>
</template>
