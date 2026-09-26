<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft, ArrowRight, Camera, Coffee, Eye, EyeOff, Handshake, Heart,
  Home, Leaf, MapPin, Settings2, Sparkles, Star, UserPlus, Users,
} from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import UserAvatar from '../components/UserAvatar.vue'
import EmptyState from '../components/EmptyState.vue'
import { UiBanner, UiButton, UiSkeleton } from '../components/ui'

const route = useRoute()
const router = useRouter()
const category = ref<any | null>(null)
const people = ref<any[]>([])
const loading = ref(true)
const busy = ref(false)
const pageError = ref('')
const pageMessage = ref('')
const iconComponents: Record<string, any> = {
  heart: Heart, handshake: Handshake, sparkles: Sparkles, home: Home, users: Users,
  coffee: Coffee, camera: Camera, star: Star, leaf: Leaf,
}

const visible = () => category.value?.is_visible === true || Number(category.value?.is_visible) === 1 || String(category.value?.is_visible) === 'true'
const icon = () => iconComponents[category.value?.icon || 'users'] || Users
const themeStyle = () => ({
  '--category-accent': category.value?.primary_color || 'var(--color-action-primary)',
  '--category-background': category.value?.background_color || 'var(--color-action-primary-soft)',
})

const load = async () => {
  loading.value = true
  pageError.value = ''
  try {
    const { data } = await http.get(`/relationship-categories/${route.params.id}`)
    category.value = data
    people.value = data.people || []
  } catch (error) { pageError.value = errorMessage(error) }
  finally { loading.value = false }
}

const toggleVisibility = async () => {
  if (!category.value) return
  busy.value = true
  pageError.value = ''
  pageMessage.value = ''
  try {
    const next = !visible()
    await http.put(`/relationship-categories/${category.value.id}/visibility`, { visible: next })
    category.value.is_visible = next
    pageMessage.value = next
      ? '分类已恢复显示，原有共同空间仍在原处。'
      : '分类已隐藏，但这里的关系、空间和所有记忆仍被完整保留。'
  } catch (error) { pageError.value = errorMessage(error) }
  finally { busy.value = false }
}

const inviteToCategory = () => router.push({ path: '/relationships', query: { inviteCategory: category.value.id } })
onMounted(load)
</script>

<template>
  <div class="relationship-category-page">
    <router-link class="relationship-back-link" to="/relationships"><ArrowLeft :size="16" />返回关系分类</router-link>

    <div v-if="loading" class="relationship-category-detail-loading" aria-label="正在打开关系分类"><UiSkeleton height="220px" radius="var(--radius-lg)" /><UiSkeleton v-for="index in 3" :key="index" height="92px" radius="var(--radius-md)" /></div>
    <EmptyState v-else-if="pageError && !category" kind="error" title="这个分类暂时无法打开" :text="pageError" action-label="重新加载" @action="load" />

    <template v-else-if="category">
      <header class="relationship-category-hero" :style="themeStyle()">
        <span class="relationship-category-hero__icon"><component :is="icon()" :size="30" /></span>
        <div><span class="memory-kicker">{{ category.category_type === 'CUSTOM' ? 'CUSTOM CIRCLE' : 'RELATIONSHIP CIRCLE' }}</span><h1>{{ category.name }}</h1><p>{{ people.length }} 位重要的人 · 每一段关系只对应双方唯一的共同空间</p></div>
        <div><UiButton variant="secondary" @click="router.push('/relationships/manage')"><Settings2 :size="16" />管理关系</UiButton><UiButton variant="ghost" :loading="busy" :disabled="busy" @click="toggleVisibility"><component :is="visible() ? EyeOff : Eye" :size="16" />{{ visible() ? '隐藏分类' : '恢复显示' }}</UiButton></div>
      </header>

      <UiBanner v-if="!visible()" tone="warning" title="这个分类当前已隐藏" description="隐藏只改变入口是否显示，不会解除关系，也不会删除共同空间或任何记忆。" />
      <UiBanner v-if="pageMessage" tone="success" title="分类显示已更新" :description="pageMessage" />
      <UiBanner v-if="pageError" tone="danger" title="操作没有完成" :description="pageError" />

      <section class="relationship-category-people" aria-labelledby="category-people-title">
        <div class="relationship-section-heading"><div><span class="memory-kicker">PEOPLE IN THIS CIRCLE</span><h2 id="category-people-title">{{ category.name }}里的重要的人</h2></div><UiButton variant="tonal" @click="inviteToCategory"><UserPlus :size="16" />邀请一个人</UiButton></div>

        <div v-if="people.length" class="relationship-person-list">
          <article v-for="person in people" :key="person.relationship_id">
            <router-link :to="`/user/${person.user_id}`" :aria-label="`查看${person.nickname}的主页`"><UserAvatar :src="person.avatar" :name="person.nickname || person.username" /></router-link>
            <div class="relationship-person-copy"><router-link :to="`/user/${person.user_id}`"><strong>{{ person.nickname || person.username }}</strong></router-link><span>@{{ person.username }}<template v-if="person.location"><MapPin :size="12" />{{ person.location }}</template></span><p v-if="person.bio">{{ person.bio }}</p></div>
            <router-link v-if="person.space_id" :to="`/space/${person.space_id}`" class="relationship-space-entry"><span><small>双方唯一的共同空间</small><strong>{{ person.space_name }}</strong><span>{{ Number(person.memory_count || 0) }} 段共同记忆</span></span><ArrowRight :size="19" /></router-link>
            <span v-else class="relationship-space-entry is-unavailable"><span><small>共同空间</small><strong>空间正在准备中</strong></span></span>
          </article>
        </div>

        <EmptyState v-else title="这个分类还没有人" :text="`搜索一位用户并发出「${category.name}」邀请；对方接受后，就会出现在这里。`" action-label="邀请一个人" @action="inviteToCategory" />
      </section>
    </template>
  </div>
</template>
