<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Capacitor } from '@capacitor/core'
import { useRouter } from 'vue-router'
import { CheckCircle2, Copy, Fingerprint, ImagePlus, LockKeyhole, LogOut, Palette, Server, ShieldCheck, Sparkles, Trash2, Upload } from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import { useAuthStore } from '../stores/auth'
import { useRealtimeStore } from '../stores/realtime'
import PrivateMedia from '../components/PrivateMedia.vue'
import UserAvatar from '../components/UserAvatar.vue'
import { UiBanner, UiButton, UiDialog, UiInput, UiSelect, UiSkeleton, UiTextarea } from '../components/ui'
import { imageLuminance, loadAppearance } from '../utils/appearance'
import { chooseNativeImage } from '../utils/nativeImagePicker'
import { PRODUCTION_SERVER_ORIGIN, saveServerOrigin, savedServerOrigin } from '../utils/serverConnection'

type AppearanceForm = { backgroundColor: string; backgroundFileId: number | null; backgroundBrightness: number; backgroundOverlay: number; clearBackgroundImage: boolean }
type SettingsSection = 'identity' | 'appearance' | 'security' | 'connection' | 'session'

const auth = useAuthStore()
const realtime = useRealtimeStore()
const router = useRouter()
const nativeApp = Capacitor.isNativePlatform()
const loading = ref(true)
const activeSection = ref<SettingsSection>('identity')
const profileBusy = ref(false)
const appearanceBusy = ref(false)
const passwordBusy = ref(false)
const nativePickerBusy = ref(false)
const pageMessage = ref('')
const pageError = ref('')
const avatarFile = ref<File | null>(null)
const avatarPreview = ref('')
const backgroundFile = ref<File | null>(null)
const backgroundPreview = ref('')
const logoutDialog = ref(false)
const theme = ref(localStorage.getItem('memospace_mode') || 'system')
const serverAddress = ref(savedServerOrigin())
const passwordForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const form = ref({ nickname: '', bio: '', location: '', gender: '', birthday: '' })
const defaultBackgroundColor = getComputedStyle(document.documentElement).getPropertyValue('--color-bg-canvas').trim()
const appearance = ref<AppearanceForm>({ backgroundColor: defaultBackgroundColor, backgroundFileId: null, backgroundBrightness: 100, backgroundOverlay: 0, clearBackgroundImage: false })
const savedAppearance = ref<AppearanceForm>({ ...appearance.value })
const memoId = computed(() => auth.user?.publicId || auth.user?.public_id || '')
const passwordError = computed(() => {
  if (!passwordForm.value.newPassword) return ''
  if (passwordForm.value.newPassword.length < 8) return '新密码至少需要 8 位'
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) return '两次输入的新密码不一致'
  return ''
})
const sectionItems = computed(() => [
  { id: 'identity' as const, label: '账号身份' },
  { id: 'appearance' as const, label: '外观' },
  { id: 'security' as const, label: '安全' },
  ...(nativeApp ? [{ id: 'connection' as const, label: '连接' }] : []),
  { id: 'session' as const, label: '会话' },
])
const selectSection = (id: SettingsSection) => { activeSection.value = id; document.getElementById(`settings-${id}`)?.scrollIntoView({ behavior: 'smooth' }) }

const success = (value: string) => { pageMessage.value = value; pageError.value = '' }
const fail = (error: unknown) => { pageError.value = errorMessage(error); pageMessage.value = '' }
const fillForm = () => {
  if (!auth.user) return
  form.value = { nickname: auth.user.nickname, bio: auth.user.bio || '', location: auth.user.location || '', gender: auth.user.gender || '', birthday: auth.user.birthday || '' }
}
const upload = async (file: File) => { const body = new FormData(); body.append('file', file); return Number((await http.post('/files', body)).data.id) }
const revoke = (value: string) => { if (value) URL.revokeObjectURL(value) }
const setAvatar = (file: File | null) => { avatarFile.value = file; revoke(avatarPreview.value); avatarPreview.value = file ? URL.createObjectURL(file) : '' }
const chooseAvatar = (event: Event) => setAvatar((event.target as HTMLInputElement).files?.[0] || null)
const chooseNativeAvatar = async () => {
  nativePickerBusy.value = true; pageError.value = ''
  try { const file = await chooseNativeImage('avatar'); if (file) setAvatar(file) } catch (error) { fail(error) } finally { nativePickerBusy.value = false }
}
const saveProfile = async () => {
  if (!form.value.nickname.trim()) return
  profileBusy.value = true; pageError.value = ''
  try {
    await http.put('/users/me', { ...form.value, birthday: form.value.birthday || null, avatar: undefined })
    if (avatarFile.value) { const fileId = await upload(avatarFile.value); await http.put('/users/me/avatar', { fileId }) }
    await auth.loadMe(); fillForm(); setAvatar(null); success('个人资料和头像已保存。')
  } catch (error) { fail(error) } finally { profileBusy.value = false }
}
const setMode = (mode: string) => {
  theme.value = mode; localStorage.setItem('memospace_mode', mode)
  if (mode === 'system') delete document.documentElement.dataset.mode; else document.documentElement.dataset.mode = mode
}
const copyMemoId = async () => {
  if (!memoId.value) return
  try { await navigator.clipboard.writeText(memoId.value); success('Memo ID 已复制，可以发给想添加你的朋友。') } catch { pageError.value = '无法复制 Memo ID，请手动选择并复制。' }
}
const setBackgroundFile = async (file: File | null) => {
  backgroundFile.value = file; revoke(backgroundPreview.value); backgroundPreview.value = file ? URL.createObjectURL(file) : ''
  if (file) await autoBalance(file)
}
const chooseBackground = async (event: Event) => setBackgroundFile((event.target as HTMLInputElement).files?.[0] || null)
const chooseNativeBackground = async () => {
  nativePickerBusy.value = true; pageError.value = ''
  try { const file = await chooseNativeImage('background'); if (file) await setBackgroundFile(file) } catch (error) { fail(error) } finally { nativePickerBusy.value = false }
}
const autoBalance = async (file = backgroundFile.value) => {
  if (!file) { appearance.value.backgroundBrightness = 72; appearance.value.backgroundOverlay = 22; success('已使用通用的清晰度设置。'); return }
  try {
    const light = await imageLuminance(file)
    appearance.value.backgroundBrightness = light > .68 ? 52 : light > .45 ? 72 : light > .25 ? 92 : 112
    appearance.value.backgroundOverlay = light > .55 ? 24 : light > .3 ? 16 : 8
    success('已根据图片平均亮度自动优化背景。')
  } catch { pageError.value = '无法分析图片，仍可手动调节亮度。' }
}
const clearBackground = () => { void setBackgroundFile(null); appearance.value.backgroundFileId = null; appearance.value.clearBackgroundImage = true }
const saveAppearance = async () => {
  appearanceBusy.value = true; pageError.value = ''
  try {
    let backgroundFileId: number | undefined
    if (backgroundFile.value) backgroundFileId = await upload(backgroundFile.value)
    const { data } = await http.put('/users/me/appearance', { backgroundColor: appearance.value.backgroundColor, backgroundFileId, backgroundBrightness: appearance.value.backgroundBrightness, backgroundOverlay: appearance.value.backgroundOverlay, clearBackgroundImage: appearance.value.clearBackgroundImage })
    appearance.value = { backgroundColor: data.background_color, backgroundFileId: data.background_file_id || null, backgroundBrightness: Number(data.background_brightness), backgroundOverlay: Number(data.background_overlay), clearBackgroundImage: false }
    savedAppearance.value = { ...appearance.value }; await setBackgroundFile(null); await loadAppearance(); success('全站背景已经保存，并会跟随这个账号。')
  } catch (error) {
    appearance.value = { ...savedAppearance.value }; await setBackgroundFile(null); fail(error)
  } finally { appearanceBusy.value = false }
}
const savePassword = async () => {
  if (!passwordForm.value.oldPassword || passwordError.value || !passwordForm.value.newPassword) return
  passwordBusy.value = true; pageError.value = ''
  try {
    await http.put('/users/me/password', { oldPassword: passwordForm.value.oldPassword, newPassword: passwordForm.value.newPassword })
    passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
    success('密码已更新。下次登录请使用新密码，当前设备会话仍然保留。')
  } catch (error) { fail(error) } finally { passwordBusy.value = false }
}
const saveServer = () => {
  try { serverAddress.value = saveServerOrigin(serverAddress.value); success(`连接地址已保存：${serverAddress.value}`) } catch (error) { pageError.value = error instanceof Error ? error.message : '服务器地址格式不正确' }
}
const logout = () => { realtime.disconnect(); auth.logout(); logoutDialog.value = false; void router.push('/login') }

onMounted(async () => {
  try {
    await auth.loadMe(); fillForm()
    const data: any = await loadAppearance()
    appearance.value = { backgroundColor: data.background_color || defaultBackgroundColor, backgroundFileId: data.background_file_id || null, backgroundBrightness: Number(data.background_brightness ?? 100), backgroundOverlay: Number(data.background_overlay ?? 0), clearBackgroundImage: false }
    savedAppearance.value = { ...appearance.value }
  } catch (error) { fail(error) } finally { loading.value = false }
})
onBeforeUnmount(() => { revoke(avatarPreview.value); revoke(backgroundPreview.value) })
</script>

<template>
  <main class="settings-page">
    <header class="relationship-domain-header"><div><span class="memory-kicker">MAKE IT YOURS</span><h1>设置</h1><p>管理你的公开身份、私人外观与账号安全；每一项都有清楚的保存边界。</p></div></header>
    <UiBanner v-if="pageMessage" tone="success" title="设置已更新" :description="pageMessage" />
    <UiBanner v-if="pageError && !loading" tone="danger" title="设置没有保存" :description="pageError" />
    <div v-if="loading" class="settings-loading"><UiSkeleton width="220px" height="360px" radius="var(--radius-md)" /><div><UiSkeleton height="220px" radius="var(--radius-lg)" /><UiSkeleton height="420px" radius="var(--radius-lg)" /></div></div>
    <div v-else class="settings-layout-v2">
      <aside class="settings-nav" aria-label="设置分组"><p>设置分组</p><button v-for="item in sectionItems" :key="item.id" :class="{active:activeSection===item.id}" @click="selectSection(item.id)">{{ item.label }}</button></aside>
      <div class="settings-content">
        <section id="settings-identity" class="settings-section" :class="{'is-active':activeSection==='identity'}">
          <header><span class="settings-section-icon"><Fingerprint :size="20" /></span><div><h2>账号身份</h2><p>这里的昵称和头像会被好友看到；Memo ID 是稳定的添加代号。</p></div></header>
          <div class="memo-identity-row"><div><span>MEMO ID</span><strong>{{ memoId || '正在获取…' }}</strong><p><LockKeyhole :size="13" />昵称和头像改变后，它仍然保持不变。</p></div><UiButton variant="tonal" :disabled="!memoId" @click="copyMemoId"><Copy :size="15" />复制 ID</UiButton></div>
          <div class="settings-profile-head"><UserAvatar :src="avatarPreview || auth.user?.avatar" :name="form.nickname" /><div><strong>{{ form.nickname || '你的头像' }}</strong><p>支持 JPEG、PNG、WebP 或 GIF。</p></div></div>
          <button v-if="nativeApp" type="button" class="settings-file-picker" :disabled="nativePickerBusy" @click="chooseNativeAvatar"><Upload :size="18" /><span><b>{{ nativePickerBusy ? '正在打开相册…' : avatarFile?.name || '从手机相册选择头像' }}</b><small>只读取你主动选择的这一张图片</small></span></button>
          <label v-else class="settings-file-picker"><Upload :size="18" /><span><b>{{ avatarFile?.name || '选择新头像' }}</b><small>保存后好友和公开主页会同步更新</small></span><input type="file" accept="image/*" @change="chooseAvatar" /></label>
          <div class="settings-form-grid"><UiInput v-model="form.nickname" label="昵称" name="settings-nickname" :maxlength="60" required /><UiInput v-model="form.location" label="所在城市" name="settings-location" :maxlength="120" /><UiSelect v-model="form.gender" label="性别（可不填）" name="settings-gender"><option value="">不设置</option><option value="FEMALE">女</option><option value="MALE">男</option><option value="OTHER">其他</option></UiSelect><UiInput v-model="form.birthday" label="生日（可不填）" name="settings-birthday" type="date" /><UiTextarea v-model="form.bio" class="settings-wide-field" label="个人签名" name="settings-bio" :maxlength="300" :rows="4" placeholder="写一句介绍自己或记录此刻的话" /></div>
          <footer><UiButton variant="primary" :loading="profileBusy" loading-text="正在保存" :disabled="!form.nickname.trim()" @click="saveProfile">保存个人资料</UiButton></footer>
        </section>

        <section id="settings-appearance" class="settings-section" :class="{'is-active':activeSection==='appearance'}">
          <header><span class="settings-section-icon"><Palette :size="20" /></span><div><h2>外观</h2><p>显示模式立即在本机生效；背景保存成功后才会跟随账号。</p></div></header>
          <fieldset class="settings-mode"><legend>显示模式</legend><div><button v-for="item in [{v:'light',l:'浅色'},{v:'dark',l:'深色'},{v:'system',l:'跟随系统'}]" :key="item.v" :class="{active:theme===item.v}" :aria-pressed="theme===item.v" @click="setMode(item.v)">{{ item.l }}</button></div></fieldset>
          <div class="settings-appearance-preview" :style="{backgroundColor:appearance.backgroundColor}"><img v-if="backgroundPreview" :src="backgroundPreview" alt="新背景预览" :style="{filter:`brightness(${appearance.backgroundBrightness}%)`} "/><PrivateMedia v-else-if="appearance.backgroundFileId" :file-id="appearance.backgroundFileId" mime-type="image/*" alt="当前背景" preview /><span v-else><ImagePlus :size="24" />纯色背景</span><i :style="{opacity:appearance.backgroundOverlay/100}"></i></div>
          <div class="settings-appearance-controls"><label><span>背景颜色</span><input v-model="appearance.backgroundColor" type="color" /></label><label><span>图片亮度 <b>{{ appearance.backgroundBrightness }}%</b></span><input v-model.number="appearance.backgroundBrightness" type="range" min="25" max="130" /></label><label><span>暗色遮罩 <b>{{ appearance.backgroundOverlay }}%</b></span><input v-model.number="appearance.backgroundOverlay" type="range" min="0" max="85" /></label></div>
          <div class="settings-file-actions"><UiButton v-if="nativeApp" variant="secondary" :disabled="nativePickerBusy" @click="chooseNativeBackground"><ImagePlus :size="16" />从手机相册选择背景</UiButton><label v-else class="ui-button ui-button--secondary ui-button--md"><span class="ui-button__content"><ImagePlus :size="16" />选择背景图</span><input type="file" accept="image/*" @change="chooseBackground" /></label><UiButton variant="ghost" @click="autoBalance()"><Sparkles :size="16" />自动优化亮度</UiButton><UiButton v-if="backgroundPreview || appearance.backgroundFileId" variant="ghost" @click="clearBackground"><Trash2 :size="16" />移除图片</UiButton></div>
          <footer><UiButton variant="primary" :loading="appearanceBusy" loading-text="正在应用" @click="saveAppearance">保存外观</UiButton></footer>
        </section>

        <section id="settings-security" class="settings-section" :class="{'is-active':activeSection==='security'}">
          <header><span class="settings-section-icon"><ShieldCheck :size="20" /></span><div><h2>安全</h2><p>旧密码不会显示，也不会被管理员读取。</p></div></header>
          <form class="settings-password-form" @submit.prevent="savePassword"><UiInput v-model="passwordForm.oldPassword" label="当前密码" name="current-password" type="password" autocomplete="current-password" revealable required /><UiInput v-model="passwordForm.newPassword" label="新密码" name="new-password" type="password" autocomplete="new-password" helper="至少 8 位" :error="passwordError" revealable required /><UiInput v-model="passwordForm.confirmPassword" label="再次输入新密码" name="confirm-password" type="password" autocomplete="new-password" :error="passwordError" revealable required /><UiButton type="submit" variant="primary" :loading="passwordBusy" loading-text="正在更新" :disabled="!passwordForm.oldPassword || !passwordForm.newPassword || Boolean(passwordError)">更新密码</UiButton></form>
        </section>

        <section v-if="nativeApp" id="settings-connection" class="settings-section" :class="{'is-active':activeSection==='connection'}">
          <header><span class="settings-section-icon"><Server :size="20" /></span><div><h2>服务器连接</h2><p>仅在服务器迁移时更改；地址必须使用 HTTPS。</p></div></header>
          <UiInput v-model="serverAddress" label="HTTPS 服务器地址" name="settings-server-origin" inputmode="url" autocomplete="url" :placeholder="PRODUCTION_SERVER_ORIGIN" />
          <p class="settings-connection-note">正式地址：{{ PRODUCTION_SERVER_ORIGIN }}。保存后，API、图片和实时消息会使用同一个服务器。</p>
          <footer><UiButton variant="primary" @click="saveServer"><CheckCircle2 :size="16" />保存连接</UiButton></footer>
        </section>

        <section id="settings-session" class="settings-section settings-session" :class="{'is-active':activeSection==='session'}">
          <header><span class="settings-section-icon"><LogOut :size="20" /></span><div><h2>当前会话</h2><p>退出只清除当前设备的登录状态，不会删除账号、记忆或共同空间。</p></div></header>
          <UiButton variant="secondary" @click="logoutDialog=true"><LogOut :size="16" />退出当前账号</UiButton>
        </section>
      </div>
    </div>

    <UiDialog :open="logoutDialog" title="退出当前账号？" description="此设备将返回登录页；所有云端数据都会保留。" @close="logoutDialog=false"><template #actions><UiButton variant="ghost" @click="logoutDialog=false">继续使用</UiButton><UiButton variant="danger" @click="logout">确认退出</UiButton></template></UiDialog>
  </main>
</template>
