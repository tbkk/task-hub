import { request } from '../../services/request'
import type { Page } from '../../services/types'
export interface Message { id:string; title:string; body:string; createdAt:string; readAt:string|null; target:{type:string;id:string;workspace:string}|null }
export const listMessages=(page=1,pageSize=20)=>request<Page<Message>>({path:`/messages?page=${page}&pageSize=${pageSize}`,workspace:'worker'})
export const markMessageRead=(id:string)=>request<Message>({path:`/messages/${encodeURIComponent(id)}/read`,method:'PUT',workspace:'worker'})
