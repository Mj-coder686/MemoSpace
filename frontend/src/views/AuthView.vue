<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, CheckCircle2, Server, Wifi, WifiOff } from 'lucide-vue-next'
import { useAuthStore } from '../stores/auth'
import { errorMessage } from '../api/http'
import UiBanner from '../components/ui/UiBanner.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiInput from '../components/ui/UiInput.vue'
import { isNativeApp, PRODUCTION_SERVER_ORIGIN, saveServerOrigin, savedServerOrigin } from '../utils/serverConnection'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const registering = computed(() => route.path === '/register')
const native = isNativeApp()
const form = ref({ username: '', password: '', nickname: '' })
const busy = ref(false)
const submitted = ref(false)
const message = ref('')
const connectionIssue = ref(false)
const successMessage = ref('')
const online = ref(typeof navigator === 'undefined' ? true : navigator.onLine)
const serverOpen = ref(false)
const serverAddress = ref(savedServerOrigin())
const serverMessage = ref('')
const serverError = ref('')

const banned = computed(() => route.query.reason === 'banned')
const usernameError = computed(() => {
  if (!submitted.value) return ''
  if (!form.value.username.trim()) return '请输入用户名'
  if (registering.value && !/^[a-zA-Z0-9_]{3,24}$/.test(form.value.username)) return '请使用 3–24 位字母、数字或下划线'
  return ''
})
const passwordError = computed(() => {
  if (!submitted.value) return ''
  if (!form.value.password) return '请输入密码'
  if (form.value.password.length < 8) return '密码至少需要 8 位'
  return ''
})
const nicknameError = computed(() => submitted.value && registering.value && !form.value.nickname.trim() ? '请输入你希望被称呼的名字' : '')
const invalid = computed(() => Boolean(usernameError.value || passwordError.value || nicknameError.value))

const updateNetworkState = () => { online.value = navigator.onLine }

onMounted(() => {
  window.addEventListener('online', updateNetworkState)
  window.addEventListener('offline', updateNetworkState)
})

onBeforeUnmount(() => {
  window.removeEventListener('online', updateNetworkState)
  window.removeEventListener('offline', updateNetworkState)
})

watch(registering, () => {
  submitted.value = false
  message.value = ''
  connectionIssue.value = false
  successMessage.value = ''
})

const saveServer = () => {
  serverMessage.value = ''
  serverError.value = ''
  try {
    const origin = saveServerOrigin(serverAddress.value)
    serverAddress.value = origin
    serverMessage.value = `连接地址已保存：${origin}`
  } catch (error) {
    serverError.value = error instanceof Error ? error.message : '服务器地址格式不正确'
  }
}

const submit = async () => {
  submitted.value = true
  message.value = ''
  connectionIssue.value = false
  successMessage.value = ''
  if (invalid.value || !online.value) return

  busy.value = true
  try {
    if (registering.value) {
      await auth.register(form.value.username.trim(), form.value.password, form.value.nickname.trim())
      successMessage.value = '私人空间已经创建，正在带你进入……'
    } else {
      await auth.login(form.value.username.trim(), form.value.password)
    }
    await router.push('/home')
  } catch (error: any) {
    const detail = errorMessage(error)
    connectionIssue.value = !error?.response
    message.value = detail === '管理员账号请从管理员入口登录'
      ? '这是管理员账号，请点击页面底部的“管理员入口”登录。'
      : detail
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="identity-page">
    <section class="identity-visual" aria-labelledby="identity-brand-title">
      <div class="identity-visual__overlay" />
      <div class="identity-brand">
        <span class="identity-brand__mark" aria-hidden="true"><i /><i /></span>
        <span class="identity-brand__name">拾光空间</span>
        <span class="identity-brand__latin">MEMOSPACE</span>
      </div>
      <div class="identity-story">
        <span class="identity-kicker">A LIVING MEMORY ATLAS</span>
        <h1 id="identity-brand-title" class="font-display">把走过的日子，<br />留在彼此身边。</h1>
        <p>收藏自己的生活，也和重要的人共同建立一段只属于彼此的记忆空间。</p>
        <span class="identity-story__note">记忆 · 陪伴 · 关系 · 生活记录</span>
      </div>
    </section>

    <section class="identity-panel" aria-label="账号入口">
      <Transition name="identity-form" mode="out-in">
        <form :key="registering ? 'register' : 'login'" class="identity-card" novalidate @submit.prevent="submit">
          <header class="identity-card__header">
            <span class="identity-kicker">{{ registering ? 'BEGIN YOUR ARCHIVE' : 'WELCOME BACK' }}</span>
            <h2>{{ registering ? '创建你的记忆空间' : '欢迎回来' }}</h2>
            <p>{{ registering ? '从今天开始，认真收藏生活。' : '继续翻阅那些值得记住的日子。' }}</p>
          </header>

          <div class="identity-card__alerts">
            <UiBanner
              v-if="banned"
              tone="danger"
              title="这个账号目前无法登录"
              description="账号已被封禁。如需核对原因，请联系管理员处理。"
            />
            <UiBanner
              v-if="!online"
              tone="warning"
              title="当前处于离线状态"
              description="连接网络后即可登录或创建账号，你已填写的内容会保留。"
            >
              <template #actions><WifiOff :size="18" aria-hidden="true" /></template>
            </UiBanner>
            <UiBanner
              v-if="message"
              :tone="connectionIssue ? 'warning' : 'danger'"
              :title="connectionIssue ? '服务器暂时不可达' : '没有完成登录'"
              :description="message"
            />
            <UiBanner v-if="successMessage" tone="success" title="创建成功" :description="successMessage" />
          </div>

          <section v-if="native" class="identity-server" aria-label="服务器连接">
            <button
              type="button"
              class="identity-server__summary"
              :aria-expanded="serverOpen"
              aria-controls="server-connection-panel"
              @click="serverOpen = !serverOpen"
            >
              <span class="identity-server__status"><Wifi :size="17" aria-hidden="true" />连接设置</span>
              <span class="identity-server__origin">{{ serverAddress }}</span>
              <span class="identity-server__action">{{ serverOpen ? '收起' : '更改' }}</span>
            </button>
            <div v-if="serverOpen" id="server-connection-panel" class="identity-server__body">
              <p>仅在迁移服务器时更改。正式地址为 {{ PRODUCTION_SERVER_ORIGIN }}，自定义地址必须使用 HTTPS。</p>
              <UiInput
                v-model="serverAddress"
                label="HTTPS 服务器地址"
                name="server-origin"
                inputmode="url"
                autocomplete="url"
                placeholder="https://example.com"
                :error="serverError"
              />
              <div class="identity-server__footer">
                <UiButton type="button" size="sm" variant="tonal" @click="saveServer"><Server :size="16" />保存连接</UiButton>
                <span v-if="serverMessage" class="identity-server__saved"><CheckCircle2 :size="15" />{{ serverMessage }}</span>
              </div>
            </div>
          </section>

          <div class="identity-card__fields">
            <UiInput
              v-if="registering"
              v-model="form.nickname"
              label="怎么称呼你"
              name="nickname"
              autocomplete="nickname"
              placeholder="你的昵称"
              :error="nicknameError"
              required
            />
            <UiInput
              v-model="form.username"
              label="用户名"
              name="username"
              autocomplete="username"
              :placeholder="registering ? '3–24 位字母、数字或下划线' : '请输入用户名'"
              :error="usernameError"
              required
            />
            <UiInput
              v-model="form.password"
              label="密码"
              name="password"
              type="password"
              :autocomplete="registering ? 'new-password' : 'current-password'"
              placeholder="至少 8 位"
              :error="passwordError"
              revealable
              required
            />
          </div>

          <UiButton
            class="identity-card__submit"
            type="submit"
            variant="primary"
            size="lg"
            block
            :loading="busy"
            :loading-text="registering ? '正在创建空间' : '正在打开空间'"
            :disabled="!online || banned"
          >
            {{ registering ? '开始记录' : '进入拾光空间' }}<ArrowRight :size="18" aria-hidden="true" />
          </UiButton>

          <p class="identity-card__switch">
            {{ registering ? '已经有账号？' : '第一次来到这里？' }}
            <router-link :to="registering ? '/login' : '/register'">{{ registering ? '直接登录' : '创建账号' }}</router-link>
          </p>

          <footer class="identity-card__footer">
            <span>你的私人内容不会向管理员开放。</span>
            <router-link to="/admin/login">管理员入口</router-link>
          </footer>
        </form>
      </Transition>
    </section>
  </main>
</template>
