export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly code: number,
    public readonly status: number,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(`${apiBaseUrl}/${path.replace(/^\//, '')}`, {
    ...init,
    headers,
  })

  let envelope: ApiResponse<T>
  try {
    envelope = await response.json() as ApiResponse<T>
  } catch {
    throw new ApiError(response.statusText || '服务返回了无法解析的响应', -1, response.status)
  }

  if (!response.ok || envelope.code !== 0) {
    throw new ApiError(envelope.message || '请求失败', envelope.code, response.status)
  }

  return envelope.data
}
