<script setup lang="ts">
defineProps<{ admin?: boolean }>()
</script>

<template>
  <nav class="login-mode-switch" :class="{ admin }" aria-label="登录身份切换">
    <span class="mode-slider"></span>
    <router-link to="/login" :aria-current="!admin?'page':undefined">用户登录</router-link>
    <router-link to="/admin/login" :aria-current="admin?'page':undefined">管理员</router-link>
  </nav>
</template>

<style scoped>
.login-mode-switch{position:relative;width:236px;height:42px;margin-bottom:24px;padding:4px;display:grid;grid-template-columns:1fr 1fr;border:1px solid var(--control-line);border-radius:15px;background:color-mix(in srgb,var(--paper) 82%,transparent);isolation:isolate}.mode-slider{position:absolute;z-index:-1;left:4px;top:4px;width:calc(50% - 4px);height:32px;border-radius:11px;background:var(--surface-solid);box-shadow:0 5px 16px rgba(50,47,49,.14);transition:transform .34s cubic-bezier(.22,1,.36,1),background .25s ease}.login-mode-switch.admin .mode-slider{transform:translateX(100%);background:#444b5c}.login-mode-switch a{display:grid;place-items:center;color:var(--muted);font-size:11px;font-weight:650;transition:color .28s ease}.login-mode-switch a[aria-current="page"]{color:var(--ink)}.login-mode-switch.admin a[aria-current="page"]{color:white}@media(prefers-reduced-motion:reduce){.mode-slider,.login-mode-switch a{transition:none}}
</style>
