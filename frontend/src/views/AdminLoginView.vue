<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, KeyRound, LockKeyhole, ShieldCheck } from 'lucide-vue-next'
import adminHttp from '../api/adminHttp'
import { errorMessage } from '../api/http'
import UiBanner from '../components/ui/UiBanner.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiInput from '../components/ui/UiInput.vue'

const router = useRouter()
const form = ref({ username: '', password: '' })
const busy = ref(false)
const submitted = ref(false)
const message = ref('')
const connectionIssue = ref(false)
const usernameError = computed(() => submitted.value && !form.value.username.trim() ? '请输入管理员账号' : '')
const passwordError = computed(() => submitted.value && !form.value.password ? '请输入管理员密码' : '')

const login = async () => {
  submitted.value = true
  message.value = ''
  connectionIssue.value = false
  if (usernameError.value || passwordError.value) return

  busy.value = true
  try {
    const { data } = await adminHttp.post('/admin/auth/login', {
      username: form.value.username.trim(),
      password: form.value.password,
    })
    localStorage.setItem('memospace_admin_token', data.token)
    localStorage.setItem('memospace_admin_user', JSON.stringify(data.user))
    await router.push('/admin')
  } catch (error: any) {
    connectionIssue.value = !error?.response
    message.value = connectionIssue.value ? '暂时无法连接服务器，请稍后重试。' : errorMessage(error)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="admin-identity-page">
    <router-link class="admin-identity-back" to="/login"><ArrowLeft :size="17" />返回拾光空间</router-link>

    <section class="admin-identity-intro" aria-labelledby="admin-identity-title">
      <span class="admin-identity-seal" aria-hidden="true"><ShieldCheck :size="30" /></span>
      <span class="identity-kicker">MEMOSPACE ADMINISTRATION</span>
      <h1 id="admin-identity-title">必要的管理，<br />明确的边界。</h1>
      <p>管理员只处理用户主动提交的举报、账号安全和必要的社区秩序问题。每次管理操作都会留下审计记录。</p>
      <div class="admin-identity-boundary">
        <LockKeyhole :size="19" aria-hidden="true" />
        <span><strong>隐私边界</strong>管理员不能浏览用户未被举报的私人记忆、图片或共享空间内容。</span>
      </div>
    </section>

    <form class="admin-identity-card" novalidate @submit.prevent="login">
      <header>
        <span class="identity-kicker">AUTHORIZED ACCESS</span>
        <h2>管理员登录</h2>
        <p>使用独立管理员会话，不会覆盖普通用户的登录状态。</p>
      </header>

      <UiBanner
        v-if="message"
        :tone="connectionIssue ? 'warning' : 'danger'"
        :title="connectionIssue ? '服务器暂时不可达' : '验证未通过'"
        :description="message"
      />

      <div class="admin-identity-card__fields">
        <UiInput
          v-model="form.username"
          label="管理员账号"
          name="admin-username"
          autocomplete="username"
          placeholder="请输入管理员账号"
          :error="usernameError"
          required
        />
        <UiInput
          v-model="form.password"
          label="管理员密码"
          name="admin-password"
          type="password"
          autocomplete="current-password"
          placeholder="请输入管理员密码"
          :error="passwordError"
          revealable
          required
        />
      </div>

      <UiButton
        type="submit"
        variant="primary"
        size="lg"
        block
        :loading="busy"
        loading-text="正在验证权限"
      ><KeyRound :size="17" />进入管理员中心</UiButton>

      <small>普通用户即使知道此地址，也无法通过后端的管理员权限校验。</small>
    </form>
  </main>
</template>
