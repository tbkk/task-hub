<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  compartments,
  detail,
  options,
  saveCompartment,
  syncCompartments,
  type Entity,
  type Compartment,
} from "./api";
const vehicles = ref<Entity[]>([]),
  vehicleId = ref(""),
  vehicle = ref<Entity>(),
  items = ref<Compartment[]>([]),
  busy = ref(false),
  error = ref(""),
  editing = ref<Compartment>();
let sequence = 0;
const fail = (e: unknown) =>
  (error.value = e instanceof Error ? e.message : "操作失败，请重试");
async function load() {
  const turn = ++sequence;
  items.value = [];
  vehicle.value = undefined;
  error.value = "";
  if (!vehicleId.value) return;
  busy.value = true;
  try {
    const [v, list] = await Promise.all([
      detail("vehicles", vehicleId.value),
      compartments(vehicleId.value),
    ]);
    if (turn === sequence) {
      vehicle.value = v;
      items.value = list;
    }
  } catch (e) {
    if (turn === sequence) fail(e);
  } finally {
    if (turn === sequence) busy.value = false;
  }
}
async function save() {
  if (!editing.value || busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    await saveCompartment(editing.value);
    editing.value = undefined;
    await load();
    ElMessage.success("格口资料已保存");
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
async function sync() {
  if (!vehicle.value || busy.value) return;
  try {
    await ElMessageBox.confirm(
      "从已配置的外部目录同步既有格口，硬件编号不可手工新增或修改。",
      "同步硬件定义",
      { confirmButtonText: "确认同步", cancelButtonText: "取消" },
    );
  } catch {
    return;
  }
  busy.value = true;
  error.value = "";
  try {
    await syncCompartments(vehicle.value);
    await load();
    ElMessage.success("硬件目录已同步");
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
onMounted(async () => {
  try {
    vehicles.value = await options("vehicles");
  } catch (e) {
    fail(e);
  }
});
</script>
<template>
  <section class="module-page">
    <header class="page-heading">
      <p class="page-kicker">HARDWARE / 硬件格口</p>
      <h1>硬件格口</h1>
      <p>同步既有硬件定义，仅维护本地标签和启停。</p>
    </header>
    <div class="content">
      <div class="toolbar">
        <el-select
          v-model="vehicleId"
          aria-label="选择车辆"
          placeholder="选择车辆"
          :disabled="busy"
          @change="load"
          ><el-option
            v-for="v in vehicles"
            :key="v.id"
            :label="v.name"
            :value="v.id" /></el-select
        ><el-button :disabled="!vehicle || busy" @click="load">刷新</el-button
        ><el-button type="primary" :disabled="!vehicle || busy" @click="sync"
          >同步硬件定义</el-button
        >
      </div>
      <el-alert
        v-if="error"
        :title="error"
        type="error"
        :closable="false"
      /><el-table
        v-loading="busy"
        :data="items"
        empty-text="请选择车辆；已选择时可同步现有格口"
        ><el-table-column
          prop="hardwareNo"
          label="硬件编号（只读）"
        /><el-table-column prop="label" label="本地标签" /><el-table-column
          label="状态"
          ><template #default="{ row }">{{
            row.enabled ? "启用" : "停用"
          }}</template></el-table-column
        ><el-table-column label="操作"
          ><template #default="{ row }"
            ><el-button
              link
              type="primary"
              :disabled="busy"
              @click="editing = { ...row }"
              >编辑</el-button
            ></template
          ></el-table-column
        ></el-table
      >
    </div>
    <el-dialog
      :model-value="!!editing"
      title="编辑格口"
      width="480px"
      class="hardware-dialog"
      :close-on-click-modal="false"
      :show-close="!busy"
      @update:model-value="
        (value: boolean) => {
          if (!value && !busy) editing = undefined;
        }
      "
      ><el-alert
        v-if="error"
        :title="error"
        type="error"
        :closable="false"
      /><el-form v-if="editing" label-position="top" :disabled="busy"
        ><el-form-item label="硬件编号"
          ><el-input :model-value="editing.hardwareNo" disabled /></el-form-item
        ><el-form-item label="本地标签"
          ><el-input v-model="editing.label" maxlength="80" /></el-form-item
        ><el-form-item label="可用状态"
          ><el-switch
            v-model="editing.enabled"
            active-text="启用"
            inactive-text="停用" /></el-form-item></el-form
      ><template #footer
        ><el-button :disabled="busy" @click="editing = undefined"
          >取消</el-button
        ><el-button type="primary" :loading="busy" @click="save"
          >保存变更</el-button
        ></template
      ></el-dialog
    >
  </section>
</template>
<style scoped>
.content {
  padding: 24px 32px;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
}
.el-select {
  width: 260px;
}
.el-alert {
  margin-bottom: 16px;
}
:deep(.hardware-dialog) {
  max-width: 95vw;
}
</style>
