<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { Archive, ArrowLeft, ArrowRight, EyeOff, FolderHeart, Save, Tags, UserRoundX } from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import UserAvatar from '../components/UserAvatar.vue'

const router = useRouter()
const categories = ref<any[]>([])
const relationships = ref<any[]>([])
const selections = ref<Record<number, number[]>>({})
const busyId = ref<number | null>(null)
const loading = ref(true)
const pageMessage = ref('')
const pageError = ref('')

const activeRelationships = computed(() => relationships.value.filter(item => item.status === 'ACTIVE'))
const archivedRelationships = computed(() => relationships.value.filter(item => item.status !== 'ACTIVE'))
const isVisible = (category: any) => category.is_visible === true || Number(category.is_visible) === 1 || String(category.is_visible) === 'true'

const syncSelections = () => {
  const next: Record<number, number[]> = {}
  for (const relationship of relationships.value) {
    next[Number(relationship.id)] = (relationship.categories || []).map((category: any) => Number(category.id))
  }
  selections.value = next
}

const load = async () => {
  pageError.value = ''
  try {
    const [categoryResponse, relationshipResponse] = await Promise.all([
      http.get('/relationship-categories', { params: { includeHidden: true } }),
      http.get('/relationships')
    ])
    categories.value = categoryResponse.data
    relationships.value = relationshipResponse.data
    syncSelections()
  } catch (error) { pageError.value = errorMessage(error) }
  finally { loading.value = false }
}

const saveCategories = async (relationship: any) => {
  const relationshipId = Number(relationship.id)
  const categoryIds = selections.value[relationshipId] || []
  if (!categoryIds.length) {
    pageError.value = '一段关系至少需要保留一个分类标签。'
    return
  }
  busyId.value = relationshipId
  pageError.value = ''
  pageMessage.value = ''
  try {
    const { data } = await http.put(`/relationships/${relationshipId}/categories`, { categoryIds })
    relationship.categories = data.categories || []
    selections.value[relationshipId] = relationship.categories.map((category: any) => Number(category.id))
    pageMessage.value = `已更新你与 ${relationship.nickname} 的分类；共同空间仍然是原来的那一个。`
  } catch (error) { pageError.value = errorMessage(error) }
  finally { busyId.value = null }
}

const archiveRelationship = async (relationship: any) => {
  const confirmed = window.confirm(`确定解除与 ${relationship.nickname} 的关系吗？共同空间会被封存，历史记忆不会删除。`)
  if (!confirmed) return
  const relationshipId = Number(relationship.id)
  busyId.value = relationshipId
  pageError.value = ''
  pageMessage.value = ''
  try {
    await http.delete(`/relationships/${relationshipId}`)
    pageMessage.value = `已解除与 ${relationship.nickname} 的关系。共同空间和历史记忆已封存保留。`
    await load()
  } catch (error) { pageError.value = errorMessage(error) }
  finally { busyId.value = null }
}

onMounted(load)
</script>

<template>
  <button class="manage-back" @click="router.push('/relationships')"><ArrowLeft :size="16" /> 返回关系分类</button>
  <header class="page-heading manage-heading">
    <div>
      <span class="eyebrow">RELATIONSHIP SETTINGS</span>
      <h1>关系管理</h1>
      <p>为同一段关系添加多个分类标签，或将不再继续的共同空间温柔封存。</p>
    </div>
    <button class="button primary" @click="router.push('/relationships')"><Tags :size="16" /> 管理分类显示与排序</button>
  </header>

  <p v-if="pageMessage" class="manage-message success" role="status">{{ pageMessage }}</p>
  <p v-if="pageError" class="manage-message error" role="alert">{{ pageError }}</p>
  <div v-if="loading" class="manage-loading">正在整理关系资料…</div>

  <template v-else>
    <section v-if="activeRelationships.length" class="relationship-manage-list" aria-label="有效关系">
      <article v-for="relationship in activeRelationships" :key="relationship.id" class="relationship-manage-card">
        <header>
          <router-link :to="`/user/${relationship.user_id}`"><UserAvatar class="manage-avatar" :src="relationship.avatar" :name="relationship.nickname||relationship.username" /></router-link>
          <div>
            <h2>{{ relationship.nickname }}</h2>
            <p>@{{ relationship.username }} · 建立于 {{ dayjs(relationship.established_at).format('YYYY 年 M 月 D 日') }}</p>
          </div>
          <router-link v-if="relationship.space_id" :to="`/space/${relationship.space_id}`" class="space-shortcut">
            <FolderHeart :size="16" /> 进入共同空间 <ArrowRight :size="15" />
          </router-link>
        </header>

        <div class="tag-editor">
          <div class="tag-editor-copy">
            <b>我的分类标签</b>
            <p>可以同时勾选多个分类。增加标签只会增加入口，不会创建第二个共同空间。</p>
          </div>
          <div class="category-checks">
            <label v-for="category in categories" :key="category.id" :class="{ hidden: !isVisible(category) }">
              <input v-model="selections[Number(relationship.id)]" type="checkbox" :value="Number(category.id)" />
              <span>{{ category.name }}</span>
              <EyeOff v-if="!isVisible(category)" :size="12" />
            </label>
          </div>
          <button class="button primary save-tags" :disabled="busyId === Number(relationship.id)" @click="saveCategories(relationship)">
            <Save :size="15" /> 保存分类
          </button>
        </div>

        <footer>
          <p><b>解除关系不会删除历史</b><span>共同空间会转为封存状态，已经留下的 Memory 和媒体仍会保留。</span></p>
          <button :disabled="busyId === Number(relationship.id)" @click="archiveRelationship(relationship)"><UserRoundX :size="15" /> 解除关系</button>
        </footer>
      </article>
    </section>

    <section v-else class="manage-empty">
      <FolderHeart :size="27" />
      <h2>还没有已建立的关系</h2>
      <p>先搜索一位用户、选择分类并发出邀请；对方接受后就能在这里管理。</p>
      <button class="button primary" @click="router.push('/relationships')">去发出邀请</button>
    </section>

    <section v-if="archivedRelationships.length" class="archived-section">
      <div class="section-heading"><div><span class="eyebrow">ARCHIVED RELATIONSHIPS</span><h2>已封存关系</h2></div></div>
      <div class="archived-list">
        <article v-for="relationship in archivedRelationships" :key="relationship.id">
          <span class="archive-icon"><Archive :size="17" /></span>
          <div><b>{{ relationship.nickname }}</b><small>已解除关系，历史共同空间与记忆仍被保留</small></div>
          <router-link v-if="relationship.space_id" :to="`/space/${relationship.space_id}`">查看封存空间 <ArrowRight :size="14" /></router-link>
        </article>
      </div>
    </section>
  </template>
</template>

<style scoped>
.manage-back { margin-bottom: 15px; padding: 8px 3px; display: inline-flex; align-items: center; gap: 6px; color: #514c50; background: transparent; border: 0; font-size: 13px; }
.manage-heading { align-items: center; }
.manage-heading p { max-width: 690px; line-height: 1.7; }
.manage-heading .button { display: flex; align-items: center; gap: 7px; }
.manage-message { margin: -6px 0 20px; padding: 14px 18px; border-radius: 15px; font-size: 13px; }
.manage-message.success { color: #3c5143; background: #edf4ed; border: 1px solid #c9d9cb; }
.manage-message.error { color: #7b303c; background: #faecee; border: 1px solid #e8c3c9; }
.manage-loading { padding: 60px 24px; color: #5d585c; text-align: center; background: rgba(255,255,255,.6); border: 1px solid #d4cbc5; border-radius: 25px; }
.relationship-manage-list { display: grid; gap: 18px; }
.relationship-manage-card { overflow: hidden; color: #353135; background: rgba(255,253,249,.9); border: 1px solid #d8cfc9; border-radius: 25px; box-shadow: 0 14px 35px rgba(51,45,45,.08); }
.relationship-manage-card > header { padding: 22px 24px; display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 13px; border-bottom: 1px solid #ded5cf; }
.manage-avatar { width: 49px; height: 49px; display: grid; place-items: center; color: #fff; background: linear-gradient(135deg,#a4737e,#635b70); border-radius: 50%; font: 600 18px 'Noto Serif SC',serif; }
.relationship-manage-card h2 { margin: 0 0 4px; color: #302c2f; font-size: 20px; }
.relationship-manage-card header p { margin: 0; color: #5f585d; font-size: 11px; }
.space-shortcut { padding: 9px 12px; display: flex; align-items: center; gap: 6px; color: #473c42; background: #f0e7e5; border: 1px solid #d7c7c4; border-radius: 12px; font-size: 11px; }
.space-shortcut:hover { background: #e9dcda; }
.tag-editor { padding: 22px 24px; display: grid; grid-template-columns: 210px 1fr auto; align-items: center; gap: 20px; }
.tag-editor-copy b { display: block; margin-bottom: 5px; color: #3a3438; font-size: 13px; }
.tag-editor-copy p { margin: 0; color: #625b5f; font-size: 11px; line-height: 1.6; }
.category-checks { display: flex; flex-wrap: wrap; gap: 8px; }
.category-checks label { padding: 8px 11px; display: flex; align-items: center; gap: 6px; color: #433d41; background: #f6f0ed; border: 1px solid #d5c9c4; border-radius: 999px; font-size: 12px; cursor: pointer; }
.category-checks label:has(input:checked) { color: #fff; background: #68545e; border-color: #68545e; }
.category-checks label.hidden:not(:has(input:checked)) { color: #645e62; background: #ece8e5; border-style: dashed; }
.category-checks input { margin: 0; accent-color: #5d4b54; }
.save-tags { min-width: 116px; display: flex; align-items: center; justify-content: center; gap: 6px; }
.relationship-manage-card > footer { padding: 15px 24px; display: flex; align-items: center; justify-content: space-between; gap: 18px; background: #f7f2ef; border-top: 1px solid #ded5cf; }
.relationship-manage-card footer p { margin: 0; }
.relationship-manage-card footer b,.relationship-manage-card footer span { display: block; }
.relationship-manage-card footer b { color: #51484d; font-size: 11px; }
.relationship-manage-card footer span { margin-top: 2px; color: #696166; font-size: 10px; }
.relationship-manage-card footer button { min-height: 36px; padding: 0 13px; display: flex; align-items: center; gap: 6px; color: #793844; background: #fff5f6; border: 1px solid #d9aeb6; border-radius: 11px; font-size: 11px; }
.relationship-manage-card footer button:disabled { opacity: .5; }
.manage-empty { padding: 60px 24px; text-align: center; color: #585156; background: rgba(255,253,249,.7); border: 1px dashed #bdaea7; border-radius: 25px; }
.manage-empty svg { color: #785e69; }
.manage-empty h2 { margin: 13px 0 8px; color: #322e31; font-size: 21px; }
.manage-empty p { margin-bottom: 20px; }
.archived-section { margin-top: 38px; }
.archived-list { display: grid; gap: 9px; }
.archived-list article { padding: 14px 16px; display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 12px; color: #454045; background: rgba(241,238,234,.82); border: 1px solid #d5cec8; border-radius: 16px; }
.archive-icon { width: 37px; height: 37px; display: grid; place-items: center; color: #5e585e; background: #dfd9d5; border-radius: 11px; }
.archived-list b,.archived-list small { display: block; }
.archived-list small { margin-top: 3px; color: #686267; font-size: 10px; }
.archived-list a { display: flex; align-items: center; gap: 4px; color: #4f3f47; font-size: 11px; }
@media (max-width: 850px) {
  .manage-heading { align-items: flex-start; }
  .relationship-manage-card > header { grid-template-columns: auto 1fr; }
  .space-shortcut { grid-column: 1 / -1; justify-content: center; }
  .tag-editor { grid-template-columns: 1fr; }
  .save-tags { width: 100%; }
}
@media (max-width: 620px) {
  .relationship-manage-card > footer { align-items: stretch; flex-direction: column; }
  .relationship-manage-card footer button { justify-content: center; }
  .archived-list article { grid-template-columns: auto 1fr; }
  .archived-list a { grid-column: 1 / -1; justify-content: flex-end; }
}
</style>
