export type RequestStatus = 'PENDING' | 'ACCEPTED' | 'FAILED' | 'UNKNOWN'
export type DoorStatus = 'OPEN' | 'CLOSED' | 'UNKNOWN'
export function displayMetric(value: number | null | undefined, unit = '') { return value == null ? '—' : `${value}${unit ? ` ${unit}` : ''}` }
export function doorStatusText(status: DoorStatus) { return { OPEN: '已打开', CLOSED: '已关闭', UNKNOWN: '状态待核对' }[status] }
export function requestStatusText(status: RequestStatus) { return { PENDING: '请求处理中', ACCEPTED: '请求已受理', FAILED: '请求失败', UNKNOWN: '结果待核对' }[status] }
