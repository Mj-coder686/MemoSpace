<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowDown, ArrowRight, ArrowUp, Camera, Coffee, Eye, EyeOff, Handshake,
  Heart, Home, Leaf, Plus, Search, Sparkles, Star, UserPlus, Users,
} from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import { useAuthStore } from '../stores/auth'
import UserAvatar from '../components/UserAvatar.vue'
import EmptyState from '../components/EmptyState.vue'
import { UiBanner, UiButton, UiDialog, UiIconButton, UiInput, UiRadio, UiSkeleton, UiTextarea } from '../components/ui'

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
const createDialogOpen = ref(false)
const inviteDialogOpen = ref(false)
const creating = ref(false)
const createForm = ref({ name: '', icon: 'users' })
const people = ref<any[]>([])
const searchQuery = ref('')
const searching = ref(false)
const selectedPerson = ref<any | null>(null)
const selectedCategoryId = ref<number | undefined>()
const invitationMessage = ref('想和你一起收藏我们的故事。')
const inviting = ref(false)

const iconComponents: Record<string, any> = {
  heart: Heart, handshake: Handshake, sparkles: Sparkles, home: Home, users: Users,
  coffee: Coffee, camera: Camera, star: Star, leaf: Leaf,
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
  { value: 'leaf', label: '成长', icon: Leaf },
]

const isVisible = (category: Category) => category.is_visible === true || Number(category.is_visible) === 1 || String(category.is_visible) === 'true'
const visibleCategories = computed(() => categories.value.filter(isVisible))
const hiddenCategories = computed(() => categories.value.filter((category) => !isVisible(category)))
const totalRelationships = computed(() => visibleCategories.value.reduce((sum, item) => sum + Number(item.relationship_count || 0), 0))
const categoryIcon = (name?: string) => iconComponents[name || 'users'] || Users
const categoryStyle = (category: Category) => ({
  '--category-accent': category.primary_color || 'var(--color-action-primary)',
  '--category-background': category.background_color || 'var(--color-action-primary-soft)',
})

const loadCategories = async () => {
  const { data } = await http.get('/relationship-categories', { params: { includeHidden: true } })
  categories.value = data
  const selectedStillVisible = visibleCategories.value.some((item) => Number(item.id) === Number(selectedCategoryId.value))
  if (!selectedStillVisible) selectedCategoryId.value = visibleCategories.value.length ? Number(visibleCategories.value[0].id) : undefined
}

const refresh = async () => {
  loading.value = true
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
  const index = ordered.findIndex((item) => Number(item.id) === Number(category.id))
  const target = index + direction
  if (index < 0 || target < 0 || target >= ordered.length) return
  ;[ordered[index], ordered[target]] = [ordered[target], ordered[index]]
  categoryBusy.value = Number(category.id)
  pageError.value = ''
  try {
    const ids = [...ordered, ...hiddenCategories.value].map((item) => Number(item.id))
    categories.value = (await http.put('/relationship-categories/reorder', { categoryIds: ids })).data
  } catch (error) { pageError.value = errorMessage(error) }
  finally { categoryBusy.value = null }
}

const createCategory = async () => {
  if (!createForm.value.name.trim()) return
  creating.value = true
  pageError.value = ''
  pageMessage.value = ''
  try {
    const { data } = await http.post('/relationship-categories', { name: createForm.value.name.trim(), icon: createForm.value.icon })
    createForm.value = { name: '', icon: 'users' }
    await loadCategories()
    createDialogOpen.value = false
    pageMessage.value = `已创建「${data.name}」，现在可以把重要的人放进这个分类。`
  } catch (error) { pageError.value = errorMessage(error) }
  finally { creating.value = false }
}

const searchPeople = async () => {
  if (!searchQuery.value.trim()) return
  searching.value = true
  pageError.value = ''
  people.value = []
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
      receiverId: Number(selectedPerson.value.id), categoryId: Number(selectedCategoryId.value), message: invitationMessage.value.trim(),
    })
    pageMessage.value = `已向 ${selectedPerson.value.nickname || selectedPerson.value.username} 发出「${data.categoryName || '关系'}」邀请。对方接受后会创建或关联双方唯一的共同空间。`
    selectedPerson.value = null
    searchQuery.value = ''
    people.value = []
    inviteDialogOpen.value = false
  } catch (error) { pageError.value = errorMessage(error) }
  finally { inviting.value = false }
}

onMounted(async () => {
  await refresh()
  const inviteCategory = Number(route.query.inviteCategory)
  if (inviteCategory && visibleCategories.value.some((item) => Number(item.id) === inviteCategory)) {
    selectedCategoryId.value = inviteCategory
    inviteDialogOpen.value = true
  }
  const inviteUser = Number(route.query.inviteUser)
  if (inviteUser) {
    selectedPerson.value = { id: inviteUser, nickname: String(route.query.inviteName || '这位用户') }
    searchQuery.value = selectedPerson.value.nickname
    inviteDialogOpen.value = true
  }
})
</script>

<template>
  <div class="relationship-categories-page">
    <header class="relationship-domain-header">
      <div><span class="memory-kicker">PEOPLE & RELATIONSHIPS</span><h1>关系分类</h1><p>用适合你的方式整理重要的人；一个人可以属于多个分类，但双方始终共用同一个空间。</p></div>
      <div><UiButton variant="secondary" @click="createDialogOpen = true"><Plus :size="17" />创建自定义分类</UiButton><UiButton variant="primary" size="lg" @click="inviteDialogOpen = true"><UserPlus :size="18" />绑定一段关系</UiButton></div>
    </header>

    <section class="relationship-boundary-note" aria-label="好友与关系的区别">
      <div><Users :size="20" /><span><strong>好友用于联系</strong><small>添加好友后可以私聊、设置备注和管理联系权限。</small></span><router-link to="/friends">前往好友中心 <ArrowRight :size="14" /></router-link></div>
      <div><Handshake :size="20" /><span><strong>关系用于共同生活记录</strong><small>关系可以有多个分类标签，但始终只关联双方唯一的共同空间。</small></span><router-link to="/relationships/manage">管理已有关系 <ArrowRight :size="14" /></router-link></div>
    </section>

    <UiBanner v-if="pageMessage" tone="success" title="关系分类已更新" :description="pageMessage" />
    <UiBanner v-if="pageError && !inviteDialogOpen && !createDialogOpen" tone="danger" title="操作没有完成" :description="pageError" />

    <section class="relationship-category-section" aria-labelledby="visible-category-title">
      <div class="relationship-section-heading"><div><span class="memory-kicker">YOUR CIRCLES</span><h2 id="visible-category-title">正在显示</h2></div><p>{{ visibleCategories.length }} 个分类 · {{ totalRelationships }} 个关系标签关联</p></div>

      <div v-if="loading" class="relationship-category-loading" aria-label="正在整理关系分类"><UiSkeleton v-for="index in 4" :key="index" height="112px" radius="var(--radius-md)" /></div>
      <div v-else-if="visibleCategories.length" class="relationship-category-list">
        <article v-for="(category, index) in visibleCategories" :key="category.id" :style="categoryStyle(category)">
          <router-link :to="`/relationships/category/${category.id}`" :aria-label="`打开${category.name}分类`" class="relationship-category-main">
            <span class="relationship-category-icon"><component :is="categoryIcon(category.icon)" :size="22" /></span>
            <span><small>{{ category.category_type === 'CUSTOM' ? '自定义分类' : '系统分类' }}</small><strong>{{ category.name }}</strong><span>{{ Number(category.relationship_count || 0) }} 位重要的人</span></span>
            <ArrowRight :size="19" aria-hidden="true" />
          </router-link>
          <div class="relationship-category-actions" aria-label="分类排序和显示">
            <UiIconButton :label="`上移${category.name}`" size="sm" variant="ghost" :disabled="index === 0 || categoryBusy === Number(category.id)" @click="moveCategory(category, -1)"><ArrowUp :size="16" /></UiIconButton>
            <UiIconButton :label="`下移${category.name}`" size="sm" variant="ghost" :disabled="index === visibleCategories.length - 1 || categoryBusy === Number(category.id)" @click="moveCategory(category, 1)"><ArrowDown :size="16" /></UiIconButton>
            <UiButton size="sm" variant="ghost" :disabled="categoryBusy === Number(category.id)" @click="setVisibility(category, false)"><EyeOff :size="15" />隐藏</UiButton>
          </div>
        </article>
      </div>
      <EmptyState v-else title="所有分类都已隐藏" text="你可以在下方随时恢复。关系、共同空间和记忆都没有被删除。" />
    </section>

    <details v-if="hiddenCategories.length" class="relationship-hidden-categories">
      <summary><span><EyeOff :size="18" /><strong>已隐藏分类</strong><small>{{ hiddenCategories.length }} 个；隐藏不等于删除</small></span><ArrowDown :size="17" /></summary>
      <div>
        <article v-for="category in hiddenCategories" :key="category.id">
          <span class="relationship-category-icon is-quiet"><component :is="categoryIcon(category.icon)" :size="18" /></span>
          <span><strong>{{ category.name }}</strong><small>{{ Number(category.relationship_count || 0) }} 段关系仍被完整保留</small></span>
          <UiButton size="sm" variant="secondary" :disabled="categoryBusy === Number(category.id)" @click="setVisibility(category, true)"><Eye :size="15" />恢复显示</UiButton>
        </article>
      </div>
    </details>

    <UiDialog :open="inviteDialogOpen" title="绑定一段关系" description="搜索用户、选择分类并发送邀请；对方接受后才会建立关系。" width="wide" compact-fullscreen :busy="inviting" @close="inviteDialogOpen = false">
      <div class="relationship-invite-flow">
        <section><h3><span>01</span>找到对方</h3><form class="relationship-people-search" role="search" @submit.prevent="searchPeople"><UiInput v-model="searchQuery" label="昵称或用户名" name="relationship-user-search" placeholder="输入昵称或用户名" :disabled="inviting" @update:model-value="selectedPerson = null" /><UiButton type="submit" variant="tonal" :loading="searching" loading-text="搜索中"><Search :size="16" />搜索</UiButton></form>
          <div v-if="people.length" class="relationship-people-results" role="listbox" aria-label="用户搜索结果"><button v-for="person in people" :key="person.id" type="button" role="option" aria-selected="false" @click="choosePerson(person)"><UserAvatar :src="person.avatar" :name="person.nickname || person.username" /><span><strong>{{ person.nickname || person.username }}</strong><small>@{{ person.username }} · {{ person.location || '未填写所在地' }}</small></span><UserPlus :size="17" /></button></div>
          <div v-else-if="searchQuery && !searching && !selectedPerson" class="relationship-search-hint">提交搜索后，从结果中选择要邀请的人。</div>
          <div v-if="selectedPerson" class="relationship-selected-person"><UserAvatar :src="selectedPerson.avatar" :name="selectedPerson.nickname || selectedPerson.username" /><span><small>准备邀请</small><strong>{{ selectedPerson.nickname || selectedPerson.username }}</strong></span></div>
        </section>
        <section><h3><span>02</span>选择关系分类</h3><div class="relationship-category-choices"><UiRadio v-for="category in visibleCategories" :key="category.id" v-model="selectedCategoryId" :value="Number(category.id)" name="relationship-category" :label="category.name" :description="`${Number(category.relationship_count || 0)} 位重要的人`" /></div></section>
        <section><h3><span>03</span>留一句话</h3><UiTextarea v-model="invitationMessage" label="邀请留言" name="relationship-invitation-message" :rows="3" :maxlength="200" :disabled="inviting" /><p>若双方已有其他分类关系，接受后会关联原来的共同空间，不会重复创建。</p></section>
        <UiBanner v-if="pageError" tone="danger" title="邀请没有发送" :description="pageError" />
      </div>
      <template #actions><UiButton variant="ghost" :disabled="inviting" @click="inviteDialogOpen = false">取消</UiButton><UiButton variant="primary" :loading="inviting" loading-text="正在发送" :disabled="!selectedPerson || !selectedCategoryId" @click="sendInvitation">发送绑定邀请</UiButton></template>
    </UiDialog>

    <UiDialog :open="createDialogOpen" title="创建自定义分类" description="为你们独有的称呼留一个入口；隐藏分类不会删除任何数据。" :busy="creating" @close="createDialogOpen = false">
      <div class="relationship-create-category"><UiInput v-model="createForm.name" label="分类名称" name="custom-category-name" :maxlength="40" placeholder="例如：旅行搭子、老同学" :disabled="creating" /><fieldset><legend>选择图标</legend><div><button v-for="choice in iconChoices" :key="choice.value" type="button" :class="{ 'is-selected': createForm.icon === choice.value }" :aria-pressed="createForm.icon === choice.value" @click="createForm.icon = choice.value"><component :is="choice.icon" :size="18" /><span>{{ choice.label }}</span></button></div></fieldset><UiBanner v-if="pageError" tone="danger" title="分类没有创建" :description="pageError" /></div>
      <template #actions><UiButton variant="ghost" :disabled="creating" @click="createDialogOpen = false">取消</UiButton><UiButton variant="primary" :loading="creating" loading-text="正在创建" :disabled="!createForm.name.trim()" @click="createCategory">创建分类</UiButton></template>
    </UiDialog>
  </div>
</template>
