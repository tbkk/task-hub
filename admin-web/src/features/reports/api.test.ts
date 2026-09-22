import { expect, it, vi } from 'vitest'
import { details } from './api'
vi.mock('../../api/request', () => ({ request: vi.fn(async (path:string) => { expect(path).toContain('from=2026-09-22'); return { items: [], total: 0, page: 1, pageSize: 20 } }), download: vi.fn() }))
it('报表查询携带业务日期和分页', async () => { await details('2026-09-22','2026-09-22'); })
