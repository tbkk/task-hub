import { request } from '../../services/request'
import type { CatalogStop,CatalogWarehouse,BookingSlot,BookingRules } from './model'
export function query(params:Record<string,string|number|undefined>) { return Object.entries(params).filter(([,v])=>v!==undefined && v!=='').map(([k,v])=>`${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`).join('&') }
export const listWarehouses=()=>request<CatalogWarehouse[]>({path:'/catalog/warehouses',workspace:'worker'})
export const listAvailableStops=(warehouseId:string)=>request<CatalogStop[]>({path:`/catalog/stops?${query({warehouseId})}`,workspace:'worker'})
export const listSlots=(warehouseId:string,stopId:string,date:string)=>request<BookingSlot[]>({path:`/catalog/slots?${query({warehouseId,stopId,date})}`,workspace:'worker'})
export const getBookingRules=(warehouseId:string)=>request<BookingRules>({path:`/catalog/rules?${query({warehouseId})}`,workspace:'worker'})
export const listFavorites=()=>request<CatalogStop[]>({path:'/favorites',workspace:'worker'})
export const setFavorite=(stopId:string,favorite:boolean)=>request<unknown>({path:`/favorites/${encodeURIComponent(stopId)}`,method:favorite?'PUT':'DELETE',workspace:'worker'})
