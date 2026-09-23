<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import BusinessPage from '@/components/BusinessPage.vue'
import BaseButton from '@/components/BaseButton.vue'
import StatePanel from '@/components/StatePanel.vue'
import { acceptTicket, addTicketNote, closeTicket, getTicket } from '@/features/ticket/api'
import { ticketStateText } from '@/features/ticket/model'
import { errorText } from '@/features/order/model'
import { Submission } from '@/features/order/submission'
const id=ref(''),ticket=ref<Awaited<ReturnType<typeof getTicket>>|null>(null),loading=ref(true),error=ref(''),note=ref(''),result=ref('')
type TicketAction={kind:'accept'|'note'|'close';id:string;expectedVersion:number;text:string;result:string}
const submission=reactive(new Submission<TicketAction>())
const pendingKind=ref<TicketAction['kind']|''>('')
async function load(){loading.value=true;error.value='';try{ticket.value=await getTicket(id.value)}catch(e){error.value=errorText(e)}finally{loading.value=false}}
async function action(kind:'accept'|'note'|'close'){
 if(!ticket.value||submission.busy)return
 if(submission.pending&&pendingKind.value!==kind){error.value='请重试上次工单操作';return}
 if((kind==='note'&&!note.value.trim())||(kind==='close'&&!result.value.trim())){error.value=kind==='note'?'请填写处理说明':'请填写关闭结果';return}
 error.value='';const t=ticket.value
 const body:TicketAction={kind,id:t.id,expectedVersion:t.version,text:note.value.trim(),result:result.value.trim()}
 if(!submission.pending)pendingKind.value=kind
 try{
  ticket.value=await submission.run(body,(value,key)=>value.kind==='accept'?acceptTicket(value.id,value.expectedVersion,key):value.kind==='note'?addTicketNote(value.id,value.expectedVersion,value.text,key):closeTicket(value.id,value.expectedVersion,value.result,key))
  note.value='';result.value=''
 }catch(e){error.value=errorText(e);if(!submission.pending)await load()}
 finally{if(!submission.pending)pendingKind.value=''}
}
onLoad(q=>{id.value=String(q?.id||'')});onShow(()=>{if(id.value)void load()})
</script>
<template><BusinessPage title="工单处理" subtitle="处理说明写入业务履历"><view class="warehouse-stack"><StatePanel v-if="loading" state="loading" title=""/><StatePanel v-else-if="error&&!ticket" state="error" title="加载失败" :description="error" retry @retry="load"/><template v-else-if="ticket"><view class="warehouse-card"><view class="warehouse-row"><text class="warehouse-heading">{{ticket.number}}</text><text class="warehouse-badge">{{ticketStateText(ticket.state)}}</text></view><text class="warehouse-muted">订单 {{ticket.orderId}}</text><text class="warehouse-muted">{{ticket.description}}</text><text v-if="ticket.result" class="warehouse-note">关闭结果：{{ticket.result}}</text></view><view v-for="item in ticket.notes||[]" :key="item.id" class="warehouse-card"><text class="warehouse-muted">{{item.authorName}} · {{item.createdAt}}</text><text>{{item.text}}</text></view><text v-if="error" class="warehouse-error">{{error}}</text><text v-if="submission.pending&&!submission.busy" class="warehouse-note">上次工单操作结果待核对，请重试同一请求。</text><template v-if="ticket.state!=='CLOSED'"><BaseButton v-if="ticket.state==='OPEN'" :loading="submission.busy" :disabled="submission.busy||submission.pending&&pendingKind!=='accept'" @click="action('accept')">{{submission.pending&&pendingKind==='accept'?'重试同一请求':'受理工单'}}</BaseButton><textarea v-model="note" class="warehouse-input-box" :disabled="submission.pending" placeholder="补充处理说明" maxlength="1000"/><BaseButton variant="secondary" :loading="submission.busy" :disabled="submission.busy||submission.pending&&pendingKind!=='note'" @click="action('note')">{{submission.pending&&pendingKind==='note'?'重试同一请求':'保存处理说明'}}</BaseButton><textarea v-model="result" class="warehouse-input-box" :disabled="submission.pending" placeholder="关闭结果（必填）" maxlength="1000"/><BaseButton :loading="submission.busy" :disabled="submission.busy||submission.pending&&pendingKind!=='close'" @click="action('close')">{{submission.pending&&pendingKind==='close'?'重试同一请求':'关闭工单'}}</BaseButton></template><BaseButton variant="plain" :disabled="submission.pending||submission.busy" @click="load">刷新工单</BaseButton></template></view></BusinessPage></template>
<style lang="scss">@import '@/styles/warehouse.scss';</style>
