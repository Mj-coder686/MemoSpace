<script setup lang="ts">
import dayjs from 'dayjs'
import { BookOpenText, ChevronRight } from 'lucide-vue-next'
import PrivateMedia from '../PrivateMedia.vue'

defineProps<{ memory: any }>()
</script>

<template>
  <article class="home-memory-row">
    <router-link :to="`/memory/${memory.id}`">
      <span class="home-memory-row__visual">
        <PrivateMedia
          v-if="memory.cover_file_id"
          :file-id="memory.cover_file_id"
          :mime-type="memory.cover_mime_type"
          :alt="memory.title"
          preview
        />
        <BookOpenText v-else :size="21" aria-hidden="true" />
      </span>
      <span class="home-memory-row__copy">
        <strong>{{ memory.title }}</strong>
        <span>{{ dayjs(memory.occurred_at).format('M月D日') }}<template v-if="memory.location"> · {{ memory.location }}</template></span>
      </span>
      <ChevronRight :size="18" aria-hidden="true" />
    </router-link>
  </article>
</template>
