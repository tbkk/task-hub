import { request } from '../../services/request'
import type { Page } from '../../services/types'
import type { Message } from './model'
export type { Message } from './model'
export const listMessages=(page=1,pageSize=20,unreadOnly=false)=>request<Page<Message>>({path:`/messages?page=${page}&pageSize=${pageSize}&unreadOnly=${unreadOnly}`})
export const getMessage=(id:string)=>request<Message>({path:`/messages/${encodeURIComponent(id)}`})
export const markMessageRead=(id:string)=>request<Message>({path:`/messages/${encodeURIComponent(id)}/read`,method:'PUT'})
export const unreadCount=()=>request<{count:number}>({path:'/messages/unread-count'})
