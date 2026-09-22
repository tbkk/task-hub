import { request } from '../../services/request'
import type { ControlRequest } from '../pickup/model'
import type { Page } from '../../services/types'
import { query } from '../catalog/api'
import type { DispatchRequest, Vehicle, VehicleTask } from './model'
import type { Batch } from '../batch/model'
export const goTask=(taskId:string,expectedVersion:number,key:string)=>request<ControlRequest>({path:`/tasks/${encodeURIComponent(taskId)}/go`,method:'POST',workspace:'dispatch',data:{expectedVersion},idempotencyKey:key})
export const cancelTask=(taskId:string,expectedVersion:number,key:string)=>request<ControlRequest>({path:`/tasks/${encodeURIComponent(taskId)}/cancel`,method:'POST',workspace:'dispatch',data:{expectedVersion},idempotencyKey:key})
export const listVehicles=(params:{warehouseId?:string;page?:number;pageSize?:number}={})=>request<Page<Vehicle>>({path:`/vehicles?${query({page:1,pageSize:20,...params})}`,workspace:'dispatch'})
export const getVehicle=(id:string)=>request<Vehicle>({path:`/vehicles/${encodeURIComponent(id)}`,workspace:'dispatch'})
export const listTasks=(params:{warehouseId?:string;state?:string;page?:number;pageSize?:number}={})=>request<Page<VehicleTask>>({path:`/tasks?${query({page:1,pageSize:20,...params})}`,workspace:'dispatch'})
export const getTask=(id:string)=>request<VehicleTask>({path:`/tasks/${encodeURIComponent(id)}`,workspace:'dispatch'})
export const dispatchBatch=(batchId:string,expectedVersion:number,key:string)=>request<DispatchRequest>({path:`/batches/${encodeURIComponent(batchId)}/dispatch`,method:'POST',workspace:'dispatch',data:{expectedVersion},idempotencyKey:key})
export const getDispatchRequest=(id:string)=>request<DispatchRequest>({path:`/dispatch-requests/${encodeURIComponent(id)}`,workspace:'dispatch'})
export const reconcileDispatch=(id:string)=>request<DispatchRequest>({path:`/dispatch-requests/${encodeURIComponent(id)}/reconcile`,method:'POST',workspace:'dispatch'})
export const listDispatchBatches=(params:{status?:string;warehouseId?:string;page?:number;pageSize?:number}={})=>request<Page<Batch>>({path:`/batches?${query({page:1,pageSize:20,...params})}`,workspace:'dispatch'})
export const getDispatchBatch=(id:string)=>request<Batch>({path:`/batches/${encodeURIComponent(id)}`,workspace:'dispatch'})
