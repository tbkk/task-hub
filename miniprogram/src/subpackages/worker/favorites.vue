<script setup lang="ts">
import {computed,ref} from 'vue'
import {onShow} from '@dcloudio/uni-app'
import BusinessPage from '@/components/BusinessPage.vue'
import StatePanel from '@/components/StatePanel.vue'
import BaseButton from '@/components/BaseButton.vue'
import {refreshAccess} from '@/features/workspace/navigation'
import {listWarehouses,listAvailableStops,listFavorites,setFavorite} from '@/features/catalog/api'
import {canSelectStop,type CatalogStop} from '@/features/catalog/model'
import {errorText} from '@/features/order/model'
const available=ref<CatalogStop[]>([]),favorites=ref<CatalogStop[]>([]),loading=ref(true),error=ref(''),busy=ref(''),notice=ref('')
const items=computed(()=>[...new Map([...available.value,...favorites.value].map(s=>[s.id,s])).values()])
const isFavorite=(id:string)=>favorites.value.some(s=>s.id===id)
async function load(){loading.value=true;error.value='';try{if(!await refreshAccess(false,'worker'))return;const warehouses=await listWarehouses();available.value=warehouses.length===1?await listAvailableStops(warehouses[0].id):[];favorites.value=await listFavorites();notice.value=warehouses.length===1?'':'未配置所属仓库，请联系管理员'}catch(e){error.value=errorText(e)}finally{loading.value=false}}
async function toggle(stop:CatalogStop){if(busy.value)return;busy.value=stop.id;error.value='';try{await setFavorite(stop.id,!isFavorite(stop.id));favorites.value=await listFavorites();uni.showToast({title:'收藏已更新',icon:'none'})}catch(e){error.value=errorText(e)}finally{busy.value=''}}
onShow(()=>void load())
</script>
<template><BusinessPage title="常用停靠点" subtitle="管理已有停靠点的收藏"><view class="worker-stack"><StatePanel v-if="loading" state="loading" title=""/><text v-if="notice" class="worker-note">{{notice}}</text><view v-if="error" class="worker-stack"><text class="worker-error">{{error}}</text><BaseButton variant="secondary" @click="load">重新加载</BaseButton></view><StatePanel v-if="!loading && !items.length && !error" title="暂无停靠点"/><view v-for="stop in items" :key="stop.id" class="worker-card worker-row"><view><text>{{stop.name}}</text><text v-if="!canSelectStop(stop) || !available.some(s=>s.id===stop.id)" class="worker-muted">暂不可用，可移除收藏</text></view><view><BaseButton variant="secondary" :loading="busy===stop.id" :disabled="!!busy || (!isFavorite(stop.id) && !canSelectStop(stop))" @click="toggle(stop)">{{isFavorite(stop.id)?'取消收藏':'收藏'}}</BaseButton></view></view><text class="worker-muted">只能收藏已配置的停靠点，不能新增任意地址</text></view></BusinessPage></template>
<style lang="scss">@import '@/styles/worker.scss';</style>
