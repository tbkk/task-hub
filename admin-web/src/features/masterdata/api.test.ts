import { expect, it } from "vitest";
import { updateBody } from "./api";
it("仓库更新只映射可编辑字段和读取版本", () => {
  expect(
    updateBody("warehouses", {
      id: "w",
      name: "仓库",
      code: "W",
      enabled: true,
      version: 2,
      createdAt: "old",
    }),
  ).toEqual({ name: "仓库", code: "W", enabled: true, expectedVersion: 2 });
});
it("车辆更新保留绑定目录且不携带只读硬件号", () => {
  expect(
    updateBody("vehicles", {
      id: "v",
      name: "车",
      externalVehicleName: "external",
      warehouseId: "w",
      enabled: true,
      boundStopIds: ["s"],
      version: 3,
      hardwareNo: "hack",
    }),
  ).toEqual({
    name: "车",
    externalVehicleName: "external",
    warehouseId: "w",
    enabled: true,
    boundStopIds: ["s"],
    expectedVersion: 3,
  });
});
