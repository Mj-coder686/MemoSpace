<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Copy, Fingerprint, LockKeyhole } from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import { useAuthStore } from '../stores/auth'
import { useRealtimeStore } from '../stores/realtime'

const auth = useAuthStore()
const realtime = useRealtimeStore()
const router = useRouter()
const form = ref({ nickname: '', bio: '', location: '', avatar: '' })
const message = ref('')
const theme = ref(localStorage.getItem('memospace_mode') || 'system')
const memoId = computed(() => auth.user?.publicId || auth.user?.public_id || '')

const fillForm = () => {
  if (auth.user) form.value = {
    nickname: auth.user.nickname,
    bio: auth.user.bio || '',
    location: auth.user.location || '',
    avatar: auth.user.avatar || ''
  }
}

const save = async () => {
  try {
    await http.put('/users/me', form.value)
    await auth.loadMe()
    fillForm()
    message.value = '个人资料已保存'
  } catch (error) { message.value = errorMessage(error) }
}

const setMode = (mode: string) => {
  theme.value = mode
  localStorage.setItem('memospace_mode', mode)
  document.documentElement.dataset.mode = mode
}

const copyMemoId = async () => {
  if (!memoId.value) return
  await navigator.clipboard.writeText(memoId.value)
  message.value = 'Memo ID 已复制，可以发给想添加你的朋友。'
}

const logout = () => {
  realtime.disconnect()
  auth.logout()
  router.push('/login')
}

onMounted(async () => {
  await auth.loadMe()
  fillForm()
})
</script>

<template>
  <header class="page-heading">
    <div><span class="eyebrow">MAKE IT YOURS</span><h1>设置</h1><p>整理你的身份、个人资料与显示习惯。</p></div>
  </header>
  <div class="settings-layout">
    <div class="settings-main">
      <section class="panel memo-identity-panel">
        <span class="identity-icon"><Fingerprint :size="25" /></span>
        <div><span class="eyebrow">YOUR MEMO ID</span><h2>{{ memoId || '正在获取…' }}</h2><p>注册时自动生成的 12 位纯数字代号。它独立于数据库内部编号，昵称改变后仍然不变。</p></div>
        <button class="button" :disabled="!memoId" @click="copyMemoId"><Copy :size="16" /> 复制</button>
        <small><LockKeyhole :size="13" /> Memo ID 目前不可修改，避免旧好友失去与你的联系。</small>
      </section>
      <section class="panel">
        <span class="eyebrow">PROFILE</span><h2>关于你</h2>
        <label class="field"><span>昵称</span><input v-model="form.nickname" /></label>
        <label class="field"><span>个人签名</span><textarea v-model="form.bio" rows="4"></textarea></label>
        <label class="field"><span>所在城市</span><input v-model="form.location" /></label>
        <label class="field"><span>头像地址</span><input v-model="form.avatar" placeholder="稍后可替换为上传文件" /></label>
        <p v-if="message" class="settings-message" role="status">{{ message }}</p>
        <button class="button primary" @click="save">保存修改</button>
      </section>
    </div>
    <aside class="settings-side">
      <section class="panel">
        <span class="eyebrow">APPEARANCE</span><h2>显示模式</h2>
        <div class="filters"><button v-for="item in [{v:'light',l:'浅色'},{v:'dark',l:'深色'},{v:'system',l:'跟随系统'}]" :key="item.v" :class="{active:theme===item.v}" @click="setMode(item.v)">{{ item.l }}</button></div>
        <p>关系空间仍会保留自己的低饱和主题，深色模式会自动重新平衡明度。</p>
      </section>
      <button class="button" @click="logout">退出当前账号</button>
    </aside>
  </div>
</template>
