<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import http, { errorMessage } from '../api/http'
import { UiBanner, UiButton, UiCheckbox, UiDialog, UiRadio } from './ui'

type Visibility = 'PRIVATE' | 'RELATIONSHIP' | 'PUBLIC'

const props = defineProps<{ memory: any | null }>()
const emit = defineEmits<{ close: []; updated: [memory: any] }>()
const visibility = ref<Visibility>('PRIVATE')
const spaces = ref<any[]>([])
const selectedSpaceIds = ref<number[]>([])
const loadingSpaces = ref(false)
const busy = ref(false)
const failure = ref('')

const options = [
  { value: 'PRIVATE' as const, label: '仅自己', description: '从公共动态和其他人的空间视图中立即隐藏' },
  { value: 'RELATIONSHIP' as const, label: '关系成员', description: '只有你选择的共同空间成员可以看见' },
  { value: 'PUBLIC' as const, label: '公开', description: '重新出现在公共动态和你的公开主页中' },
]
const invalid = computed(() => visibility.value === 'RELATIONSHIP' && selectedSpaceIds.value.length === 0)

const load = async () => {
  if (!props.memory) return
  visibility.value = ['PRIVATE', 'RELATIONSHIP', 'PUBLIC'].includes(props.memory.visibility)
    ? props.memory.visibility as Visibility : 'PRIVATE'
  failure.value = ''
  loadingSpaces.value = true
  try {
    const [spacesResponse, detailResponse] = await Promise.all([
      http.get('/spaces'),
      Array.isArray(props.memory.spaces) ? Promise.resolve({ data: props.memory }) : http.get(`/memories/${props.memory.id}`),
    ])
    spaces.value = spacesResponse.data.filter((space: any) => space.space_type === 'RELATIONSHIP' && space.status === 'ACTIVE')
    selectedSpaceIds.value = (detailResponse.data.spaces || [])
      .filter((space: any) => space.space_type === 'RELATIONSHIP')
      .map((space: any) => Number(space.id))
  } catch (error) {
    failure.value = `共同空间暂时无法读取：${errorMessage(error)}`
  } finally {
    loadingSpaces.value = false
  }
}

watch(() => props.memory, (value) => { if (value) void load() })

const toggleSpace = (id: number, selected: boolean) => {
  selectedSpaceIds.value = selected
    ? [...new Set([...selectedSpaceIds.value, id])]
    : selectedSpaceIds.value.filter((value) => value !== id)
}

const save = async () => {
  if (!props.memory || invalid.value || busy.value) return
  busy.value = true
  failure.value = ''
  try {
    const { data } = await http.put(`/memories/${props.memory.id}`, {
      visibility: visibility.value,
      spaceIds: visibility.value === 'RELATIONSHIP' ? selectedSpaceIds.value : undefined,
    })
    emit('updated', data)
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
    title="调整谁可以看见"
    description="修改后会立即同步到记忆库、动态、个人主页和共同空间。"
    :busy="busy"
    @close="emit('close')"
  >
    <fieldset class="memory-visibility-editor" :disabled="busy">
      <legend class="sr-only">选择可见范围</legend>
      <UiRadio
        v-for="item in options"
        :key="item.value"
        v-model="visibility"
        :value="item.value"
        name="updated-memory-visibility"
        :label="item.label"
        :description="item.description"
        :disabled="item.value === 'RELATIONSHIP' && !loadingSpaces && !spaces.length"
      />
    </fieldset>

    <section v-if="visibility === 'RELATIONSHIP'" class="memory-visibility-spaces" aria-labelledby="visibility-space-title">
      <h3 id="visibility-space-title">选择共同空间</h3>
      <p v-if="loadingSpaces">正在读取共同空间……</p>
      <div v-else-if="spaces.length">
        <UiCheckbox
          v-for="space in spaces"
          :key="space.id"
          :model-value="selectedSpaceIds.includes(Number(space.id))"
          :label="space.name"
          description="该空间的成员可以查看"
          :disabled="busy"
          @update:model-value="toggleSpace(Number(space.id), $event)"
        />
      </div>
      <p v-else>当前没有可用的共同空间，请先建立关系。</p>
      <p v-if="invalid" class="create-memory-inline-error" role="alert">至少选择一个共同空间。</p>
    </section>

    <UiBanner v-if="failure" tone="danger" title="可见范围没有保存" :description="failure" />
    <template #actions>
      <UiButton variant="secondary" :disabled="busy" @click="emit('close')">取消</UiButton>
      <UiButton variant="primary" :loading="busy" loading-text="正在保存" :disabled="invalid" @click="save">保存可见范围</UiButton>
    </template>
  </UiDialog>
</template>
