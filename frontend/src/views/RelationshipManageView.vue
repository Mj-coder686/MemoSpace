<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { Archive, ArrowLeft, ArrowRight, EyeOff, FolderHeart, Save, Tags, UserRoundX } from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import UserAvatar from '../components/UserAvatar.vue'
import EmptyState from '../components/EmptyState.vue'
import { UiBanner, UiButton, UiDialog, UiSkeleton } from '../components/ui'

const router = useRouter()
const categories = ref<any[]>([])
const relationships = ref<any[]>([])
const selections = ref<Record<number, number[]>>({})
const busyId = ref<number | null>(null)
const loading = ref(true)
const pageMessage = ref('')
const pageError = ref('')
const archiveTarget = ref<any | null>(null)

const activeRelationships = computed(() => relationships.value.filter(item => item.status === 'ACTIVE'))
const archivedRelationships = computed(() => relationships.value.filter(item => item.status !== 'ACTIVE'))
const isVisible = (category: any) => category.is_visible === true || Number(category.is_visible) === 1 || String(category.is_visible) === 'true'
const displayName = (relationship: any) => relationship.nickname || relationship.username || '未命名用户'

const syncSelections = () => {
  const next: Record<number, number[]> = {}
  for (const relationship of relationships.value) next[Number(relationship.id)] = (relationship.categories || []).map((category: any) => Number(category.id))
  selections.value = next
}

const load = async () => {
  loading.value = true
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
    pageMessage.value = `已更新你与 ${displayName(relationship)} 的分类；共同空间仍然是原来的那一个。`
  } catch (error) { pageError.value = errorMessage(error) }
  finally { busyId.value = null }
}

const archiveRelationship = async () => {
  const relationship = archiveTarget.value
  if (!relationship) return
  const relationshipId = Number(relationship.id)
  busyId.value = relationshipId
  pageError.value = ''
  pageMessage.value = ''
  try {
    await http.delete(`/relationships/${relationshipId}`)
    archiveTarget.value = null
    pageMessage.value = `已解除与 ${displayName(relationship)} 的关系。共同空间和历史记忆已封存保留。`
    await load()
  } catch (error) { pageError.value = errorMessage(error) }
  finally { busyId.value = null }
}

onMounted(load)
</script>

<template>
  <main class="relationship-manage-page">
    <router-link class="relationship-back-link" to="/relationships"><ArrowLeft :size="16" />返回关系分类</router-link>

    <header class="relationship-domain-header relationship-manage-header">
      <div><span class="memory-kicker">RELATIONSHIP LEDGER</span><h1>关系管理</h1><p>一段关系可以属于多个分类，但始终只对应双方唯一的共同空间。你可以整理入口，也可以封存一段不再继续的关系。</p></div>
      <UiButton variant="secondary" @click="router.push('/relationships')"><Tags :size="16" />分类显示与排序</UiButton>
    </header>

    <UiBanner v-if="pageMessage" tone="success" title="关系资料已更新" :description="pageMessage" />
    <UiBanner v-if="pageError" tone="danger" title="操作没有完成" :description="pageError"><template #actions><UiButton variant="ghost" size="sm" @click="pageError = ''">知道了</UiButton></template></UiBanner>

    <div v-if="loading" class="relationship-manage-loading" aria-label="正在整理关系资料"><UiSkeleton v-for="index in 2" :key="index" height="244px" radius="var(--radius-lg)" /></div>

    <template v-else>
      <section class="relationship-manage-section" aria-labelledby="active-relationships-title">
        <div class="relationship-section-heading"><div><span class="memory-kicker">ACTIVE RELATIONSHIPS</span><h2 id="active-relationships-title">正在同行的人</h2><p>{{ activeRelationships.length }} 段已建立关系</p></div></div>

        <div v-if="activeRelationships.length" class="relationship-ledger">
          <article v-for="relationship in activeRelationships" :key="relationship.id" class="relationship-ledger-entry">
            <header class="relationship-ledger-person">
              <router-link :to="`/user/${relationship.user_id}`" :aria-label="`查看${displayName(relationship)}的主页`"><UserAvatar :src="relationship.avatar" :name="displayName(relationship)" /></router-link>
              <div><h3>{{ displayName(relationship) }}</h3><p>@{{ relationship.username }} · 建立于 {{ dayjs(relationship.established_at).format('YYYY 年 M 月 D 日') }}</p></div>
              <router-link v-if="relationship.space_id" :to="`/space/${relationship.space_id}`" class="relationship-one-space"><span><small>双方唯一的共同空间</small><strong>{{ relationship.space_name || '共同记忆空间' }}</strong></span><ArrowRight :size="18" /></router-link>
            </header>

            <div class="relationship-tag-editor">
              <div class="relationship-tag-copy"><strong>这段关系出现在哪里</strong><p>标签只增加分类入口，不会复制关系、共同空间或 Memory。</p></div>
              <fieldset><legend class="sr-only">选择与{{ displayName(relationship) }}的关系分类</legend><label v-for="category in categories" :key="category.id" :class="{ 'is-hidden-category': !isVisible(category) }"><input v-model="selections[Number(relationship.id)]" type="checkbox" :value="Number(category.id)" /><span>{{ category.name }}</span><EyeOff v-if="!isVisible(category)" :size="13" /><small v-if="!isVisible(category)">已隐藏</small></label></fieldset>
              <UiButton variant="tonal" :loading="busyId === Number(relationship.id)" loading-text="保存中" @click="saveCategories(relationship)"><Save :size="15" />保存分类</UiButton>
            </div>

            <footer class="relationship-ledger-actions"><p><strong>解除后，历史不会消失</strong><span>关系与共同空间将转为封存状态，已有 Memory 和媒体继续保留。</span></p><UiButton variant="danger" size="sm" :disabled="busyId === Number(relationship.id)" @click="archiveTarget = relationship"><UserRoundX :size="15" />解除关系</UiButton></footer>
          </article>
        </div>

        <EmptyState v-else title="还没有已建立的关系" text="先搜索一位用户、选择分类并发出邀请；对方接受后就能在这里管理。" action-label="去发出邀请" @action="router.push('/relationships')" />
      </section>

      <details v-if="archivedRelationships.length" class="relationship-archive">
        <summary><span><Archive :size="18" /><strong>已封存关系</strong><small>{{ archivedRelationships.length }} 段历史仍被保留</small></span><span>展开查看</span></summary>
        <div><article v-for="relationship in archivedRelationships" :key="relationship.id"><UserAvatar :src="relationship.avatar" :name="displayName(relationship)" /><span><strong>{{ displayName(relationship) }}</strong><small>关系已解除；共同空间与记忆只读保留</small></span><router-link v-if="relationship.space_id" :to="`/space/${relationship.space_id}`">查看封存空间<ArrowRight :size="14" /></router-link></article></div>
      </details>
    </template>

    <UiDialog :open="Boolean(archiveTarget)" title="解除这段关系？" :description="archiveTarget ? `你与 ${displayName(archiveTarget)} 的共同空间会被封存，不会删除任何历史记忆。` : ''" :busy="busyId !== null" @close="archiveTarget = null">
      <div class="relationship-archive-confirm"><FolderHeart :size="24" /><p>解除后，这段关系不会再接收新的共同 Memory。已有空间、图片和记录仍会保留，可以从“已封存关系”中查看。</p></div>
      <template #actions><UiButton variant="ghost" :disabled="busyId !== null" @click="archiveTarget = null">暂不解除</UiButton><UiButton variant="danger" :loading="busyId !== null" loading-text="正在封存" @click="archiveRelationship">确认解除并封存</UiButton></template>
    </UiDialog>
  </main>
</template>
