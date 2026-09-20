export interface ApiEnvelope<T> { code: number; message: string; data: T }
export type EntityId = string

export class ApiRequestError extends Error {
  constructor(message: string, readonly statusCode?: number, readonly code?: number) {
    super(message)
    this.name = 'ApiRequestError'
  }
}

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
const parsedTimeout = Number(import.meta.env.VITE_API_TIMEOUT || 10000)
const timeout = Number.isFinite(parsedTimeout) && parsedTimeout > 0 ? parsedTimeout : 10000

export interface ApiRequestOptions {
  path: string
  method?: UniApp.RequestOptions['method']
  data?: UniApp.RequestOptions['data']
  header?: UniApp.RequestOptions['header']
}

export function request<TResponse>(options: ApiRequestOptions): Promise<TResponse> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${apiBaseUrl}${options.path.startsWith('/') ? options.path : `/${options.path}`}`,
      method: options.method || 'GET', data: options.data, header: options.header, timeout,
      success(response) {
        const data = response.data as Partial<ApiEnvelope<TResponse>> | null
        if (response.statusCode < 200 || response.statusCode >= 300) {
          reject(new ApiRequestError(data?.message || `请求失败（HTTP ${response.statusCode}）`, response.statusCode, data?.code))
          return
        }
        if (!data || data.code !== 0) {
          reject(new ApiRequestError(data?.message || '服务返回异常', response.statusCode, data?.code))
          return
        }
        resolve(data.data as TResponse)
      },
      fail(error) { reject(new ApiRequestError(error.errMsg || '网络连接失败')) },
    })
  })
}
