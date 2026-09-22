import { expect, it, vi } from 'vitest'
import { history } from './api'
vi.mock('../../api/request', () => ({ request: vi.fn(async (path:string) => { expect(path).toContain('/admin/history?'); return { items: [], total: 0, page: 1, pageSize: 50 } }) }))
it('审计查询使用历史接口', async () => { await history('2026-09-22','2026-09-22') })
