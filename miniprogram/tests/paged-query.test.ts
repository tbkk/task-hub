import assert from 'node:assert/strict'
import { test } from 'node:test'
import { PagedQuery } from '../src/features/common/paged-query'

test('分页刷新替换并在追加时按字符串 id 去重', () => {
  const state = new PagedQuery<{ id: string; value: string }>()
  state.replace({ items: [{ id: '1', value: 'old' }], page: 1, pageSize: 1, total: 2 })
  state.append({ items: [{ id: '1', value: 'new' }, { id: '2', value: 'two' }], page: 2, pageSize: 1, total: 2 })
  assert.deepEqual(state.items, [{ id: '1', value: 'new' }, { id: '2', value: 'two' }])
  assert.equal(state.hasMore, false)
})

test('分页追加失败保留已有内容，reset 清空全部状态', () => {
  const state = new PagedQuery<{ id: string }>()
  state.replace({ items: [{ id: '1' }], page: 1, pageSize: 20, total: 2 })
  state.fail('网络连接失败', true)
  assert.deepEqual(state.items, [{ id: '1' }])
  assert.equal(state.error, '网络连接失败')
  state.reset()
  assert.deepEqual(state.items, [])
  assert.equal(state.error, '')
  assert.equal(state.page, 0)
})
