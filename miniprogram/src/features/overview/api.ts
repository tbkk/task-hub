import { request } from '../../services/request'
import { query } from '../catalog/api'
export interface Overview { date: string; orderCounts: Record<string, number>; batchCounts: Record<string, number>; taskCounts: Record<string, number>; openTickets: number }
export const getOverview=(date?:string,warehouseId?:string)=>request<Overview>({path:`/overview?${query({date,warehouseId})}`,workspace:'overview'})
