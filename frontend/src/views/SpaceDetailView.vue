<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { AlarmClock, Archive, CalendarHeart, ImagePlus, Palette, Pencil, Plus, Send, Sparkles, Trash2, X } from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import EmptyState from '../components/EmptyState.vue'
import PrivateMedia from '../components/PrivateMedia.vue'
import UserAvatar from '../components/UserAvatar.vue'
import { imageLuminance } from '../utils/appearance'

const route = useRoute(); const router = useRouter(); const id = Number(route.params.id)
const space = ref<any>({}); const timeline = ref<any[]>([]); const messages = ref<any[]>([]); const events = ref<any[]>([])
const note = ref(''); const feedback = ref('')
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
  const [a,b,c,d] = await Promise.all([http.get(`/spaces/${id}`),http.get(`/spaces/${id}/timeline`),http.get(`/spaces/${id}/messages`),http.get(`/spaces/${id}/events`)])
  space.value=a.data; timeline.value=b.data; messages.value=c.data; events.value=d.data;await loadSpaceBackground()
}
onMounted(load)
onBeforeUnmount(()=>{if(spaceBackground.value)URL.revokeObjectURL(spaceBackground.value);if(backgroundPreview.value)URL.revokeObjectURL(backgroundPreview.value)})
const loadSpaceBackground=async()=>{if(spaceBackground.value)URL.revokeObjectURL(spaceBackground.value);spaceBackground.value='';if(!space.value.background_file_id)return;try{const{data}=await http.get(`/files/${space.value.background_file_id}/content`,{responseType:'blob'});spaceBackground.value=URL.createObjectURL(data)}catch{feedback.value='共享空间背景暂时无法读取。'}}
const leave = async () => { if(!note.value.trim())return; await http.post(`/spaces/${id}/messages`,{content:note.value});note.value='';messages.value=(await http.get(`/spaces/${id}/messages`)).data }
const archive = async () => {
  if (!space.value.relationship_id || !confirm('封存后将不能继续添加共同记忆，但历史会被保留。确认吗？')) return
  try { await http.delete(`/relationships/${space.value.relationship_id}`); await load() } catch(e){ feedback.value=errorMessage(e) }
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
  if (!confirm(`确定删除纪念日「${day.title}」吗？共同空间里的记忆不会受影响。`)) return
  try { await http.delete(`/spaces/${id}/anniversaries/${day.id}`); await load() }
  catch(e) { feedback.value=errorMessage(e) }
}
const createReminder = (day:any) => router.push({ path:'/reminders', query:{
  relationship:String(space.value.relationship_id), title:day.title, kind:'ANNIVERSARY',
  date:dayjs(day.anniversary_date).format('YYYY-MM-DD')
} })
const openAppearance=async()=>{if(!themes.value.length)themes.value=(await http.get('/spaces/themes')).data;appearanceForm.value={name:space.value.name||'',themeId:String(space.value.theme_id||''),primaryColor:space.value.primary_color||'#7f879e',backgroundColor:space.value.background_color||'#f7f6f4',textColor:space.value.text_color||'#373a45',backgroundBrightness:Number(space.value.background_brightness||100),backgroundOverlay:Number(space.value.background_overlay??18),clearBackgroundImage:false};backgroundFile.value=null;if(backgroundPreview.value)URL.revokeObjectURL(backgroundPreview.value);backgroundPreview.value='';appearanceModal.value=true}
const applyPreset=(theme:any)=>{appearanceForm.value.themeId=String(theme.id);appearanceForm.value.primaryColor=theme.primary_color;appearanceForm.value.backgroundColor=theme.background_color;appearanceForm.value.textColor=theme.text_color}
const chooseSpaceBackground=async(event:Event)=>{const file=(event.target as HTMLInputElement).files?.[0]||null;backgroundFile.value=file;if(backgroundPreview.value)URL.revokeObjectURL(backgroundPreview.value);backgroundPreview.value=file?URL.createObjectURL(file):'';if(file)await autoSpaceBalance(file)}
const autoSpaceBalance=async(file=backgroundFile.value)=>{if(!file){appearanceForm.value.backgroundBrightness=72;appearanceForm.value.backgroundOverlay=24;appearanceForm.value.textColor='#fffaf5';return}try{const light=await imageLuminance(file);appearanceForm.value.backgroundBrightness=light>.68?50:light>.45?70:light>.25?90:108;appearanceForm.value.backgroundOverlay=light>.55?30:light>.3?20:10;appearanceForm.value.textColor=light>.22?'#fffaf5':'#f8f4ef'}catch{feedback.value='无法自动分析图片，请手动调节亮度。'}}
const clearSpaceBackground=()=>{backgroundFile.value=null;if(backgroundPreview.value)URL.revokeObjectURL(backgroundPreview.value);backgroundPreview.value='';appearanceForm.value.clearBackgroundImage=true}
const saveAppearance=async()=>{appearanceBusy.value=true;feedback.value='';try{let backgroundFileId:number|undefined;if(backgroundFile.value){const body=new FormData();body.append('file',backgroundFile.value);backgroundFileId=Number((await http.post('/files',body)).data.id)}await http.put(`/spaces/${id}/appearance`,{name:appearanceForm.value.name,themeId:Number(appearanceForm.value.themeId)||undefined,primaryColor:appearanceForm.value.primaryColor,backgroundColor:appearanceForm.value.backgroundColor,textColor:appearanceForm.value.textColor,backgroundFileId,backgroundBrightness:appearanceForm.value.backgroundBrightness,backgroundOverlay:appearanceForm.value.backgroundOverlay,clearBackgroundImage:appearanceForm.value.clearBackgroundImage});appearanceModal.value=false;await load()}catch(error){feedback.value=errorMessage(error)}finally{appearanceBusy.value=false}}
</script>

<template>
  <div :style="style">
    <section class="space-hero" :class="{archived:space.status==='ARCHIVED','has-background':spaceBackground}">
      <span class="eyebrow">{{ space.status==='ARCHIVED' ? 'A STORY SAFELY ARCHIVED' : space.space_type==='PERSONAL' ? 'MY PRIVATE UNIVERSE' : 'OUR SHARED DAYS' }}</span>
      <h1>{{ space.name }}</h1>
      <p v-if="space.status==='ARCHIVED'">此空间已于 {{ dayjs(space.archived_at).format('YYYY-MM-DD') }} 封存，历史记忆仍然被好好保存。</p>
      <p v-else-if="space.space_type==='RELATIONSHIP'">我们已经一起记录了 {{ days }} 天，每一次回望都有迹可循。</p>
      <p v-else>你的私人数字档案，只有你可以决定谁能走进这里。</p>
      <div class="space-stats"><div><b>{{ space.memoryCount || 0 }}</b><small>共同记忆</small></div><div><b>{{ space.photoCount || 0 }}</b><small>照片故事</small></div><div><b>{{ space.placeCount || 0 }}</b><small>一起去过</small></div></div>
      <button v-if="space.status==='ACTIVE'" class="space-appearance-button" @click="openAppearance"><Palette :size="16" /> 自定义空间</button>
    </section>

    <div class="two-column">
      <section>
        <div class="section-heading"><div><span class="eyebrow">MEMORY TIMELINE</span><h2>我们的时间轴</h2></div></div>
        <div v-if="timeline.length" class="timeline">
          <article v-for="item in timeline" :key="item.id" class="timeline-item">
            <div class="timeline-date">{{ dayjs(item.occurred_at).format('YYYY · MMMM · DD') }}</div>
            <router-link :to="`/memory/${item.id}`" class="timeline-card">
              <PrivateMedia v-if="item.cover_file_id" class="timeline-cover" :file-id="item.cover_file_id" :mime-type="item.cover_mime_type" :alt="item.title" preview />
              <h3>{{ item.title }}</h3><p>{{ item.content || '一段安静的记录' }}</p>
            </router-link>
          </article>
        </div>
        <EmptyState v-else title="这里还没有属于你们的故事" text="从第一张照片、第一句话或第一次旅行开始吧。" />
      </section>
      <aside style="display:grid;gap:18px">
        <section class="panel"><span class="eyebrow">PEOPLE HERE</span><h2 style="font-size:19px">空间成员</h2><div class="space-members"><UserAvatar v-for="member in space.members" :key="member.id" :src="member.avatar" :name="member.nickname" :title="member.nickname" /></div></section>
        <section v-if="space.space_type==='RELATIONSHIP'" class="panel anniversary-panel">
          <div class="anniversary-heading"><div><span class="eyebrow">ANNIVERSARIES</span><h2><CalendarHeart :size="18" /> 重要的日子</h2></div><button v-if="space.status==='ACTIVE'" class="icon-button" aria-label="添加纪念日" @click="openAnniversary()"><Plus :size="17" /></button></div>
          <div v-if="space.anniversaries?.length" class="anniversary-list">
            <article v-for="day in space.anniversaries" :key="day.id" class="anniversary-item">
              <div class="anniversary-date"><b>{{ dayjs(day.anniversary_date).format('DD') }}</b><span>{{ dayjs(day.anniversary_date).format('MM 月') }}</span></div>
              <div class="anniversary-copy"><b>{{ day.title }}</b><p>{{ day.repeat_yearly ? '每年纪念' : dayjs(day.anniversary_date).format('YYYY 年') }} · {{ countdown(day) }}</p></div>
              <div v-if="space.status==='ACTIVE'" class="anniversary-actions">
                <button title="创建年度提醒" @click="createReminder(day)"><AlarmClock :size="15" /></button>
                <button title="编辑纪念日" @click="openAnniversary(day)"><Pencil :size="15" /></button>
                <button title="删除纪念日" @click="removeAnniversary(day)"><Trash2 :size="15" /></button>
              </div>
            </article>
          </div>
          <p v-else class="anniversary-empty">还没有重要日期。第一次见面、生日或某个约定，都可以从这里记住。</p>
        </section>
        <section v-if="events.length" class="panel"><span class="eyebrow">SHARED EVENTS</span><h2 style="font-size:19px">共同事件</h2><router-link v-for="event in events" :key="event.id" :to="`/event/${event.id}`" class="message-note" style="display:block"><b>{{ event.name }}</b><p>{{ dayjs(event.start_at).format('YYYY.MM.DD') }} · {{ event.location || '共同故事' }}</p></router-link></section>
        <section class="panel"><span class="eyebrow">MESSAGE WALL</span><h2 style="font-size:19px">空间留言</h2><div class="message-list"><div v-for="item in messages.slice(0,5)" :key="item.id" class="message-note"><b>{{ item.nickname }} · {{ dayjs(item.created_at).format('MM.DD') }}</b><p>{{ item.content }}</p></div></div><div v-if="space.status==='ACTIVE'" style="display:flex;gap:7px;margin-top:13px"><input v-model="note" style="min-width:0;flex:1;padding:10px;border:1px solid var(--line);border-radius:12px" placeholder="留一句话…" @keyup.enter="leave" /><button class="icon-button" @click="leave"><Send :size="16" /></button></div></section>
        <button v-if="space.relationship_id && space.status==='ACTIVE'" class="button ghost" @click="archive"><Archive :size="15" /> 封存这段关系</button><p v-if="feedback" class="form-error">{{ feedback }}</p>
      </aside>
    </div>

    <div v-if="anniversaryModal" class="modal-backdrop" @click.self="anniversaryModal=false">
      <section class="create-modal anniversary-modal" role="dialog" aria-modal="true" aria-labelledby="anniversary-title">
        <header><div><span class="eyebrow">A DATE WE KEEP</span><h2 id="anniversary-title">{{ editingAnniversaryId ? '编辑纪念日' : '添加纪念日' }}</h2></div><button class="icon-button" aria-label="关闭" @click="anniversaryModal=false"><X :size="18" /></button></header>
        <label class="field"><span>名称</span><input v-model="anniversaryForm.title" maxlength="100" placeholder="例如：我们第一次见面的日子" /></label>
        <label class="field"><span>日期</span><input v-model="anniversaryForm.date" type="date" /></label>
        <label class="anniversary-repeat"><input v-model="anniversaryForm.repeatYearly" type="checkbox" /><span><b>每年纪念</b><small>可一键创建年度提醒，以后每年自动出现</small></span></label>
        <footer><button class="button" @click="anniversaryModal=false">取消</button><button class="button primary" :disabled="savingAnniversary || !anniversaryForm.title.trim() || !anniversaryForm.date" @click="saveAnniversary">{{ savingAnniversary ? '正在保存…' : '保存纪念日' }}</button></footer>
      </section>
    </div>

    <div v-if="appearanceModal" class="modal-backdrop" @click.self="appearanceModal=false">
      <section class="create-modal space-appearance-modal" role="dialog" aria-modal="true" aria-labelledby="space-appearance-title">
        <header><div><span class="eyebrow">OUR OWN ATMOSPHERE</span><h2 id="space-appearance-title">自定义共享空间</h2></div><button class="icon-button" aria-label="关闭" @click="appearanceModal=false"><X :size="18" /></button></header>
        <label class="field"><span>空间名称</span><input v-model="appearanceForm.name" maxlength="80" /></label>
        <div class="space-theme-presets"><button v-for="theme in themes" :key="theme.id" :class="{active:String(theme.id)===appearanceForm.themeId}" :style="{background:`linear-gradient(135deg,${theme.background_color},${theme.primary_color})`}" @click="applyPreset(theme)"><span>{{theme.preset_name}}</span></button></div>
        <div class="space-color-grid"><label><span>主题色</span><input v-model="appearanceForm.primaryColor" type="color" /></label><label><span>背景色</span><input v-model="appearanceForm.backgroundColor" type="color" /></label><label><span>文字色</span><input v-model="appearanceForm.textColor" type="color" /></label></div>
        <div class="space-bg-preview" :style="{backgroundColor:appearanceForm.backgroundColor,color:appearanceForm.textColor}"><img v-if="backgroundPreview" :src="backgroundPreview" alt="背景预览" :style="{filter:`brightness(${appearanceForm.backgroundBrightness}%)`} "/><PrivateMedia v-else-if="space.background_file_id&&!appearanceForm.clearBackgroundImage" :file-id="Number(space.background_file_id)" mime-type="image/*" alt="当前空间背景" preview /><i :style="{opacity:appearanceForm.backgroundOverlay/100}"></i><b>我们的共享空间</b></div>
        <div class="space-range-grid"><label><span>图片亮度 <b>{{appearanceForm.backgroundBrightness}}%</b></span><input v-model.number="appearanceForm.backgroundBrightness" type="range" min="25" max="130" /></label><label><span>暗色遮罩 <b>{{appearanceForm.backgroundOverlay}}%</b></span><input v-model.number="appearanceForm.backgroundOverlay" type="range" min="0" max="85" /></label></div>
        <div class="space-bg-actions"><label class="button"><ImagePlus :size="16" />选择背景图<input type="file" accept="image/*" @change="chooseSpaceBackground" /></label><button class="button" @click="autoSpaceBalance()"><Sparkles :size="16" />自动调节亮度</button><button v-if="backgroundPreview||space.background_file_id" class="button" @click="clearSpaceBackground"><Trash2 :size="16" />移除图片</button></div>
        <footer><button class="button" @click="appearanceModal=false">取消</button><button class="button primary" :disabled="appearanceBusy||!appearanceForm.name.trim()" @click="saveAppearance">{{appearanceBusy?'正在保存…':'应用到共享空间'}}</button></footer>
      </section>
    </div>
  </div>
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
