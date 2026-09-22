import { request } from '../../api/request'
export interface AuditRow { id:string;actorName:string;objectType:string;objectId:string;action:string;reason:string;occurredAt:string }
export interface Page<T>{items:T[];total:number;page:number;pageSize:number}
export function history(from:string,to:string){return request<Page<AuditRow>>(`/admin/history?from=${from}&to=${to}&page=1&pageSize=50`)}
export function correct(objectType:string,objectId:string,input:{fieldName:string;afterValue:string;reason:string;expectedVersion?:number}){return request(`/admin/history/${objectType}/${objectId}/corrections`,{method:'POST',body:JSON.stringify(input)})}
