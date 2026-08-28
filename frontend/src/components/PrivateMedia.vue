<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import http from '../api/http'

const props = defineProps<{ fileId: number; mimeType: string; alt?: string; preview?: boolean }>()
const source = ref('')
const failed = ref(false)
const loading = ref(false)
let request: AbortController | undefined

const revokeSource = () => {
  if (source.value) URL.revokeObjectURL(source.value)
  source.value = ''
}

const load = async () => {
  request?.abort()
  revokeSource()
  failed.value = false
  loading.value = true
  request = new AbortController()
  try {
    const { data } = await http.get(`/files/${props.fileId}/content`, {
      responseType: 'blob',
      signal: request.signal,
    })
    if (!(data instanceof Blob) || (!data.type.startsWith('image/') && !data.type.startsWith('video/'))) {
      throw new Error('媒体响应格式无效')
    }
    source.value = URL.createObjectURL(data)
  } catch (error: any) {
    if (error?.code !== 'ERR_CANCELED') failed.value = true
  } finally {
    loading.value = false
  }
}

const mediaFailed = () => {
  failed.value = true
  revokeSource()
}

watch(() => props.fileId, load, { immediate: true })
onBeforeUnmount(() => {
  request?.abort()
  revokeSource()
})
</script>

<template>
  <div class="private-media" :aria-busy="loading">
    <video v-if="source && mimeType.startsWith('video/')" :src="source" :controls="!preview" :muted="preview" preload="metadata" @error="mediaFailed" />
    <img v-else-if="source" :src="source" :alt="alt || '记忆照片'" loading="lazy" @error="mediaFailed" />
    <div v-else class="media-skeleton" role="status">{{ failed ? '媒体读取失败，请稍后重试' : '正在读取媒体…' }}</div>
  </div>
</template>
