<script setup lang="ts">
import { ref } from 'vue'
import { Bell, Plus } from 'lucide-vue-next'
import EmptyState from '../components/EmptyState.vue'
import {
  UiAvatar,
  UiBadge,
  UiBanner,
  UiBottomSheet,
  UiButton,
  UiCheckbox,
  UiChip,
  UiDialog,
  UiDivider,
  UiDrawer,
  UiIconButton,
  UiInput,
  UiLoadMore,
  UiPagination,
  UiProgress,
  UiRadio,
  UiSelect,
  UiSkeleton,
  UiSwitch,
  UiTextarea,
  UiTooltip,
} from '../components/ui'
import { pushToast } from '../composables/useToast'

const colors = [
  ['画布', 'var(--color-bg-canvas)'],
  ['柔和背景', 'var(--color-bg-subtle)'],
  ['内容表面', 'var(--color-surface-default)'],
  ['浮层表面', 'var(--color-surface-raised)'],
  ['主操作', 'var(--color-action-primary)'],
  ['情感强调', 'var(--color-accent-emotional)'],
]

const spacing = ['1', '2', '3', '4', '5', '6', '8', '10', '12', '16']
const title = ref('江边的晚风')
const note = ref('我们赶在日落前走到了江边。')
const visibility = ref('PRIVATE')
const selectedFilter = ref('全部')
const includeLocation = ref(true)
const followSystem = ref(false)
const audience = ref('PRIVATE')
const dialogOpen = ref(false)
const drawerOpen = ref(false)
const sheetOpen = ref(false)
const page = ref(2)
const password = ref('memospace-demo')
</script>

<template>
  <main id="main-content" class="ds-page container-wide">
    <header class="ds-intro">
      <p class="ds-kicker">MemoSpace Design System</p>
      <h1>拾光空间基础视觉</h1>
      <p>这是仅在开发环境开放的 Token 与基础状态检查页，不承载产品业务。</p>
    </header>

    <section aria-labelledby="ds-colors">
      <h2 id="ds-colors">语义颜色</h2>
      <div class="ds-color-grid">
        <article v-for="color in colors" :key="color[0]" class="ds-swatch" :style="{ '--swatch': color[1] }">
          <span aria-hidden="true" />
          <strong>{{ color[0] }}</strong>
          <code>{{ color[1] }}</code>
        </article>
      </div>
    </section>

    <section aria-labelledby="ds-type">
      <h2 id="ds-type">文字层级</h2>
      <div class="ds-type-stack surface-default">
        <p class="ds-display font-display">时间会走远，故事可以留下。</p>
        <h1>页面一级标题</h1>
        <h2>主要内容分区</h2>
        <h3>卡片组与弹层标题</h3>
        <p class="ds-body">正文承载真实生活、关系与隐私说明。它需要安静，但不能以低对比换取所谓高级感。</p>
        <small>辅助文字与时间 2026.09.22</small>
      </div>
    </section>

    <section aria-labelledby="ds-controls">
      <h2 id="ds-controls">基础交互状态</h2>
      <div class="ds-controls surface-default">
        <div class="ds-button-row">
          <UiButton variant="primary">保存记忆</UiButton>
          <UiButton>稍后再说</UiButton>
          <UiButton variant="tonal">加入收藏</UiButton>
          <UiButton variant="ghost">预览</UiButton>
          <UiButton variant="danger">删除</UiButton>
          <UiButton loading loading-text="正在保存">保存记忆</UiButton>
          <UiIconButton label="新建记忆" variant="tonal"><Plus :size="19" /></UiIconButton>
          <UiIconButton label="查看通知" :badge="108"><Bell :size="19" /></UiIconButton>
        </div>
        <UiDivider />
        <div class="ds-form-grid">
          <UiInput v-model="title" label="记忆标题" helper="最多 160 个字符。" placeholder="写下一句能找到它的话" required />
          <UiSelect v-model="visibility" label="谁可以看见" helper="隐私权限由后端继续执行。">
            <option value="PRIVATE">仅自己</option>
            <option value="RELATIONSHIP">关系成员</option>
            <option value="PUBLIC">公开</option>
          </UiSelect>
          <UiTextarea v-model="note" label="记忆正文" :maxlength="5000" />
          <UiInput label="地点" error="没有找到这个地点，请换一种写法。" placeholder="例如：武汉东湖" />
          <UiInput v-model="password" label="密码" type="password" autocomplete="current-password" revealable />
        </div>
        <UiDivider />
        <div class="ds-inline-group">
          <UiChip v-for="filter in ['全部', '照片', '文字', '地点']" :key="filter" interactive :selected="selectedFilter === filter" @click="selectedFilter = filter">{{ filter }}</UiChip>
          <UiBadge tone="success">已确认</UiBadge>
          <UiBadge tone="warning">待处理</UiBadge>
          <UiBadge tone="danger">已封存</UiBadge>
          <UiAvatar name="林溪" :size="48" online />
          <UiAvatar name="江屿" :size="48" />
        </div>
        <div class="ds-choice-grid">
          <UiCheckbox v-model="includeLocation" label="保存当前地点" description="只有在你主动保存时才会读取定位。" />
          <UiSwitch v-model="followSystem" label="跟随系统外观" description="切换后立即生效，可以随时恢复。" />
          <UiRadio v-model="audience" name="audience" value="PRIVATE" label="仅自己" description="只有你可以查看。" />
          <UiRadio v-model="audience" name="audience" value="RELATIONSHIP" label="关系成员" description="指定共同空间成员可以查看。" />
        </div>
        <UiBanner tone="warning" title="当前处于离线状态" description="已经保存的内容仍可浏览；恢复网络后再重试发送。">
          <template #actions><UiButton variant="link" size="sm">重试</UiButton></template>
        </UiBanner>
        <div class="ds-button-row">
          <UiButton @click="dialogOpen = true">打开确认框</UiButton>
          <UiButton @click="drawerOpen = true">打开详情抽屉</UiButton>
          <UiButton @click="sheetOpen = true">打开底部面板</UiButton>
          <UiButton variant="tonal" @click="pushToast({ title: '记忆已保存', message: '你可以继续添加照片或地点。', tone: 'success' })">显示提示</UiButton>
          <UiTooltip text="只有管理员可以执行这项操作"><UiButton variant="ghost">为什么不可用</UiButton></UiTooltip>
        </div>
        <UiProgress label="正在上传 2/5" :value="42" />
        <div class="ds-skeletons" role="status" aria-label="内容正在加载">
          <UiSkeleton height="132px" radius="var(--radius-lg)" />
          <UiSkeleton width="68%" height="20px" />
          <UiSkeleton width="92%" height="14px" />
        </div>
        <EmptyState kind="search" title="没有找到相关记忆" text="换一个关键词，或者清除当前筛选后再试。" action-label="清除筛选" />
        <UiPagination v-model:page="page" :total-pages="8" />
        <UiLoadMore />
      </div>
    </section>

    <section aria-labelledby="ds-spacing">
      <h2 id="ds-spacing">间距节奏</h2>
      <div class="ds-spacing surface-default">
        <div v-for="item in spacing" :key="item">
          <code>space-{{ item }}</code>
          <span :style="{ width: `var(--space-${item})` }" />
        </div>
      </div>
    </section>

    <UiDialog :open="dialogOpen" title="删除这条记忆？" description="删除后无法恢复，图片和评论也会一起移除。" @close="dialogOpen = false">
      <p>请确认你要删除的是“江边的晚风”。</p>
      <template #actions>
        <UiButton @click="dialogOpen = false">取消</UiButton>
        <UiButton variant="danger" @click="dialogOpen = false">确认删除</UiButton>
      </template>
    </UiDialog>
    <UiDrawer :open="drawerOpen" title="记忆详情检查" description="详情检查使用抽屉，不打断当前列表位置。" @close="drawerOpen = false">
      <p>这里会承载媒体、权限、人物和地点等较长内容。</p>
      <template #actions><UiButton variant="primary" @click="drawerOpen = false">完成</UiButton></template>
    </UiDrawer>
    <UiBottomSheet :open="sheetOpen" title="选择可见范围" description="Compact 端的简短选择使用底部面板。" @close="sheetOpen = false">
      <div class="ds-choice-stack">
        <UiRadio v-model="audience" name="sheet-audience" value="PRIVATE" label="仅自己" />
        <UiRadio v-model="audience" name="sheet-audience" value="RELATIONSHIP" label="关系成员" />
      </div>
      <template #actions><UiButton variant="primary" block @click="sheetOpen = false">确认</UiButton></template>
    </UiBottomSheet>
  </main>
</template>

<style scoped>
.ds-page { padding-block: var(--space-16); }
.ds-intro { max-width: var(--container-reading); margin-bottom: var(--space-16); }
.ds-intro h1 { margin-block: var(--space-2) var(--space-3); font-size: var(--type-heading-1-size); line-height: var(--type-heading-1-line); }
.ds-intro>p:last-child,.ds-body,.ds-type-stack>small { color: var(--color-text-secondary); }
.ds-kicker { color: var(--color-text-link); font-size: var(--type-caption-size); font-weight: 600; letter-spacing: .08em; }
section+section { margin-top: var(--space-16); }
section>h2 { margin-bottom: var(--space-6); font-size: var(--type-heading-2-size); line-height: var(--type-heading-2-line); }
.ds-color-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: var(--space-4); }
.ds-swatch { overflow: hidden; background: var(--color-surface-default); border: var(--border-hairline) solid var(--color-border-subtle); border-radius: var(--radius-lg); }
.ds-swatch>span { display: block; height: 96px; background: var(--swatch); border-bottom: var(--border-hairline) solid var(--color-border-subtle); }
.ds-swatch strong,.ds-swatch code { display: block; margin-inline: var(--space-4); }
.ds-swatch strong { margin-top: var(--space-3); }
.ds-swatch code { margin-block: var(--space-1) var(--space-4); color: var(--color-text-tertiary); font-size: var(--type-caption-size); }
.ds-type-stack,.ds-controls,.ds-spacing { padding: var(--space-8); border-radius: var(--radius-lg); }
.ds-type-stack>*+* { margin-top: var(--space-4); }
.ds-display { max-width: 18ch; font-size: var(--type-display-sm-size); line-height: var(--type-display-sm-line); font-weight: 700; }
.ds-type-stack h1 { font-size: var(--type-heading-1-size); line-height: var(--type-heading-1-line); }
.ds-type-stack h2 { font-size: var(--type-heading-2-size); line-height: var(--type-heading-2-line); }
.ds-type-stack h3 { font-size: var(--type-heading-3-size); line-height: var(--type-heading-3-line); }
.ds-body { max-width: 66ch; font-size: var(--type-body-lg-size); line-height: var(--type-body-lg-line); }
.ds-controls { display: grid; gap: var(--space-6); }
.ds-button-row,.ds-inline-group { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-3); }
.ds-form-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: var(--space-4); }
.ds-form-grid>*:nth-child(3) { grid-column: 1/-1; }
.ds-choice-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: var(--space-6); }
.ds-choice-stack { display: grid; gap: var(--space-4); }
.ds-skeletons { max-width: 440px; display: grid; gap: var(--space-3); }
.ds-spacing { display: grid; gap: var(--space-3); }
.ds-spacing>div { display: grid; grid-template-columns: 100px 1fr; align-items: center; gap: var(--space-4); }
.ds-spacing span { display: block; min-width: 2px; height: var(--space-3); background: var(--color-action-primary); border-radius: var(--radius-full); }
@media (max-width: 599px) { .ds-page { padding-block: var(--space-10); } .ds-type-stack,.ds-controls,.ds-spacing { padding: var(--space-5); } .ds-form-grid,.ds-choice-grid { grid-template-columns: 1fr; } .ds-form-grid>*:nth-child(3) { grid-column: auto; } }
</style>
