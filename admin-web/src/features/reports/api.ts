import { download, request } from '../../api/request'
export interface Summary { orders:number; batches:number; tasks:number }
export interface ReportRow { id:string; number:string; warehouseId:string; status:string; createdAt:string }
export interface Page<T> { items:T[]; total:number; page:number; pageSize:number }
export function summary(from:string,to:string,warehouseId?:string) { return request<Summary>(`/admin/reports/summary?from=${from}&to=${to}${warehouseId ? `&warehouseId=${encodeURIComponent(warehouseId)}` : ''}`) }
export function details(from:string,to:string,page=1) { return request<Page<ReportRow>>(`/admin/reports/details?from=${from}&to=${to}&page=${page}&pageSize=20`) }
export function exportReport(from:string,to:string) { return download(`/admin/reports/export?from=${from}&to=${to}`) }
