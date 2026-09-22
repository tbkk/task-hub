export interface PickupOrder { id:string; number:string; receiverName:string; status:string; version:number; compartmentId?:string }
export interface PickupScan { vehicle:Record<string, unknown>; taskId:string; businessStatus:string; orders:PickupOrder[]; canContinue:boolean; blockers:{code:string;message:string}[] }
export interface ControlRequest { id:string; type:string; status:'PENDING'|'ACCEPTED'|'FAILED'|'UNKNOWN'; vehicleId:string; taskId:string; orderId?:string; compartmentIds:string[]; message:string }
