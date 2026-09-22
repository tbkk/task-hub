<script setup lang="ts">
import {ref} from 'vue'
import {onLoad} from '@dcloudio/uni-app'
import BusinessPage from '@/components/BusinessPage.vue'
import StatePanel from '@/components/StatePanel.vue'
import BaseButton from '@/components/BaseButton.vue'
import {refreshAccess} from '@/features/workspace/navigation'
import {getOrder} from '@/features/order/api'
import {errorText,type Order} from '@/features/order/model'
import {slotText} from '@/features/catalog/model'
const order=ref<Order|null>(null),id=ref(''),loading=ref(true),error=ref('')
async function load(){loading.value=true;error.value='';try{if(await refreshAccess(false,'worker'))order.value=await getOrder(id.value)}catch(e){error.value=errorText(e)}finally{loading.value=false}}
onLoad(q=>{id.value=String(q?.id||'');void load()})
function detail(){uni.redirectTo({url:`/subpackages/worker/order-detail?id=${encodeURIComponent(id.value)}`})}
function home(){uni.reLaunch({url:'/subpackages/worker/index'})}
</script>
<template><BusinessPage title="申请提交结果" :back="false"><view class="worker-stack"><StatePanel v-if="loading" title="" state="loading"/><StatePanel v-else-if="error" title="订单详情加载失败" :description="error" state="error" retry @retry="load"/><template v-else-if="order"><text class="worker-success">✓</text><text class="worker-heading">申请已提交</text><text class="worker-muted">仓库受理后可在订单中查看配送进度</text><view class="worker-card"><text class="worker-heading">{{order.description}}</text><text class="worker-muted">{{order.stopName || order.stopId}} · {{slotText({start:order.slotStart,end:order.slotEnd})}}</text><text class="worker-muted">订单 #{{order.number}}</text></view><text v-if="!order.receiverBound" class="worker-note">接收手机号暂未匹配已验证账号，请接收人完成本人手机号验证后取货。</text><BaseButton @click="detail">查看订单</BaseButton></template><BaseButton variant="secondary" @click="home">返回申请首页</BaseButton></view></BusinessPage></template>
<style lang="scss">@import '@/styles/worker.scss';</style>
