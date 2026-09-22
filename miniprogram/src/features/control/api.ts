import { request } from '../../services/request'
import type { ControlRequest } from '../pickup/model'
export const goTask=(taskId:string,expectedVersion:number,key:string)=>request<ControlRequest>({path:`/tasks/${encodeURIComponent(taskId)}/go`,method:'POST',workspace:'dispatch',data:{expectedVersion},idempotencyKey:key})
export const cancelTask=(taskId:string,expectedVersion:number,key:string)=>request<ControlRequest>({path:`/tasks/${encodeURIComponent(taskId)}/cancel`,method:'POST',workspace:'dispatch',data:{expectedVersion},idempotencyKey:key})
