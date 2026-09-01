<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Capacitor } from '@capacitor/core'
import { useRouter } from 'vue-router'
import { Copy, Fingerprint, ImagePlus, LockKeyhole, Palette, Sparkles, Trash2, Upload } from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import { useAuthStore } from '../stores/auth'
import { useRealtimeStore } from '../stores/realtime'
import PrivateMedia from '../components/PrivateMedia.vue'
import UserAvatar from '../components/UserAvatar.vue'
import { imageLuminance, loadAppearance } from '../utils/appearance'
import { chooseNativeImage } from '../utils/nativeImagePicker'

const auth = useAuthStore();const realtime = useRealtimeStore();const router = useRouter()
const form = ref({ nickname:'', bio:'', location:'', gender:'', birthday:'' })
const message = ref('');const busy=ref(false);const avatarFile=ref<File|null>(null);const avatarPreview=ref('')
const nativeApp=Capacitor.isNativePlatform();const nativePickerBusy=ref(false)
const theme = ref(localStorage.getItem('memospace_mode') || 'system')
const appearance=ref({ backgroundColor:'#f5f2ec',backgroundFileId:null as number|null,backgroundBrightness:100,backgroundOverlay:0,clearBackgroundImage:false })
const backgroundFile=ref<File|null>(null);const backgroundPreview=ref('');const appearanceBusy=ref(false)
const memoId = computed(() => auth.user?.publicId || auth.user?.public_id || '')

const fillForm=()=>{if(auth.user)form.value={nickname:auth.user.nickname,bio:auth.user.bio||'',location:auth.user.location||'',gender:auth.user.gender||'',birthday:auth.user.birthday||''}}
const upload=async(file:File)=>{const body=new FormData();body.append('file',file);return Number((await http.post('/files',body)).data.id)}
const setAvatar=(file:File|null)=>{avatarFile.value=file;if(avatarPreview.value)URL.revokeObjectURL(avatarPreview.value);avatarPreview.value=file?URL.createObjectURL(file):''}
const chooseAvatar=(event:Event)=>setAvatar((event.target as HTMLInputElement).files?.[0]||null)
const chooseNativeAvatar=async()=>{nativePickerBusy.value=true;message.value='';try{const file=await chooseNativeImage('avatar');if(file)setAvatar(file)}catch(error){message.value=errorMessage(error)}finally{nativePickerBusy.value=false}}
const saveProfile=async()=>{
  busy.value=true;message.value=''
  try{await http.put('/users/me',{...form.value,birthday:form.value.birthday||null,avatar:undefined});if(avatarFile.value){const fileId=await upload(avatarFile.value);await http.put('/users/me/avatar',{fileId})}await auth.loadMe();fillForm();avatarFile.value=null;message.value='个人资料和头像已保存。'}catch(error){message.value=errorMessage(error)}finally{busy.value=false}
}
const setMode=(mode:string)=>{theme.value=mode;localStorage.setItem('memospace_mode',mode);if(mode==='system')delete document.documentElement.dataset.mode;else document.documentElement.dataset.mode=mode}
const copyMemoId=async()=>{if(!memoId.value)return;await navigator.clipboard.writeText(memoId.value);message.value='Memo ID 已复制，可以发给想添加你的朋友。'}
const chooseBackground=async(event:Event)=>{
  const file=(event.target as HTMLInputElement).files?.[0]||null;backgroundFile.value=file
  if(backgroundPreview.value)URL.revokeObjectURL(backgroundPreview.value);backgroundPreview.value=file?URL.createObjectURL(file):''
  if(file)await autoBalance(file)
}
const chooseNativeBackground=async()=>{nativePickerBusy.value=true;message.value='';try{const file=await chooseNativeImage('background');if(!file)return;backgroundFile.value=file;if(backgroundPreview.value)URL.revokeObjectURL(backgroundPreview.value);backgroundPreview.value=URL.createObjectURL(file);await autoBalance(file)}catch(error){message.value=errorMessage(error)}finally{nativePickerBusy.value=false}}
const autoBalance=async(file=backgroundFile.value)=>{
  if(!file){appearance.value.backgroundBrightness=72;appearance.value.backgroundOverlay=22;message.value='已使用通用的清晰度设置。';return}
  try{const light=await imageLuminance(file);appearance.value.backgroundBrightness=light>.68?52:light>.45?72:light>.25?92:112;appearance.value.backgroundOverlay=light>.55?24:light>.3?16:8;message.value='已根据图片平均亮度自动优化背景。'}catch{message.value='无法分析图片，仍可手动调节亮度。'}
}
const saveAppearance=async()=>{
  appearanceBusy.value=true;message.value=''
  try{let backgroundFileId: number|undefined;if(backgroundFile.value)backgroundFileId=await upload(backgroundFile.value);const {data}=await http.put('/users/me/appearance',{backgroundColor:appearance.value.backgroundColor,backgroundFileId,backgroundBrightness:appearance.value.backgroundBrightness,backgroundOverlay:appearance.value.backgroundOverlay,clearBackgroundImage:appearance.value.clearBackgroundImage});appearance.value={backgroundColor:data.background_color,backgroundFileId:data.background_file_id||null,backgroundBrightness:Number(data.background_brightness),backgroundOverlay:Number(data.background_overlay),clearBackgroundImage:false};backgroundFile.value=null;await loadAppearance();message.value='全站背景已经保存，并会跟随这个账号。'}catch(error){message.value=errorMessage(error)}finally{appearanceBusy.value=false}
}
const clearBackground=()=>{backgroundFile.value=null;if(backgroundPreview.value)URL.revokeObjectURL(backgroundPreview.value);backgroundPreview.value='';appearance.value.backgroundFileId=null;appearance.value.clearBackgroundImage=true}
const logout=()=>{realtime.disconnect();auth.logout();router.push('/login')}

onMounted(async()=>{await auth.loadMe();fillForm();try{const data:any=await loadAppearance();appearance.value={backgroundColor:data.background_color||'#f5f2ec',backgroundFileId:data.background_file_id||null,backgroundBrightness:Number(data.background_brightness??100),backgroundOverlay:Number(data.background_overlay??0),clearBackgroundImage:false}}catch(error){message.value=errorMessage(error)}})
onBeforeUnmount(()=>{if(avatarPreview.value)URL.revokeObjectURL(avatarPreview.value);if(backgroundPreview.value)URL.revokeObjectURL(backgroundPreview.value)})
</script>

<template>
  <header class="page-heading"><div><span class="eyebrow">MAKE IT YOURS</span><h1>设置</h1><p>整理个人资料，也把整个拾光空间调整成你喜欢的样子。</p></div></header>
  <p v-if="message" class="relationship-notice success" role="status">{{message}}</p>
  <div class="settings-layout">
    <div class="settings-main">
      <section class="panel memo-identity-panel"><span class="identity-icon"><Fingerprint :size="25" /></span><div><span class="eyebrow">YOUR MEMO ID</span><h2>{{memoId||'正在获取…'}}</h2><p>注册时自动生成的 12 位纯数字代号，昵称和头像改变后仍然不变。</p></div><button class="button" :disabled="!memoId" @click="copyMemoId"><Copy :size="16" /> 复制</button><small><LockKeyhole :size="13" /> Memo ID 目前不可修改，避免旧好友失去与你的联系。</small></section>

      <section class="panel profile-editor">
        <div class="settings-section-heading"><div><span class="eyebrow">PROFILE</span><h2>编辑个人资料</h2></div><UserAvatar class="settings-avatar" :src="avatarPreview||auth.user?.avatar" :name="form.nickname" /></div>
        <button v-if="nativeApp" type="button" class="profile-avatar-picker" :disabled="nativePickerBusy" @click="chooseNativeAvatar"><Upload :size="18" /><span><b>{{nativePickerBusy?'正在打开相册…':avatarFile?.name||'从手机相册选择头像'}}</b><small>只读取你主动选择的这一张图片。</small></span></button>
        <label v-else class="profile-avatar-picker"><Upload :size="18" /><span><b>{{avatarFile?.name||'上传新头像'}}</b><small>支持 JPEG、PNG、WebP 或 GIF，保存后好友也能看到。</small></span><input type="file" accept="image/*" @change="chooseAvatar" /></label>
        <div class="field-row"><label class="field"><span>昵称</span><input v-model="form.nickname" maxlength="60" /></label><label class="field"><span>所在城市</span><input v-model="form.location" maxlength="120" /></label></div>
        <div class="field-row"><label class="field"><span>性别（可不填）</span><select v-model="form.gender"><option value="">不设置</option><option value="FEMALE">女</option><option value="MALE">男</option><option value="OTHER">其他</option></select></label><label class="field"><span>生日（可不填）</span><input v-model="form.birthday" type="date" /></label></div>
        <label class="field"><span>个人签名</span><textarea v-model="form.bio" maxlength="300" rows="4" placeholder="写一句介绍自己或记录此刻的话"></textarea></label>
        <button class="button primary" :disabled="busy||!form.nickname.trim()" @click="saveProfile">{{busy?'正在保存…':'保存个人资料'}}</button>
      </section>

      <section class="panel appearance-editor">
        <div class="settings-section-heading"><div><span class="eyebrow">PERSONAL CANVAS</span><h2>全站背景</h2><p>背景设置保存在账号里，换设备登录后仍然生效。</p></div><span class="identity-icon"><Palette :size="22" /></span></div>
        <div class="appearance-preview" :style="{backgroundColor:appearance.backgroundColor}"><img v-if="backgroundPreview" :src="backgroundPreview" alt="新背景预览" :style="{filter:`brightness(${appearance.backgroundBrightness}%)`} "/><PrivateMedia v-else-if="appearance.backgroundFileId" :file-id="appearance.backgroundFileId" mime-type="image/*" alt="当前背景" preview /><span v-else><ImagePlus :size="25" /> 纯色背景</span><i :style="{opacity:appearance.backgroundOverlay/100}"></i></div>
        <div class="appearance-controls"><label class="field color-field"><span>背景颜色</span><input v-model="appearance.backgroundColor" type="color" /></label><label class="range-field"><span>图片亮度 <b>{{appearance.backgroundBrightness}}%</b></span><input v-model.number="appearance.backgroundBrightness" type="range" min="25" max="130" /></label><label class="range-field"><span>暗色遮罩 <b>{{appearance.backgroundOverlay}}%</b></span><input v-model.number="appearance.backgroundOverlay" type="range" min="0" max="85" /></label></div>
        <div class="appearance-actions"><button v-if="nativeApp" type="button" class="button" :disabled="nativePickerBusy" @click="chooseNativeBackground"><ImagePlus :size="16" />{{nativePickerBusy?'正在打开相册…':'从手机相册选择背景'}}</button><label v-else class="button"><ImagePlus :size="16" />选择背景图<input type="file" accept="image/*" @change="chooseBackground" /></label><button class="button" @click="autoBalance()"><Sparkles :size="16" />自动优化亮度</button><button v-if="backgroundPreview||appearance.backgroundFileId" class="button" @click="clearBackground"><Trash2 :size="16" />移除图片</button></div>
        <button class="button primary" :disabled="appearanceBusy" @click="saveAppearance">{{appearanceBusy?'正在应用…':'保存全站背景'}}</button>
      </section>
    </div>
    <aside class="settings-side"><section class="panel"><span class="eyebrow">APPEARANCE</span><h2>显示模式</h2><div class="filters"><button v-for="item in [{v:'light',l:'浅色'},{v:'dark',l:'深色'},{v:'system',l:'跟随系统'}]" :key="item.v" :class="{active:theme===item.v}" @click="setMode(item.v)">{{item.l}}</button></div><p>显示模式负责文字和卡片颜色；自定义背景负责页面底图，两者可以同时使用。</p></section><button class="button" @click="logout">退出当前账号</button></aside>
  </div>
</template>

<style scoped>
.settings-section-heading{display:flex;align-items:center;justify-content:space-between;gap:18px;margin-bottom:18px}.settings-section-heading h2{margin:0}.settings-section-heading p{margin:7px 0 0;color:var(--muted);font-size:12px}.settings-avatar{width:78px;height:78px;flex:none;border:4px solid var(--surface-solid);border-radius:50%;color:white;background:linear-gradient(135deg,#8b6160,#5e687d);font:600 23px 'Noto Serif SC',serif;box-shadow:0 8px 20px rgba(55,49,51,.18)}.profile-avatar-picker{width:100%;margin-bottom:15px;padding:14px;display:flex;align-items:center;gap:12px;border:1px dashed var(--control-line);border-radius:16px;color:var(--ink);background:transparent;text-align:left;cursor:pointer}.profile-avatar-picker:disabled{opacity:.62}.profile-avatar-picker span{flex:1}.profile-avatar-picker b,.profile-avatar-picker small{display:block}.profile-avatar-picker small{margin-top:3px;color:var(--muted);font-size:10px}.profile-avatar-picker input,.appearance-actions input{display:none}.appearance-preview{position:relative;height:230px;overflow:hidden;display:grid;place-items:center;border:1px solid var(--line);border-radius:22px;color:var(--muted)}.appearance-preview>img,.appearance-preview :deep(.private-media),.appearance-preview :deep(img){width:100%;height:100%;min-height:0;object-fit:cover;border:0;border-radius:0}.appearance-preview>i{position:absolute;inset:0;background:#15141a;pointer-events:none}.appearance-preview>span{display:grid;place-items:center;gap:7px;font-size:11px}.appearance-controls{margin:16px 0;display:grid;grid-template-columns:130px 1fr 1fr;gap:12px}.color-field input{height:42px;padding:4px}.range-field{display:grid;gap:10px;padding:8px 0}.range-field span{display:flex;justify-content:space-between;color:var(--muted);font-size:11px}.range-field input{width:100%;accent-color:var(--accent-deep)}.appearance-actions{margin-bottom:14px;display:flex;flex-wrap:wrap;gap:8px}.appearance-actions .button{display:inline-flex;align-items:center;gap:6px}@media(max-width:650px){.appearance-controls{grid-template-columns:1fr}.settings-avatar{width:62px;height:62px}}
</style>
