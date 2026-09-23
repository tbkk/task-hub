import type { BookingRules } from '../catalog/model'
export interface OrderDraft { warehouseId:string;stopId:string;slotId:string;description:string;size:string;receiverName:string;receiverPhone:string;remark:string }
export type OrderStatus='PENDING'|'ACCEPTED'|'READY'|'IN_TRANSIT'|'AWAITING_PICKUP'|'COMPLETED'|'REJECTED'|'CANCELLED'
export interface History { id:string;action:string;actorName:string;occurredAt:string;summary:string;reason:string|null }
export interface Order extends OrderDraft { id:string;number:string;version:number;slotStart:string;slotEnd:string;applicantId:string;applicantName?:string;status:OrderStatus;receiverBound:boolean;warehouseName?:string;stopName?:string;relation?:'APPLICANT'|'RECEIVER'|'BOTH'|'STAFF';cancellation:{id:string;status:'PENDING'|'APPROVED'|'REJECTED';reason:string;result:string|null}|null;allowedActions:string[];events?:History[];createdAt:string;updatedAt:string }
export const orderStatuses:Record<OrderStatus,string>={PENDING:'待受理',ACCEPTED:'已受理',READY:'待发车',IN_TRANSIT:'配送中',AWAITING_PICKUP:'待取货',COMPLETED:'已完成',REJECTED:'已驳回',CANCELLED:'已取消'}
export const orderStatusText=(status:string)=>orderStatuses[status as OrderStatus]||status
export const cancellationText=(status:string)=>({PENDING:'取消处理中',APPROVED:'取消已批准',REJECTED:'取消未批准'}[status]||status)
export function canCancel(order:{applicantId:string;allowedActions:string[];cancellation:{status:string}|null},userId?:string) { return order.applicantId===userId && order.allowedActions.includes('ORDER_CANCEL') && order.cancellation?.status!=='PENDING' }
export function validateOrderDraft(draft:OrderDraft,rules:BookingRules) {
 const errors:Record<string,string>={}
 const required:Record<string,string>={warehouseId:'所属仓库',stopId:'目的地',slotId:'预约时段',description:'货物名称及描述',size:'货物大小',receiverName:'接收人姓名',receiverPhone:'接收人手机号'}
 for(const [field,label] of Object.entries(required)) if(!draft[field as keyof OrderDraft].trim()) errors[field]=`请填写${label}`
 if(!/^1[3-9]\d{9}$/.test(draft.receiverPhone.trim())) errors.receiverPhone='请输入正确手机号'
 for(const [field,max] of [['description',rules.descriptionMaxLength],['size',rules.sizeMaxLength],['remark',rules.remarkMaxLength],['receiverName',80]] as const) if(draft[field].length>max) errors[field]=`最多填写 ${max} 字`
 return errors
}
export const emptyDraft=():OrderDraft=>({warehouseId:'',stopId:'',slotId:'',description:'',size:'',receiverName:'',receiverPhone:'',remark:''})
export function errorText(cause:unknown) { return cause instanceof Error?cause.message:'请求失败，请重试' }
