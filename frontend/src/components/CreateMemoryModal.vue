<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Crosshair, FileImage, Image, MapPin, Type, Video, X } from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import { coordinateLabel, getCurrentDevicePosition } from '../utils/geolocation'
import { UiBanner, UiButton, UiCheckbox, UiDialog, UiInput, UiProgress, UiRadio, UiTextarea } from './ui'

type MemoryType = 'TEXT' | 'PHOTO' | 'VIDEO' | 'LOCATION'
type Visibility = 'PRIVATE' | 'RELATIONSHIP' | 'PUBLIC'

const props = withDefaults(defineProps<{ initialType?: MemoryType }>(), { initialType: 'TEXT' })
const emit = defineEmits<{ close: [] }>()
const router = useRouter()
const spaces = ref<any[]>([])
const spacesLoading = ref(true)
const saving = ref(false)
const submitted = ref(false)
const message = ref('')
const files = ref<File[]>([])
const previews = ref<{ file: File; url: string }[]>([])
const locating = ref(false)
const locationMessage = ref('')
const uploadLabel = ref('')
const uploadPercent = ref(0)
const fileInput = ref<HTMLInputElement | null>(null)
const form = ref({
  title: '', content: '', memoryType: props.initialType,
  occurredAt: new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16),
  location: '', latitude: null as number | null, longitude: null as number | null,
  visibility: 'PRIVATE' as Visibility, spaceIds: [] as number[],
})

const typeOptions = [
  { value: 'TEXT' as const, label: '文字', description: '用文字保存此刻', icon: Type },
  { value: 'PHOTO' as const, label: '照片', description: '一张或多张照片', icon: Image },
  { value: 'VIDEO' as const, label: '视频', description: 'MP4 或 WebM', icon: Video },
  { value: 'LOCATION' as const, label: '地点', description: '记录去过的地方', icon: MapPin },
]
const visibilityOptions = [
  { value: 'PRIVATE' as const, label: '仅自己', description: '只在你的私人空间里可见' },
  { value: 'RELATIONSHIP' as const, label: '关系成员', description: '仅所选共同空间的成员可见' },
  { value: 'PUBLIC' as const, label: '公开', description: '关注你的人可能在动态中看到' },
]

const fileAccept = computed(() => form.value.memoryType === 'VIDEO' ? 'video/mp4,video/webm' : 'image/jpeg,image/png,image/webp,image/gif')
const titleError = computed(() => submitted.value && !form.value.title.trim() ? '给这段记忆起个名字吧' : '')
const occurredError = computed(() => submitted.value && !form.value.occurredAt ? '请选择这段记忆发生的时间' : '')
const mediaError = computed(() => submitted.value && ['PHOTO', 'VIDEO'].includes(form.value.memoryType) && !files.value.length
  ? (form.value.memoryType === 'PHOTO' ? '照片记忆至少需要选择一张图片' : '视频记忆至少需要选择一个视频') : '')
const locationError = computed(() => submitted.value && form.value.memoryType === 'LOCATION' && !form.value.location.trim() && form.value.latitude === null ? '请填写地点名称或主动获取当前位置' : '')
const relationshipError = computed(() => submitted.value && form.value.visibility === 'RELATIONSHIP' && !form.value.spaceIds.length ? '关系成员可见时，至少选择一个共同空间' : '')
const invalid = computed(() => Boolean(titleError.value || occurredError.value || mediaError.value || locationError.value || relationshipError.value))

const revokePreviews = () => {
  previews.value.forEach((item) => URL.revokeObjectURL(item.url))
  previews.value = []
}
const refreshPreviews = () => {
  revokePreviews()
  previews.value = files.value.map((file) => ({ file, url: URL.createObjectURL(file) }))
}
const fileSize = (value: number) => value >= 1024 * 1024 ? `${(value / 1024 / 1024).toFixed(1)} MB` : `${Math.ceil(value / 1024)} KB`

onMounted(async () => {
  try {
    const { data } = await http.get('/spaces')
    spaces.value = data.filter((item: any) => item.space_type === 'RELATIONSHIP' && item.status === 'ACTIVE')
  } catch (error) {
    message.value = `共同空间暂时无法读取：${errorMessage(error)}`
  } finally {
    spacesLoading.value = false
  }
})

onBeforeUnmount(revokePreviews)

const selectType = (type: MemoryType) => {
  if (saving.value || form.value.memoryType === type) return
  if (files.value.length) {
    files.value = []
    revokePreviews()
    if (fileInput.value) fileInput.value.value = ''
  }
  form.value.memoryType = type
  submitted.value = false
  message.value = ''
}

const chooseFiles = (event: Event) => {
  const input = event.target as HTMLInputElement
  const selected = Array.from(input.files || [])
  const allowed = form.value.memoryType === 'VIDEO'
    ? new Set(['video/mp4', 'video/webm'])
    : new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif'])
  if (selected.some((file) => !allowed.has(file.type))) {
    message.value = form.value.memoryType === 'VIDEO' ? '视频请选择 MP4 或 WebM 文件。' : '照片请选择 JPEG、PNG、WebP 或 GIF。'
    input.value = ''
    return
  }
  if (selected.some((file) => file.size > 30 * 1024 * 1024)) {
    message.value = '单个文件不能超过 30MB。'
    input.value = ''
    return
  }
  files.value = selected
  message.value = ''
  refreshPreviews()
}

const removeFile = (index: number) => {
  if (saving.value) return
  files.value.splice(index, 1)
  refreshPreviews()
  if (!files.value.length && fileInput.value) fileInput.value.value = ''
}

const toggleSpace = (id: number, selected: boolean) => {
  form.value.spaceIds = selected ? [...new Set([...form.value.spaceIds, id])] : form.value.spaceIds.filter((value) => value !== id)
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
    locationMessage.value = `当前位置已记录，精度约 ${Math.round(position.accuracy)} 米；地点名称仍可修改。`
  } catch (error) {
    locationMessage.value = `${error instanceof Error ? error.message : '暂时无法获取当前位置'}，你仍可以手动填写地点。`
  } finally {
    locating.value = false
  }
}

const submit = async () => {
  submitted.value = true
  message.value = ''
  if (invalid.value || saving.value) return

  saving.value = true
  uploadPercent.value = 0
  try {
    const fileIds: number[] = []
    const totalBytes = files.value.reduce((sum, file) => sum + file.size, 0)
    let uploadedBefore = 0
    for (const [index, file] of files.value.entries()) {
      uploadLabel.value = `正在上传 ${index + 1} / ${files.value.length}：${file.name}`
      const payload = new FormData()
      payload.append('file', file)
      const { data } = await http.post('/files', payload, {
        onUploadProgress: (progress) => {
          if (totalBytes) uploadPercent.value = ((uploadedBefore + Math.min(progress.loaded, file.size)) / totalBytes) * 100
        },
      })
      fileIds.push(data.id)
      uploadedBefore += file.size
      if (totalBytes) uploadPercent.value = (uploadedBefore / totalBytes) * 100
    }
    uploadLabel.value = files.value.length ? '媒体上传完成，正在保存记忆' : ''
    const { data } = await http.post('/memories', { ...form.value, occurredAt: `${form.value.occurredAt}:00`, fileIds })
    emit('close')
    await router.push(`/memory/${data.id}`)
  } catch (error) {
    message.value = errorMessage(error)
  } finally {
    saving.value = false
    uploadLabel.value = ''
    uploadPercent.value = 0
  }
}
</script>

<template>
  <UiDialog
    :open="true"
    title="把这一刻留下来"
    description="从记录本身开始，再决定它属于哪里、谁可以看见。"
    width="wide"
    compact-fullscreen
    :busy="saving"
    :close-on-backdrop="!saving"
    :close-on-escape="!saving"
    @close="emit('close')"
  >
    <form id="create-memory-form" class="create-memory-form" novalidate @submit.prevent="submit">
      <fieldset class="create-memory-section create-memory-types" :disabled="saving">
        <legend><span>01</span>选择记录方式</legend>
        <div>
          <button
            v-for="item in typeOptions"
            :key="item.value"
            type="button"
            :class="{ 'is-selected': form.memoryType === item.value }"
            :aria-pressed="form.memoryType === item.value"
            @click="selectType(item.value)"
          >
            <component :is="item.icon" :size="20" aria-hidden="true" />
            <span><strong>{{ item.label }}</strong><small>{{ item.description }}</small></span>
          </button>
        </div>
      </fieldset>

      <section class="create-memory-section">
        <h3><span>02</span>写下这段记忆</h3>
        <UiInput v-model="form.title" label="标题" name="memory-title" :maxlength="160" placeholder="今天发生了什么值得记住的事？" :error="titleError" :disabled="saving" required />
        <UiTextarea v-model="form.content" label="故事" name="memory-content" :rows="5" :maxlength="5000" placeholder="写下当时的光线、声音和心情……" :disabled="saving" />
      </section>

      <section v-if="form.memoryType === 'PHOTO' || form.memoryType === 'VIDEO'" class="create-memory-section">
        <h3><span>03</span>添加{{ form.memoryType === 'VIDEO' ? '视频' : '照片' }}</h3>
        <label class="create-memory-dropzone" :class="{ 'has-error': mediaError }">
          <input ref="fileInput" type="file" multiple :accept="fileAccept" :disabled="saving" @change="chooseFiles" />
          <FileImage :size="25" aria-hidden="true" />
          <span><strong>{{ files.length ? `重新选择文件（当前 ${files.length} 个）` : '选择照片或视频' }}</strong><small>单个文件最大 30MB，上传后按可见性规则私密存储</small></span>
        </label>
        <p v-if="mediaError" class="create-memory-inline-error" role="alert">{{ mediaError }}</p>
        <div v-if="previews.length" class="create-memory-previews">
          <figure v-for="(item, index) in previews" :key="`${item.file.name}-${item.file.lastModified}`">
            <video v-if="item.file.type.startsWith('video/')" :src="item.url" muted preload="metadata" />
            <img v-else :src="item.url" :alt="`待上传预览：${item.file.name}`" />
            <figcaption><span><strong>{{ item.file.name }}</strong><small>{{ fileSize(item.file.size) }}</small></span><button type="button" :disabled="saving" :aria-label="`移除 ${item.file.name}`" @click="removeFile(index)"><X :size="15" /></button></figcaption>
          </figure>
        </div>
      </section>

      <section class="create-memory-section">
        <h3><span>{{ form.memoryType === 'PHOTO' || form.memoryType === 'VIDEO' ? '04' : '03' }}</span>时间与地点</h3>
        <div class="create-memory-field-row">
          <UiInput v-model="form.occurredAt" label="发生时间" name="occurred-at" type="datetime-local" :error="occurredError" :disabled="saving" required />
          <UiInput v-model="form.location" label="地点" name="memory-location" placeholder="可手动填写地点名称" :error="locationError" :disabled="saving" />
        </div>
        <div class="create-memory-location">
          <UiButton type="button" size="sm" variant="tonal" :loading="locating" loading-text="正在定位" :disabled="saving" @click="locate"><Crosshair :size="16" />获取当前位置</UiButton>
          <span>{{ locationMessage || '仅在你主动点击后读取一次位置，不会持续跟踪。' }}</span>
          <UiButton v-if="form.latitude !== null" type="button" size="sm" variant="link" :disabled="saving" @click="clearCoordinates">清除坐标</UiButton>
        </div>
      </section>

      <section class="create-memory-section">
        <h3><span>{{ form.memoryType === 'PHOTO' || form.memoryType === 'VIDEO' ? '05' : '04' }}</span>属于哪里</h3>
        <p v-if="spacesLoading" class="create-memory-supporting">正在读取共同空间……</p>
        <div v-else-if="spaces.length" class="create-memory-space-list">
          <UiCheckbox
            v-for="space in spaces"
            :key="space.id"
            :model-value="form.spaceIds.includes(space.id)"
            :label="space.name"
            description="同步到这段共同生活"
            :disabled="saving"
            @update:model-value="toggleSpace(space.id, $event)"
          />
        </div>
        <p v-else class="create-memory-supporting">当前没有可同步的共同空间，这条记忆仍会保存在你的私人空间。</p>
      </section>

      <fieldset class="create-memory-section create-memory-visibility" :disabled="saving">
        <legend><span>{{ form.memoryType === 'PHOTO' || form.memoryType === 'VIDEO' ? '06' : '05' }}</span>谁可以看见</legend>
        <div>
          <UiRadio
            v-for="item in visibilityOptions"
            :key="item.value"
            v-model="form.visibility"
            :value="item.value"
            name="memory-visibility"
            :label="item.label"
            :description="item.description"
            :disabled="item.value === 'RELATIONSHIP' && !spaces.length"
          />
        </div>
        <p v-if="relationshipError" class="create-memory-inline-error" role="alert">{{ relationshipError }}</p>
      </fieldset>

      <UiBanner v-if="message" tone="danger" title="这段记忆还没有保存" :description="message" />
      <UiProgress v-if="saving && files.length" :value="uploadPercent" :label="uploadLabel || '正在上传媒体'" />
    </form>

    <template #actions>
      <UiButton type="button" variant="ghost" :disabled="saving" @click="emit('close')">先不记录</UiButton>
      <UiButton type="submit" form="create-memory-form" variant="primary" :loading="saving" loading-text="正在保存">保存这段记忆</UiButton>
    </template>
  </UiDialog>
</template>
