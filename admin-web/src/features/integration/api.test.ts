import { expect, it, vi } from 'vitest'
import { saveSettings } from './api'
vi.mock('../../api/request', () => ({ request: vi.fn(async (_path:string, init:RequestInit) => { expect(String(init.body)).not.toContain('password'); return {} }) }))
it('接入设置请求不携带密码字段', async () => { await saveSettings({ provider:'SIMULATOR',baseUrl:'http://localhost',orgId:'o',appId:'a',enabled:false,expectedVersion:0 }) })
