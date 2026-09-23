<script setup lang="ts">
import AppPage from '@/components/AppPage.vue'
import StatePanel from '@/components/StatePanel.vue'
import { currentUser } from '@/features/auth/session'
import { workspace } from '@/features/workspace/store'
import { useAccess } from '@/features/workspace/navigation'
import { reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import BaseButton from '@/components/BaseButton.vue'
import PagedListState from '@/components/PagedListState.vue'
import { PagedQuery } from '@/features/common/paged-query'
import { listMessages } from '@/features/message/api'
import type { Message } from '@/features/message/model'
import { errorText } from '@/features/order/model'
useAccess()
const state=reactive(new PagedQuery<Message>()); const loading=ref(true); const error=ref('');let serial=0
async function load(more=false){if(more&&(state.loading||state.loadingMore||!state.hasMore))return;const s=++serial;if(more)state.loadingMore=true;else{state.reset();state.loading=true}try{const result=await listMessages(more?state.page+1:1,20);if(s===serial)(more?state.append(result):state.replace(result))}catch(e){if(s===serial)state.fail(errorText(e),more)}finally{loading.value=false}}
function read(item:Message){uni.navigateTo({url:`/pages/messages/detail?id=${encodeURIComponent(item.id)}`})}
onShow(()=>void load())
</script>
<template><AppPage v-if="currentUser && workspace.activeRole" title="消息" subtitle="与本人业务有关的通知" :tab="2"><view class="messages"><PagedListState :loading="state.loading" :loading-more="state.loadingMore" :error="state.error||error" :has-content="!!state.items.length" :has-more="state.hasMore" empty-text="暂无消息" @retry="load()" @more="load(true)"><view v-for="item in state.items" :key="item.id" class="message" :class="{unread:!item.readAt}" @click="read(item)"><view class="title"><text>{{item.title}}</text><text class="date">{{item.createdAt}}</text></view><text class="body">{{item.body}}</text><text v-if="item.target" class="target">查看关联业务　›</text></view></PagedListState><BaseButton variant="plain" :loading="loading" @click="load()">刷新消息</BaseButton></view></AppPage></template>
<style scoped>.messages{margin-top:8px}.message{padding:16px 0;border-bottom:1px solid #e6eaf0}.title{display:flex;justify-content:space-between;gap:8px;font-weight:600}.date{color:#8a94a6;font-size:12px;font-weight:400}.body{display:block;margin-top:8px;color:#667387;font-size:14px;line-height:1.5}.unread .title text:first-child{color:#1677ff}</style>
