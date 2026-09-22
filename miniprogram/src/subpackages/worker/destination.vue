<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import BusinessPage from '@/components/BusinessPage.vue'
import StatePanel from '@/components/StatePanel.vue'
import BaseButton from '@/components/BaseButton.vue'
import { refreshAccess } from '@/features/workspace/navigation'
import { listAvailableStops,listFavorites } from '@/features/catalog/api'
import { canSelectStop,type CatalogStop } from '@/features/catalog/model'
import { useSelectionResult } from '@/features/catalog/selection'
import { errorText } from '@/features/order/model'
const finish=useSelectionResult(),warehouseId=ref(''),items=ref<CatalogStop[]>([]),favorites=ref<string[]>([]),keyword=ref(''),selected=ref<CatalogStop|null>(null),loading=ref(true),error=ref('')
const filtered=computed(()=>items.value.filter(s=>s.name.includes(keyword.value.trim())))
async function load(){loading.value=true;error.value='';try{if(!await refreshAccess(false,'worker'))return;items.value=await listAvailableStops(warehouseId.value);try{favorites.value=(await listFavorites()).map(s=>s.id)}catch{favorites.value=[]}}catch(e){error.value=errorText(e)}finally{loading.value=false}}
onLoad(q=>{warehouseId.value=String(q?.warehouseId||'');void load()})
</script>
<template><BusinessPage title="选择目的地" subtitle="只能选择所属仓库可配送的停靠点"><view class="worker-stack"><input v-model="keyword" class="worker-select" placeholder="搜索停靠点" aria-label="搜索停靠点" /><StatePanel v-if="loading" state="loading" title="" /><StatePanel v-else-if="error" state="error" title="加载失败" :description="error" retry @retry="load" /><template v-else><text class="worker-label">可用停靠点</text><StatePanel v-if="!filtered.length" title="暂无可用停靠点" description="请联系管理员核对车辆与停靠点配置" /><button v-for="stop in filtered" :key="stop.id" class="worker-select" :class="{'worker-picked':selected?.id===stop.id}" :disabled="!canSelectStop(stop)" @click="selected=stop"><view class="worker-row"><text>{{stop.name}}</text><text class="worker-muted">{{favorites.includes(stop.id)?'常用 · ':''}}{{canSelectStop(stop)?'可配送':'暂不可用'}}</text></view></button><BaseButton :disabled="!selected" @click="selected && finish(selected)">确定目的地</BaseButton></template></view></BusinessPage></template>
<style lang="scss">@import '@/styles/worker.scss';</style>
