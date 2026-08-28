<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Image, MapPin, Type, Video, X } from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'

const emit = defineEmits<{ close: [] }>()
const router = useRouter()
const spaces = ref<any[]>([])
const saving = ref(false)
const message = ref('')
const files = ref<File[]>([])
const form = ref({
  title: '', content: '', memoryType: 'TEXT',
  occurredAt: new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16),
  location: '', visibility: 'PRIVATE', spaceIds: [] as number[]
})

onMounted(async () => {
  const { data } = await http.get('/spaces')
  spaces.value = data.filter((item: any) => item.space_type === 'RELATIONSHIP' && item.status === 'ACTIVE')
})

const submit = async () => {
  message.value = ''
  if (!form.value.title.trim()) { message.value = '给这段记忆起个名字吧'; return }
  saving.value = true
  try {
    const fileIds: number[] = []
    for (const file of files.value) {
      const payload = new FormData()
      payload.append('file', file)
      const { data } = await http.post('/files', payload)
      fileIds.push(data.id)
    }
    const { data } = await http.post('/memories', { ...form.value, occurredAt: form.value.occurredAt + ':00', fileIds })
    emit('close')
    router.push(`/memory/${data.id}`)
  } catch (error) { message.value = errorMessage(error) }
  finally { saving.value = false }
}
</script>

<template>
  <Teleport to="body">
    <div class="modal-backdrop" @mousedown.self="emit('close')">
      <section class="create-modal">
        <header><div><span class="eyebrow">NEW MEMORY</span><h2>把这一刻留下来</h2></div><button class="icon-button" @click="emit('close')"><X /></button></header>
        <div class="memory-types">
          <button v-for="item in [{v:'TEXT',l:'文字',i:Type},{v:'PHOTO',l:'照片',i:Image},{v:'VIDEO',l:'视频',i:Video},{v:'LOCATION',l:'地点',i:MapPin}]"
                  :key="item.v" :class="{ active: form.memoryType === item.v }" @click="form.memoryType = item.v">
            <component :is="item.i" :size="19" />{{ item.l }}
          </button>
        </div>
        <label class="field"><span>标题</span><input v-model="form.title" maxlength="160" placeholder="今天发生了什么值得记住的事？" /></label>
        <label class="field"><span>故事</span><textarea v-model="form.content" rows="5" placeholder="写下当时的光线、声音、心情……"></textarea></label>
        <div class="field-row">
          <label class="field"><span>发生时间</span><input v-model="form.occurredAt" type="datetime-local" /></label>
          <label class="field"><span>地点</span><input v-model="form.location" placeholder="可选" /></label>
        </div>
        <label v-if="form.memoryType === 'PHOTO' || form.memoryType === 'VIDEO'" class="drop-zone">
          <input type="file" multiple accept="image/jpeg,image/png,image/webp,image/gif,video/mp4,video/webm" @change="files = Array.from(($event.target as HTMLInputElement).files || [])" />
          <Image :size="24" /><b>{{ files.length ? `已选择 ${files.length} 个文件` : '把照片或视频放在这里' }}</b><span>单个文件最大 30MB，私密存储</span>
        </label>
        <div v-if="spaces.length" class="field"><span>同步到共同空间</span><div class="check-pills">
          <label v-for="space in spaces" :key="space.id"><input v-model="form.spaceIds" type="checkbox" :value="space.id" />{{ space.name }}</label>
        </div></div>
        <div class="field"><span>谁可以看见</span><div class="visibility-options">
          <label v-for="item in [{v:'PRIVATE',l:'仅自己'},{v:'RELATIONSHIP',l:'关系成员'},{v:'PUBLIC',l:'公开'}]" :key="item.v">
            <input v-model="form.visibility" type="radio" :value="item.v" />{{ item.l }}
          </label>
        </div></div>
        <p v-if="message" class="form-error">{{ message }}</p>
        <footer><button class="button ghost" @click="emit('close')">先不记录</button><button class="button primary" :disabled="saving" @click="submit">{{ saving ? '正在收藏…' : '保存这段记忆' }}</button></footer>
      </section>
    </div>
  </Teleport>
</template>
