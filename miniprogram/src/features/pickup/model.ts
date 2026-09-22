export interface PickupOrder { id:string; number:string; receiverName:string; status:string; version:number; compartmentId?:string }
export interface PickupScan { vehicle:Record<string, unknown>; taskId:string; businessStatus:string; orders:PickupOrder[]; canContinue:boolean; blockers:{code:string;message:string}[] }
export interface ControlRequest { id:string; type:string; status:'PENDING'|'ACCEPTED'|'FAILED'|'UNKNOWN'; vehicleId:string; taskId:string; orderId?:string; compartmentIds:string[]; message:string }

export function parseVehicleQr(value: string) {
  const match = /^taskhub:vehicle:([^\s/]+)$/.exec(value.trim())
  return match?.[1] || null
}

export function controlStatusText(status: string) {
  return ({ PENDING: '请求处理中', ACCEPTED: '请求已受理', FAILED: '请求失败', UNKNOWN: '结果待核对' } as Record<string, string>)[status] || '状态待核对'
}

export function blockerText(blocker: { code?: string; message?: string }) {
  return blocker.message?.trim() || ({ DOOR_UNKNOWN: '格口门状态未知', DOOR_OPEN: '格口门尚未关闭', VEHICLE_STALE: '车辆状态已过期', NO_NEXT_STOP: '没有明确的下一站', ORDER_NOT_PICKED: '仍有订单未完成取货', ACTIVE_TICKET: '存在待处理工单', TASK_MISMATCH: '任务与车辆或站点不匹配' } as Record<string, string>)[blocker.code || ''] || '当前条件未满足'
}
