<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowDown, ArrowRight, ArrowUp, Camera, Coffee, Eye, EyeOff, Handshake,
  Heart, Home, Leaf, Plus, Search, Sparkles, Star, UserPlus, Users
} from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import { useAuthStore } from '../stores/auth'

type Category = {
  id: number
  name: string
  icon?: string
  category_key?: string
  category_type?: string
  is_visible: boolean | number
  relationship_count?: number
  primary_color?: string
  background_color?: string
  preset_name?: string
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const categories = ref<Category[]>([])
const loading = ref(true)
const pageMessage = ref('')
const pageError = ref('')
const categoryBusy = ref<number | null>(null)
const creating = ref(false)
const createForm = ref({ name: '', icon: 'users' })
const people = ref<any[]>([])
const searchQuery = ref('')
const searching = ref(false)
const selectedPerson = ref<any | null>(null)
const selectedCategoryId = ref<number | null>(null)
const invitationMessage = ref('想和你一起收藏我们的故事。')
const inviting = ref(false)

const iconComponents: Record<string, any> = {
  heart: Heart,
  handshake: Handshake,
  sparkles: Sparkles,
  home: Home,
  users: Users,
  coffee: Coffee,
  camera: Camera,
  star: Star,
  leaf: Leaf
}
const iconChoices = [
  { value: 'users', label: '伙伴', icon: Users },
  { value: 'heart', label: '心意', icon: Heart },
  { value: 'handshake', label: '默契', icon: Handshake },
  { value: 'sparkles', label: '闪光', icon: Sparkles },
  { value: 'home', label: '家', icon: Home },
  { value: 'coffee', label: '闲谈', icon: Coffee },
  { value: 'camera', label: '同好', icon: Camera },
  { value: 'star', label: '珍贵', icon: Star },
  { value: 'leaf', label: '成长', icon: Leaf }
]

const isVisible = (category: Category) => category.is_visible === true || Number(category.is_visible) === 1 || String(category.is_visible) === 'true'
const visibleCategories = computed(() => categories.value.filter(isVisible))
const hiddenCategories = computed(() => categories.value.filter(category => !isVisible(category)))
const totalRelationships = computed(() => visibleCategories.value.reduce((sum, item) => sum + Number(item.relationship_count || 0), 0))
const categoryIcon = (name?: string) => iconComponents[name || 'users'] || Users
const categoryStyle = (category: Category) => ({
  '--category-accent': category.primary_color || '#80616a',
  '--category-background': category.background_color || '#f5ebe7'
})

const loadCategories = async () => {
  const { data } = await http.get('/relationship-categories', { params: { includeHidden: true } })
  categories.value = data
  const selectedStillVisible = visibleCategories.value.some(item => Number(item.id) === Number(selectedCategoryId.value))
  if (!selectedStillVisible) selectedCategoryId.value = visibleCategories.value.length ? Number(visibleCategories.value[0].id) : null
}

const refresh = async () => {
  pageError.value = ''
  try { await loadCategories() } catch (error) { pageError.value = errorMessage(error) }
  finally { loading.value = false }
}

const setVisibility = async (category: Category, visible: boolean) => {
  categoryBusy.value = Number(category.id)
  pageMessage.value = ''
  pageError.value = ''
  try {
    await http.put(`/relationship-categories/${category.id}/visibility`, { visible })
    await loadCategories()
    pageMessage.value = visible
      ? `「${category.name}」已经重新显示，原有关系和共同空间仍然在这里。`
      : `「${category.name}」已隐藏；关系、共同空间和记忆均未删除。`
  } catch (error) { pageError.value = errorMessage(error) }
  finally { categoryBusy.value = null }
}

const moveCategory = async (category: Category, direction: -1 | 1) => {
  const ordered = [...visibleCategories.value]
  const index = ordered.findIndex(item => Number(item.id) === Number(category.id))
  const target = index + direction
  if (index < 0 || target < 0 || target >= ordered.length) return
  ;[ordered[index], ordered[target]] = [ordered[target], ordered[index]]
  categoryBusy.value = Number(category.id)
  pageError.value = ''
  try {
    const ids = [...ordered, ...hiddenCategories.value].map(item => Number(item.id))
    const { data } = await http.put('/relationship-categories/reorder', { categoryIds: ids })
    categories.value = data
  } catch (error) { pageError.value = errorMessage(error) }
  finally { categoryBusy.value = null }
}

const createCategory = async () => {
  if (!createForm.value.name.trim()) return
  creating.value = true
  pageError.value = ''
  pageMessage.value = ''
  try {
    const { data } = await http.post('/relationship-categories', {
      name: createForm.value.name.trim(),
      icon: createForm.value.icon
    })
    createForm.value = { name: '', icon: 'users' }
    await loadCategories()
    pageMessage.value = `已创建「${data.name}」，现在可以把重要的人放进这个分类。`
  } catch (error) { pageError.value = errorMessage(error) }
  finally { creating.value = false }
}

const searchPeople = async () => {
  if (!searchQuery.value.trim()) return
  searching.value = true
  pageError.value = ''
  try {
    const { data } = await http.get('/users/search', { params: { q: searchQuery.value.trim() } })
    people.value = data.filter((person: any) => Number(person.id) !== Number(auth.user?.id))
  } catch (error) { pageError.value = errorMessage(error) }
  finally { searching.value = false }
}

const choosePerson = (person: any) => {
  selectedPerson.value = person
  people.value = []
  searchQuery.value = person.nickname || person.username
}

const sendInvitation = async () => {
  if (!selectedPerson.value || !selectedCategoryId.value) return
  inviting.value = true
  pageError.value = ''
  pageMessage.value = ''
  try {
    const { data } = await http.post('/relationships/invitations', {
      receiverId: Number(selectedPerson.value.id),
      categoryId: Number(selectedCategoryId.value),
      message: invitationMessage.value.trim()
    })
    pageMessage.value = `已向 ${selectedPerson.value.nickname || selectedPerson.value.username} 发出「${data.categoryName || '关系'}」邀请。对方接受后会创建或关联双方唯一的共同空间。`
    selectedPerson.value = null
    searchQuery.value = ''
    people.value = []
  } catch (error) { pageError.value = errorMessage(error) }
  finally { inviting.value = false }
}

onMounted(async () => {
  await refresh()
  const inviteUser = Number(route.query.inviteUser)
  if (inviteUser) {
    selectedPerson.value = { id: inviteUser, nickname: String(route.query.inviteName || '这位用户') }
    searchQuery.value = selectedPerson.value.nickname
  }
})
</script>

<template>
  <header class="page-heading relationship-heading">
    <div>
      <span class="eyebrow">PEOPLE & RELATIONSHIPS</span>
      <h1>关系分类</h1>
      <p>用适合你的方式整理重要的人；一个人可以属于多个分类，但双方始终共用同一个空间。</p>
    </div>
    <div class="heading-actions">
      <button class="button" @click="router.push('/spaces')">查看全部空间</button>
      <button class="button primary" @click="router.push('/relationships/manage')">关系管理</button>
    </div>
  </header>

  <p v-if="pageMessage" class="relationship-notice success" role="status">{{ pageMessage }}</p>
  <p v-if="pageError" class="relationship-notice error" role="alert">{{ pageError }}</p>

  <section class="relationship-summary" aria-label="关系分类概况">
    <div><b>{{ visibleCategories.length }}</b><span>正在显示的分类</span></div>
    <div><b>{{ totalRelationships }}</b><span>分类标签关联数</span></div>
    <div><b>{{ hiddenCategories.length }}</b><span>已隐藏，数据仍保留</span></div>
  </section>

  <section aria-labelledby="visible-category-title">
    <div class="section-heading category-section-heading">
      <div><span class="eyebrow">YOUR CIRCLES</span><h2 id="visible-category-title">我的分类</h2></div>
      <span class="section-help">使用箭头调整展示顺序</span>
    </div>

    <div v-if="loading" class="relationship-loading">正在整理你的关系分类…</div>
    <div v-else-if="visibleCategories.length" class="category-grid">
      <article
        v-for="(category, index) in visibleCategories"
        :key="category.id"
        class="category-card"
        :style="categoryStyle(category)"
      >
        <button class="category-main" :aria-label="`打开${category.name}分类`" @click="router.push(`/relationships/category/${category.id}`)">
          <span class="category-icon"><component :is="categoryIcon(category.icon)" :size="24" /></span>
          <span class="category-copy">
            <small>{{ category.category_type === 'CUSTOM' ? '自定义分类' : '系统分类' }}</small>
            <strong>{{ category.name }}</strong>
            <span>{{ Number(category.relationship_count || 0) }} 位重要的人</span>
          </span>
          <ArrowRight class="category-arrow" :size="20" />
        </button>
        <div class="category-controls" aria-label="分类操作">
          <button :disabled="index === 0 || categoryBusy === Number(category.id)" :aria-label="`上移${category.name}`" @click="moveCategory(category, -1)"><ArrowUp :size="16" /></button>
          <button :disabled="index === visibleCategories.length - 1 || categoryBusy === Number(category.id)" :aria-label="`下移${category.name}`" @click="moveCategory(category, 1)"><ArrowDown :size="16" /></button>
          <button :disabled="categoryBusy === Number(category.id)" :aria-label="`隐藏${category.name}`" @click="setVisibility(category, false)"><EyeOff :size="16" /> 隐藏</button>
        </div>
      </article>
    </div>
    <div v-else-if="!loading" class="relationship-empty">所有分类都已隐藏。你可以在下方随时恢复，任何关系和记忆都没有被删除。</div>
  </section>

  <section v-if="hiddenCategories.length" aria-labelledby="hidden-category-title">
    <div class="section-heading compact-heading">
      <div><span class="eyebrow">HIDDEN, NOT DELETED</span><h2 id="hidden-category-title">已隐藏分类</h2></div>
    </div>
    <div class="hidden-category-list">
      <article v-for="category in hiddenCategories" :key="category.id">
        <span class="category-icon quiet"><component :is="categoryIcon(category.icon)" :size="18" /></span>
        <div><strong>{{ category.name }}</strong><small>{{ Number(category.relationship_count || 0) }} 段关系仍被完整保留</small></div>
        <button class="button" :disabled="categoryBusy === Number(category.id)" @click="setVisibility(category, true)"><Eye :size="15" /> 恢复显示</button>
      </article>
    </div>
  </section>

  <div class="relationship-workbench">
    <section class="relationship-panel" aria-labelledby="invite-title">
      <span class="eyebrow">START A SHARED STORY</span>
      <h2 id="invite-title">绑定一段关系</h2>
      <p>先找到对方，再为这次邀请选择一个关系分类。</p>

      <form class="people-search" @submit.prevent="searchPeople">
        <Search :size="18" />
        <input v-model="searchQuery" aria-label="搜索用户" placeholder="输入昵称或用户名" @input="selectedPerson = null" />
        <button type="submit" :disabled="searching">{{ searching ? '搜索中' : '搜索' }}</button>
      </form>
      <div v-if="people.length" class="people-results" role="listbox" aria-label="用户搜索结果">
        <button v-for="person in people" :key="person.id" type="button" @click="choosePerson(person)">
          <span class="person-avatar">{{ (person.nickname || person.username)?.slice(0, 1) }}</span>
          <span><b>{{ person.nickname }}</b><small>@{{ person.username }} · {{ person.location || '未填写所在地' }}</small></span>
          <UserPlus :size="17" />
        </button>
      </div>
      <div v-if="selectedPerson" class="selected-person">
        <span class="person-avatar">{{ (selectedPerson.nickname || selectedPerson.username)?.slice(0, 1) }}</span>
        <span><small>准备邀请</small><b>{{ selectedPerson.nickname || selectedPerson.username }}</b></span>
      </div>

      <label class="relationship-field">
        <span>选择关系分类</span>
        <select v-model="selectedCategoryId" aria-label="选择关系分类">
          <option v-for="category in visibleCategories" :key="category.id" :value="Number(category.id)">{{ category.name }}</option>
        </select>
      </label>
      <label class="relationship-field">
        <span>邀请留言</span>
        <textarea v-model="invitationMessage" aria-label="邀请留言" maxlength="200" rows="3"></textarea>
      </label>
      <button class="button primary full-button" :disabled="!selectedPerson || !selectedCategoryId || inviting" @click="sendInvitation">
        <UserPlus :size="17" /> {{ inviting ? '正在发送…' : '发送绑定邀请' }}
      </button>
      <p class="privacy-note">对方接受后才会建立关系。若双方已有其他分类关系，将关联原有共同空间，不会重复创建。</p>
    </section>

    <section class="relationship-panel" aria-labelledby="custom-title">
      <span class="eyebrow">MAKE IT YOURS</span>
      <h2 id="custom-title">创建自定义分类</h2>
      <p>除了恋人、死党、闺蜜和家人，也可以为你们独有的称呼留一个位置。</p>
      <label class="relationship-field">
        <span>分类名称</span>
        <input v-model="createForm.name" aria-label="自定义分类名称" maxlength="40" placeholder="例如：旅行搭子、老同学" @keyup.enter="createCategory" />
      </label>
      <fieldset class="icon-picker">
        <legend>选择一个图标</legend>
        <button
          v-for="choice in iconChoices"
          :key="choice.value"
          type="button"
          :class="{ active: createForm.icon === choice.value }"
          :aria-label="choice.label"
          :aria-pressed="createForm.icon === choice.value"
          @click="createForm.icon = choice.value"
        ><component :is="choice.icon" :size="18" /><span>{{ choice.label }}</span></button>
      </fieldset>
      <button class="button primary full-button" :disabled="!createForm.name.trim() || creating" @click="createCategory">
        <Plus :size="17" /> {{ creating ? '正在创建…' : '创建分类' }}
      </button>
      <p class="privacy-note">隐藏分类只影响展示；无论系统分类还是自定义分类，都不会因此删除关系、空间或记忆。</p>
    </section>
  </div>
</template>

<style scoped>
.relationship-heading { align-items: center; }
.relationship-heading p { max-width: 690px; line-height: 1.75; }
.heading-actions { display: flex; flex-wrap: wrap; gap: 10px; flex: none; }
.relationship-notice { margin: -6px 0 22px; padding: 14px 18px; border-radius: 15px; font-size: 13px; line-height: 1.6; }
.relationship-notice.success { color: #3c5143; background: #edf4ed; border: 1px solid #c9d9cb; }
.relationship-notice.error { color: #7b303c; background: #faecee; border: 1px solid #e8c3c9; }
.relationship-summary { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; padding: 18px; color: #39363a; background: #f3ebe6; border: 1px solid #ddcec5; border-radius: 22px; }
.relationship-summary div { padding: 4px 18px; border-right: 1px solid #d8c6bc; }
.relationship-summary div:last-child { border: 0; }
.relationship-summary b { display: block; margin-bottom: 3px; color: #4f3e43; font: 600 25px 'Noto Serif SC', serif; }
.relationship-summary span { color: #625b5e; font-size: 12px; }
.category-section-heading { margin-top: 36px; }
.section-help { color: #5f5b60; font-size: 12px; }
.category-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 17px; }
.category-card { overflow: hidden; color: #302d30; background: linear-gradient(135deg, var(--category-background), color-mix(in srgb, var(--category-accent) 15%, #fffaf5)); border: 1px solid color-mix(in srgb, var(--category-accent) 28%, #cfc5be); border-radius: 25px; box-shadow: 0 13px 32px rgba(56, 47, 48, .09); }
.category-main { width: 100%; min-height: 135px; padding: 24px; display: grid; grid-template-columns: auto 1fr auto; gap: 16px; align-items: center; text-align: left; color: inherit; background: transparent; border: 0; }
.category-main:hover { background: rgba(255, 255, 255, .28); }
.category-icon { width: 48px; height: 48px; display: grid; place-items: center; flex: none; color: #fff; background: var(--category-accent); border-radius: 16px; box-shadow: 0 8px 20px color-mix(in srgb, var(--category-accent) 30%, transparent); }
.category-copy { min-width: 0; display: grid; gap: 4px; }
.category-copy small { color: #655d60; font-size: 10px; letter-spacing: .08em; }
.category-copy strong { color: #2e2a2d; font: 600 23px 'Noto Serif SC', serif; }
.category-copy span { color: #574f53; font-size: 12px; }
.category-arrow { color: #494147; }
.category-controls { padding: 10px 13px; display: flex; justify-content: flex-end; gap: 6px; border-top: 1px solid color-mix(in srgb, var(--category-accent) 18%, #d8cec8); background: rgba(255, 255, 255, .38); }
.category-controls button { min-width: 35px; height: 34px; padding: 0 10px; display: inline-flex; align-items: center; justify-content: center; gap: 5px; color: #494248; background: rgba(255, 255, 255, .72); border: 1px solid rgba(70, 60, 64, .2); border-radius: 10px; font-size: 11px; }
.category-controls button:disabled { opacity: .42; cursor: default; }
.relationship-loading,.relationship-empty { padding: 42px 24px; color: #5d585c; text-align: center; background: rgba(255,255,255,.58); border: 1px dashed #bcb0aa; border-radius: 22px; }
.compact-heading { margin-top: 32px; margin-bottom: 14px; }
.hidden-category-list { display: grid; gap: 9px; }
.hidden-category-list article { padding: 13px 15px; display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 13px; color: #403c40; background: rgba(246, 242, 237, .88); border: 1px solid #d6cec8; border-radius: 17px; }
.category-icon.quiet { width: 38px; height: 38px; color: #585159; background: #e0d9d4; box-shadow: none; border-radius: 12px; }
.hidden-category-list strong,.hidden-category-list small { display: block; }
.hidden-category-list small { margin-top: 3px; color: #696267; font-size: 11px; }
.hidden-category-list .button { min-height: 36px; display: inline-flex; align-items: center; gap: 6px; }
.relationship-workbench { margin-top: 38px; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px; align-items: start; }
.relationship-panel { padding: 27px; color: #393539; background: rgba(255, 253, 249, .9); border: 1px solid #d9d0ca; border-radius: 25px; box-shadow: 0 16px 38px rgba(55, 49, 48, .08); }
.relationship-panel h2 { margin-bottom: 8px; color: #302d30; font-size: 23px; }
.relationship-panel > p { color: #5f595e; font-size: 13px; line-height: 1.7; }
.people-search { height: 46px; margin: 20px 0 0; padding-left: 13px; display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 8px; color: #5f565c; background: #fff; border: 1px solid #cfc4bd; border-radius: 14px; }
.people-search:focus-within { border-color: #806b74; box-shadow: 0 0 0 3px rgba(128, 107, 116, .12); }
.people-search input { min-width: 0; height: 100%; color: #312d30; background: transparent; border: 0; outline: 0; }
.people-search button { align-self: stretch; padding: 0 16px; color: #fff; background: #61525a; border: 0; border-radius: 0 13px 13px 0; }
.people-results { position: relative; z-index: 3; max-height: 235px; overflow: auto; margin-top: 6px; padding: 6px; background: #fff; border: 1px solid #d5cbc5; border-radius: 14px; box-shadow: 0 15px 35px rgba(48, 42, 43, .14); }
.people-results button { width: 100%; padding: 10px; display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 10px; text-align: left; color: #383337; background: transparent; border: 0; border-radius: 10px; }
.people-results button:hover { background: #f4eeea; }
.people-results b,.people-results small { display: block; }
.people-results small { margin-top: 2px; color: #625c60; font-size: 11px; }
.person-avatar { width: 38px; height: 38px; display: grid; place-items: center; flex: none; color: #fff; background: linear-gradient(135deg, #a4737e, #635b70); border-radius: 50%; font-weight: 600; }
.selected-person { margin-top: 10px; padding: 11px 13px; display: flex; align-items: center; gap: 11px; color: #373237; background: #f2e9e6; border: 1px solid #d9c8c4; border-radius: 14px; }
.selected-person small,.selected-person b { display: block; }
.selected-person small { color: #675e63; font-size: 10px; }
.relationship-field { display: block; margin: 16px 0; }
.relationship-field > span { display: block; margin-bottom: 7px; color: #514a4f; font-size: 12px; font-weight: 600; }
.relationship-field input,.relationship-field select,.relationship-field textarea { width: 100%; padding: 12px 13px; color: #302c2f; background: #fff; border: 1px solid #cfc4bd; border-radius: 12px; outline: 0; resize: vertical; }
.relationship-field input:focus,.relationship-field select:focus,.relationship-field textarea:focus { border-color: #806b74; box-shadow: 0 0 0 3px rgba(128,107,116,.12); }
.full-button { width: 100%; display: flex; align-items: center; justify-content: center; gap: 7px; }
.privacy-note { margin: 12px 0 0 !important; color: #655e62 !important; font-size: 11px !important; }
.icon-picker { margin: 16px 0; padding: 0; border: 0; }
.icon-picker legend { margin-bottom: 8px; color: #514a4f; font-size: 12px; font-weight: 600; }
.icon-picker { display: flex; flex-wrap: wrap; gap: 7px; }
.icon-picker legend { width: 100%; }
.icon-picker button { min-width: 65px; padding: 9px 8px; display: grid; justify-items: center; gap: 4px; color: #554e53; background: #f7f2ef; border: 1px solid #d8cec8; border-radius: 11px; font-size: 10px; }
.icon-picker button.active { color: #fff; background: #66535d; border-color: #66535d; }
@media (max-width: 760px) {
  .relationship-heading { align-items: flex-start; }
  .heading-actions { width: 100%; }
  .heading-actions .button { flex: 1; padding: 0 10px; }
  .relationship-summary { grid-template-columns: 1fr; gap: 0; }
  .relationship-summary div { padding: 10px 8px; border-right: 0; border-bottom: 1px solid #d8c6bc; }
  .category-grid,.relationship-workbench { grid-template-columns: 1fr; }
  .category-main { min-height: 120px; padding: 19px; }
  .category-copy strong { font-size: 20px; }
  .section-help { display: none; }
  .hidden-category-list article { grid-template-columns: auto 1fr; }
  .hidden-category-list .button { grid-column: 1 / -1; width: 100%; justify-content: center; }
  .relationship-panel { padding: 22px; }
}
</style>
