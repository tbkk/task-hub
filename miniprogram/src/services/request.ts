import type { Role } from '../features/workspace/model'
import { workspace } from '../features/workspace/store'
import { notifySessionInvalidated, notifyAuthorizationChanged, sessionToken } from './session'
import type { ApiEnvelope, ApiErrorData } from './types'
export type { ApiEnvelope, ApiErrorData, EntityId } from './types'

export class ApiRequestError extends Error {
  constructor(message: string, readonly statusCode?: number, readonly code?: number, readonly data?: ApiErrorData | null) {
    super(message)
    this.name = 'ApiRequestError'
  }
}

const env = import.meta.env ?? {}
const apiBaseUrl = (env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
const parsedTimeout = Number(env.VITE_API_TIMEOUT || 10000)
const timeout = Number.isFinite(parsedTimeout) && parsedTimeout > 0 ? parsedTimeout : 10000
const invalidSessionCodes = new Set([40101, 40301])

export interface ApiRequestOptions {
  path: string
  method?: UniApp.RequestOptions['method']
  data?: UniApp.RequestOptions['data']
  header?: UniApp.RequestOptions['header']
  workspace?: Role | false
  idempotencyKey?: string
}

export function request<TResponse>(options: ApiRequestOptions): Promise<TResponse> {
  const header: Record<string, string> = { ...(options.header as Record<string, string> | undefined) }
  const token = sessionToken.get()
  const activeWorkspace = options.workspace === undefined ? workspace.activeRole : options.workspace
  if (token) header.Authorization = `Bearer ${token}`
  if (activeWorkspace) header['X-Workspace'] = activeWorkspace
  if (options.idempotencyKey) header['Idempotency-Key'] = options.idempotencyKey
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${apiBaseUrl}${options.path.startsWith('/') ? options.path : `/${options.path}`}`,
      method: options.method || 'GET', data: options.data, header, timeout,
      success(response) {
        const data = response.data as Partial<ApiEnvelope<TResponse>> | null
        if (response.statusCode < 200 || response.statusCode >= 300 || !data || data.code !== 0) {
          if (token && sessionToken.get() === token) {
            if (response.statusCode === 401 || (data?.code !== undefined && invalidSessionCodes.has(data.code))) notifySessionInvalidated()
            else if (data?.code === 40302 && options.path !== '/identity/me') notifyAuthorizationChanged()
          }
          reject(new ApiRequestError(data?.message || (response.statusCode >= 200 && response.statusCode < 300 ? '服务返回异常' : `请求失败（HTTP ${response.statusCode}）`), response.statusCode, data?.code, data?.data as ApiErrorData | null | undefined))
          return
        }
        resolve(data.data as TResponse)
      },
      fail(error) { reject(new ApiRequestError(error.errMsg || '网络连接失败')) },
    })
  })
}
