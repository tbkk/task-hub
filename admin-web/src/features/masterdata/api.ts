import { request } from "../../api/request";
import type { Page } from "../people/api";
export type Resource = "warehouses" | "stops" | "vehicles";
export interface Entity {
  id: string;
  name: string;
  enabled: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
  code?: string;
  externalStopId?: string;
  externalVehicleName?: string;
  warehouseId?: string;
  warehouseIds?: string[];
  boundStopIds?: string[];
}
export interface Compartment {
  id: string;
  vehicleId: string;
  hardwareNo: string;
  label: string;
  enabled: boolean;
  version: number;
}
export interface Hour {
  weekday: number;
  start: string;
  end: string;
}
export interface Rule {
  version: number;
  businessHours: Hour[];
  slotCapacity: number;
  bookingDays: number;
  descriptionMaxLength: number;
  sizeMaxLength: number;
  remarkMaxLength: number;
  telemetryMaxAgeSeconds: number;
  doorMaxAgeSeconds: number;
  pickupTimeoutMinutes: number;
  sharedCompartmentEnabled: false;
}
export const names: Record<Resource, string> = {
  warehouses: "仓库",
  stops: "停靠点",
  vehicles: "车辆",
};
export function updateBody(
  resource: Resource,
  source: Record<string, unknown>,
) {
  const fields =
    resource === "warehouses"
      ? ["name", "code", "enabled"]
      : resource === "stops"
        ? ["name", "externalStopId", "warehouseIds", "enabled"]
        : [
            "name",
            "externalVehicleName",
            "warehouseId",
            "boundStopIds",
            "enabled",
          ];
  return Object.fromEntries([
    ...fields.map((key) => [key, source[key]]),
    ...(source.version === undefined
      ? []
      : [["expectedVersion", source.version]]),
  ]);
}
export function list(
  resource: Resource,
  page = 1,
  pageSize = 20,
): Promise<Page<Entity>> {
  return request(`/admin/${resource}?page=${page}&pageSize=${pageSize}`);
}
export async function options(resource: Resource): Promise<Entity[]> {
  let page = 1;
  const result: Entity[] = [];
  while (true) {
    const data = await list(resource, page++, 100);
    result.push(...data.items);
    if (result.length >= data.total || !data.items.length) return result;
  }
}
export function detail(resource: Resource, id: string): Promise<Entity> {
  return request(`/admin/${resource}/${encodeURIComponent(id)}`);
}
export function save(
  resource: Resource,
  body: Record<string, unknown>,
  id?: string,
): Promise<Entity> {
  return request(
    `/admin/${resource}${id ? "/" + encodeURIComponent(id) : ""}`,
    {
      method: id ? "PUT" : "POST",
      body: JSON.stringify(updateBody(resource, body)),
    },
  );
}
export async function external(
  resource: "stops" | "vehicles",
): Promise<{ id: string; name: string }[]> {
  const result: { id: string; name: string }[] = [];
  let page = 1;
  while (true) {
    const data = await request<Page<{ id: string; name: string }>>(
      `/admin/integration-catalog/${resource}?page=${page++}&pageSize=100`,
    );
    result.push(...data.items);
    if (result.length >= data.total || !data.items.length) return result;
  }
}
export function compartments(vehicleId: string): Promise<Compartment[]> {
  return request(
    `/admin/vehicles/${encodeURIComponent(vehicleId)}/compartments`,
  );
}
export function saveCompartment(item: Compartment): Promise<Compartment> {
  return request(
    `/admin/vehicles/${encodeURIComponent(item.vehicleId)}/compartments/${encodeURIComponent(item.id)}`,
    {
      method: "PUT",
      body: JSON.stringify({
        expectedVersion: item.version,
        label: item.label,
        enabled: item.enabled,
      }),
    },
  );
}
export function syncCompartments(vehicle: Entity): Promise<Compartment[]> {
  return request(
    `/admin/vehicles/${encodeURIComponent(vehicle.id)}/compartments/sync`,
    {
      method: "POST",
      body: JSON.stringify({ expectedVersion: vehicle.version }),
    },
  );
}
export function getRules(warehouseId: string): Promise<Rule> {
  return request(`/admin/rules/${encodeURIComponent(warehouseId)}`);
}
export function saveRules(warehouseId: string, rule: Rule): Promise<Rule> {
  const { version, ...body } = rule;
  return request(`/admin/rules/${encodeURIComponent(warehouseId)}`, {
    method: "PUT",
    body: JSON.stringify({ ...body, expectedVersion: version }),
  });
}
export interface LoadingStopBinding { id: string; stopId: string; version: number }
export function getLoadingStop(warehouseId: string): Promise<LoadingStopBinding | null> {
  return request(`/admin/warehouses/${encodeURIComponent(warehouseId)}/loading-stop`);
}
export function saveLoadingStop(warehouseId: string, stopId: string, expectedVersion: number): Promise<LoadingStopBinding> {
  return request(`/admin/warehouses/${encodeURIComponent(warehouseId)}/loading-stop`, {
    method: 'PUT', body: JSON.stringify({ stopId, expectedVersion }),
  });
}
