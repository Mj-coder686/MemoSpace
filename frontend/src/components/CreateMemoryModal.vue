<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Crosshair, Image, MapPin, Type, Video, X } from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import { coordinateLabel, getCurrentDevicePosition } from '../utils/geolocation'

const emit = defineEmits<{ close: [] }>()
const router = useRouter()
const spaces = ref<any[]>([])
const saving = ref(false)
const message = ref('')
const files = ref<File[]>([])
const locating = ref(false)
const locationMessage = ref('')
const uploadProgress = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const form = ref({
  title: '', content: '', memoryType: 'TEXT',
  occurredAt: new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16),
  location: '', latitude: null as number | null, longitude: null as number | null,
  visibility: 'PRIVATE', spaceIds: [] as number[]
})

const fileAccept = computed(() => form.value.memoryType === 'VIDEO' ? 'video/mp4,video/webm' : 'image/jpeg,image/png,image/webp,image/gif')
const fileSize = (value: number) => value >= 1024 * 1024 ? `${(value / 1024 / 1024).toFixed(1)} MB` : `${Math.ceil(value / 1024)} KB`

onMounted(async () => {
  try {
    const { data } = await http.get('/spaces')
    spaces.value = data.filter((item: any) => item.space_type === 'RELATIONSHIP' && item.status === 'ACTIVE')
  } catch (error) { message.value = errorMessage(error) }
})

const selectType = (type: string) => {
  if (form.value.memoryType !== type && files.value.length) {
    files.value = []
    if (fileInput.value) fileInput.value.value = ''
  }
  form.value.memoryType = type
  message.value = ''
}

const chooseFiles = (event: Event) => {
  const input = event.target as HTMLInputElement
  const selected = Array.from(input.files || [])
  const allowed = form.value.memoryType === 'VIDEO'
    ? new Set(['video/mp4', 'video/webm'])
    : new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif'])
  if (selected.some(file => !allowed.has(file.type))) {
    message.value = form.value.memoryType === 'VIDEO' ? '视频 Memory 请选择 MP4 或 WebM 文件。' : '照片 Memory 请选择图片文件。'
    input.value = ''
    return
  }
  if (selected.some(file => file.size > 30 * 1024 * 1024)) {
    message.value = '单个文件不能超过 30MB。'
    input.value = ''
    return
  }
  files.value = selected
  message.value = ''
}

const removeFile = (index: number) => {
  files.value.splice(index, 1)
  if (!files.value.length && fileInput.value) fileInput.value.value = ''
}
const clearCoordinates = () => {
  form.value.latitude = null
  form.value.longitude = null
  locationMessage.value = '定位坐标已清除，地点名称仍会保留。'
}

const locate = async () => {
  locating.value = true
  locationMessage.value = ''
  try {
    const position = await getCurrentDevicePosition()
    form.value.latitude = Number(position.latitude.toFixed(7))
    form.value.longitude = Number(position.longitude.toFixed(7))
    if (!form.value.location.trim()) form.value.location = coordinateLabel(position)
    locationMessage.value = `当前位置已记录，精度约 ${Math.round(position.accuracy)} 米；地点名称可以继续修改。`
  } catch (error) {
    locationMessage.value = error instanceof Error ? error.message : '暂时无法获取当前位置'
  } finally { locating.value = false }
}

const submit = async () => {
  message.value = ''
  if (!form.value.title.trim()) { message.value = '给这段记忆起个名字吧'; return }
  if (!form.value.occurredAt) { message.value = '请选择这段记忆发生的时间'; return }
  if ((form.value.memoryType === 'PHOTO' || form.value.memoryType === 'VIDEO') && !files.value.length) {
    message.value = form.value.memoryType === 'PHOTO' ? '照片 Memory 至少需要选择一张图片' : '视频 Memory 至少需要选择一个视频'
    return
  }
  if (form.value.memoryType === 'LOCATION' && !form.value.location.trim() && form.value.latitude === null) {
    message.value = '地点 Memory 请填写地点名称或获取当前位置'
    return
  }
  if (form.value.visibility === 'RELATIONSHIP' && !form.value.spaceIds.length) {
    message.value = '关系成员可见时，至少选择一个共同空间'
    return
  }
  saving.value = true
  try {
    const fileIds: number[] = []
    for (const [index, file] of files.value.entries()) {
      uploadProgress.value = `正在上传 ${index + 1} / ${files.value.length}`
      const payload = new FormData()
      payload.append('file', file)
      const { data } = await http.post('/files', payload)
      fileIds.push(data.id)
    }
    const { data } = await http.post('/memories', { ...form.value, occurredAt: form.value.occurredAt + ':00', fileIds })
    emit('close')
    router.push(`/memory/${data.id}`)
  } catch (error) { message.value = errorMessage(error) }
  finally { saving.value = false; uploadProgress.value = '' }
}
</script>

<template>
  <Teleport to="body">
    <div class="modal-backdrop" @mousedown.self="!saving && emit('close')">
      <section class="create-modal" role="dialog" aria-modal="true" aria-labelledby="create-memory-title">
        <header><div><span class="eyebrow">NEW MEMORY</span><h2 id="create-memory-title">把这一刻留下来</h2></div><button type="button" class="icon-button" :disabled="saving" aria-label="关闭" @click="emit('close')"><X /></button></header>
        <div class="memory-types">
          <button v-for="item in [{v:'TEXT',l:'文字',i:Type},{v:'PHOTO',l:'照片',i:Image},{v:'VIDEO',l:'视频',i:Video},{v:'LOCATION',l:'地点',i:MapPin}]"
                  :key="item.v" type="button" :class="{ active: form.memoryType === item.v }" @click="selectType(item.v)">
            <component :is="item.i" :size="19" />{{ item.l }}
          </button>
        </div>
        <label class="field"><span>标题</span><input v-model="form.title" maxlength="160" placeholder="今天发生了什么值得记住的事？" /></label>
        <label class="field"><span>故事</span><textarea v-model="form.content" rows="5" placeholder="写下当时的光线、声音、心情……"></textarea></label>
        <div class="field-row">
          <label class="field"><span>发生时间</span><input v-model="form.occurredAt" type="datetime-local" /></label>
          <label class="field"><span>地点</span><input v-model="form.location" placeholder="可选，可手动填写地点名称" /></label>
        </div>
        <div class="location-capture">
          <button type="button" class="button" :disabled="locating" @click="locate"><Crosshair :size="15" />{{ locating ? '正在定位…' : '获取当前位置' }}</button>
          <small>{{ locationMessage || '仅在你主动点击后读取一次位置，不会持续跟踪。' }}</small>
          <button v-if="form.latitude !== null" type="button" class="location-clear" @click="clearCoordinates">清除坐标</button>
        </div>
        <label v-if="form.memoryType === 'PHOTO' || form.memoryType === 'VIDEO'" class="drop-zone">
          <input ref="fileInput" type="file" multiple :accept="fileAccept" @change="chooseFiles" />
          <Image :size="24" /><b>{{ files.length ? `已选择 ${files.length} 个文件` : '把照片或视频放在这里' }}</b><span>单个文件最大 30MB，私密存储</span>
        </label>
        <div v-if="files.length" class="selected-file-list">
          <div v-for="(file,index) in files" :key="`${file.name}-${file.lastModified}`"><span><b>{{file.name}}</b><small>{{fileSize(file.size)}}</small></span><button type="button" :disabled="saving" :aria-label="`移除 ${file.name}`" @click="removeFile(index)"><X :size="14" /></button></div>
        </div>
        <div v-if="spaces.length" class="field"><span>同步到共同空间</span><div class="check-pills">
          <label v-for="space in spaces" :key="space.id"><input v-model="form.spaceIds" type="checkbox" :value="space.id" />{{ space.name }}</label>
        </div></div>
        <div class="field"><span>谁可以看见</span><div class="visibility-options">
          <label v-for="item in [{v:'PRIVATE',l:'仅自己'},{v:'RELATIONSHIP',l:'关系成员'},{v:'PUBLIC',l:'公开'}]" :key="item.v" :class="{disabled:item.v==='RELATIONSHIP'&&!spaces.length}">
            <input v-model="form.visibility" type="radio" :value="item.v" :disabled="item.v==='RELATIONSHIP'&&!spaces.length" />{{ item.l }}
          </label>
        </div></div>
        <p v-if="message" class="form-error" role="alert">{{ message }}</p>
        <footer><button type="button" class="button ghost" :disabled="saving" @click="emit('close')">先不记录</button><button type="button" class="button primary" :disabled="saving" @click="submit">{{ saving ? (uploadProgress || '正在收藏…') : '保存这段记忆' }}</button></footer>
      </section>
    </div>
  </Teleport>
</template>
