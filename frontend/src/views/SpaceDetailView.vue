<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { AlarmClock, Archive, ArrowLeft, ArrowRight, CalendarHeart, ImagePlus, MapPin, MessageCircle, Palette, Pencil, Plus, Send, Sparkles, Trash2, Users } from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import EmptyState from '../components/EmptyState.vue'
import PrivateMedia from '../components/PrivateMedia.vue'
import UserAvatar from '../components/UserAvatar.vue'
import { imageLuminance } from '../utils/appearance'
import { UiBanner, UiButton, UiCheckbox, UiDialog, UiIconButton, UiInput, UiSkeleton } from '../components/ui'

const route = useRoute(); const router = useRouter(); const id = Number(route.params.id)
const space = ref<any>({}); const timeline = ref<any[]>([]); const messages = ref<any[]>([]); const events = ref<any[]>([])
const note = ref(''); const feedback = ref('')
const loading = ref(true); const loadError = ref(''); const archiveDialog = ref(false); const anniversaryDeleteTarget = ref<any | null>(null)
const anniversaryModal = ref(false); const savingAnniversary = ref(false); const editingAnniversaryId = ref<number | null>(null)
const anniversaryForm = ref({ title:'', date:dayjs().format('YYYY-MM-DD'), repeatYearly:true })
const appearanceModal=ref(false);const appearanceBusy=ref(false);const themes=ref<any[]>([]);const backgroundFile=ref<File|null>(null);const backgroundPreview=ref('');const spaceBackground=ref('')
const appearanceForm=ref({name:'',themeId:'',primaryColor:'#7f879e',backgroundColor:'#f7f6f4',textColor:'#373a45',backgroundBrightness:100,backgroundOverlay:18,clearBackgroundImage:false})
const style = computed(() => ({ '--space-primary':space.value.primary_color || '#7f879e','--space-background':space.value.background_color || '#f7f6f4','--space-text':space.value.text_color || '#373a45','--space-muted':space.value.muted_color || '#848691','--space-image':spaceBackground.value?`url("${spaceBackground.value}")`:'none','--space-brightness':`${space.value.background_brightness||100}%`,'--space-overlay':`${(Number(space.value.background_overlay)||0)/100}` }))
const days = computed(() => Math.max(1, dayjs().diff(dayjs(space.value.created_at), 'day')))

const nextOccurrence = (value:string) => {
  const source = dayjs(value); const now = dayjs().startOf('day')
  let next = source.year(now.year())
  if (next.isBefore(now, 'day')) next = next.add(1, 'year')
  return next
}
const countdown = (day:any) => {
  if (!day.repeat_yearly && dayjs(day.anniversary_date).isBefore(dayjs(), 'day')) return '已经珍藏'
  const count = (day.repeat_yearly ? nextOccurrence(day.anniversary_date) : dayjs(day.anniversary_date)).diff(dayjs().startOf('day'), 'day')
  return count === 0 ? '就是今天' : count > 0 ? `还有 ${count} 天` : '已经珍藏'
}

const load = async () => {
  loading.value=true; loadError.value=''
  try {
    const [a,b,c,d] = await Promise.all([http.get(`/spaces/${id}`),http.get(`/spaces/${id}/timeline`),http.get(`/spaces/${id}/messages`),http.get(`/spaces/${id}/events`)])
    space.value=a.data; timeline.value=b.data; messages.value=c.data; events.value=d.data;await loadSpaceBackground()
  } catch(error) { loadError.value=errorMessage(error) }
  finally { loading.value=false }
}
onMounted(load)
onBeforeUnmount(()=>{if(spaceBackground.value)URL.revokeObjectURL(spaceBackground.value);if(backgroundPreview.value)URL.revokeObjectURL(backgroundPreview.value)})
const loadSpaceBackground=async()=>{if(spaceBackground.value)URL.revokeObjectURL(spaceBackground.value);spaceBackground.value='';if(!space.value.background_file_id)return;try{const{data}=await http.get(`/files/${space.value.background_file_id}/content`,{responseType:'blob'});spaceBackground.value=URL.createObjectURL(data)}catch{feedback.value='共享空间背景暂时无法读取。'}}
const leave = async () => { if(!note.value.trim())return; try { await http.post(`/spaces/${id}/messages`,{content:note.value});note.value='';messages.value=(await http.get(`/spaces/${id}/messages`)).data } catch(error) { feedback.value=errorMessage(error) } }
const archive = async () => {
  if (!space.value.relationship_id) return
  try { await http.delete(`/relationships/${space.value.relationship_id}`); archiveDialog.value=false; await load() } catch(e){ feedback.value=errorMessage(e) }
}
const openAnniversary = (day?:any) => {
  editingAnniversaryId.value = day ? Number(day.id) : null
  anniversaryForm.value = day
    ? { title:day.title, date:dayjs(day.anniversary_date).format('YYYY-MM-DD'), repeatYearly:Boolean(day.repeat_yearly) }
    : { title:'', date:dayjs().format('YYYY-MM-DD'), repeatYearly:true }
  feedback.value=''; anniversaryModal.value=true
}
const saveAnniversary = async () => {
  if (!anniversaryForm.value.title.trim() || !anniversaryForm.value.date) return
  savingAnniversary.value=true; feedback.value=''
  const body = { title:anniversaryForm.value.title.trim(), date:anniversaryForm.value.date, repeatYearly:anniversaryForm.value.repeatYearly }
  try {
    if (editingAnniversaryId.value) await http.put(`/spaces/${id}/anniversaries/${editingAnniversaryId.value}`, body)
    else await http.post(`/spaces/${id}/anniversaries`, body)
    anniversaryModal.value=false; await load()
  } catch(e) { feedback.value=errorMessage(e) }
  finally { savingAnniversary.value=false }
}
const removeAnniversary = async (day:any) => {
  try { await http.delete(`/spaces/${id}/anniversaries/${day.id}`); anniversaryDeleteTarget.value=null; await load() }
  catch(e) { feedback.value=errorMessage(e) }
}
const createReminder = (day:any) => router.push({ path:'/reminders', query:{
  relationship:String(space.value.relationship_id), title:day.title, kind:'ANNIVERSARY',
  date:dayjs(day.anniversary_date).format('YYYY-MM-DD')
} })
const openAppearance=async()=>{try{if(!themes.value.length)themes.value=(await http.get('/spaces/themes')).data;appearanceForm.value={name:space.value.name||'',themeId:String(space.value.theme_id||''),primaryColor:space.value.primary_color||appearanceForm.value.primaryColor,backgroundColor:space.value.background_color||appearanceForm.value.backgroundColor,textColor:space.value.text_color||appearanceForm.value.textColor,backgroundBrightness:Number(space.value.background_brightness||100),backgroundOverlay:Number(space.value.background_overlay??18),clearBackgroundImage:false};backgroundFile.value=null;if(backgroundPreview.value)URL.revokeObjectURL(backgroundPreview.value);backgroundPreview.value='';appearanceModal.value=true}catch(error){feedback.value=errorMessage(error)}}
const applyPreset=(theme:any)=>{appearanceForm.value.themeId=String(theme.id);appearanceForm.value.primaryColor=theme.primary_color;appearanceForm.value.backgroundColor=theme.background_color;appearanceForm.value.textColor=theme.text_color}
const chooseSpaceBackground=async(event:Event)=>{const file=(event.target as HTMLInputElement).files?.[0]||null;backgroundFile.value=file;if(backgroundPreview.value)URL.revokeObjectURL(backgroundPreview.value);backgroundPreview.value=file?URL.createObjectURL(file):'';if(file)await autoSpaceBalance(file)}
const autoSpaceBalance=async(file=backgroundFile.value)=>{if(!file){appearanceForm.value.backgroundBrightness=72;appearanceForm.value.backgroundOverlay=24;appearanceForm.value.textColor='#fffaf5';return}try{const light=await imageLuminance(file);appearanceForm.value.backgroundBrightness=light>.68?50:light>.45?70:light>.25?90:108;appearanceForm.value.backgroundOverlay=light>.55?30:light>.3?20:10;appearanceForm.value.textColor=light>.22?'#fffaf5':'#f8f4ef'}catch{feedback.value='无法自动分析图片，请手动调节亮度。'}}
const clearSpaceBackground=()=>{backgroundFile.value=null;if(backgroundPreview.value)URL.revokeObjectURL(backgroundPreview.value);backgroundPreview.value='';appearanceForm.value.clearBackgroundImage=true}
const saveAppearance=async()=>{appearanceBusy.value=true;feedback.value='';try{let backgroundFileId:number|undefined;if(backgroundFile.value){const body=new FormData();body.append('file',backgroundFile.value);backgroundFileId=Number((await http.post('/files',body)).data.id)}await http.put(`/spaces/${id}/appearance`,{name:appearanceForm.value.name,themeId:Number(appearanceForm.value.themeId)||undefined,primaryColor:appearanceForm.value.primaryColor,backgroundColor:appearanceForm.value.backgroundColor,textColor:appearanceForm.value.textColor,backgroundFileId,backgroundBrightness:appearanceForm.value.backgroundBrightness,backgroundOverlay:appearanceForm.value.backgroundOverlay,clearBackgroundImage:appearanceForm.value.clearBackgroundImage});appearanceModal.value=false;await load()}catch(error){feedback.value=errorMessage(error)}finally{appearanceBusy.value=false}}
</script>

<template>
  <div v-if="loading" class="space-detail-loading" aria-label="正在打开记忆空间"><UiSkeleton height="320px" radius="var(--radius-lg)" /><div><UiSkeleton height="520px" radius="var(--radius-lg)" /><UiSkeleton height="300px" radius="var(--radius-lg)" /></div></div>
  <EmptyState v-else-if="loadError" kind="error" title="这个空间暂时无法打开" :text="loadError" action-label="重新加载" @action="load" />
  <main v-else class="space-detail-page" :style="style">
    <router-link class="relationship-back-link" to="/spaces"><ArrowLeft :size="16" />返回记忆空间</router-link>
    <section class="space-detail-hero" :class="{ 'is-archived': space.status==='ARCHIVED', 'has-background': spaceBackground }">
      <div class="space-detail-hero__copy"><span class="memory-kicker">{{ space.status==='ARCHIVED' ? 'A STORY SAFELY ARCHIVED' : space.space_type==='PERSONAL' ? 'MY PRIVATE ARCHIVE' : 'OUR SHARED DAYS' }}</span><h1>{{ space.name }}</h1><p v-if="space.status==='ARCHIVED'">此空间已于 {{ dayjs(space.archived_at).format('YYYY-MM-DD') }} 封存，历史记忆仍然被好好保存。</p><p v-else-if="space.space_type==='RELATIONSHIP'">我们已经一起记录了 {{ days }} 天，每一次回望都有迹可循。</p><p v-else>你的私人数字档案，只有你可以决定谁能走进这里。</p></div>
      <dl class="space-detail-stats"><div><dt>{{ space.memoryCount || 0 }}</dt><dd>{{ space.space_type==='RELATIONSHIP' ? '共同记忆' : '私人记忆' }}</dd></div><div><dt>{{ space.photoCount || 0 }}</dt><dd>照片故事</dd></div><div><dt>{{ space.placeCount || 0 }}</dt><dd>记录地点</dd></div></dl>
      <UiButton v-if="space.status==='ACTIVE'" variant="secondary" class="space-detail-appearance" @click="openAppearance"><Palette :size="16" />自定义空间</UiButton>
    </section>

    <UiBanner v-if="space.status==='ARCHIVED'" tone="warning" title="这是一个只读的历史空间" description="关系已经结束，但空间、Memory 与图片没有删除。" />
    <UiBanner v-if="feedback" tone="danger" title="操作没有完成" :description="feedback"><template #actions><UiButton variant="ghost" size="sm" @click="feedback=''">知道了</UiButton></template></UiBanner>

    <div class="space-detail-layout">
      <section class="space-detail-timeline" aria-labelledby="space-timeline-title">
        <div class="relationship-section-heading"><div><span class="memory-kicker">MEMORY TIMELINE</span><h2 id="space-timeline-title">{{ space.space_type==='RELATIONSHIP' ? '我们的时间轴' : '我的时间轴' }}</h2><p>按照真实发生的时间，慢慢向前翻阅。</p></div></div>
        <div v-if="timeline.length" class="space-timeline-list">
          <article v-for="item in timeline" :key="item.id" class="space-timeline-entry">
            <time :datetime="item.occurred_at">{{ dayjs(item.occurred_at).format('YYYY · MM · DD') }}</time>
            <router-link :to="`/memory/${item.id}`">
              <PrivateMedia v-if="item.cover_file_id" class="space-timeline-cover" :file-id="item.cover_file_id" :mime-type="item.cover_mime_type" :alt="item.title" preview />
              <span><small>{{ item.memory_type || 'MEMORY' }}</small><strong>{{ item.title }}</strong><p>{{ item.content || '一段安静的记录' }}</p></span>
              <ArrowRight :size="18" />
            </router-link>
          </article>
        </div>
        <EmptyState v-else title="这里还没有属于你们的故事" text="从第一张照片、第一句话或第一次旅行开始吧。" />
      </section>
      <aside class="space-detail-rail">
        <section class="space-rail-section"><div class="space-rail-heading"><span><Users :size="17" /></span><div><small>PEOPLE HERE</small><h2>空间成员</h2></div></div><div class="space-member-list"><div v-for="member in space.members" :key="member.id"><UserAvatar :src="member.avatar" :name="member.nickname" /><span><strong>{{ member.nickname }}</strong><small>空间成员</small></span></div></div></section>

        <section v-if="space.space_type==='RELATIONSHIP'" class="space-rail-section">
          <div class="space-rail-heading"><span><CalendarHeart :size="17" /></span><div><small>ANNIVERSARIES</small><h2>重要的日子</h2></div><UiIconButton v-if="space.status==='ACTIVE'" label="添加纪念日" size="sm" variant="tonal" @click="openAnniversary()"><Plus :size="17" /></UiIconButton></div>
          <div v-if="space.anniversaries?.length" class="space-anniversary-list">
            <article v-for="day in space.anniversaries" :key="day.id">
              <time :datetime="day.anniversary_date"><b>{{ dayjs(day.anniversary_date).format('DD') }}</b><span>{{ dayjs(day.anniversary_date).format('MM 月') }}</span></time>
              <div><strong>{{ day.title }}</strong><p>{{ day.repeat_yearly ? '每年纪念' : dayjs(day.anniversary_date).format('YYYY 年') }} · {{ countdown(day) }}</p></div>
              <div v-if="space.status==='ACTIVE'" class="space-anniversary-actions"><UiIconButton label="创建年度提醒" size="sm" variant="ghost" @click="createReminder(day)"><AlarmClock :size="15" /></UiIconButton><UiIconButton label="编辑纪念日" size="sm" variant="ghost" @click="openAnniversary(day)"><Pencil :size="15" /></UiIconButton><UiIconButton label="删除纪念日" size="sm" variant="ghost" @click="anniversaryDeleteTarget=day"><Trash2 :size="15" /></UiIconButton></div>
            </article>
          </div>
          <p v-else class="space-rail-empty">还没有重要日期。第一次见面、生日或某个约定，都可以从这里记住。</p>
        </section>

        <section v-if="events.length" class="space-rail-section"><div class="space-rail-heading"><span><MapPin :size="17" /></span><div><small>SHARED EVENTS</small><h2>共同事件</h2></div></div><div class="space-event-list"><router-link v-for="event in events" :key="event.id" :to="`/event/${event.id}`"><span><strong>{{ event.name }}</strong><small>{{ dayjs(event.start_at).format('YYYY.MM.DD') }} · {{ event.location || '共同故事' }}</small></span><ArrowRight :size="15" /></router-link></div></section>

        <section class="space-rail-section"><div class="space-rail-heading"><span><MessageCircle :size="17" /></span><div><small>MESSAGE WALL</small><h2>空间留言</h2></div></div><div v-if="messages.length" class="space-message-list"><article v-for="item in messages.slice(0,5)" :key="item.id"><strong>{{ item.nickname }}<time :datetime="item.created_at">{{ dayjs(item.created_at).format('MM.DD') }}</time></strong><p>{{ item.content }}</p></article></div><p v-else class="space-rail-empty">还没有留言，写下第一句话吧。</p><div v-if="space.status==='ACTIVE'" class="space-message-compose"><label class="sr-only" for="space-note">空间留言</label><input id="space-note" v-model="note" maxlength="500" placeholder="留一句话…" @keyup.enter="leave" /><UiIconButton label="发送留言" variant="tonal" :disabled="!note.trim()" @click="leave"><Send :size="16" /></UiIconButton></div></section>

        <UiButton v-if="space.relationship_id && space.status==='ACTIVE'" variant="danger" block @click="archiveDialog=true"><Archive :size="15" />解除关系并封存空间</UiButton>
      </aside>
    </div>

    <UiDialog :open="anniversaryModal" :title="editingAnniversaryId ? '编辑纪念日' : '添加纪念日'" description="第一次见面、生日或某个约定，都可以在这里被记住。" :busy="savingAnniversary" @close="anniversaryModal=false">
      <div class="space-anniversary-form"><UiInput v-model="anniversaryForm.title" label="名称" :maxlength="100" placeholder="例如：我们第一次见面的日子" required /><UiInput v-model="anniversaryForm.date" label="日期" type="date" required /><UiCheckbox v-model="anniversaryForm.repeatYearly" label="每年纪念" description="之后可以一键创建年度提醒" /></div>
      <template #actions><UiButton variant="ghost" :disabled="savingAnniversary" @click="anniversaryModal=false">取消</UiButton><UiButton variant="primary" :loading="savingAnniversary" loading-text="正在保存" :disabled="!anniversaryForm.title.trim() || !anniversaryForm.date" @click="saveAnniversary">保存纪念日</UiButton></template>
    </UiDialog>

    <UiDialog :open="Boolean(anniversaryDeleteTarget)" title="删除这个纪念日？" :description="anniversaryDeleteTarget ? `「${anniversaryDeleteTarget.title}」会从日期列表中移除，但共同空间里的 Memory 不受影响。` : ''" @close="anniversaryDeleteTarget=null"><template #actions><UiButton variant="ghost" @click="anniversaryDeleteTarget=null">保留</UiButton><UiButton variant="danger" @click="removeAnniversary(anniversaryDeleteTarget)">确认删除</UiButton></template></UiDialog>

    <UiDialog :open="archiveDialog" title="解除关系并封存空间？" description="封存后不能继续添加共同记忆，但已有空间、图片、留言和时间轴都会保留。" @close="archiveDialog=false"><template #actions><UiButton variant="ghost" @click="archiveDialog=false">暂不封存</UiButton><UiButton variant="danger" @click="archive">确认解除并封存</UiButton></template></UiDialog>

    <UiDialog :open="appearanceModal" title="自定义记忆空间" description="选择一套气氛，也可以上传属于你们的图片并调整可读性。" width="wide" compact-fullscreen :busy="appearanceBusy" @close="appearanceModal=false">
      <div class="space-appearance-form">
        <UiInput v-model="appearanceForm.name" label="空间名称" :maxlength="80" required />
        <div class="space-theme-presets"><button v-for="theme in themes" :key="theme.id" :class="{active:String(theme.id)===appearanceForm.themeId}" :style="{background:`linear-gradient(135deg,${theme.background_color},${theme.primary_color})`}" @click="applyPreset(theme)"><span>{{theme.preset_name}}</span></button></div>
        <div class="space-color-grid"><label><span>主题色</span><input v-model="appearanceForm.primaryColor" type="color" /></label><label><span>背景色</span><input v-model="appearanceForm.backgroundColor" type="color" /></label><label><span>文字色</span><input v-model="appearanceForm.textColor" type="color" /></label></div>
        <div class="space-bg-preview" :style="{backgroundColor:appearanceForm.backgroundColor,color:appearanceForm.textColor}"><img v-if="backgroundPreview" :src="backgroundPreview" alt="背景预览" :style="{filter:`brightness(${appearanceForm.backgroundBrightness}%)`} "/><PrivateMedia v-else-if="space.background_file_id&&!appearanceForm.clearBackgroundImage" :file-id="Number(space.background_file_id)" mime-type="image/*" alt="当前空间背景" preview /><i :style="{opacity:appearanceForm.backgroundOverlay/100}"></i><b>我们的共享空间</b></div>
        <div class="space-range-grid"><label><span>图片亮度 <b>{{appearanceForm.backgroundBrightness}}%</b></span><input v-model.number="appearanceForm.backgroundBrightness" type="range" min="25" max="130" /></label><label><span>暗色遮罩 <b>{{appearanceForm.backgroundOverlay}}%</b></span><input v-model.number="appearanceForm.backgroundOverlay" type="range" min="0" max="85" /></label></div>
        <div class="space-bg-actions"><label class="button"><ImagePlus :size="16" />选择背景图<input type="file" accept="image/*" @change="chooseSpaceBackground" /></label><button class="button" @click="autoSpaceBalance()"><Sparkles :size="16" />自动调节亮度</button><button v-if="backgroundPreview||space.background_file_id" class="button" @click="clearSpaceBackground"><Trash2 :size="16" />移除图片</button></div>
      </div>
      <template #actions><UiButton variant="ghost" :disabled="appearanceBusy" @click="appearanceModal=false">取消</UiButton><UiButton variant="primary" :loading="appearanceBusy" loading-text="正在保存" :disabled="!appearanceForm.name.trim()" @click="saveAppearance">应用到记忆空间</UiButton></template>
    </UiDialog>
  </main>
</template>

<style scoped>
.timeline-cover { width:100%; height:230px; min-height:230px; margin:-20px -20px 18px; width:calc(100% + 40px); border-radius:21px 21px 0 0; }
.timeline-cover :deep(img), .timeline-cover :deep(video) { width:100%; height:100%; max-height:none; object-fit:cover; }
.anniversary-heading { display:flex;justify-content:space-between;align-items:start;gap:12px; }
.anniversary-heading h2 { margin:0;font-size:19px;display:flex;align-items:center;gap:8px; }
.anniversary-list { display:grid;gap:9px;margin-top:16px; }
.anniversary-item { display:grid;grid-template-columns:48px 1fr auto;align-items:center;gap:11px;padding:10px;border:1px solid var(--line);border-radius:17px;background:var(--surface-solid); }
.anniversary-date { height:48px;display:grid;place-content:center;text-align:center;border-radius:13px;color:white;background:linear-gradient(145deg,var(--space-primary),color-mix(in srgb,var(--space-primary) 72%,#333)); }
.anniversary-date b { font-size:17px;line-height:1; }.anniversary-date span { margin-top:3px;font-size:9px; }
.anniversary-copy { min-width:0; }.anniversary-copy>b { display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:13px; }.anniversary-copy p,.anniversary-empty { margin:4px 0 0;color:var(--muted);font-size:11px;line-height:1.5; }
.anniversary-actions { display:flex;gap:3px; }.anniversary-actions button { width:28px;height:28px;display:grid;place-items:center;padding:0;border:0;border-radius:9px;color:var(--muted);background:transparent; }.anniversary-actions button:hover { color:var(--ink);background:color-mix(in srgb,var(--surface-solid) 82%,var(--space-primary)); }
.anniversary-empty { margin-top:15px; }.anniversary-modal { width:min(470px,calc(100vw - 28px)); }
.anniversary-repeat { display:flex;align-items:center;gap:12px;padding:14px;border:1px solid var(--line);border-radius:15px;background:var(--surface-solid);cursor:pointer; }.anniversary-repeat input { width:18px;height:18px;accent-color:var(--accent-deep); }.anniversary-repeat span { display:grid;gap:3px; }.anniversary-repeat small { color:var(--muted); }
.space-appearance-button{position:absolute;z-index:2;right:max(32px,calc((100vw - 1180px)/2));bottom:34px;padding:10px 14px;display:flex;align-items:center;gap:7px;border:1px solid color-mix(in srgb,var(--space-text) 42%,transparent);border-radius:14px;color:var(--space-text);background:color-mix(in srgb,var(--space-background) 76%,transparent);backdrop-filter:blur(12px);font-weight:600}.space-appearance-modal{width:min(650px,calc(100vw - 28px));max-height:90vh;overflow:auto}.space-theme-presets{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin-bottom:15px}.space-theme-presets button{height:58px;padding:8px;border:2px solid transparent;border-radius:15px;color:#242329;font-weight:700}.space-theme-presets button.active{border-color:var(--ink)}.space-theme-presets span{padding:3px 7px;border-radius:7px;background:rgba(255,255,255,.7)}.space-color-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin-bottom:15px}.space-color-grid label{display:grid;gap:5px;color:var(--muted);font-size:11px}.space-color-grid input{width:100%;height:40px;padding:3px}.space-bg-preview{position:relative;height:190px;overflow:hidden;display:grid;place-items:center;border-radius:20px}.space-bg-preview>img,.space-bg-preview :deep(.private-media),.space-bg-preview :deep(img){position:absolute;inset:0;width:100%;height:100%;min-height:0;object-fit:cover;border:0;border-radius:0}.space-bg-preview>i{position:absolute;inset:0;background:#15141a}.space-bg-preview>b{position:relative;z-index:2;font:600 24px 'Noto Serif SC',serif;text-shadow:0 2px 12px rgba(0,0,0,.35)}.space-range-grid{display:grid;grid-template-columns:1fr 1fr;gap:15px;margin:15px 0}.space-range-grid label{display:grid;gap:8px}.space-range-grid span{display:flex;justify-content:space-between;color:var(--muted);font-size:11px}.space-range-grid input{width:100%;accent-color:var(--accent-deep)}.space-bg-actions{display:flex;flex-wrap:wrap;gap:7px}.space-bg-actions .button{display:flex;align-items:center;gap:6px}.space-bg-actions input{display:none}.two-column .panel{border-color:color-mix(in srgb,var(--space-primary) 25%,var(--line))}@media(max-width:700px){.space-appearance-button{position:relative;right:auto;bottom:auto;margin-top:20px}.space-theme-presets,.space-color-grid{grid-template-columns:1fr 1fr}.space-range-grid{grid-template-columns:1fr}}
</style>
