<script setup lang="ts">
import { ref } from 'vue'
import { onLoad,onShow } from '@dcloudio/uni-app'
import BusinessPage from '@/components/BusinessPage.vue'
import BaseButton from '@/components/BaseButton.vue'
import StatePanel from '@/components/StatePanel.vue'
import { getMessage, markMessageRead } from '@/features/message/api'
import { messageTargetUrl, type Message } from '@/features/message/model'
import { errorText } from '@/features/order/model'
const id=ref(''),message=ref<Message|null>(null),loading=ref(true),error=ref('')
async function load(){loading.value=true;error.value='';try{message.value=await getMessage(id.value);if(message.value&&!message.value.readAt)message.value=await markMessageRead(id.value)}catch(e){error.value=errorText(e)}finally{loading.value=false}}
function openTarget(){const url=message.value&&messageTargetUrl(message.value.target);if(url)uni.navigateTo({url})}
onLoad(q=>{id.value=String(q?.id||'')});onShow(()=>{if(id.value)void load()})
</script>
<template><BusinessPage title="消息详情" subtitle="通知正文与关联业务"><view class="message-detail"><StatePanel v-if="loading" state="loading" title=""/><StatePanel v-else-if="error" state="error" title="加载失败" :description="error" retry @retry="load"/><template v-else-if="message"><text class="detail-title">{{message.title}}</text><text class="detail-date">{{message.createdAt}}</text><text class="detail-body">{{message.body}}</text><BaseButton v-if="messageTargetUrl(message.target)" @click="openTarget">查看关联业务</BaseButton><BaseButton variant="plain" @click="load">刷新消息</BaseButton></template></view></BusinessPage></template>
<style scoped>.message-detail{display:flex;flex-direction:column;gap:12px}.detail-title{font-size:20px;font-weight:700}.detail-date{font-size:12px;color:#8a94a6}.detail-body{padding:16px;background:#fff;border-radius:8px;color:#4c586b;line-height:1.7;white-space:pre-wrap}</style>
