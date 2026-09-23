import { getAccessToken, invalidateSession } from './auth-state'

export interface ApiResponse<T> { code: number; message: string; data: T }
export type ApiErrorKind = 'business' | 'unauthorized' | 'forbidden' | 'conflict' | 'unknown-result' | 'invalid-response'
export class ApiError extends Error {
  constructor(message: string, public readonly code: number, public readonly status: number,
    public readonly details: unknown = null, public readonly kind: ApiErrorKind = 'business') {
    super(message)
    this.name = 'ApiError'
  }
}
const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
function isWrite(init: RequestInit) { return !['GET', 'HEAD'].includes((init.method || 'GET').toUpperCase()) }

async function send(path: string, init: RequestInit): Promise<Response> {
  const headers = new Headers(init.headers)
  const token = getAccessToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  headers.set('X-Request-Id', crypto.randomUUID())
  let response: Response
  try {
    response = await fetch(`${apiBaseUrl}/${path.replace(/^\//, '')}`, { ...init, headers, credentials: 'omit' })
  } catch {
    throw new ApiError(isWrite(init) ? '请求结果待核对，请先刷新查询实际状态' : '网络连接失败，请重试', -1, 0, null, isWrite(init) ? 'unknown-result' : 'business')
  }
  if (!response.ok) {
    let envelope: Partial<ApiResponse<unknown>> = {}
    try { envelope = await response.json() as Partial<ApiResponse<unknown>> } catch { /* 不回显 HTML 或代理错误正文。 */ }
    if (response.status === 401 || envelope.code === 40301) invalidateSession(token)
    const kind = response.status === 401 ? 'unauthorized' : response.status === 403 ? 'forbidden' : response.status === 409 ? 'conflict' : isWrite(init) && response.status >= 500 ? 'unknown-result' : 'business'
    throw new ApiError(envelope.message || `请求失败（${response.status}）`, envelope.code ?? response.status, response.status, envelope.data, kind)
  }
  return response
}
export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await send(path, init)
  let envelope: ApiResponse<T>
  try { envelope = await response.json() as ApiResponse<T> } catch {
    throw new ApiError('响应无法解析，请刷新核对结果', -1, response.status, null, isWrite(init) ? 'unknown-result' : 'invalid-response')
  }
  if (!envelope || envelope.code !== 0) throw new ApiError(envelope?.message || '请求失败', envelope?.code ?? -1, response.status, envelope?.data)
  return envelope.data
}
export async function download(path: string): Promise<{ blob: Blob; filename: string }> {
  const response = await send(path, {})
  if (!response.headers.get('content-type')?.includes('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')) {
    throw new ApiError('导出响应格式不正确，请重试', -1, response.status, null, 'invalid-response')
  }
  const disposition = response.headers.get('content-disposition') || ''
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1]
  let name = '订单报表.xlsx'
  try { if (encoded) name = decodeURIComponent(encoded) } catch { /* 使用安全默认名称。 */ }
  return { blob: await response.blob(), filename: name.replace(/[\\/\x00-\x1f]/g, '_') }
}
