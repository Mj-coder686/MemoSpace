<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Sparkles } from 'lucide-vue-next'
import { useAuthStore } from '../stores/auth'
import { errorMessage } from '../api/http'
import LoginModeSwitch from '../components/LoginModeSwitch.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const registering = computed(() => route.path === '/register')
const form = ref({ username: registering.value ? '' : 'demo', password: registering.value ? '' : 'Memo123!', nickname: '' })
const busy = ref(false)
const message = ref('')

const submit = async () => {
  busy.value = true; message.value = ''
  try {
    if (registering.value) await auth.register(form.value.username, form.value.password, form.value.nickname)
    else await auth.login(form.value.username, form.value.password)
    router.push('/home')
  } catch (error) { message.value = errorMessage(error) }
  finally { busy.value = false }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-story">
      <div class="brand" style="color:white"><span class="brand-mark"><Sparkles :size="18" /></span><span><b>拾光空间</b><small style="color:rgba(255,255,255,.55)">MEMOSPACE</small></span></div>
      <div><span class="eyebrow" style="color:#e6d0d3">A PLACE FOR US</span><h1>时间会走远，<br />故事可以留下。</h1><p>收藏自己的生活，也和重要的人共同建立一段只属于彼此的数字空间。</p></div>
      <div class="auth-quote">“后来我才发现，最珍贵的并不是照片本身，而是我们还记得照片之外发生了什么。”</div>
    </section>
    <section class="auth-form-wrap">
      <form class="auth-form" @submit.prevent="submit">
        <LoginModeSwitch v-if="!registering" />
        <span class="eyebrow">{{ registering ? 'CREATE YOUR SPACE' : 'WELCOME BACK' }}</span>
        <h2>{{ registering ? '创建你的记忆空间' : '欢迎回来' }}</h2>
        <p>{{ registering ? '从今天开始，认真收藏生活。' : '继续翻阅那些值得记住的日子。' }}</p>
        <label v-if="registering" class="field"><span>怎么称呼你</span><input v-model="form.nickname" required maxlength="60" placeholder="你的昵称" /></label>
        <label class="field"><span>用户名</span><input v-model="form.username" required autocomplete="username" placeholder="3-24 位字母、数字或下划线" /></label>
        <label class="field"><span>密码</span><input v-model="form.password" required minlength="8" type="password" autocomplete="current-password" placeholder="至少 8 位" /></label>
        <p v-if="message" class="form-error">{{ message }}</p>
        <button class="button primary" :disabled="busy">{{ busy ? '正在打开空间…' : registering ? '开始记录' : '进入拾光空间' }}</button>
        <div class="demo-note" v-if="!registering">演示账号已填好：<b>demo / Memo123!</b><br />也可以使用 mia / Memo123! 查看另一位成员视角。</div>
        <p style="margin-top:20px;text-align:center;font-size:13px">{{ registering ? '已经有账号？' : '第一次来到这里？' }} <router-link class="text-link" :to="registering ? '/login' : '/register'">{{ registering ? '直接登录' : '创建账号' }}</router-link></p>
      </form>
    </section>
  </main>
</template>
