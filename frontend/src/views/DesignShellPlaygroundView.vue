<template>
  <div class="shell-playground">
    <header>
      <p>MemoSpace Shell</p>
      <h2>跨端壳层检查页</h2>
      <span>用于验证 Web 顶栏、Compact 顶栏、二级导航、底部导航和滚动中的创建按钮。</span>
    </header>
    <section v-for="index in 5" :key="index" class="surface-default">
      <small>SECTION {{ index }}</small>
      <h3>{{ index === 1 ? '页面内容从导航之后开始' : '滚动位置与视觉节奏' }}</h3>
      <p>这里不连接后端，也不承载真实数据。它只检查壳层在不同宽度、主题与滚动状态下是否稳定。</p>
    </section>
  </div>
</template>

<style scoped>
.shell-playground { display: grid; gap: var(--space-8); }
.shell-playground header { max-width: var(--container-reading); display: grid; gap: var(--space-2); }
.shell-playground header>p,.shell-playground small { color: var(--color-text-link); font-size: var(--type-caption-size); font-weight: 700; letter-spacing: .08em; }
.shell-playground h2 { font-size: var(--type-heading-1-size); }
.shell-playground header>span,.shell-playground section p { color: var(--color-text-secondary); line-height: var(--type-body-lg-line); }
.shell-playground section { min-height: 280px; padding: var(--space-8); display: grid; align-content: center; gap: var(--space-3); border-radius: var(--radius-lg); }
.shell-playground section h3 { font-size: var(--type-heading-2-size); }
@media (max-width: 599px) { .shell-playground { gap: var(--space-5); } .shell-playground section { min-height: 220px; padding: var(--space-5); } }
</style>
