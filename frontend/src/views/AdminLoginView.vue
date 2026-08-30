<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, KeyRound, ShieldCheck } from 'lucide-vue-next'
import adminHttp from '../api/adminHttp'
import { errorMessage } from '../api/http'
import LoginModeSwitch from '../components/LoginModeSwitch.vue'

const router = useRouter()
const form = ref({ username:'admin', password:'' })
const busy = ref(false)
const message = ref('')

const login = async () => {
  busy.value=true; message.value=''
  try {
    const { data } = await adminHttp.post('/admin/auth/login', form.value)
    localStorage.setItem('memospace_admin_token', data.token)
    localStorage.setItem('memospace_admin_user', JSON.stringify(data.user))
    await router.push('/admin')
  } catch (error) { message.value=errorMessage(error) }
  finally { busy.value=false }
}
</script>

<template>
  <main class="admin-login-page">
    <router-link class="admin-back" to="/login"><ArrowLeft :size="15" />返回拾光空间</router-link>
    <section class="admin-login-story">
      <span class="admin-seal"><ShieldCheck :size="30" /></span>
      <span class="eyebrow">MEMOSPACE CONTROL ROOM</span>
      <h1>只处理必要的事，<br />守住朋友们的数据。</h1>
      <p>管理员可以协助重置密码和修改 Memo ID。所有操作都会写入审计记录。</p>
    </section>
    <form class="admin-login-card" @submit.prevent="login">
      <LoginModeSwitch admin />
      <span class="eyebrow">ADMINISTRATOR ONLY</span>
      <h2>管理员登录</h2>
      <p>这里使用独立的管理员会话，不会影响普通网站登录。</p>
      <label class="field"><span>管理员账号</span><input v-model="form.username" required autocomplete="username" /></label>
      <label class="field"><span>管理员密码</span><input v-model="form.password" required type="password" autocomplete="current-password" placeholder="请输入管理员密码" /></label>
      <p v-if="message" class="form-error">{{message}}</p>
      <button class="button primary" :disabled="busy"><KeyRound :size="16" />{{busy?'正在验证…':'进入管理员中心'}}</button>
      <small>普通用户即使知道这个地址，也会被后端权限拦截。</small>
    </form>
  </main>
</template>

<style scoped>
.admin-login-page{min-height:100vh;padding:7vw;display:grid;grid-template-columns:1fr minmax(360px,470px);align-items:center;gap:8vw;color:#ede9e4;background:radial-gradient(circle at 15% 18%,rgba(151,111,104,.22),transparent 25rem),linear-gradient(145deg,#252831,#343640 52%,#443637)}.admin-back{position:absolute;left:32px;top:28px;display:flex;align-items:center;gap:6px;color:rgba(255,255,255,.7);font-size:12px}.admin-login-story{max-width:620px}.admin-seal{width:64px;height:64px;margin-bottom:35px;display:grid;place-items:center;border:1px solid rgba(255,255,255,.28);border-radius:21px;background:rgba(255,255,255,.08)}.admin-login-story h1{margin:15px 0 20px;font-size:clamp(42px,5vw,68px);line-height:1.25}.admin-login-story p{max-width:540px;color:rgba(255,255,255,.67);line-height:1.9}.admin-login-card{padding:40px;color:var(--ink);background:#f8f5ef;border:1px solid rgba(255,255,255,.5);border-radius:31px;box-shadow:0 35px 90px rgba(8,9,13,.34)}.admin-login-card h2{margin:9px 0 8px;font-size:31px}.admin-login-card>p{margin:0 0 25px;color:var(--muted);font-size:12px;line-height:1.7}.admin-login-card .field{margin-top:13px}.admin-login-card .button{width:100%;margin-top:18px;display:flex;align-items:center;justify-content:center;gap:8px}.admin-login-card>small{display:block;margin-top:17px;color:var(--muted);text-align:center;line-height:1.6}@media(max-width:850px){.admin-login-page{padding:90px 20px 35px;grid-template-columns:1fr}.admin-login-story{display:none}.admin-login-card{width:min(470px,100%);margin:auto}}
</style>
