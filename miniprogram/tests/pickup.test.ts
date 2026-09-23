import test from 'node:test'
import assert from 'node:assert/strict'
import { blockerText, controlStatusText, parseVehicleQr } from '../src/features/pickup/model'

test('二维码只识别 taskhub vehicle 格式并提取字符串 id', () => {
  assert.equal(parseVehicleQr('taskhub:vehicle:van/1'), null)
  assert.equal(parseVehicleQr(' taskhub:vehicle:vehicle-01 '), 'vehicle-01')
  assert.equal(parseVehicleQr('https://example.test/vehicle-01'), null)
})

test('取货控制展示服务端 blocker 与未知状态', () => {
  assert.equal(controlStatusText('UNKNOWN'), '结果待核对')
  assert.equal(blockerText({ code: 'DOOR_OPEN' }), '格口门尚未关闭')
  assert.equal(blockerText({ message: '服务端说明' }), '服务端说明')
})
