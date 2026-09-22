import { request } from '../../services/request'
import type { Page } from '../../services/types'
import { query } from '../catalog/api'
import type { Order,OrderDraft,History } from './model'
export const createOrder=(input:OrderDraft,key:string)=>request<Order>({path:'/orders',method:'POST',workspace:'worker',data:input,idempotencyKey:key})
export const listOrders=(params:{status?:string;keyword?:string;page:number;pageSize:number})=>request<Page<Order>>({path:`/orders?${query(params)}`,workspace:'worker'})
export const getOrder=(id:string)=>request<Order>({path:`/orders/${encodeURIComponent(id)}`,workspace:'worker'})
export const requestCancellation=(id:string,expectedVersion:number,reason:string,key:string)=>request<Order>({path:`/orders/${encodeURIComponent(id)}/cancellations`,method:'POST',workspace:'worker',data:{expectedVersion,reason},idempotencyKey:key})
export const getOrderHistory=(id:string,page:number)=>request<Page<History>>({path:`/history?${query({objectType:'ORDER',objectId:id,page,pageSize:20})}`,workspace:'worker'})
