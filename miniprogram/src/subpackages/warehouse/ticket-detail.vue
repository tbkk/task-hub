<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import BusinessPage from '@/components/BusinessPage.vue'
import BaseButton from '@/components/BaseButton.vue'
import StatePanel from '@/components/StatePanel.vue'
import { acceptTicket, addTicketNote, closeTicket, getTicket } from '@/features/ticket/api'
import { ticketStateText } from '@/features/ticket/model'
import { errorText } from '@/features/order/model'
import { newIdempotencyKey } from '@/features/common/idempotency'
const id=ref(''),ticket=ref<Awaited<ReturnType<typeof getTicket>>|null>(null),loading=ref(true),busy=ref(false),error=ref(''),note=ref(''),result=ref('')
async function load(){loading.value=true;error.value='';try{ticket.value=await getTicket(id.value)}catch(e){error.value=errorText(e)}finally{loading.value=false}}
async function action(kind:'accept'|'note'|'close'){if(!ticket.value||busy.value)return;if((kind==='note'&&!note.value.trim())||(kind==='close'&&!result.value.trim())){error.value=kind==='note'?'请填写处理说明':'请填写关闭结果';return}busy.value=true;error.value='';try{const t=ticket.value;if(kind==='accept')ticket.value=await acceptTicket(t.id,t.version,newIdempotencyKey());if(kind==='note')ticket.value=await addTicketNote(t.id,t.version,note.value.trim(),newIdempotencyKey());if(kind==='close')ticket.value=await closeTicket(t.id,t.version,result.value.trim(),newIdempotencyKey());note.value='';result.value=''}catch(e){error.value=errorText(e);await load()}finally{busy.value=false}}
onLoad(q=>{id.value=String(q?.id||'')});onShow(()=>{if(id.value)void load()})
</script>
<template><BusinessPage title="工单处理" subtitle="处理说明写入业务履历"><view class="warehouse-stack"><StatePanel v-if="loading" state="loading" title=""/><StatePanel v-else-if="error&&!ticket" state="error" title="加载失败" :description="error" retry @retry="load"/><template v-else-if="ticket"><view class="warehouse-card"><view class="warehouse-row"><text class="warehouse-heading">{{ticket.number}}</text><text class="warehouse-badge">{{ticketStateText(ticket.state)}}</text></view><text class="warehouse-muted">订单 {{ticket.orderId}}</text><text class="warehouse-muted">{{ticket.description}}</text><text v-if="ticket.result" class="warehouse-note">关闭结果：{{ticket.result}}</text></view><view v-for="item in ticket.notes||[]" :key="item.id" class="warehouse-card"><text class="warehouse-muted">{{item.authorName}} · {{item.createdAt}}</text><text>{{item.text}}</text></view><text v-if="error" class="warehouse-error">{{error}}</text><template v-if="ticket.state!=='CLOSED'"><BaseButton v-if="ticket.state==='OPEN'" :loading="busy" @click="action('accept')">受理工单</BaseButton><textarea v-model="note" class="warehouse-input-box" placeholder="补充处理说明" maxlength="1000"/><BaseButton variant="secondary" :loading="busy" @click="action('note')">保存处理说明</BaseButton><textarea v-model="result" class="warehouse-input-box" placeholder="关闭结果（必填）" maxlength="1000"/><BaseButton :loading="busy" @click="action('close')">关闭工单</BaseButton></template><BaseButton variant="plain" @click="load">刷新工单</BaseButton></template></view></BusinessPage></template>
<style lang="scss">@import '@/styles/warehouse.scss';</style>
