<script setup lang="ts">
import { ref } from 'vue'
import { Flag, X } from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'

const props = defineProps<{ targetType:'MEMORY'|'COMMENT'; targetId:number; targetTitle:string }>()
const emit = defineEmits<{ close:[]; reported:[] }>()
const reason = ref('ILLEGAL')
const description = ref('')
const busy = ref(false)
const message = ref('')
const reasons = [
  ['ILLEGAL','违法或违禁信息'],['HARASSMENT','骚扰、辱骂或仇恨'],['PORNOGRAPHY','色情或低俗内容'],
  ['VIOLENCE','暴力或危险行为'],['FRAUD','诈骗或虚假信息'],['PRIVACY','泄露他人隐私'],['OTHER','其他问题'],
]

const submit = async () => {
  busy.value = true; message.value = ''
  try {
    await http.post('/reports', { targetType:props.targetType,targetId:props.targetId,reasonCategory:reason.value,description:description.value })
    emit('reported')
  } catch (error) { message.value = errorMessage(error) }
  finally { busy.value = false }
}
</script>

<template>
  <Teleport to="body">
    <div class="modal-backdrop" @click.self="!busy && emit('close')" @keydown.esc="!busy && emit('close')">
      <form class="report-modal" role="dialog" aria-modal="true" aria-labelledby="report-title" @submit.prevent="submit">
        <header><div><span class="report-mark"><Flag :size="18" /></span><div><span class="eyebrow">COMMUNITY REPORT</span><h2 id="report-title">举报这条{{targetType==='MEMORY'?'记忆':'评论'}}</h2></div></div><button type="button" class="icon-button" :disabled="busy" aria-label="关闭" @click="emit('close')"><X :size="18" /></button></header>
        <p>举报对象：<b>{{targetTitle}}</b>。管理员只能查看这条被举报的证据，不会因此获得浏览用户其他私密内容的权限。</p>
        <label class="field"><span>举报原因</span><select v-model="reason"><option v-for="item in reasons" :key="item[0]" :value="item[0]">{{item[1]}}</option></select></label>
        <label class="field"><span>补充说明</span><textarea v-model="description" maxlength="500" rows="4" placeholder="请说明具体问题，便于管理员判断（可选）"></textarea></label>
        <p v-if="message" class="form-error" role="alert">{{message}}</p>
        <footer><button type="button" class="button" :disabled="busy" @click="emit('close')">取消</button><button type="submit" class="button primary" :disabled="busy">{{busy?'正在提交…':'提交举报'}}</button></footer>
      </form>
    </div>
  </Teleport>
</template>

<style scoped>
.report-modal{width:min(540px,calc(100vw - 24px));padding:28px;border:1px solid var(--line);border-radius:26px;background:var(--surface-solid);box-shadow:0 28px 80px rgba(26,23,25,.32)}.report-modal header,.report-modal header>div,.report-modal footer{display:flex;align-items:center}.report-modal header{justify-content:space-between;gap:15px}.report-modal header>div{gap:12px}.report-mark{width:42px;height:42px;display:grid;place-items:center;border-radius:13px;color:#8d4f55;background:#f4e1e1}.report-modal h2{margin:3px 0 0;font-size:24px}.report-modal>p{margin:18px 0;color:var(--muted);font-size:12px;line-height:1.75}.report-modal footer{justify-content:flex-end;gap:8px;margin-top:20px}.report-modal select{width:100%;height:44px;padding:0 12px;border:1px solid var(--control-line);border-radius:12px;color:var(--ink);background:var(--surface-solid)}
</style>
