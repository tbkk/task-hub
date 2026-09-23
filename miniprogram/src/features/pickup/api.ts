import { request } from '../../services/request'
import type { PickupScan, ControlRequest } from './model'
export const scanVehicle=(qrText:string)=>request<PickupScan>({path:'/pickup/scan',method:'POST',workspace:'worker',data:{qrText}})
export const openPickup=(orderId:string,expectedVersion:number,key:string)=>request<ControlRequest>({path:`/orders/${encodeURIComponent(orderId)}/pickup/open`,method:'POST',workspace:'worker',data:{expectedVersion},idempotencyKey:key})
export const confirmPickup=(orderId:string,expectedVersion:number,key:string)=>request<Record<string,unknown>>({path:`/orders/${encodeURIComponent(orderId)}/pickup/confirm`,method:'POST',workspace:'worker',data:{expectedVersion},idempotencyKey:key})
