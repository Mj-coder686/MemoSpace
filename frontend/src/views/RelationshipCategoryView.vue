<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft, ArrowRight, Camera, Coffee, Eye, EyeOff, Handshake, Heart,
  Home, Leaf, MapPin, Settings2, Sparkles, Star, Users
} from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import UserAvatar from '../components/UserAvatar.vue'

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
  coffee: Coffee, camera: Camera, star: Star, leaf: Leaf
}

const visible = () => category.value?.is_visible === true || Number(category.value?.is_visible) === 1 || String(category.value?.is_visible) === 'true'
const icon = () => iconComponents[category.value?.icon || 'users'] || Users
const themeStyle = () => ({
  '--category-accent': category.value?.primary_color || '#80616a',
  '--category-background': category.value?.background_color || '#f5ebe7'
})

const load = async () => {
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

onMounted(load)
</script>

<template>
  <button class="category-back" @click="router.push('/relationships')"><ArrowLeft :size="16" /> 返回关系分类</button>

  <p v-if="pageError" class="category-message error" role="alert">{{ pageError }}</p>
  <p v-if="pageMessage" class="category-message success" role="status">{{ pageMessage }}</p>
  <div v-if="loading" class="category-loading">正在打开这个分类…</div>

  <template v-else-if="category">
    <header class="category-hero" :style="themeStyle()">
      <div class="hero-icon"><component :is="icon()" :size="30" /></div>
      <div class="hero-copy">
        <span>{{ category.category_type === 'CUSTOM' ? 'CUSTOM CIRCLE' : 'RELATIONSHIP CIRCLE' }}</span>
        <h1>{{ category.name }}</h1>
        <p>{{ people.length }} 位重要的人 · 每一段关系只对应双方唯一的共同空间</p>
      </div>
      <div class="hero-actions">
        <button @click="router.push('/relationships/manage')"><Settings2 :size="16" /> 管理关系</button>
        <button :disabled="busy" @click="toggleVisibility">
          <component :is="visible() ? EyeOff : Eye" :size="16" /> {{ visible() ? '隐藏分类' : '恢复显示' }}
        </button>
      </div>
    </header>

    <aside v-if="!visible()" class="hidden-reminder">
      <EyeOff :size="18" />
      <div><b>这个分类当前已隐藏</b><p>隐藏只改变入口是否显示，不会解除关系，也不会删除共同空间或任何记忆。</p></div>
    </aside>

    <div class="section-heading people-heading">
      <div><span class="eyebrow">PEOPLE IN THIS CIRCLE</span><h2>{{ category.name }}里的重要的人</h2></div>
      <button class="button" @click="router.push('/relationships')">邀请新的人</button>
    </div>

    <div v-if="people.length" class="category-people-grid">
      <article v-for="person in people" :key="person.relationship_id" class="person-space-card">
        <div class="person-line">
          <router-link :to="`/user/${person.user_id}`" :aria-label="`查看${person.nickname}的主页`"><UserAvatar class="person-avatar" :src="person.avatar" :name="person.nickname||person.username" /></router-link>
          <div class="person-info">
            <router-link :to="`/user/${person.user_id}`"><h3>{{ person.nickname }}</h3></router-link>
            <p>@{{ person.username }}<span v-if="person.location"> · <MapPin :size="12" />{{ person.location }}</span></p>
          </div>
        </div>
        <p v-if="person.bio" class="person-bio">{{ person.bio }}</p>
        <router-link v-if="person.space_id" :to="`/space/${person.space_id}`" class="shared-space-link">
          <span><small>双方唯一的共同空间</small><b>{{ person.space_name }}</b><em>{{ Number(person.memory_count || 0) }} 段共同记忆</em></span>
          <ArrowRight :size="20" />
        </router-link>
        <div v-else class="shared-space-link unavailable"><span><small>共同空间</small><b>空间正在准备中</b></span></div>
      </article>
    </div>

    <section v-else class="empty-category">
      <span><component :is="icon()" :size="25" /></span>
      <h2>这个分类还没有人</h2>
      <p>搜索一位用户并发出「{{ category.name }}」邀请；对方接受后，就会出现在这里。</p>
      <button class="button primary" @click="router.push('/relationships')">去发出邀请</button>
    </section>
  </template>
</template>

<style scoped>
.category-back { margin-bottom: 20px; padding: 8px 3px; display: inline-flex; align-items: center; gap: 6px; color: #514c50; background: transparent; border: 0; font-size: 13px; }
.category-back:hover { color: #2f2c2f; }
.category-message { margin: 0 0 18px; padding: 14px 18px; border-radius: 15px; font-size: 13px; }
.category-message.success { color: #3c5143; background: #edf4ed; border: 1px solid #c9d9cb; }
.category-message.error { color: #7b303c; background: #faecee; border: 1px solid #e8c3c9; }
.category-loading { padding: 60px 24px; color: #5d585c; text-align: center; background: rgba(255,255,255,.6); border: 1px solid #d4cbc5; border-radius: 25px; }
.category-hero { min-height: 220px; padding: 35px 38px; display: grid; grid-template-columns: auto 1fr auto; gap: 22px; align-items: center; color: #302d30; background: linear-gradient(130deg, var(--category-background), color-mix(in srgb, var(--category-accent) 17%, #fffaf5)); border: 1px solid color-mix(in srgb, var(--category-accent) 30%, #cfc5be); border-radius: 31px; box-shadow: 0 18px 44px rgba(56,47,48,.1); }
.hero-icon { width: 68px; height: 68px; display: grid; place-items: center; color: #fff; background: var(--category-accent); border-radius: 22px; box-shadow: 0 12px 27px color-mix(in srgb, var(--category-accent) 32%, transparent); }
.hero-copy > span { color: #655d60; font-size: 10px; font-weight: 600; letter-spacing: .2em; }
.hero-copy h1 { margin: 8px 0; color: #2e2a2d; font-size: clamp(32px, 5vw, 49px); }
.hero-copy p { margin: 0; color: #554f53; font-size: 13px; }
.hero-actions { display: grid; gap: 8px; }
.hero-actions button { min-height: 40px; padding: 0 14px; display: flex; align-items: center; justify-content: center; gap: 7px; color: #3f393e; background: rgba(255,255,255,.66); border: 1px solid rgba(65,55,59,.22); border-radius: 13px; }
.hero-actions button:hover { background: #fff; }
.hero-actions button:disabled { opacity: .5; }
.hidden-reminder { margin-top: 15px; padding: 15px 18px; display: flex; align-items: flex-start; gap: 11px; color: #554a43; background: #f5eee8; border: 1px solid #d9c9bd; border-radius: 16px; }
.hidden-reminder b { color: #443a35; font-size: 13px; }
.hidden-reminder p { margin: 3px 0 0; color: #625750; font-size: 12px; line-height: 1.6; }
.people-heading { margin-top: 38px; }
.category-people-grid { display: grid; grid-template-columns: repeat(2, minmax(0,1fr)); gap: 17px; }
.person-space-card { padding: 23px; color: #343034; background: rgba(255,253,249,.88); border: 1px solid #d8cfc9; border-radius: 23px; box-shadow: 0 12px 31px rgba(52,46,46,.07); }
.person-line { display: flex; align-items: center; gap: 13px; }
.person-avatar { width: 51px; height: 51px; display: grid; place-items: center; flex: none; color: #fff; background: linear-gradient(135deg, #a4737e, #635b70); border: 3px solid #fff; border-radius: 50%; box-shadow: 0 7px 18px rgba(73,55,64,.18); font: 600 18px 'Noto Serif SC', serif; }
.person-info { min-width: 0; }
.person-info h3 { margin: 0 0 4px; color: #302c30; font: 600 19px 'Noto Serif SC', serif; }
.person-info p { margin: 0; color: #5e575c; font-size: 11px; }
.person-info p span { display: inline-flex; align-items: center; gap: 2px; }
.person-bio { min-height: 38px; margin: 15px 0 12px; color: #554f53; font-size: 12px; line-height: 1.65; }
.shared-space-link { min-height: 82px; margin-top: 16px; padding: 14px 16px; display: flex; align-items: center; justify-content: space-between; gap: 12px; color: #42393f; background: #f1e9e6; border: 1px solid #d8c8c5; border-radius: 16px; }
.shared-space-link:hover { background: #ebe0dd; transform: translateY(-1px); }
.shared-space-link span { min-width: 0; }
.shared-space-link small,.shared-space-link b,.shared-space-link em { display: block; }
.shared-space-link small { margin-bottom: 3px; color: #665d62; font-size: 9px; letter-spacing: .08em; }
.shared-space-link b { overflow: hidden; color: #372f34; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.shared-space-link em { margin-top: 3px; color: #5f575c; font-size: 10px; font-style: normal; }
.shared-space-link.unavailable { opacity: .72; }
.empty-category { padding: 60px 24px; text-align: center; color: #565055; background: rgba(255,253,249,.68); border: 1px dashed #bdaea7; border-radius: 25px; }
.empty-category > span { width: 55px; height: 55px; margin: 0 auto 16px; display: grid; place-items: center; color: #fff; background: #78616b; border-radius: 18px; }
.empty-category h2 { margin-bottom: 8px; color: #332f32; font-size: 21px; }
.empty-category p { max-width: 520px; margin: 0 auto 20px; color: #5b5559; line-height: 1.7; }
@media (max-width: 760px) {
  .category-hero { padding: 27px 22px; grid-template-columns: auto 1fr; gap: 15px; }
  .hero-icon { width: 56px; height: 56px; border-radius: 18px; }
  .hero-actions { grid-column: 1 / -1; grid-template-columns: 1fr 1fr; }
  .category-people-grid { grid-template-columns: 1fr; }
  .people-heading { align-items: flex-start; }
}
</style>
