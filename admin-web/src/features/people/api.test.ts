import { afterEach, expect, it, vi } from "vitest";
import {
  employeeDraft,
  listEmployees,
  saveEmployee,
  type Employee,
} from "./api";
const original: Employee = {
  id: "one",
  name: "张三",
  phone: "13800000011",
  enabled: true,
  version: 7,
  createdAt: "",
  updatedAt: "",
  verifiedPhone: null,
  homeWarehouseId: null,
  wechatBound: false,
  grants: [
    { role: "warehouse", scope: "WAREHOUSES", warehouseIds: ["w1"] },
    { role: "worker", scope: "SELF", warehouseIds: [] },
  ],
  platformGrants: [],
};
afterEach(() => vi.unstubAllGlobals());
it("编辑草稿保留多角色且取消编辑不污染原授权", () => {
  const draft = employeeDraft(original);
  draft.grants[0]!.warehouseIds.push("w2");
  expect(original.grants[0]!.warehouseIds).toEqual(["w1"]);
  expect(draft.grants).toHaveLength(2);
  expect(draft.expectedVersion).toBe(7);
  expect(draft).not.toHaveProperty("verifiedPhone");
});
it("更新保留读取版本并正确编码员工标识", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValue(
      new Response(JSON.stringify({ code: 0, data: original })),
    );
  vi.stubGlobal("fetch", fetch);
  await saveEmployee(employeeDraft(original), "user/one");
  expect(fetch.mock.calls[0]![0]).toBe("/api/admin/employees/user%2Fone");
  const init = fetch.mock.calls[0]![1] as RequestInit;
  expect(init.method).toBe("PUT");
  expect(JSON.parse(init.body as string).expectedVersion).toBe(7);
});
it("列表按服务端分页筛选，明确保留停用 false 条件", async () => {
  const fetch = vi.fn().mockResolvedValue(
    new Response(
      JSON.stringify({
        code: 0,
        data: { items: [], total: 0, page: 2, pageSize: 20 },
      }),
    ),
  );
  vi.stubGlobal("fetch", fetch);
  await listEmployees({
    keyword: "张 三",
    enabled: false,
    page: 2,
    pageSize: 20,
  });
  const url = new URL(fetch.mock.calls[0]![0] as string, "http://localhost");
  expect(url.searchParams.get("keyword")).toBe("张 三");
  expect(url.searchParams.get("enabled")).toBe("false");
  expect(url.searchParams.get("page")).toBe("2");
});
it("授权变更摘要明确列出仓库范围前后值", async () => {
  const { employeeChanges } = await import("./api");
  const draft = employeeDraft(original);
  draft.grants[0]!.warehouseIds = ["w2"];
  expect(employeeChanges(original, draft)).toContain(
    "仓库人员：指定仓库 w1 → 指定仓库 w2",
  );
});
