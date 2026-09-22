<script setup lang="ts">
import AppPage from '@/components/AppPage.vue'
import StatePanel from '@/components/StatePanel.vue'
import { currentUser } from '@/features/auth/session'
import { workspace } from '@/features/workspace/store'
import { useAccess } from '@/features/workspace/navigation'
import { onMounted, ref } from 'vue'
import { listMessages, markMessageRead, type Message } from '@/features/message/api'
useAccess()
const items=ref<Message[]>([]); const loading=ref(true); const error=ref('')
onMounted(async()=>{try{items.value=(await listMessages()).items}catch(e){error.value=e instanceof Error?e.message:'消息加载失败'}finally{loading.value=false}})
async function read(item:Message){if(!item.readAt){const updated=await markMessageRead(item.id); Object.assign(item,updated)}}
</script>
<template><AppPage v-if="currentUser && workspace.activeRole" title="消息" subtitle="与本人业务有关的通知" :tab="2"><StatePanel v-if="loading" title="正在加载消息" description="请稍候"/><StatePanel v-else-if="error" title="加载失败" :description="error"/><StatePanel v-else-if="!items.length" title="暂无消息" description="新的业务通知会显示在这里"/><view v-else class="messages"><view v-for="item in items" :key="item.id" class="message" :class="{unread:!item.readAt}" @click="read(item)"><view class="title"><text>{{item.title}}</text><text class="date">{{item.createdAt}}</text></view><text class="body">{{item.body}}</text></view></view></AppPage></template>
<style scoped>.messages{margin-top:8px}.message{padding:16px 0;border-bottom:1px solid #e6eaf0}.title{display:flex;justify-content:space-between;gap:8px;font-weight:600}.date{color:#8a94a6;font-size:12px;font-weight:400}.body{display:block;margin-top:8px;color:#667387;font-size:14px;line-height:1.5}.unread .title text:first-child{color:#1677ff}</style>
