import { afterEach, describe, expect, it, vi } from 'vitest'

import { ApiError, request } from './request'

describe('request', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('unwraps successful API data and preserves string IDs', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: 0,
      message: 'ok',
      data: { id: '9007199254740993' },
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })))

    await expect(request<{ id: string }>('/people')).resolves.toEqual({
      id: '9007199254740993',
    })
  })

  it('throws an ApiError for a non-2xx response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: 40101,
      message: '登录状态已失效',
      data: null,
    }), { status: 401, headers: { 'Content-Type': 'application/json' } })))

    await expect(request('/people')).rejects.toMatchObject({
      name: 'ApiError',
      message: '登录状态已失效',
      code: 40101,
      status: 401,
    })
  })

  it('throws an ApiError when the business code is non-zero', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: 40012,
      message: '参数不合法',
      data: null,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })))

    await expect(request('/rules')).rejects.toBeInstanceOf(ApiError)
  })
})
