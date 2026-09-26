<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Ban, ChevronLeft, ChevronRight, Fingerprint, Flag, KeyRound, LogOut, Search, ShieldCheck, TimerOff, UserCheck, Users } from 'lucide-vue-next'
import dayjs from 'dayjs'
import adminHttp from '../api/adminHttp'
import { errorMessage } from '../api/http'
import { UiBanner, UiButton, UiDialog } from '../components/ui'

type AdminUser = { id:number;public_id:string;username:string;nickname:string;is_admin:boolean;account_status:string;muted_until?:string;violation_count:number;created_at:string }
type ReportItem = { id:number;target_type:string;reason_category:string;description?:string;status:string;created_at:string;reported_nickname:string;reported_username:string;reported_user_id:number;violation_count:number;account_status:string;muted_until?:string }
type EvidenceMedia = { url:string; mime:string; name:string }
const router=useRouter();const adminUser=ref<any>(JSON.parse(localStorage.getItem('memospace_admin_user')||'{}'))
const activeTab=ref<'reports'|'users'>('reports');const users=ref<AdminUser[]>([]);const audits=ref<any[]>([]);const reports=ref<ReportItem[]>([])
const total=ref(0);const reportTotal=ref(0);const page=ref(1);const reportPage=ref(1);const size=20;const keyword=ref('');const reportFilter=ref('PENDING')
const loading=ref(true);const message=ref('');const modal=ref<'password'|'memo'|''>('');const selected=ref<AdminUser|null>(null);const value=ref('');const saving=ref(false)
const reportDetail=ref<any|null>(null);const reportBusy=ref(false);const evidenceMedia=ref<EvidenceMedia[]>([]);const evidenceLoading=ref(false)
const action=ref({removeContent:true,penalty:'WARNING',note:''})
const messageTone=ref<'success'|'danger'>('success')
const confirmation=ref<null|{kind:'report'|'status';title:string;description:string;decision?:'CONFIRM'|'DISMISS';user?:AdminUser;status?:'ACTIVE'|'MUTE_7_DAYS'|'BAN'}>(null)
const pages=computed(()=>Math.max(1,Math.ceil(total.value/size)));const reportPages=computed(()=>Math.max(1,Math.ceil(reportTotal.value/size)))
const reasonLabels:Record<string,string>={ILLEGAL:'违法或违禁',HARASSMENT:'骚扰辱骂',PORNOGRAPHY:'色情低俗',VIOLENCE:'暴力危险',FRAUD:'诈骗虚假',PRIVACY:'隐私泄露',OTHER:'其他'}
const statusLabel=(value:string)=>({PENDING:'待处理',RESOLVED:'已确认',DISMISSED:'已驳回'}[value]||value)
const resolutionLabel=(value?:string)=>{if(!value)return'未记录处理结果';const labels:Record<string,string>={NONE:'只记录违规',WARNING:'正式警告',MUTE_7_DAYS:'禁言 7 天',BAN:'封号',DISMISS:'驳回举报'};const removed=value.startsWith('DELETE+');const penalty=value.replace('DELETE+','');return`${removed?'已删除内容 · ':''}${labels[penalty]||penalty}`}
const auditLabel=(value:string)=>({RESET_PASSWORD:'重置密码',CHANGE_MEMO_ID:'修改 Memo ID',RESOLVE_REPORT:'处理举报',DISMISS_REPORT:'驳回举报',ACCOUNT_STATUS:'调整账号状态'}[value]||value)

const loadUsers=async()=>{const result=await adminHttp.get('/admin/users',{params:{keyword:keyword.value,page:page.value,size}});users.value=result.data.items;total.value=Number(result.data.total)}
const loadReports=async()=>{const result=await adminHttp.get('/admin/reports',{params:{status:reportFilter.value,page:reportPage.value,size}});reports.value=result.data.items;reportTotal.value=Number(result.data.total)}
const load=async()=>{loading.value=true;try{const [, ,auditResult,meResult]=await Promise.all([loadUsers(),loadReports(),adminHttp.get('/admin/audit'),adminHttp.get('/admin/me')]);audits.value=auditResult.data;adminUser.value=meResult.data;localStorage.setItem('memospace_admin_user',JSON.stringify(meResult.data))}catch(error){message.value=errorMessage(error)}finally{loading.value=false}}
onMounted(load);onBeforeUnmount(()=>evidenceMedia.value.forEach(item=>URL.revokeObjectURL(item.url)))
const search=()=>{page.value=1;void loadUsers()};const turn=(next:number)=>{page.value=Math.min(pages.value,Math.max(1,next));void loadUsers()};const turnReports=(next:number)=>{reportPage.value=Math.min(reportPages.value,Math.max(1,next));void loadReports()}
const open=(kind:'password'|'memo',user:AdminUser)=>{modal.value=kind;selected.value=user;value.value=kind==='memo'?user.public_id:'';message.value=''}
const save=async()=>{if(!selected.value)return;saving.value=true;message.value='';try{if(modal.value==='password')await adminHttp.put(`/admin/users/${selected.value.id}/password`,{newPassword:value.value});else await adminHttp.put(`/admin/users/${selected.value.id}/memo-id`,{memoId:value.value});message.value=modal.value==='password'?`已为 ${selected.value.nickname} 设置临时密码。`:`${selected.value.nickname} 的 Memo ID 已更新。`;modal.value='';await load()}catch(error){message.value=errorMessage(error)}finally{saving.value=false}}
const clearEvidence=()=>{evidenceMedia.value.forEach(item=>URL.revokeObjectURL(item.url));evidenceMedia.value=[]}
const closeReport=()=>{if(reportBusy.value)return;clearEvidence();reportDetail.value=null}
const openReport=async(id:number)=>{if(reportBusy.value)return;reportBusy.value=true;message.value='';clearEvidence();try{const {data}=await adminHttp.get(`/admin/reports/${id}`);reportDetail.value=data;action.value={removeContent:true,penalty:'WARNING',note:''};evidenceLoading.value=true;const media=await Promise.all((data.media||[]).map(async(item:any)=>{const file=await adminHttp.get(String(item.content_url).replace(/^\/api\//,''),{responseType:'blob'});return{url:URL.createObjectURL(file.data),mime:String(item.mime_type||file.data.type||''),name:String(item.original_name||'举报证据')}}));evidenceMedia.value=media}catch(error){message.value=errorMessage(error);clearEvidence();reportDetail.value=null}finally{evidenceLoading.value=false;reportBusy.value=false}}
const requestReportResolution=(decision:'CONFIRM'|'DISMISS')=>{if(!reportDetail.value||reportBusy.value)return;const summary=decision==='CONFIRM'?[action.value.removeContent?'删除被举报内容':'保留被举报内容',resolutionLabel(action.value.penalty)].join('、'):'保留被举报内容并将举报标记为未发现违规';confirmation.value={kind:'report',decision,title:decision==='CONFIRM'?'确认违规并执行处罚？':'驳回这条举报？',description:`将执行：${summary}。操作会写入审计记录并通知相关用户。`}}
const resolveReport=requestReportResolution
const requestStatus=(user:AdminUser,status:'ACTIVE'|'MUTE_7_DAYS'|'BAN')=>{confirmation.value={kind:'status',user,status,title:status==='BAN'?`封禁 ${user.nickname}？`:status==='MUTE_7_DAYS'?`禁言 ${user.nickname} 7 天？`:`解除 ${user.nickname} 的账号限制？`,description:status==='BAN'?'账号将无法继续登录，现有会话也会失效；不会删除其数据。':status==='MUTE_7_DAYS'?'用户仍可浏览，但 7 天内不能发布内容、评论或私聊。':'解除现有封禁或禁言限制，操作会写入审计记录。'}}
const executeConfirmation=async()=>{const current=confirmation.value;if(!current)return;confirmation.value=null;if(current.kind==='report'&&reportDetail.value&&current.decision){const reportId=reportDetail.value.id;reportBusy.value=true;try{await adminHttp.put(`/admin/reports/${reportId}/resolve`,{decision:current.decision,removeContent:current.decision==='CONFIRM'&&action.value.removeContent,penalty:current.decision==='CONFIRM'?action.value.penalty:'NONE',note:action.value.note});messageTone.value='success';message.value=current.decision==='DISMISS'?'举报已驳回。':'举报处理结果已生效并通知用户。';reportBusy.value=false;closeReport();await load()}catch(error){messageTone.value='danger';message.value=errorMessage(error)}finally{reportBusy.value=false};return}if(current.kind==='status'&&current.user&&current.status){try{await adminHttp.put(`/admin/users/${current.user.id}/status`,{action:current.status,note:'管理员从用户目录手动调整'});messageTone.value='success';message.value='账号状态已更新。';await load()}catch(error){messageTone.value='danger';message.value=errorMessage(error)}}}
const logout=()=>{localStorage.removeItem('memospace_admin_token');localStorage.removeItem('memospace_admin_user');router.push('/admin/login')}
</script>

<template>
  <main class="admin-page">
    <header class="admin-header"><router-link class="admin-brand" to="/admin"><span><ShieldCheck :size="20" /></span><div><b>拾光管理员中心</b><small>安全与账号管理</small></div></router-link><div class="admin-account"><span>{{adminUser.nickname||adminUser.username}} · 独立管理会话</span><button class="icon-button" aria-label="退出管理员登录" title="退出管理员登录" @click="logout"><LogOut :size="17" /></button></div></header>
    <div class="admin-wrap">
      <section class="admin-intro"><div><h1>社区安全与账号管理</h1><p>只查看用户主动举报的目标证据；管理员不能浏览其他私人 Memory、共同空间、聊天或媒体。</p></div><div class="admin-stats"><span><Flag :size="18" /><b>{{reportTotal}}</b><small>当前筛选举报</small></span><span><Users :size="18" /><b>{{total}}</b><small>全部用户</small></span></div></section>
      <nav class="admin-tabs" aria-label="管理员工作区"><button :class="{active:activeTab==='reports'}" :aria-pressed="activeTab==='reports'" @click="activeTab='reports'">举报中心</button><button :class="{active:activeTab==='users'}" :aria-pressed="activeTab==='users'" @click="activeTab='users'">用户目录</button></nav>
      <UiBanner v-if="message" :tone="messageTone" :title="messageTone==='danger'?'管理操作没有完成':'管理操作已记录'" :description="message" />

      <section v-if="activeTab==='reports'" class="admin-grid">
        <div class="admin-panel report-panel">
          <div class="admin-panel-heading"><h2>用户举报</h2><label class="admin-filter"><span class="sr-only">筛选举报状态</span><select v-model="reportFilter" aria-label="筛选举报状态" @change="reportPage=1;loadReports()"><option value="PENDING">待处理</option><option value="RESOLVED">已确认</option><option value="DISMISSED">已驳回</option><option value="ALL">全部</option></select></label></div>
          <div v-if="loading" class="admin-empty">正在读取举报…</div>
          <div v-else-if="reports.length" class="report-list"><button v-for="item in reports" :key="item.id" :disabled="reportBusy" @click="openReport(item.id)"><span class="report-icon"><Flag :size="16" /></span><div><strong>{{reasonLabels[item.reason_category]||item.reason_category}} · {{item.target_type==='MEMORY'?'记忆':'评论'}}</strong><p>被举报：{{item.reported_nickname}}（@{{item.reported_username}}）· 已确认违规 {{item.violation_count||0}} 次</p><small>{{dayjs(item.created_at).format('YYYY.MM.DD HH:mm')}} · {{statusLabel(item.status)}}</small></div><em>{{reportBusy?'读取中…':'查看详情'}}</em></button></div>
          <div v-else class="admin-empty">当前没有符合条件的举报。</div>
          <footer class="admin-pagination"><span>第 {{reportPage}} / {{reportPages}} 页</span><div><button :disabled="reportPage<=1" aria-label="上一页举报" @click="turnReports(reportPage-1)"><ChevronLeft :size="16" /></button><button :disabled="reportPage>=reportPages" aria-label="下一页举报" @click="turnReports(reportPage+1)"><ChevronRight :size="16" /></button></div></footer>
        </div>
        <aside class="admin-panel audit-panel"><h2>最近管理记录</h2><div v-if="audits.length" class="audit-list"><article v-for="item in audits" :key="item.id"><i aria-hidden="true"></i><div><b>{{auditLabel(item.action_type)}}</b><p>{{item.target_nickname||'未知用户'}} · {{item.detail}}</p><time>{{dayjs(item.created_at).format('MM.DD HH:mm')}}</time></div></article></div><p v-else class="admin-empty">还没有管理操作。</p></aside>
      </section>

      <section v-else class="admin-grid">
        <div class="admin-panel user-panel"><div class="admin-panel-heading"><h2>用户列表</h2><form class="admin-search" role="search" @submit.prevent="search"><Search :size="15" aria-hidden="true" /><label class="sr-only" for="admin-user-search">搜索用户</label><input id="admin-user-search" v-model="keyword" placeholder="昵称、用户名或完整 Memo ID" /><button>搜索</button></form></div>
          <div v-if="loading" class="admin-empty">正在读取用户…</div><div v-else-if="users.length" class="admin-user-list"><article v-for="user in users" :key="user.id" class="admin-user-row"><span class="admin-avatar">{{user.nickname?.slice(0,1)||'拾'}}</span><div class="admin-user-copy"><div><b>{{user.nickname}}</b><em v-if="user.is_admin">管理员</em><em v-else-if="user.account_status==='BANNED'" class="danger">已封号</em><em v-else-if="user.muted_until&&dayjs(user.muted_until).isAfter(dayjs())" class="warning">禁言中</em></div><p>@{{user.username}} · 违规 {{user.violation_count||0}} 次 · 注册于 {{dayjs(user.created_at).format('YYYY.MM.DD')}}</p><code>Memo ID {{user.public_id}}</code></div><div v-if="!user.is_admin" class="admin-user-actions"><button class="button" @click="open('memo',user)"><Fingerprint :size="14" />改 ID</button><button class="button" @click="open('password',user)"><KeyRound :size="14" />重置密码</button><button v-if="user.account_status==='BANNED'||(user.muted_until&&dayjs(user.muted_until).isAfter(dayjs()))" class="button" @click="requestStatus(user,'ACTIVE')"><UserCheck :size="14" />解除</button><button v-else class="button" @click="requestStatus(user,'MUTE_7_DAYS')"><TimerOff :size="14" />禁言</button><button v-if="user.account_status!=='BANNED'" class="button danger-button" @click="requestStatus(user,'BAN')"><Ban :size="14" />封号</button></div></article></div><div v-else class="admin-empty">没有找到匹配的用户。</div>
          <footer class="admin-pagination"><span>第 {{page}} / {{pages}} 页</span><div><button :disabled="page<=1" aria-label="上一页用户" @click="turn(page-1)"><ChevronLeft :size="16" /></button><button :disabled="page>=pages" aria-label="下一页用户" @click="turn(page+1)"><ChevronRight :size="16" /></button></div></footer>
        </div><aside class="admin-panel audit-panel"><h2>处罚说明</h2><p class="policy">警告会累计违规次数；禁言期间用户可以浏览，但不能发布 Memory、评论、共同空间留言或私聊；封号后现有登录令牌也会立即失效。</p></aside>
      </section>
    </div>

    <UiDialog :open="Boolean(modal)" :title="modal==='password'?'重置临时密码':'修改 Memo ID'" :description="`正在修改：${selected?.nickname || ''}（@${selected?.username || ''}）`" :busy="saving" @close="modal=''">
      <label class="field"><span>{{modal==='password'?'新的临时密码':'新的 12 位纯数字 Memo ID'}}</span><input v-model="value" :type="modal==='password'?'password':'text'" :maxlength="modal==='password'?72:12" /></label>
      <p class="admin-dialog-help">{{modal==='password'?'管理员无法查看旧密码，用户登录后可自行修改。':'只修改对外代号，不改变原有好友、关系和 Memory。'}}</p>
      <template #actions><UiButton variant="ghost" :disabled="saving" @click="modal=''">取消</UiButton><UiButton variant="primary" :loading="saving" loading-text="正在保存" :disabled="modal==='password'?value.length<8:!/^\d{12}$/.test(value)" @click="save">确认修改</UiButton></template>
    </UiDialog>

    <UiDialog :open="Boolean(reportDetail)" :title="`${reasonLabels[reportDetail?.reason_category]||reportDetail?.reason_category||''}举报详情`" :description="reportDetail ? `举报 #${reportDetail.id} · ${statusLabel(reportDetail.status)}` : undefined" width="wide" compact-fullscreen :busy="reportBusy" @close="closeReport">
      <template v-if="reportDetail">
        <div class="report-meta"><span>举报人：{{reportDetail.reporter_nickname}}</span><span>被举报：{{reportDetail.reported_nickname}}（@{{reportDetail.reported_username}}）</span><span>已确认违规：{{reportDetail.violation_count||0}} 次</span><span>提交于 {{dayjs(reportDetail.created_at).format('YYYY.MM.DD HH:mm')}}</span></div><p v-if="reportDetail.description" class="report-description">用户说明：{{reportDetail.description}}</p><article class="evidence"><h3>{{reportDetail.target?.title||reportDetail.target?.memory_title||'被举报内容'}}</h3><p>{{reportDetail.target?.content||'（这条内容没有文字）'}}</p><p v-if="evidenceLoading" class="evidence-loading">正在读取举报关联的媒体证据…</p><template v-for="media in evidenceMedia" :key="media.url"><video v-if="media.mime.startsWith('video/')" :src="media.url" :aria-label="media.name" controls preload="metadata"></video><img v-else :src="media.url" :alt="media.name" loading="lazy" /></template></article><template v-if="reportDetail.status==='PENDING'"><label class="check-row"><input v-model="action.removeContent" type="checkbox" />确认违规后删除这条{{reportDetail.target_type==='MEMORY'?'记忆':'评论'}}</label><label class="field"><span>账号处罚</span><select v-model="action.penalty"><option value="NONE">只记录违规</option><option value="WARNING">正式警告</option><option value="MUTE_7_DAYS">禁言 7 天</option><option value="BAN">直接封号</option></select></label><label class="field"><span>给用户的说明</span><textarea v-model="action.note" maxlength="500" rows="3" placeholder="说明判断依据或需要用户改正的内容"></textarea></label></template><p v-else class="resolved-note">处理结果：{{resolutionLabel(reportDetail.resolution_action)}}<template v-if="reportDetail.admin_note"><br />管理员说明：{{reportDetail.admin_note}}</template></p>
      </template>
      <template #actions><template v-if="reportDetail?.status==='PENDING'"><UiButton variant="ghost" :disabled="reportBusy" @click="resolveReport('DISMISS')">驳回举报</UiButton><UiButton variant="danger" :loading="reportBusy" loading-text="正在处理" @click="resolveReport('CONFIRM')">确认违规并执行</UiButton></template></template>
    </UiDialog>
    <UiDialog :open="Boolean(confirmation)" :title="confirmation?.title || '确认管理操作'" :description="confirmation?.description" :busy="reportBusy" @close="confirmation=null"><template #actions><UiButton variant="ghost" :disabled="reportBusy" @click="confirmation=null">取消</UiButton><UiButton variant="danger" :loading="reportBusy" loading-text="正在执行" @click="executeConfirmation">确认并记录</UiButton></template></UiDialog>
  </main>
</template>

<style scoped>
.admin-page { min-height: 100vh; color: var(--color-text-primary); background: var(--color-bg-canvas); }
.admin-header { height: 72px; padding-inline: max(var(--space-6), calc((100vw - var(--container-wide)) / 2)); display: flex; align-items: center; justify-content: space-between; border-bottom: var(--border-hairline) solid var(--color-border-subtle); background: var(--color-surface-default); }
.admin-brand,.admin-account { display: flex; align-items: center; gap: var(--space-3); }
.admin-brand > span { width: var(--control-md); height: var(--control-md); display: grid; place-items: center; color: var(--color-text-inverse); background: var(--color-action-primary); border-radius: var(--radius-md); }
.admin-brand b,.admin-brand small { display: block; }
.admin-brand small { margin-top: var(--space-0-5); color: var(--color-text-secondary); font-size: var(--type-caption-size); }
.admin-account span { color: var(--color-text-secondary); font-size: var(--type-body-sm-size); }
.admin-wrap { max-width: var(--container-wide); margin: auto; padding: var(--space-10) var(--space-6) var(--space-20); }
.admin-intro { margin-bottom: var(--space-6); display: flex; align-items: end; justify-content: space-between; gap: var(--space-8); }
.admin-intro h1 { margin: 0 0 var(--space-2); font-family: var(--font-display); font-size: var(--type-heading-1-size); line-height: var(--type-heading-1-line); }
.admin-intro p { max-width: 68ch; color: var(--color-text-secondary); line-height: var(--type-body-md-line); }
.admin-stats { display: flex; gap: var(--space-2); }
.admin-stats > span { min-width: 132px; padding: var(--space-3) var(--space-4); display: grid; grid-template-columns: auto 1fr; gap: var(--space-0-5) var(--space-2); color: var(--color-text-inverse); background: var(--color-action-primary); border-radius: var(--radius-md); }
.admin-stats svg { grid-row: 1 / 3; }
.admin-stats b { font-size: var(--type-title-lg-size); font-variant-numeric: tabular-nums; }
.admin-stats small { font-size: var(--type-caption-size); opacity: .82; }
.admin-tabs { margin-bottom: var(--space-5); display: flex; gap: var(--space-2); }
.admin-tabs button { min-height: var(--control-md); padding-inline: var(--space-4); color: var(--color-text-secondary); background: transparent; border: var(--border-hairline) solid var(--color-border-default); border-radius: var(--radius-md); }
.admin-tabs button.active { color: var(--color-text-inverse); background: var(--color-action-primary); border-color: var(--color-action-primary); }
.admin-grid { display: grid; grid-template-columns: minmax(0,1fr) 340px; gap: var(--space-5); align-items: start; }
.admin-panel { padding: var(--space-6); background: var(--color-surface-default); border: var(--border-hairline) solid var(--color-border-subtle); border-radius: var(--radius-lg); box-shadow:var(--shadow-sm); }
.admin-panel-heading { margin-bottom: var(--space-5); display: flex; align-items: center; justify-content: space-between; gap: var(--space-5); }
.admin-panel h2 { margin: 0; font-family: var(--font-display); font-size: var(--type-heading-3-size); }
.admin-filter select,.report-meta + .field select { min-height: var(--control-md); padding-inline: var(--space-3); color: var(--color-text-primary); background: var(--color-surface-raised); border: var(--border-hairline) solid var(--color-border-default); border-radius: var(--radius-md); }
.admin-search { min-height: var(--control-md); display: flex; align-items: center; gap: var(--space-2); padding-left: var(--space-3); background: var(--color-surface-raised); border: var(--border-hairline) solid var(--color-border-default); border-radius: var(--radius-md); }
.admin-search:focus-within { border-color: var(--color-focus-ring); box-shadow:var(--shadow-focus); }
.admin-search input { width: 240px; min-width: 0; color: var(--color-text-primary); background: transparent; border: 0; outline: 0; }
.admin-search button { align-self: stretch; padding-inline: var(--space-4); color: var(--color-text-inverse); background: var(--color-action-primary); border: 0; border-radius: 0 var(--radius-md) var(--radius-md) 0; }
.admin-empty { padding: var(--space-10); color: var(--color-text-secondary); text-align: center; font-size: var(--type-body-sm-size); }
.report-list,.admin-user-list { display: grid; gap: var(--space-2); }
.report-list > button { width: 100%; min-height: 88px; padding: var(--space-4); display: grid; grid-template-columns: 44px minmax(0,1fr) auto; align-items: center; gap: var(--space-3); color: var(--color-text-primary); background: var(--color-surface-raised); border: var(--border-hairline) solid var(--color-border-subtle); border-radius: var(--radius-md); text-align: left; }
.report-list > button:hover { border-color: var(--color-border-strong); }
.report-list > button:disabled { opacity: .58; cursor: wait; }
.report-icon { width: 44px; height: 44px; display: grid; place-items: center; color: var(--color-danger-fg); background: var(--color-danger-bg); border-radius: var(--radius-md); }
.report-list strong,.report-list p,.report-list small { display: block; }
.report-list p { margin: var(--space-1) 0; color: var(--color-text-secondary); font-size: var(--type-body-sm-size); }
.report-list small,.report-list em { color: var(--color-text-tertiary); font-size: var(--type-caption-size); font-style: normal; }
.admin-user-row { padding: var(--space-4); display: grid; grid-template-columns: 48px minmax(0,1fr) auto; align-items: center; gap: var(--space-3); background: var(--color-surface-raised); border: var(--border-hairline) solid var(--color-border-subtle); border-radius: var(--radius-md); }
.admin-avatar { width: 48px; height: 48px; display: grid; place-items: center; color: var(--color-text-inverse); background: var(--color-action-primary); border-radius: var(--radius-md); font: 600 var(--type-title-lg-size) var(--font-display); }
.admin-user-copy > div { display: flex; align-items: center; gap: var(--space-2); }
.admin-user-copy em { padding: var(--space-0-5) var(--space-2); color: var(--color-accent-emotional); background: var(--color-selection); border-radius: var(--radius-full); font-size: var(--type-micro-size); font-style: normal; }
.admin-user-copy em.danger { color: var(--color-danger-fg); background: var(--color-danger-bg); }
.admin-user-copy em.warning { color: var(--color-warning-fg); background: var(--color-warning-bg); }
.admin-user-copy p { margin: var(--space-1) 0; color: var(--color-text-secondary); font-size: var(--type-body-sm-size); }
.admin-user-copy code { font-size: var(--type-caption-size); font-variant-numeric: tabular-nums; }
.admin-user-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: var(--space-2); }
.admin-user-actions .button { min-height: var(--control-md); padding-inline: var(--space-3); display: inline-flex; align-items: center; gap: var(--space-1); font-size: var(--type-body-sm-size); }
.danger-button { color: var(--color-danger-fg); border-color: var(--color-danger-border); }
.admin-pagination { margin-top: var(--space-4); display: flex; align-items: center; justify-content: space-between; color: var(--color-text-secondary); font-size: var(--type-body-sm-size); font-variant-numeric: tabular-nums; }
.admin-pagination div { display: flex; gap: var(--space-2); }
.admin-pagination button { width: var(--control-md); height: var(--control-md); display: grid; place-items: center; color: var(--color-text-primary); background: var(--color-surface-raised); border: var(--border-hairline) solid var(--color-border-default); border-radius: var(--radius-md); }
.audit-list { margin-top: var(--space-4); display: grid; }
.audit-list article { padding: var(--space-3) 0; display: grid; grid-template-columns: 8px 1fr; gap: var(--space-3); border-bottom: var(--border-hairline) solid var(--color-border-subtle); }
.audit-list i { width: 8px; height: 8px; margin-top: var(--space-1-5); background: var(--color-action-primary); border-radius: var(--radius-full); }
.audit-list p,.policy { color: var(--color-text-secondary); font-size: var(--type-body-sm-size); line-height: var(--type-body-sm-line); }
.audit-list p { margin: var(--space-1) 0; }
.audit-list time { color: var(--color-text-tertiary); font-size: var(--type-caption-size); }
.admin-dialog-help { margin-top: var(--space-3); color: var(--color-text-secondary); font-size: var(--type-body-sm-size); }
.report-meta { margin-bottom: var(--space-4); display: flex; flex-wrap: wrap; gap: var(--space-2); }
.report-meta span { padding: var(--space-2) var(--space-3); color: var(--color-text-secondary); background: var(--color-bg-subtle); border-radius: var(--radius-sm); font-size: var(--type-caption-size); }
.report-description,.resolved-note { padding: var(--space-3); color: var(--color-danger-fg); background: var(--color-danger-bg); border-radius: var(--radius-md); font-size: var(--type-body-sm-size); line-height: var(--type-body-sm-line); }
.evidence { margin: var(--space-4) 0; padding: var(--space-4); background: var(--color-surface-raised); border: var(--border-hairline) solid var(--color-border-subtle); border-radius: var(--radius-lg); }
.evidence h3 { margin-bottom: var(--space-2); font-family: var(--font-display); font-size: var(--type-title-lg-size); }
.evidence p { white-space: pre-wrap; line-height: var(--type-body-md-line); }
.evidence img,.evidence video { width: 100%; max-height: 420px; margin-top: var(--space-3); object-fit: contain; background: var(--color-bg-subtle); border-radius: var(--radius-md); }
.evidence-loading { color: var(--color-text-secondary); font-size: var(--type-body-sm-size); }
.check-row { margin: var(--space-4) 0; padding: var(--space-3); display: flex; align-items: center; gap: var(--space-2); border: var(--border-hairline) solid var(--color-border-default); border-radius: var(--radius-md); font-size: var(--type-body-sm-size); }
@media (max-width: 1000px) { .admin-grid { grid-template-columns: 1fr; } .audit-panel { order: 2; } }
@media (max-width: 720px) {
  .admin-header { padding-inline: var(--space-3); }
  .admin-account span,.admin-stats { display: none; }
  .admin-wrap { padding: var(--space-8) var(--space-3) var(--space-16); }
  .admin-intro h1 { font-size: var(--type-heading-1-size); line-height: var(--type-heading-1-line); }
  .admin-intro p { font-size: var(--type-body-sm-size); }
  .admin-tabs { overflow-x: auto; }
  .admin-tabs button { flex: 1 0 auto; }
  .admin-panel { padding: var(--space-4); }
  .admin-panel-heading { align-items: stretch; flex-direction: column; }
  .admin-search input { width: 100%; }
  .admin-user-row { grid-template-columns: 48px minmax(0,1fr); }
  .admin-user-actions { grid-column: 1 / -1; justify-content: flex-start; }
  .report-list > button { grid-template-columns: 44px minmax(0,1fr); }
  .report-list em { display: none; }
}
</style>
