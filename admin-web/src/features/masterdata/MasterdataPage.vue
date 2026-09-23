<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import LoadingStopDialog from './LoadingStopDialog.vue';
import {
  detail,
  external,
  list,
  names,
  options,
  save,
  type Entity,
  type Resource,
} from "./api";
const route = useRoute(),
  resource = computed(() => route.path.split("/").slice(-1)[0] as Resource);
const loadingStopDialog = ref<InstanceType<typeof LoadingStopDialog>>();
const items = ref<Entity[]>([]),
  page = ref(1),
  total = ref(0),
  loading = ref(false),
  busy = ref(false),
  error = ref(""),
  editError = ref(""),
  opened = ref(false),
  selected = ref<string>();
const draft = ref<Record<string, unknown>>({}),
  warehouses = ref<Entity[]>([]),
  stops = ref<Entity[]>([]),
  candidates = ref<{ id: string; name: string }[]>([]);
let generation = 0;
const message = (e: unknown) =>
  e instanceof Error ? e.message : "操作失败，请重试";
async function load() {
  const current = ++generation;
  loading.value = true;
  error.value = "";
  try {
    const data = await list(resource.value, page.value);
    if (current !== generation) return;
    items.value = data.items;
    total.value = data.total;
  } catch (e) {
    if (current === generation) error.value = message(e);
  } finally {
    if (current === generation) loading.value = false;
  }
}
watch(
  resource,
  () => {
    opened.value = false;
    page.value = 1;
    void load();
  },
  { immediate: true },
);
async function edit(item?: Entity) {
  if (busy.value) return;
  busy.value = true;
  opened.value = true;
  editError.value = "";
  selected.value = item?.id;
  draft.value = {
    name: "",
    code: "",
    enabled: true,
    externalStopId: "",
    externalVehicleName: "",
    warehouseId: "",
    warehouseIds: [],
    boundStopIds: [],
  };
  try {
    if (item)
      draft.value = JSON.parse(
        JSON.stringify(await detail(resource.value, item.id)),
      );
    warehouses.value =
      resource.value === "warehouses" ? [] : await options("warehouses");
    stops.value = resource.value === "vehicles" ? await options("stops") : [];
    candidates.value =
      resource.value === "warehouses" || item
        ? []
        : await external(resource.value);
  } catch (e) {
    editError.value = message(e);
  } finally {
    busy.value = false;
  }
}
async function submit() {
  if (busy.value) return;
  editError.value = "";
  if (!String(draft.value.name || "").trim()) {
    editError.value = "请填写名称";
    return;
  }
  try {
    await ElMessageBox.confirm(
      draft.value.enabled
        ? "确认保存基础资料变更？"
        : "停用后不可用于新业务，已有业务和历史记录将保留。",
      "确认变更",
      { confirmButtonText: "确认保存", cancelButtonText: "返回检查" },
    );
  } catch {
    return;
  }
  busy.value = true;
  try {
    await save(resource.value, draft.value, selected.value);
    opened.value = false;
    ElMessage.success("基础资料已保存");
    await load();
  } catch (e) {
    editError.value = message(e);
  } finally {
    busy.value = false;
  }
}
const reachableStops = computed(() =>
  stops.value.filter((s) =>
    s.warehouseIds?.includes(String(draft.value.warehouseId)),
  ),
);
</script>
<template>
  <section class="module-page">
    <header class="page-heading">
      <p class="page-kicker">MASTER DATA / 基础资料</p>
      <div class="heading">
        <div>
          <h1>{{ names[resource] }}资料</h1>
          <p>维护基础信息与既有资源绑定，保留历史记录。</p>
        </div>
        <el-button type="primary" :disabled="busy" @click="edit()"
          >新增{{ names[resource] }}</el-button
        >
      </div>
    </header>
    <div class="content">
      <el-alert
        v-if="error"
        type="error"
        :title="error"
        :closable="false"
      /><el-button :loading="loading" @click="load">刷新列表</el-button>
      <el-table v-loading="loading" :data="items" empty-text="暂无资料"
        ><el-table-column
          prop="name"
          label="名称"
          min-width="180"
        /><el-table-column
          v-if="resource === 'warehouses'"
          prop="code"
          label="仓库编码"
        /><el-table-column
          v-if="resource === 'stops'"
          prop="externalStopId"
          label="外部点位标识"
        /><el-table-column
          v-if="resource === 'vehicles'"
          prop="externalVehicleName"
          label="外部车辆标识"
        /><el-table-column label="状态" width="100"
          ><template #default="{ row }"
            ><el-tag :type="row.enabled ? 'success' : 'info'">{{
              row.enabled ? "启用" : "停用"
            }}</el-tag></template
          ></el-table-column
        ><el-table-column
          prop="updatedAt"
          label="更新时间"
          min-width="180"
        /><el-table-column label="操作" :width="resource === 'warehouses' ? 170 : 90"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="edit(row)"
              >编辑</el-button
            ><el-button v-if="resource === 'warehouses'" link type="primary" :disabled="!row.enabled" @click="loadingStopDialog?.open(row)">装货点</el-button></template
          ></el-table-column
        ></el-table
      ><el-pagination
        v-model:current-page="page"
        :page-size="20"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="load"
      />
    </div>
    <el-drawer
      v-model="opened"
      class="data-drawer"
      size="600px"
      :title="(selected ? '编辑' : '新增') + names[resource]"
      :close-on-click-modal="false"
      :show-close="!busy"
      :before-close="
        (done: () => void) => {
          if (!busy) done();
        }
      "
    >
      <el-alert
        v-if="editError"
        type="error"
        :title="editError"
        :closable="false"
      /><el-form label-position="top" :disabled="busy"
        ><el-form-item label="名称"
          ><el-input v-model="draft.name" maxlength="80" /></el-form-item
        ><el-form-item v-if="resource === 'warehouses'" label="仓库编码"
          ><el-input v-model="draft.code" maxlength="80"
        /></el-form-item>
        <template v-else
          ><el-alert
            v-if="!selected"
            type="info"
            title="目录来自当前配置的外部提供者；本地开发使用模拟目录。"
            :closable="false" /><el-form-item
            :label="resource === 'stops' ? '外部点位' : '外部车辆'"
            ><el-input
              v-if="selected"
              :model-value="
                String(
                  resource === 'stops'
                    ? draft.externalStopId
                    : draft.externalVehicleName,
                )
              "
              disabled /><el-select
              v-else-if="resource === 'stops'"
              v-model="draft.externalStopId"
              placeholder="选择已有点位"
              ><el-option
                v-for="c in candidates"
                :key="c.id"
                :label="c.name"
                :value="c.id" /></el-select
            ><el-select
              v-else
              v-model="draft.externalVehicleName"
              placeholder="选择已有车辆"
              ><el-option
                v-for="c in candidates"
                :key="c.id"
                :label="c.name"
                :value="c.name" /></el-select
          ></el-form-item>
          <el-form-item label="关联仓库"
            ><el-select
              v-if="resource === 'stops'"
              v-model="draft.warehouseIds"
              multiple
              ><el-option
                v-for="w in warehouses"
                :key="w.id"
                :label="w.name"
                :value="w.id"
                :disabled="!w.enabled" /></el-select
            ><el-select
              v-else
              v-model="draft.warehouseId"
              @change="draft.boundStopIds = []"
              ><el-option
                v-for="w in warehouses"
                :key="w.id"
                :label="w.name"
                :value="w.id"
                :disabled="!w.enabled" /></el-select></el-form-item
          ><el-form-item v-if="resource === 'vehicles'" label="可达停靠点"
            ><el-select v-model="draft.boundStopIds" multiple
              ><el-option
                v-for="s in reachableStops"
                :key="s.id"
                :label="s.name"
                :value="s.id"
                :disabled="!s.enabled" /></el-select></el-form-item
        ></template>
        <el-form-item label="可用状态"
          ><el-switch
            v-model="draft.enabled"
            active-text="启用"
            inactive-text="停用" /></el-form-item></el-form
      ><template #footer
        ><el-button :disabled="busy" @click="opened = false">取消</el-button
        ><el-button
          v-if="selected && editError"
          :disabled="busy"
          @click="edit(items.find((i) => i.id === selected))"
          >重新读取</el-button
        ><el-button type="primary" :loading="busy" @click="submit"
          >保存资料</el-button
        ></template
      ></el-drawer
    >
  </section>
  <LoadingStopDialog ref="loadingStopDialog" />
</template>
<style scoped>
.content {
  padding: 24px 32px;
}
.heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
}
.el-table {
  margin: 18px 0;
}
.el-pagination {
  justify-content: flex-end;
}
.el-alert {
  margin-bottom: 16px;
}
:deep(.data-drawer) {
  max-width: 100vw;
}
.el-select {
  width: 100%;
}
@media (max-width: 720px) {
  .content {
    padding: 16px;
  }
  .heading {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
