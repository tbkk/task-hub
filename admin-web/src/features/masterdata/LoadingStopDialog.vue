<script setup lang="ts">
import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import { getLoadingStop, saveLoadingStop, options, type Entity } from './api';
const opened = ref(false), busy = ref(false), error = ref(''), ready = ref(false);
const warehouse = ref<Entity>(), candidates = ref<Entity[]>([]), stopId = ref(''), version = ref(0);
let generation = 0;
const message = (e: unknown) => e instanceof Error ? e.message : '操作失败，请重试';
async function open(item: Entity) {
  if (busy.value) return;
  warehouse.value = item; opened.value = true; ready.value = false; error.value = '';
  stopId.value = ''; candidates.value = []; version.value = 0;
  const current = ++generation;
  busy.value = true;
  try {
    const [binding, stops] = await Promise.all([getLoadingStop(item.id), options('stops')]);
    if (current !== generation) return;
    candidates.value = stops.filter(s => s.enabled && s.warehouseIds?.includes(item.id));
    stopId.value = binding?.stopId ?? ''; version.value = binding?.version ?? 0; ready.value = true;
  } catch (e) { if(current === generation) error.value = message(e); }
  finally { if(current === generation) busy.value = false; }
}
async function save() {
  if (busy.value || !ready.value || !warehouse.value) return;
  if (!stopId.value) { error.value = '请选择装货点'; return; }
  busy.value = true; error.value = '';
  try {
    const saved = await saveLoadingStop(warehouse.value.id, stopId.value, version.value);
    version.value = saved.version; opened.value = false; ElMessage.success('装货点已保存');
  } catch (e) { error.value = message(e); }
  finally { busy.value = false; }
}
defineExpose({ open });
</script>
<template>
  <el-dialog v-model="opened" title="配置仓库装货点" width="min(520px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy">
    <p class="warehouse-name">{{ warehouse?.name }}</p>
    <p class="help">选择车辆装货时所在的停靠点。派发时将核对车辆位置；有活动批次时不能更换既有装货点。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form label-position="top" :disabled="busy || !ready">
      <el-form-item label="装货停靠点">
        <el-select v-model="stopId" placeholder="选择已关联本仓的启用点位" :loading="busy" style="width:100%" filterable>
          <el-option v-for="stop in candidates" :key="stop.id" :label="stop.name" :value="stop.id" />
        </el-select>
      </el-form-item>
    </el-form>
    <p v-if="ready && !candidates.length" class="help">暂无可选点位，请先在停靠点资料中关联本仓库。</p>
    <template #footer>
      <el-button :disabled="busy" @click="opened=false">关闭</el-button>
      <el-button v-if="warehouse" :disabled="busy" @click="open(warehouse)">重新读取</el-button>
      <el-button type="primary" :loading="busy" :disabled="!ready || !stopId" @click="save">保存装货点</el-button>
    </template>
  </el-dialog>
</template>
<style scoped>
.warehouse-name { color: var(--ink, #1c2c2b); font-weight: 600; margin-top: 0; }
.help { color: #667773; line-height: 1.65; margin-bottom: 20px; }
.el-alert { margin-bottom: 18px; }
</style>
