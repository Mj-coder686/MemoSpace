<script setup lang="ts">
import { ref } from 'vue'
import { Flag } from 'lucide-vue-next'
import http, { errorMessage } from '../api/http'
import { UiButton, UiDialog } from './ui'

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
  <UiDialog :open="true" :title="`举报这条${targetType==='MEMORY'?'记忆':'评论'}`" :busy="busy" @close="emit('close')">
      <form id="community-report-form" class="report-modal" @submit.prevent="submit">
        <p class="report-privacy"><span class="report-mark" aria-hidden="true"><Flag :size="18" /></span><span>举报对象：<b>{{targetTitle}}</b>。管理员只能查看这条被举报的证据，不会因此获得浏览用户其他私密内容的权限。</span></p>
        <label class="field"><span>举报原因</span><select v-model="reason"><option v-for="item in reasons" :key="item[0]" :value="item[0]">{{item[1]}}</option></select></label>
        <label class="field"><span>补充说明</span><textarea v-model="description" maxlength="500" rows="4" placeholder="请说明具体问题，便于管理员判断（可选）"></textarea></label>
        <p v-if="message" class="form-error" role="alert">{{message}}</p>
      </form>
      <template #actions><UiButton variant="ghost" :disabled="busy" @click="emit('close')">取消</UiButton><UiButton type="submit" form="community-report-form" variant="danger" :loading="busy" loading-text="正在提交">提交举报</UiButton></template>
  </UiDialog>
</template>

<style scoped>
.report-modal { display: grid; gap: var(--space-4); }
.report-privacy { display: grid; grid-template-columns: 44px minmax(0,1fr); align-items: start; gap: var(--space-3); color: var(--color-text-secondary); font-size: var(--type-body-sm-size); line-height: var(--type-body-sm-line); }
.report-mark { width: 44px; height: 44px; display: grid; place-items: center; color: var(--color-danger-fg); background: var(--color-danger-bg); border-radius: var(--radius-md); }
.report-modal select { width: 100%; min-height: var(--control-md); padding-inline: var(--space-3); color: var(--color-text-primary); background: var(--color-surface-raised); border: var(--border-hairline) solid var(--color-border-default); border-radius: var(--radius-md); }
</style>
