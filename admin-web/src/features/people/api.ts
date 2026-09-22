import { request } from "../../api/request";
import type { Identity, PlatformGrant } from "../auth/session";
export interface Page<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
}
export interface Employee {
  id: string;
  name: string;
  phone: string;
  enabled: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
  verifiedPhone: string | null;
  homeWarehouseId: string | null;
  wechatBound: boolean;
  grants: Identity["grants"];
  platformGrants: PlatformGrant[];
}
export interface EmployeeDraft {
  homeWarehouseId: string | null;
  name: string;
  phone: string;
  enabled: boolean;
  expectedVersion?: number;
  grants: Identity["grants"];
  platformGrants: PlatformGrant[];
}
export interface EmployeeQuery {
  role?: Identity["grants"][number]["role"];
  warehouseId?: string;
  phoneVerified?: boolean;
  wechatBound?: boolean;
  keyword?: string;
  enabled?: boolean;
  page: number;
  pageSize: number;
}
export function employeeDraft(employee?: Employee): EmployeeDraft {
  if (!employee)
    return {
      homeWarehouseId: null,
      name: "",
      phone: "",
      enabled: true,
      grants: [],
      platformGrants: [],
    };
  return {
    homeWarehouseId: employee.homeWarehouseId,
    name: employee.name,
    phone: employee.phone,
    enabled: employee.enabled,
    expectedVersion: employee.version,
    grants: employee.grants.map((grant) => ({
      ...grant,
      warehouseIds: [...grant.warehouseIds],
    })),
    platformGrants: employee.platformGrants.map((grant) => ({
      ...grant,
      warehouseIds: [...grant.warehouseIds],
    })),
  };
}
export function listEmployees(query: EmployeeQuery): Promise<Page<Employee>> {
  const params = new URLSearchParams({
    page: String(query.page),
    pageSize: String(query.pageSize),
  });
  if (query.keyword?.trim()) params.set("keyword", query.keyword.trim());
  if (query.enabled !== undefined) params.set("enabled", String(query.enabled));
  for (const key of [
    "role",
    "warehouseId",
    "phoneVerified",
    "wechatBound",
  ] as const) {
    const value = query[key];
    if (value !== undefined && value !== "") params.set(key, String(value));
  }
  return request(`/admin/employees?${params}`);
}
export function getEmployee(id: string): Promise<Employee> {
  return request(`/admin/employees/${encodeURIComponent(id)}`);
}
export function saveEmployee(
  draft: EmployeeDraft,
  id?: string,
): Promise<Employee> {
  return request(`/admin/employees${id ? `/${encodeURIComponent(id)}` : ""}`, {
    method: id ? "PUT" : "POST",
    body: JSON.stringify(draft),
  });
}
export function resetCredentials(
  id: string,
  username: string,
  temporaryPassword: string,
): Promise<null> {
  return request(`/admin/employees/${encodeURIComponent(id)}/credentials`, {
    method: "POST",
    body: JSON.stringify({ username, temporaryPassword }),
  });
}

export const roleLabels: Record<Identity["grants"][number]["role"], string> = {
  worker: "工人",
  warehouse: "仓库人员",
  dispatch: "调度人员",
  overview: "业务概览",
};
export const capabilityLabels: Record<PlatformGrant["capability"], string> = {
  EMPLOYEE_MANAGE: "人员与权限",
  MASTERDATA_MANAGE: "基础资料",
  RULE_MANAGE: "业务规则",
  INTEGRATION_MANAGE: "服务接入",
  REPORT_VIEW: "报表查看",
  REPORT_EXPORT: "报表导出",
  AUDIT_VIEW: "系统审计",
};
export function employeeChanges(
  before: Employee | undefined,
  after: EmployeeDraft,
): string[] {
  function scope(grant?: { scope: string; warehouseIds: string[] }) {
    if (!grant) return "无授权";
    return grant.scope === "SELF"
      ? "本人订单"
      : grant.scope === "ALL"
        ? "全厂"
        : `指定仓库 ${[...grant.warehouseIds].sort().join("、")}`;
  }
  const result: string[] = [];
  if ((before?.homeWarehouseId || null) !== after.homeWarehouseId)
    result.push(
      `所属仓库：${before?.homeWarehouseId || "未设置"} → ${after.homeWarehouseId || "未设置"}`,
    );
  for (const role of Object.keys(roleLabels) as (keyof typeof roleLabels)[]) {
    const oldValue = scope(before?.grants.find((g) => g.role === role)),
      newValue = scope(after.grants.find((g) => g.role === role));
    if (oldValue !== newValue)
      result.push(`${roleLabels[role]}：${oldValue} → ${newValue}`);
  }
  for (const capability of Object.keys(
    capabilityLabels,
  ) as (keyof typeof capabilityLabels)[]) {
    const oldValue = scope(
        before?.platformGrants.find((g) => g.capability === capability),
      ),
      newValue = scope(
        after.platformGrants.find((g) => g.capability === capability),
      );
    if (oldValue !== newValue)
      result.push(`${capabilityLabels[capability]}：${oldValue} → ${newValue}`);
  }
  return result;
}

export async function employeeWarehouseOptions(): Promise<
  { id: string; name: string; enabled: boolean }[]
> {
  const items: { id: string; name: string; enabled: boolean }[] = [];
  let page = 1;
  while (true) {
    const data = await request<
      Page<{ id: string; name: string; enabled: boolean }>
    >(`/admin/employees/warehouse-options?page=${page}&pageSize=100`);
    items.push(...data.items);
    if (items.length >= data.total || !data.items.length) return items;
    page++;
  }
}
