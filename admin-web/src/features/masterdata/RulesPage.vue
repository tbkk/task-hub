<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { ApiError, request } from "../../api/request";
import { getRules, saveRules, type Entity, type Rule } from "./api";
import type { Page } from "../people/api";
const warehouses = ref<{ id: string; name: string }[]>([]),
  warehouseId = ref(""),
  rule = ref<Rule>(),
  busy = ref(false),
  error = ref("");
const newRule = ref(false);
let generation = 0;
const fields: {
  key: keyof Pick<
    Rule,
    | "slotCapacity"
    | "bookingDays"
    | "descriptionMaxLength"
    | "sizeMaxLength"
    | "remarkMaxLength"
    | "telemetryMaxAgeSeconds"
    | "doorMaxAgeSeconds"
    | "pickupTimeoutMinutes"
  >;
  label: string;
}[] = [
  { key: "slotCapacity", label: "每半小时申请容量" },
  { key: "bookingDays", label: "可预约天数" },
  { key: "descriptionMaxLength", label: "物料描述最多字数" },
  { key: "sizeMaxLength", label: "尺寸描述最多字数" },
  { key: "remarkMaxLength", label: "备注最多字数" },
  { key: "telemetryMaxAgeSeconds", label: "车辆状态有效秒数" },
  { key: "doorMaxAgeSeconds", label: "格口门状态有效秒数" },
  { key: "pickupTimeoutMinutes", label: "取货超时分钟数" },
];
const fail = (e: unknown) =>
  (error.value = e instanceof Error ? e.message : "请求失败，请重试");
async function load() {
  const turn = ++generation;
  error.value = "";
  rule.value = undefined;
  newRule.value = false;
  if (!warehouseId.value) return;
  busy.value = true;
  try {
    const value = await getRules(warehouseId.value);
    if (turn === generation) rule.value = value;
  } catch (e) {
    if (turn === generation) {
      if (e instanceof ApiError && e.status === 404) {
        newRule.value = true;
        rule.value = {
          version: 0,
          businessHours: [],
          slotCapacity: 10,
          bookingDays: 7,
          descriptionMaxLength: 200,
          sizeMaxLength: 80,
          remarkMaxLength: 200,
          telemetryMaxAgeSeconds: 30,
          doorMaxAgeSeconds: 30,
          pickupTimeoutMinutes: 15,
          sharedCompartmentEnabled: false,
        };
      } else fail(e);
    }
  } finally {
    if (turn === generation) busy.value = false;
  }
}
async function save() {
  if (!rule.value || busy.value) return;
  try {
    await ElMessageBox.confirm(
      "新规则将用于后续预约校验，不保证已有预约准时送达。",
      "确认规则变更",
      { confirmButtonText: "确认保存", cancelButtonText: "返回检查" },
    );
  } catch {
    return;
  }
  busy.value = true;
  error.value = "";
  try {
    rule.value = await saveRules(warehouseId.value, rule.value);
    newRule.value = false;
    ElMessage.success("业务规则已保存");
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
onMounted(async () => {
  try {
    let page = 1;
    while (true) {
      const data = await request<Page<Entity>>(
        `/admin/rules/warehouses?page=${page++}&pageSize=100`,
      );
      warehouses.value.push(...data.items);
      if (warehouses.value.length >= data.total || !data.items.length) break;
    }
  } catch (e) {
    fail(e);
  }
});
</script>
<template>
  <section class="module-page">
    <header class="page-heading">
      <p class="page-kicker">RULES / 营业与业务规则</p>
      <h1>营业与业务规则</h1>
      <p>配置可预约时段、容量与文字限制，车辆控制继续由实时条件校验。</p>
    </header>
    <div class="content">
      <div class="toolbar">
        <el-select
          v-model="warehouseId"
          aria-label="规则仓库"
          placeholder="选择仓库"
          :disabled="busy"
          @change="load"
          ><el-option
            v-for="w in warehouses"
            :key="w.id"
            :label="w.name"
            :value="w.id" /></el-select
        ><el-button :disabled="!warehouseId || busy" @click="load"
          >重新读取</el-button
        >
      </div>
      <el-alert
        v-if="error"
        :title="error"
        type="error"
        :closable="false"
      /><el-alert
        v-if="newRule"
        title="该仓库尚未配置规则。下方数值为待确认的建议值，添加营业时段并保存后才生效。"
        type="warning"
        :closable="false"
      />
      <el-form
        v-if="rule"
        v-loading="busy"
        label-position="top"
        :disabled="busy"
        ><h2>营业时段</h2>
        <p class="hint">
          每行代表一个星期内的营业时段，起止需为整点或半点，同一天不能重叠。
        </p>
        <div
          v-for="(hour, index) in rule.businessHours"
          :key="index"
          class="hour"
        >
          <el-select v-model="hour.weekday" aria-label="星期"
            ><el-option
              v-for="(day, i) in ['一', '二', '三', '四', '五', '六', '日']"
              :key="i"
              :label="'星期' + day"
              :value="i + 1" /></el-select
          ><el-input
            v-model="hour.start"
            aria-label="开始时间"
            placeholder="08:00"
          /><span>至</span
          ><el-input
            v-model="hour.end"
            aria-label="结束时间"
            placeholder="17:00"
          /><el-button @click="rule.businessHours.splice(index, 1)"
            >移除</el-button
          >
        </div>
        <el-button
          @click="
            rule.businessHours.push({
              weekday: 1,
              start: '08:00',
              end: '17:00',
            })
          "
          >添加营业时段</el-button
        >
        <h2>业务限制</h2>
        <div class="grid">
          <el-form-item
            v-for="field in fields"
            :key="field.key"
            :label="field.label"
            ><el-input-number v-model="rule[field.key]" :min="1" :precision="0"
          /></el-form-item>
        </div>
        <p class="hint">当前采用独立格口，不启用共享格口。</p>
        <el-button type="primary" :loading="busy" @click="save"
          >保存规则</el-button
        ></el-form
      ><el-empty v-else-if="!busy && !error" description="请选择仓库查看规则" />
    </div>
  </section>
</template>
<style scoped>
.content {
  padding: 24px 32px;
  max-width: 1100px;
}
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
}
.toolbar .el-select {
  width: 280px;
}
.hour {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.hour .el-input {
  width: 110px;
}
.hour .el-select {
  width: 130px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 30px;
  margin-top: 20px;
}
h2 {
  font-size: 16px;
  margin: 24px 0 12px;
}
.hint {
  color: #667387;
  font-size: 13px;
  line-height: 1.7;
}
.el-alert {
  margin-bottom: 20px;
}
@media (max-width: 650px) {
  .content {
    padding: 16px;
  }
  .hour {
    flex-wrap: wrap;
  }
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
