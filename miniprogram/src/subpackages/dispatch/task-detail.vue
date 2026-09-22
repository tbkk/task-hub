<script setup lang="ts">
import { ref } from 'vue'
import { onLoad,onShow } from '@dcloudio/uni-app'
import BusinessPage from '@/components/BusinessPage.vue'
import BaseButton from '@/components/BaseButton.vue'
import StatePanel from '@/components/StatePanel.vue'
import { cancelTask, getTask, goTask } from '@/features/control/api'
import { canControl, taskStateText, type VehicleTask } from '@/features/control/model'
import { controlStatusText } from '@/features/pickup/model'
import { newIdempotencyKey } from '@/features/common/idempotency'
import { errorText } from '@/features/order/model'
const id=ref(''),task=ref<VehicleTask|null>(null),loading=ref(true),busy=ref(false),error=ref(''),result=ref('')
async function load(){loading.value=true;error.value='';try{task.value=await getTask(id.value)}catch(e){error.value=errorText(e)}finally{loading.value=false}}
async function control(action:'go'|'cancel'){if(!task.value||busy.value)return;busy.value=true;error.value='';try{const response=action==='go'?await goTask(task.value.id,0,newIdempotencyKey()):await cancelTask(task.value.id,0,newIdempotencyKey());result.value=controlStatusText(response.status);await load()}catch(e){error.value=errorText(e)}finally{busy.value=false}}
onLoad(q=>{id.value=String(q?.id||'')});onShow(()=>{if(id.value)void load()})
</script>
<template><BusinessPage title="任务详情" subtitle="控制结果以服务端实际状态为准"><view class="dispatch-stack"><StatePanel v-if="loading" state="loading" title=""/><StatePanel v-else-if="error&&!task" state="error" title="加载失败" :description="error" retry @retry="load"/><template v-else-if="task"><view class="dispatch-card"><view class="dispatch-row"><text class="dispatch-heading">{{task.id}}</text><text class="dispatch-badge">{{taskStateText(task.state)}}</text></view><text class="dispatch-muted">车辆 {{task.vehicleId}} · 批次 {{task.batchId}}</text><text class="dispatch-muted">当前站 {{task.currentStopId||'未到站'}} · 下一站 {{task.nextStopId||'未明确'}}</text></view><view v-if="result" class="dispatch-card"><text class="dispatch-muted">最近控制结果：{{result}}</text></view><text v-if="error" class="dispatch-warning">{{error}}</text><view class="dispatch-actions"><BaseButton :disabled="!canControl(task,'GO')" :loading="busy" @click="control('go')">继续出发</BaseButton><BaseButton variant="secondary" :disabled="!canControl(task,'CANCEL')" :loading="busy" @click="control('cancel')">取消任务</BaseButton><BaseButton variant="plain" @click="load">刷新状态</BaseButton></view></template></view></BusinessPage></template>
<style scoped>.dispatch-stack{display:flex;flex-direction:column;gap:16px}.dispatch-card{padding:16px;background:#fff;border-radius:8px}.dispatch-row{display:flex;justify-content:space-between;gap:12px}.dispatch-heading{font-size:16px;font-weight:700}.dispatch-badge{color:#1677ff;font-size:12px}.dispatch-muted{display:block;color:#667387;font-size:13px;line-height:22px;margin-top:8px}.dispatch-warning{display:block;color:#b54708}.dispatch-actions{display:flex;flex-direction:column;gap:8px}</style>
