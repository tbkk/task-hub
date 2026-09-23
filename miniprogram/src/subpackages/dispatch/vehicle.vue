<script setup lang="ts">
import { ref } from 'vue'
import { onLoad,onShow } from '@dcloudio/uni-app'
import BusinessPage from '@/components/BusinessPage.vue'
import BaseButton from '@/components/BaseButton.vue'
import StatePanel from '@/components/StatePanel.vue'
import { getVehicle } from '@/features/control/api'
import type { Vehicle } from '@/features/control/model'
import { errorText } from '@/features/order/model'
const id=ref(''),vehicle=ref<Vehicle|null>(null),loading=ref(true),error=ref('')
async function load(){loading.value=true;error.value='';try{vehicle.value=await getVehicle(id.value)}catch(e){error.value=errorText(e)}finally{loading.value=false}}
onLoad(q=>{id.value=String(q?.id||'')});onShow(()=>{if(id.value)void load()})
</script>
<template><BusinessPage title="车辆详情" subtitle="车辆快照与格口状态"><view class="dispatch-stack"><StatePanel v-if="loading" state="loading" title=""/><StatePanel v-else-if="error" state="error" title="加载失败" :description="error" retry @retry="load"/><template v-else-if="vehicle"><view class="dispatch-card"><text class="dispatch-heading">{{vehicle.name}}</text><text class="dispatch-muted">在线：{{vehicle.online===true?'是':vehicle.online===false?'否':'待核对'}} · 状态：{{vehicle.businessStatus||'待核对'}}</text><text class="dispatch-muted">速度：{{vehicle.speed==null?'—':vehicle.speed+' km/h'}} · 电量：{{vehicle.batteryPercent==null?'—':vehicle.batteryPercent+'%'}}</text></view><view v-for="door in vehicle.doors" :key="door.compartmentId" class="dispatch-card"><view class="dispatch-row"><text>格口 {{door.compartmentId}}</text><text class="dispatch-badge">{{door.status==='CLOSED'?'已关闭':door.status==='OPEN'?'已打开':'待核对'}}</text></view></view><text v-if="vehicle.blockers?.length" class="dispatch-warning">{{vehicle.blockers.map(item=>item.message).join('；')}}</text><BaseButton variant="plain" @click="load">刷新车辆</BaseButton></template></view></BusinessPage></template>
<style scoped>.dispatch-stack{display:flex;flex-direction:column;gap:12px}.dispatch-card{padding:16px;background:#fff;border-radius:8px}.dispatch-row{display:flex;justify-content:space-between}.dispatch-heading{font-size:17px;font-weight:700}.dispatch-muted{display:block;color:#667387;font-size:13px;line-height:22px;margin-top:8px}.dispatch-badge{color:#1677ff}.dispatch-warning{color:#b54708;line-height:22px}</style>
