<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Archive, ArrowRight, BookOpenText, FolderHeart, Image, LockKeyhole, MapPin, Users } from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import EmptyState from '../components/EmptyState.vue'
import { UiBanner, UiButton, UiSkeleton } from '../components/ui'

const spaces = ref<any[]>([])
const loading = ref(true)
const pageError = ref('')

const personalSpaces = computed(() => spaces.value.filter(space => space.space_type === 'PERSONAL' && space.status === 'ACTIVE'))
const relationshipSpaces = computed(() => spaces.value.filter(space => space.space_type === 'RELATIONSHIP' && space.status === 'ACTIVE'))
const archivedSpaces = computed(() => spaces.value.filter(space => space.status !== 'ACTIVE'))
const totalMemories = computed(() => spaces.value.reduce((total, space) => total + Number(space.memoryCount || 0), 0))
const spaceStyle = (space: any) => ({
  '--space-accent': space.primary_color || 'var(--color-action-primary)',
  '--space-wash': space.background_color || 'var(--color-action-primary-soft)',
})

const load = async () => {
  loading.value = true
  pageError.value = ''
  try { spaces.value = (await http.get('/spaces')).data }
  catch (error) { pageError.value = errorMessage(error) }
  finally { loading.value = false }
}

onMounted(load)
</script>

<template>
  <main class="spaces-page">
    <header class="spaces-index-header">
      <div><span class="memory-kicker">PLACES WE BELONG</span><h1>记忆空间</h1><p>空间不是文件夹，而是记忆发生的边界：一个只属于你，其余属于你和某个重要的人。</p></div>
      <dl v-if="!loading && !pageError"><div><dt>{{ spaces.length }}</dt><dd>全部空间</dd></div><div><dt>{{ totalMemories }}</dt><dd>收录记忆</dd></div><div><dt>{{ archivedSpaces.length }}</dt><dd>封存历史</dd></div></dl>
    </header>

    <UiBanner v-if="pageError" tone="danger" title="空间暂时没有载入" :description="pageError"><template #actions><UiButton variant="ghost" size="sm" @click="load">重新载入</UiButton></template></UiBanner>

    <div v-if="loading" class="spaces-index-loading" aria-label="正在载入你的空间"><UiSkeleton height="250px" radius="var(--radius-lg)" /><UiSkeleton v-for="index in 2" :key="index" height="132px" radius="var(--radius-md)" /></div>

    <template v-else-if="!pageError">
      <section v-if="personalSpaces.length" class="spaces-personal-section" aria-labelledby="personal-spaces-title">
        <div class="spaces-section-label"><LockKeyhole :size="17" /><span><strong id="personal-spaces-title">只属于我的空间</strong><small>默认私密，只有你可以进入</small></span></div>
        <router-link v-for="space in personalSpaces" :key="space.id" :to="`/space/${space.id}`" class="spaces-personal-entry" :style="spaceStyle(space)" :aria-label="`进入${space.name}`">
          <span class="spaces-entry-mark"><BookOpenText :size="27" /></span>
          <span class="spaces-personal-copy"><small>PRIVATE MEMORY ARCHIVE</small><strong>{{ space.name }}</strong><span>{{ space.preset_name || '我的私人记忆主题' }}</span></span>
          <span class="spaces-stat-strip"><span><b>{{ Number(space.memoryCount || 0) }}</b>记忆</span><span><b>{{ Number(space.photoCount || 0) }}</b>照片</span><span><b>{{ Number(space.placeCount || 0) }}</b>地点</span></span>
          <ArrowRight :size="21" />
        </router-link>
      </section>

      <section class="spaces-shared-section" aria-labelledby="shared-spaces-title">
        <div class="relationship-section-heading"><div><span class="memory-kicker">SHARED WITH SOMEONE</span><h2 id="shared-spaces-title">共同生活的索引</h2><p>每一段已建立的关系只有一个共同空间，不会因分类标签增加而重复。</p></div></div>

        <div v-if="relationshipSpaces.length" class="spaces-shared-list">
          <router-link v-for="space in relationshipSpaces" :key="space.id" :to="`/space/${space.id}`" class="spaces-shared-entry" :style="spaceStyle(space)" :aria-label="`进入${space.name}`">
            <span class="spaces-entry-mark"><Users :size="22" /></span>
            <span class="spaces-shared-copy"><small>BETWEEN US</small><strong>{{ space.name }}</strong><span>{{ space.preset_name || '共同空间' }}</span></span>
            <span class="spaces-shared-stats"><span><BookOpenText :size="15" />{{ Number(space.memoryCount || 0) }} 段</span><span><Image :size="15" />{{ Number(space.photoCount || 0) }} 张</span><span><MapPin :size="15" />{{ Number(space.placeCount || 0) }} 处</span></span>
            <ArrowRight :size="19" />
          </router-link>
        </div>

        <EmptyState v-else title="还没有共同空间" text="共同空间会在关系邀请被接受后自动建立，不需要单独创建一个空空间。"><template #actions><UiButton variant="primary" @click="$router.push('/relationships')"><FolderHeart :size="16" />去建立一段关系</UiButton></template></EmptyState>
      </section>

      <details v-if="archivedSpaces.length" class="spaces-archive">
        <summary><span><Archive :size="18" /><strong>已封存空间</strong><small>历史仍在，只是不再增加新内容</small></span><span>{{ archivedSpaces.length }} 个</span></summary>
        <div><router-link v-for="space in archivedSpaces" :key="space.id" :to="`/space/${space.id}`"><span class="spaces-entry-mark"><Archive :size="18" /></span><span><strong>{{ space.name }}</strong><small>{{ Number(space.memoryCount || 0) }} 段历史记忆被保留</small></span><ArrowRight :size="16" /></router-link></div>
      </details>
    </template>
  </main>
</template>
