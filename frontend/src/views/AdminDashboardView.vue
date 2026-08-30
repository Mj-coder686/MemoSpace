<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ChevronLeft, ChevronRight, Fingerprint, KeyRound, LogOut, Search, ShieldCheck, Users, X } from 'lucide-vue-next'
import dayjs from 'dayjs'
import adminHttp from '../api/adminHttp'
import { errorMessage } from '../api/http'

type AdminUser = { id:number; public_id:string; username:string; nickname:string; is_admin:boolean; created_at:string }
const router=useRouter()
const adminUser=ref<any>(JSON.parse(localStorage.getItem('memospace_admin_user')||'{}'))
const users=ref<AdminUser[]>([]);const audits=ref<any[]>([]);const total=ref(0);const page=ref(1);const size=20
const keyword=ref('');const loading=ref(true);const message=ref('')
const modal=ref<'password'|'memo'|''>('');const selected=ref<AdminUser|null>(null);const value=ref('');const saving=ref(false)
const pages=computed(()=>Math.max(1,Math.ceil(total.value/size)))

const load=async()=>{loading.value=true;try{const [userResult,auditResult,meResult]=await Promise.all([adminHttp.get('/admin/users',{params:{keyword:keyword.value,page:page.value,size}}),adminHttp.get('/admin/audit'),adminHttp.get('/admin/me')]);users.value=userResult.data.items;total.value=Number(userResult.data.total);audits.value=auditResult.data;adminUser.value=meResult.data;localStorage.setItem('memospace_admin_user',JSON.stringify(meResult.data))}catch(error){message.value=errorMessage(error)}finally{loading.value=false}}
onMounted(load)
const search=()=>{page.value=1;load()}
const turn=(next:number)=>{page.value=Math.min(pages.value,Math.max(1,next));load()}
const open=(kind:'password'|'memo',user:AdminUser)=>{modal.value=kind;selected.value=user;value.value=kind==='memo'?user.public_id:'';message.value=''}
const save=async()=>{if(!selected.value)return;saving.value=true;message.value='';try{if(modal.value==='password')await adminHttp.put(`/admin/users/${selected.value.id}/password`,{newPassword:value.value});else await adminHttp.put(`/admin/users/${selected.value.id}/memo-id`,{memoId:value.value});message.value=modal.value==='password'?`已为 ${selected.value.nickname} 设置临时密码。`:`${selected.value.nickname} 的 Memo ID 已更新。`;modal.value='';await load()}catch(error){message.value=errorMessage(error)}finally{saving.value=false}}
const logout=()=>{localStorage.removeItem('memospace_admin_token');localStorage.removeItem('memospace_admin_user');router.push('/admin/login')}
</script>

<template>
  <main class="admin-page">
    <header class="admin-header"><router-link class="admin-brand" to="/admin"><span><ShieldCheck :size="20" /></span><div><b>拾光管理员中心</b><small>MEMOSPACE CONTROL ROOM</small></div></router-link><div class="admin-account"><span>{{adminUser.nickname||adminUser.username}}</span><router-link class="button" to="/home">返回网站</router-link><button class="icon-button" title="退出管理员登录" @click="logout"><LogOut :size="17" /></button></div></header>
    <div class="admin-wrap">
      <section class="admin-intro"><div><span class="eyebrow">PRIVATE COMMUNITY ADMINISTRATION</span><h1>朋友账号管理</h1><p>这里只提供小型社区真正需要的两项能力：重置临时密码、修改对外显示的 12 位 Memo ID。</p></div><div class="admin-stat"><Users :size="22" /><b>{{total}}</b><span>全部用户</span></div></section>
      <p v-if="message" class="admin-message" role="status">{{message}}</p>
      <section class="admin-grid">
        <div class="admin-panel user-panel">
          <div class="admin-panel-heading"><div><span class="eyebrow">USER DIRECTORY</span><h2>用户列表</h2></div><form class="admin-search" @submit.prevent="search"><Search :size="15" /><input v-model="keyword" placeholder="昵称、用户名或完整 Memo ID" /><button>搜索</button></form></div>
          <div v-if="loading" class="admin-empty">正在读取用户…</div>
          <div v-else-if="users.length" class="admin-user-list">
            <article v-for="user in users" :key="user.id" class="admin-user-row">
              <span class="admin-avatar">{{user.nickname?.slice(0,1)||'拾'}}</span>
              <div class="admin-user-copy"><div><b>{{user.nickname}}</b><em v-if="user.is_admin">管理员</em></div><p>@{{user.username}} · 注册于 {{dayjs(user.created_at).format('YYYY.MM.DD')}}</p><code>Memo ID {{user.public_id}}</code></div>
              <div class="admin-user-actions"><button class="button" @click="open('memo',user)"><Fingerprint :size="15" />修改 ID</button><button class="button" @click="open('password',user)"><KeyRound :size="15" />重置密码</button></div>
            </article>
          </div>
          <div v-else class="admin-empty">没有找到匹配的用户。</div>
          <footer class="admin-pagination"><span>第 {{page}} / {{pages}} 页</span><div><button :disabled="page<=1" @click="turn(page-1)"><ChevronLeft :size="16" /></button><button :disabled="page>=pages" @click="turn(page+1)"><ChevronRight :size="16" /></button></div></footer>
        </div>
        <aside class="admin-panel audit-panel"><span class="eyebrow">AUDIT TRAIL</span><h2>最近管理记录</h2><div v-if="audits.length" class="audit-list"><article v-for="item in audits" :key="item.id"><i :class="item.action_type==='RESET_PASSWORD'?'password':'memo'"></i><div><b>{{item.action_type==='RESET_PASSWORD'?'重置密码':'修改 Memo ID'}}</b><p>{{item.target_nickname||'未知用户'}} · {{item.detail}}</p><time>{{dayjs(item.created_at).format('MM.DD HH:mm')}}</time></div></article></div><p v-else class="admin-empty">还没有管理操作。</p></aside>
      </section>
    </div>

    <div v-if="modal" class="modal-backdrop" @click.self="modal=''">
      <section class="admin-action-modal" role="dialog" aria-modal="true"><header><div><span class="eyebrow">ADMIN ACTION</span><h2>{{modal==='password'?'重置临时密码':'修改 Memo ID'}}</h2></div><button class="icon-button" aria-label="关闭" @click="modal='' "><X :size="17" /></button></header><p>正在修改：<b>{{selected?.nickname}}</b>（@{{selected?.username}}）</p><label class="field"><span>{{modal==='password'?'新的临时密码':'新的 12 位纯数字 Memo ID'}}</span><input v-model="value" :type="modal==='password'?'password':'text'" :maxlength="modal==='password'?72:12" :placeholder="modal==='password'?'至少 8 位':'例如：100000000001'" /></label><small v-if="modal==='password'">管理员无法查看旧密码。用户登录后可在设置页自行修改。</small><small v-else>这里只修改对外代号，不改变数据库内部主键和原有好友、关系、Memory。</small><footer><button class="button" @click="modal=''">取消</button><button class="button primary" :disabled="saving||(modal==='password'?value.length<8:!/^\d{12}$/.test(value))" @click="save">{{saving?'正在保存…':'确认修改'}}</button></footer></section>
    </div>
  </main>
</template>

<style scoped>
.admin-page{min-height:100vh;color:#292b33;background:#eeece7}.admin-header{height:78px;padding:0 max(26px,calc((100vw - 1320px)/2));display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid #d7d3cb;background:#f7f4ee}.admin-brand,.admin-account{display:flex;align-items:center;gap:11px}.admin-brand>span{width:40px;height:40px;display:grid;place-items:center;border-radius:13px;color:white;background:#3d4352}.admin-brand b,.admin-brand small{display:block}.admin-brand small{margin-top:3px;color:#81828a;font-size:8px;letter-spacing:.18em}.admin-account span{color:#686973;font-size:12px}.admin-account .button{min-height:36px;padding:0 13px;font-size:11px}.admin-wrap{max-width:1320px;margin:auto;padding:44px 26px 70px}.admin-intro{display:flex;align-items:end;justify-content:space-between;gap:30px;margin-bottom:28px}.admin-intro h1{margin:8px 0;font-size:43px}.admin-intro p{max-width:690px;margin:0;color:#6e7079;line-height:1.7}.admin-stat{min-width:150px;padding:17px;display:grid;grid-template-columns:auto 1fr;gap:3px 11px;border-radius:20px;color:white;background:#424857}.admin-stat svg{grid-row:1/3}.admin-stat b{font-size:22px}.admin-stat span{font-size:10px;opacity:.72}.admin-message{padding:12px 16px;border:1px solid #c9b7ae;border-radius:14px;color:#704f49;background:#f6ebe5;font-size:12px}.admin-grid{display:grid;grid-template-columns:minmax(0,1fr) 340px;gap:20px;align-items:start}.admin-panel{padding:25px;border:1px solid #d7d3cb;border-radius:25px;background:#faf8f3;box-shadow:0 14px 45px rgba(54,50,45,.08)}.admin-panel-heading{display:flex;align-items:center;justify-content:space-between;gap:18px;margin-bottom:18px}.admin-panel h2{margin:5px 0 0;font-size:22px}.admin-search{height:39px;display:flex;align-items:center;gap:8px;padding-left:11px;border:1px solid #d2cec6;border-radius:13px;background:white}.admin-search input{width:220px;border:0;outline:0;background:transparent}.admin-search button{height:100%;padding:0 13px;border:0;border-left:1px solid #ddd8d0;color:#555a69;background:transparent}.admin-user-list{display:grid;gap:8px}.admin-user-row{padding:14px;display:grid;grid-template-columns:45px minmax(0,1fr) auto;align-items:center;gap:12px;border:1px solid #e0dcd5;border-radius:17px;background:white}.admin-avatar{width:44px;height:44px;display:grid;place-items:center;border-radius:14px;color:white;background:linear-gradient(145deg,#7e6870,#4f596b);font:600 17px 'Noto Serif SC',serif}.admin-user-copy{min-width:0}.admin-user-copy>div{display:flex;align-items:center;gap:7px}.admin-user-copy em{padding:2px 6px;border-radius:999px;color:#744b55;background:#f1dde1;font-size:8px;font-style:normal}.admin-user-copy p{margin:3px 0;color:#85858c;font-size:9px}.admin-user-copy code{color:#565b69;font:600 10px ui-monospace,monospace;letter-spacing:.06em}.admin-user-actions{display:flex;gap:6px}.admin-user-actions .button{min-height:35px;padding:0 10px;display:flex;align-items:center;gap:5px;font-size:10px}.admin-pagination{margin-top:16px;display:flex;align-items:center;justify-content:space-between;color:#85858c;font-size:10px}.admin-pagination div{display:flex;gap:5px}.admin-pagination button{width:32px;height:32px;display:grid;place-items:center;border:1px solid #d7d3cb;border-radius:10px;background:white}.audit-list{display:grid;margin-top:17px}.audit-list article{padding:12px 0;display:grid;grid-template-columns:9px 1fr;gap:11px;border-bottom:1px solid #e3ded7}.audit-list i{width:9px;height:9px;margin-top:5px;border-radius:50%;background:#718092}.audit-list i.password{background:#a76c73}.audit-list b{font-size:12px}.audit-list p{margin:4px 0;color:#74757d;font-size:10px;line-height:1.55}.audit-list time{color:#9a999d;font-size:9px}.admin-empty{padding:34px;text-align:center;color:#85858c;font-size:11px}.admin-action-modal{width:min(500px,calc(100vw - 30px));padding:27px;border-radius:25px;background:#faf8f3}.admin-action-modal header{display:flex;align-items:start;justify-content:space-between}.admin-action-modal h2{margin:5px 0;font-size:25px}.admin-action-modal>p{color:#70717a;font-size:12px}.admin-action-modal>small{display:block;margin-top:10px;color:#85858c;font-size:10px;line-height:1.6}.admin-action-modal footer{margin-top:22px;display:flex;justify-content:flex-end;gap:8px}@media(max-width:1000px){.admin-grid{grid-template-columns:1fr}.audit-panel{order:2}}@media(max-width:720px){.admin-header{padding:0 14px}.admin-account span,.admin-account .button{display:none}.admin-wrap{padding:30px 14px}.admin-intro{align-items:start}.admin-stat{display:none}.admin-panel{padding:16px}.admin-panel-heading{align-items:stretch;flex-direction:column}.admin-search input{width:100%}.admin-user-row{grid-template-columns:40px 1fr}.admin-user-actions{grid-column:1/-1}.admin-user-actions .button{flex:1;justify-content:center}}
</style>
