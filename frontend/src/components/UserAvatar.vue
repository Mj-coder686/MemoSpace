<script setup lang="ts">
import { computed } from 'vue'
import PrivateMedia from './PrivateMedia.vue'

const props = defineProps<{ src?: string | null; name?: string; alt?: string }>()
const fileId = computed(() => {
  const match = props.src?.match(/^\/api\/files\/(\d+)\/content$/)
  return match ? Number(match[1]) : 0
})
</script>

<template>
  <span class="user-avatar">
    <PrivateMedia v-if="fileId" :file-id="fileId" mime-type="image/*" :alt="alt || name || '用户头像'" preview />
    <img v-else-if="src" :src="src" :alt="alt || name || '用户头像'" />
    <span v-else>{{ name?.slice(0,1) || '拾' }}</span>
  </span>
</template>

<style scoped>
.user-avatar{display:grid;place-items:center;overflow:hidden}.user-avatar>img,.user-avatar :deep(.private-media),.user-avatar :deep(img){width:100%;height:100%;min-height:0;border:0;border-radius:inherit;object-fit:cover}.user-avatar :deep(.media-skeleton){font-size:0}.user-avatar :deep(.media-skeleton)::after{content:'拾';font-size:inherit}
</style>
