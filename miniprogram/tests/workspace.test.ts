import assert from 'node:assert/strict'
import { test } from 'node:test'
import { WorkspaceState, type Grant } from '../src/features/workspace/model'

const worker: Grant = { role: 'worker', scope: '本人申请与接收的订单', warehouseIds: ['a'] }
const warehouse: Grant = { role: 'warehouse', scope: '一号仓库', warehouseIds: ['a'] }
const overview: Grant = { role: 'overview', scope: '全厂', warehouseIds: ['a', 'b'] }

test('单角色直接进入，多角色首次选择；无效历史角色不恢复', () => {
  const state = new WorkspaceState()
  state.refresh('one', [worker])
  assert.equal(state.activeRole, 'worker')
  state.refresh('two', [worker, warehouse], 'dispatch')
  assert.equal(state.activeRole, null)
  state.refresh('two', [worker, warehouse], 'warehouse')
  assert.equal(state.activeRole, 'warehouse')
})

test('不能选择未授权角色；范围来自当前角色，切换清空数据', () => {
  const state = new WorkspaceState()
  state.refresh('one', [warehouse, overview], 'warehouse')
  assert.throws(() => state.switchTo('dispatch'), /未授权/)
  assert.deepEqual(state.activeGrant?.warehouseIds, ['a'])
  state.cache.order = 'old'
  state.switchTo('overview')
  assert.deepEqual(state.cache, {})
  assert.deepEqual(state.activeGrant?.warehouseIds, ['a', 'b'])
})

test('未提交内容需明确放弃，取消切换保留状态', () => {
  const state = new WorkspaceState()
  state.refresh('one', [worker, warehouse], 'worker')
  state.dirty = true
  state.cache.form = 'draft'
  assert.throws(() => state.switchTo('warehouse'), /未提交/)
  assert.equal(state.activeRole, 'worker')
  assert.equal(state.cache.form, 'draft')
  state.switchTo('warehouse', true)
  assert.equal(state.dirty, false)
  assert.deepEqual(state.cache, {})
})

test('当前角色被撤销或范围变更时清除旧数据；无权限不保留工作区', () => {
  const state = new WorkspaceState()
  state.refresh('one', [worker, warehouse, overview], 'warehouse')
  state.cache.order = 'private'
  state.refresh('one', [worker, overview], 'warehouse')
  assert.equal(state.activeRole, null)
  assert.deepEqual(state.cache, {})
  state.switchTo('overview')
  state.cache.order = 'other-warehouse'
  state.refresh('one', [{ ...overview, warehouseIds: ['a'] }])
  assert.deepEqual(state.cache, {})
  state.refresh('one', [])
  assert.equal(state.activeRole, null)
})

test('选择当前工作区并确认放弃时也会清除草稿', () => {
  const state = new WorkspaceState()
  state.refresh('one', [worker])
  state.cache.form = 'draft'
  state.dirty = true
  state.switchTo('worker', true)
  assert.equal(state.dirty, false)
  assert.deepEqual(state.cache, {})
})
