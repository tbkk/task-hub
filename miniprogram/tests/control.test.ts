import test from 'node:test'
import assert from 'node:assert/strict'
import { canControl, taskStateText } from '../src/features/control/model'

test('车辆控制只在服务端条件无阻断时开放', () => {
  assert.equal(canControl({ state: 'AT_STOP' }, 'GO'), true)
  assert.equal(canControl({ state: 'AT_STOP' }, 'GO', [{ code: 'ORDER_NOT_PICKED' }]), false)
  assert.equal(canControl({ state: 'PLANNING' }, 'GO'), false)
  assert.equal(canControl({ state: 'RUNNING' }, 'CANCEL'), true)
  assert.equal(taskStateText('RECONCILIATION'), '待核对')
})
