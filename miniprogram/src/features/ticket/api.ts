import { request } from '../../services/request'
import type { Page } from '../../services/types'
import type { Ticket } from './model'
import { query } from '../catalog/api'
export const listTickets=(params:{state?:string;warehouseId?:string;page?:number;pageSize?:number}={})=>request<Page<Ticket>>({path:`/tickets?${query({page:1,pageSize:20,...params})}`})
export const getTicket=(id:string)=>request<Ticket>({path:`/tickets/${encodeURIComponent(id)}`})
export const createTicket=(orderId:string,description:string,key:string)=>request<Ticket>({path:'/tickets',method:'POST',workspace:'worker',data:{orderId,description},idempotencyKey:key})
export const acceptTicket=(id:string,expectedVersion:number,key:string)=>request<Ticket>({path:`/tickets/${encodeURIComponent(id)}/accept`,method:'POST',workspace:'warehouse',data:{expectedVersion},idempotencyKey:key})
export const addTicketNote=(id:string,expectedVersion:number,text:string,key:string)=>request<Ticket>({path:`/tickets/${encodeURIComponent(id)}/notes`,method:'POST',workspace:'warehouse',data:{expectedVersion,text},idempotencyKey:key})
export const closeTicket=(id:string,expectedVersion:number,result:string,key:string)=>request<Ticket>({path:`/tickets/${encodeURIComponent(id)}/close`,method:'POST',workspace:'warehouse',data:{expectedVersion,result},idempotencyKey:key})
