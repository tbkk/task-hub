<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { session, type Identity, type Capability } from "../auth/session";
import {
  employeeChanges,
  roleLabels,
  capabilityLabels,
  employeeDraft,
  getEmployee,
  listEmployees,
  resetCredentials,
  saveEmployee,
  type Employee,
  type EmployeeDraft,
  employeeWarehouseOptions,
} from "./api";
const items = ref<Employee[]>([]),
  total = ref(0),
  page = ref(1),
  keyword = ref(""),
  enabled = ref<boolean | undefined>();
const roleFilter = ref<Identity["grants"][number]["role"]>();
const warehouseFilter = ref<string>();
const verifiedFilter = ref<boolean>();
const wechatFilter = ref<boolean>();
const loading = ref(false),
  busy = ref(false),
  error = ref(""),
  editError = ref(""),
  drawer = ref(false),
  readonly = ref(false);
const selected = ref<Employee>(),
  draft = ref<EmployeeDraft>(employeeDraft());
const warehouses = ref<{ id: string; name: string; enabled: boolean }[]>([]),
  warehouseError = ref("");
const credentialDialog = ref(false),
  username = ref(""),
  temporaryPassword = ref("");
const roles = roleLabels;
const capabilities = capabilityLabels;
const delegable = computed(() => session.value?.user.platformGrants || []);
let revision = 0;
let editRevision = 0;
async function load() {
  const current = ++revision;
  loading.value = true;
  error.value = "";
  try {
    const result = await listEmployees({
      keyword: keyword.value,
      enabled: enabled.value,
      role: roleFilter.value || undefined,
      warehouseId: warehouseFilter.value || undefined,
      phoneVerified: verifiedFilter.value,
      wechatBound: wechatFilter.value,
      page: page.value,
      pageSize: 20,
    });
    if (current !== revision) return;
    items.value = result.items;
    total.value = result.total;
  } catch (cause) {
    if (current === revision) error.value = message(cause);
  } finally {
    if (current === revision) loading.value = false;
  }
}
function message(cause: unknown) {
  return cause instanceof Error ? cause.message : "操作失败，请重试";
}
function query() {
  page.value = 1;
  void load();
}
function reset() {
  keyword.value = "";
  enabled.value = undefined;
  roleFilter.value = undefined;
  warehouseFilter.value = undefined;
  verifiedFilter.value = undefined;
  wechatFilter.value = undefined;
  query();
}
async function open(employee?: Employee, view = false) {
  const currentEdit = ++editRevision;
  editError.value = "";
  readonly.value = view;
  selected.value = employee;
  draft.value = employeeDraft(employee);
  drawer.value = true;
  if (employee) {
    busy.value = true;
    try {
      const current = await getEmployee(employee.id);
      if (currentEdit !== editRevision) return;
      selected.value = current;
      draft.value = employeeDraft(current);
    } catch (cause) {
      editError.value = message(cause);
      readonly.value = true;
    } finally {
      busy.value = false;
    }
  }
  try {
    const data = await employeeWarehouseOptions();
    if (currentEdit !== editRevision) return;
    warehouses.value = data;
    warehouseError.value = "";
  } catch {
    warehouseError.value = "仓库列表暂不可用，已有授权保留，请稍后重试。";
  }
}
function roleToggle(
  role: Identity["grants"][number]["role"],
  checked: unknown,
) {
  draft.value.grants = draft.value.grants.filter((g) => g.role !== role);
  if (checked)
    draft.value.grants.push({
      role,
      scope: role === "worker" ? "SELF" : "WAREHOUSES",
      warehouseIds: [],
    });
}
function capabilityToggle(capability: Capability, checked: unknown) {
  draft.value.platformGrants = draft.value.platformGrants.filter(
    (g) => g.capability !== capability,
  );
  const available = delegable.value.find((g) => g.capability === capability);
  if (checked && available)
    draft.value.platformGrants.push({
      ...available,
      warehouseIds: [...available.warehouseIds],
    });
}
async function save() {
  if (busy.value) return;
  editError.value = "";
  if (!draft.value.name.trim() || !/^1[3-9]\d{9}$/.test(draft.value.phone)) {
    editError.value = "请填写姓名和正确的 11 位手机号";
    return;
  }
  if (
    draft.value.grants.some(
      (g) => g.scope === "WAREHOUSES" && !g.warehouseIds.length,
    ) ||
    draft.value.platformGrants.some(
      (g) => g.scope === "WAREHOUSES" && !g.warehouseIds.length,
    )
  ) {
    editError.value = "指定仓库范围至少选择一个仓库";
    return;
  }
  const before = selected.value;
  const summary = [
    `姓名：${before?.name || "新员工"} → ${draft.value.name}`,
    `准入手机号：${mask(before?.phone)} → ${mask(draft.value.phone)}`,
    `角色：${before?.grants.map((g) => roles[g.role]).join("、") || "无"} → ${draft.value.grants.map((g) => roles[g.role]).join("、") || "无"}`,
    `账号状态：${draft.value.enabled ? "启用" : "停用（立即失去访问权限，保留业务历史）"}`,
    ...employeeChanges(before, draft.value),
    `录入手机号不代表完成验证。`,
  ].join("\n");
  try {
    await ElMessageBox.confirm(summary, "确认保存授权变更", {
      confirmButtonText: "确认保存",
      cancelButtonText: "返回检查",
    });
  } catch {
    return;
  }
  busy.value = true;
  try {
    await saveEmployee(draft.value, before?.id);
    drawer.value = false;
    ElMessage.success("人员资料已保存");
    await load();
  } catch (cause) {
    editError.value = message(cause);
  } finally {
    busy.value = false;
  }
}
function mask(phone?: string | null) {
  return phone?.replace(/^(\d{3})\d{4}(\d{4})$/, "$1 **** $2") || "未验证";
}
async function credentials() {
  if (!selected.value || busy.value) return;
  if (
    !/^[a-zA-Z0-9_.@-]{3,80}$/.test(username.value) ||
    temporaryPassword.value.length < 12
  ) {
    editError.value = "内部账号至少 3 位；临时密码至少 12 个字符";
    return;
  }
  busy.value = true;
  editError.value = "";
  try {
    await resetCredentials(
      selected.value.id,
      username.value,
      temporaryPassword.value,
    );
    temporaryPassword.value = "";
    credentialDialog.value = false;
    ElMessage.success("内部凭据已更新，首次登录须修改密码");
  } catch (cause) {
    editError.value = message(cause);
  } finally {
    busy.value = false;
  }
}
onMounted(async () => {
  void load();
  try {
    warehouses.value = await employeeWarehouseOptions();
  } catch {
    warehouseError.value = "仓库列表暂不可用，请稍后重试。";
  }
});
</script>
<template>
  <section class="module-page people-page">
    <header class="page-heading">
      <p class="page-kicker">PEOPLE / 人员与权限</p>
      <div class="heading-row">
        <div>
          <h1>人员与权限</h1>
          <p>维护内部员工准入、工作角色和管理权限。</p>
        </div>
        <el-button type="primary" @click="open()">新增准入员工</el-button>
      </div>
    </header>
    <div class="people-content">
      <form class="filters" @submit.prevent="query">
        <el-input
          v-model="keyword"
          aria-label="姓名或手机号"
          placeholder="搜索姓名或手机号"
          clearable
        /><el-select
          v-model="enabled"
          aria-label="账号状态"
          placeholder="全部账号状态"
          clearable
          ><el-option label="启用" :value="true" /><el-option
            label="停用"
            :value="false" /></el-select
        ><el-select
          v-model="roleFilter"
          aria-label="业务角色筛选"
          placeholder="全部角色"
          clearable
          ><el-option
            v-for="(label, role) in roles"
            :key="role"
            :label="label"
            :value="role"
        /></el-select>
        <el-select
          v-model="warehouseFilter"
          aria-label="所属仓库筛选"
          placeholder="全部仓库"
          clearable
          ><el-option
            v-for="w in warehouses"
            :key="w.id"
            :label="w.name"
            :value="w.id"
        /></el-select>
        <el-select
          v-model="verifiedFilter"
          aria-label="手机验证筛选"
          placeholder="手机验证状态"
          clearable
          ><el-option label="已验证" :value="true" /><el-option
            label="未验证"
            :value="false"
        /></el-select>
        <el-select
          v-model="wechatFilter"
          aria-label="微信绑定筛选"
          placeholder="微信绑定状态"
          clearable
          ><el-option label="已绑定" :value="true" /><el-option
            label="未绑定"
            :value="false"
        /></el-select>
        <el-button native-type="submit" type="primary">查询</el-button
        ><el-button @click="reset">重置</el-button>
      </form>
      <el-alert
        v-if="error"
        :title="error"
        type="error"
        :closable="false"
        show-icon
      /><el-table
        v-loading="loading"
        :data="items"
        empty-text="暂无符合条件的员工"
        row-key="id"
        ><el-table-column
          prop="name"
          label="姓名"
          min-width="120"
        /><el-table-column label="手机号" min-width="150"
          ><template #default="{ row }">{{
            mask(row.phone)
          }}</template></el-table-column
        ><el-table-column label="所属仓库" min-width="140"
          ><template #default="{ row }">{{
            warehouses.find((w) => w.id === row.homeWarehouseId)?.name ||
            row.homeWarehouseId ||
            "未设置"
          }}</template></el-table-column
        >
        <el-table-column label="全部角色" min-width="220"
          ><template #default="{ row }"
            ><el-tag
              v-for="grant in row.grants"
              :key="grant.role"
              class="role-tag"
              >{{ roles[grant.role as keyof typeof roles] }}</el-tag
            ><span v-if="!row.grants.length">暂无业务角色</span></template
          ></el-table-column
        ><el-table-column label="账号状态" width="100"
          ><template #default="{ row }"
            ><el-tag :type="row.enabled ? 'success' : 'info'">{{
              row.enabled ? "启用" : "停用"
            }}</el-tag></template
          ></el-table-column
        ><el-table-column label="手机验证" width="110"
          ><template #default="{ row }">{{
            row.verifiedPhone ? "已验证" : "未验证"
          }}</template></el-table-column
        ><el-table-column label="微信绑定" width="110"
          ><template #default="{ row }">{{
            row.wechatBound ? "已绑定" : "未绑定"
          }}</template></el-table-column
        >
        <el-table-column prop="updatedAt" label="更新时间" min-width="180" />
        <el-table-column label="操作" width="140" fixed="right"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="open(row, true)"
              >查看</el-button
            ><el-button link type="primary" @click="open(row)"
              >编辑</el-button
            ></template
          ></el-table-column
        ></el-table
      >
      <el-pagination
        v-model:current-page="page"
        :total="total"
        :page-size="20"
        layout="total, prev, pager, next"
        @current-change="load"
      />
    </div>
    <el-drawer
      v-model="drawer"
      :title="readonly ? '员工详情' : selected ? '编辑员工' : '新增准入员工'"
      size="680px"
      class="people-drawer"
      :close-on-click-modal="false"
      :before-close="
        (done: () => void) => {
          if (!busy) done();
        }
      "
    >
      <el-alert
        v-if="editError"
        :title="editError"
        type="error"
        :closable="false"
      /><el-alert
        v-if="warehouseError"
        :title="warehouseError"
        type="warning"
        :closable="false"
      />
      <el-form label-position="top" :disabled="busy || readonly"
        ><el-form-item label="姓名"
          ><el-input v-model="draft.name" maxlength="80" /></el-form-item
        ><el-form-item label="准入手机号"
          ><el-input v-model="draft.phone" maxlength="11"
        /></el-form-item>
        <p class="hint">
          已验证手机号：{{
            mask(selected?.verifiedPhone)
          }}。管理员录入或修改号码不会完成手机号验证。
        </p>
        <el-form-item label="所属仓库"
          ><el-select
            v-model="draft.homeWarehouseId"
            aria-label="所属仓库"
            clearable
            placeholder="未配置时工人不能提交申请"
            @clear="draft.homeWarehouseId = null"
            ><el-option
              v-for="w in warehouses"
              :key="w.id"
              :value="w.id"
              :label="w.name + (w.enabled ? '' : '（停用）')"
              :disabled="!w.enabled" /></el-select
        ></el-form-item>
        <p class="hint">
          微信绑定：{{ selected?.wechatBound ? "已绑定" : "未绑定" }}（只读）
        </p>
        <el-form-item label="账号状态"
          ><el-switch
            v-model="draft.enabled"
            active-text="启用"
            inactive-text="停用"
        /></el-form-item>
        <h3>业务角色 · 可多选</h3>
        <el-checkbox
          v-for="(label, role) in roles"
          :key="role"
          :model-value="draft.grants.some((g) => g.role === role)"
          @change="(value: unknown) => roleToggle(role, value)"
          >{{ label }}</el-checkbox
        >
        <div v-for="grant in draft.grants" :key="grant.role" class="scope-card">
          <strong>{{ roles[grant.role] }}</strong>
          <p v-if="grant.role === 'worker'" class="hint">
            只访问本人申请或接收的订单。
          </p>
          <template v-else
            ><el-select v-model="grant.scope" @change="grant.warehouseIds = []"
              ><el-option label="指定仓库" value="WAREHOUSES" /><el-option
                label="全厂"
                value="ALL" /></el-select
            ><el-select
              v-if="grant.scope === 'WAREHOUSES'"
              v-model="grant.warehouseIds"
              multiple
              placeholder="选择授权仓库"
              ><el-option
                v-for="w in warehouses"
                :key="w.id"
                :label="w.name"
                :value="w.id" /></el-select
          ></template>
        </div>
        <h3>管理平台能力</h3>
        <p class="hint">平台能力与日常工作角色分别授权。</p>
        <el-checkbox
          v-for="available in delegable"
          :key="available.capability"
          :model-value="
            draft.platformGrants.some(
              (g) => g.capability === available.capability,
            )
          "
          @change="
            (value: unknown) => capabilityToggle(available.capability, value)
          "
          >{{ capabilities[available.capability] }}</el-checkbox
        >
        <div
          v-for="grant in draft.platformGrants"
          :key="grant.capability"
          class="scope-card"
        >
          <strong>{{ capabilities[grant.capability] }}</strong
          ><el-select v-model="grant.scope" @change="grant.warehouseIds = []"
            ><el-option label="指定仓库" value="WAREHOUSES" /><el-option
              v-if="
                delegable.some(
                  (g) => g.capability === grant.capability && g.scope === 'ALL',
                )
              "
              label="全厂"
              value="ALL" /></el-select
          ><el-select
            v-if="grant.scope === 'WAREHOUSES'"
            v-model="grant.warehouseIds"
            multiple
            placeholder="选择授权仓库"
            ><el-option
              v-for="w in warehouses.filter((w) =>
                delegable.some(
                  (g) =>
                    g.capability === grant.capability &&
                    (g.scope === 'ALL' || g.warehouseIds.includes(w.id)),
                ),
              )"
              :key="w.id"
              :label="w.name"
              :value="w.id"
          /></el-select>
        </div> </el-form
      ><template #footer
        ><el-button :disabled="busy" @click="drawer = false">关闭</el-button
        ><el-button
          v-if="selected && !readonly"
          :disabled="busy"
          @click="
            username = '';
            temporaryPassword = '';
            editError = '';
            credentialDialog = true;
          "
          >设置内部凭据</el-button
        ><el-button
          v-if="editError && selected"
          :disabled="busy"
          @click="open(selected, readonly)"
          >重新读取</el-button
        ><el-button
          v-if="!readonly"
          type="primary"
          :loading="busy"
          @click="save"
          >保存变更</el-button
        ></template
      >
    </el-drawer>
    <el-dialog
      v-model="credentialDialog"
      title="设置内部登录凭据"
      width="min(480px, 95%)"
      :close-on-click-modal="false"
      :show-close="!busy"
      @closed="temporaryPassword = ''"
    >
      <p class="hint">
        更新后撤销该员工的管理平台会话，下次登录须修改临时密码。
      </p>
      <el-alert
        v-if="editError"
        :title="editError"
        type="error"
        :closable="false"
      /><el-form label-position="top"
        ><el-form-item label="内部账号"
          ><el-input
            v-model="username"
            :disabled="busy"
            autocomplete="off" /></el-form-item
        ><el-form-item label="临时密码（至少 12 个字符）"
          ><el-input
            v-model="temporaryPassword"
            :disabled="busy"
            type="password"
            autocomplete="new-password" /></el-form-item></el-form
      ><template #footer
        ><el-button :disabled="busy" @click="credentialDialog = false"
          >取消</el-button
        ><el-button type="primary" :loading="busy" @click="credentials"
          >确认设置</el-button
        ></template
      >
    </el-dialog>
  </section>
</template>
<style scoped>
:deep(.people-drawer) {
  max-width: 100vw;
}
.heading-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
}
.people-content {
  padding: 24px 32px;
}
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 24px;
}
.filters .el-input {
  max-width: 320px;
}
.filters .el-select {
  width: 160px;
}
.el-pagination {
  margin-top: 24px;
  justify-content: flex-end;
}
.role-tag {
  margin: 3px;
}
.hint {
  font-size: 13px;
  line-height: 1.7;
  color: #6b7685;
}
.scope-card {
  display: grid;
  gap: 12px;
  padding: 16px;
  margin: 12px 0;
  background: #f6f8fb;
  border: 1px solid #e6eaf0;
  border-radius: 8px;
}
h3 {
  font-size: 15px;
  margin-top: 24px;
}
.el-alert {
  margin-bottom: 16px;
}
@media (max-width: 720px) {
  .people-content {
    padding: 16px;
  }
  .filters {
    flex-wrap: wrap;
  }
  :deep(.people-drawer) {
    max-width: 100vw;
  }
  .heading-row {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
