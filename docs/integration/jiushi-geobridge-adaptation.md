# 九识车辆接入适配约定

日期：2026-09-22

本文根据 `/Users/qin/Projects/GeoBridge` 的现有实现，以及仓库内《九识开放 api 文档 V2.9》《九识通用推送文档 V2.9》整理。它只确定 Task Hub 的适配边界，不代表已经具备九识凭证、MQTT 账号或测试车辆。

## 认证与开放 API

- 认证请求使用 `POST {authBaseUrl}/app/accessToken`，请求体为 `{appId, appKey}`。
- 除认证接口外，请求在 HTTP header 携带 `token`。响应统一按 `{success,errorCode,message,data}` 解包。
- `data.expiresAfter` 的单位是分钟。客户端在过期前提前失效缓存；收到 token 失效类错误时只重试一次，先清理缓存再重新认证。
- 车辆查询使用 `GET /vehicles`、`GET /vehicle?vehicleName=...`；查询结果中的 `online`、`businessStatus`、位置、`dispatchId` 和格口信息只作为快照/核对资料，不能替代 MQTT 实时状态。
- 控制适配器映射如下：

| Task Hub 动作 | 九识接口 | 请求关键字段 | 语义 |
| --- | --- | --- | --- |
| 派发普通任务 | `POST /vehicle/add_dispatch` | `vehicleName, fromStopId?, toStopIds, speedLimit?` | 返回 `dispatchId` 仅表示九识受理/规划中 |
| 派发订单任务 | `POST /vehicle/add_dispatch_order_and_go` | `vehicleName, fromStopId?, toStops, speedLimit?` | 订单格口与联系人按业务需要映射；未确认字段不得发送 |
| 取消任务 | `POST /vehicle/cancel_dispatch` | `vehicleName` | 九识要求车辆无速度；超时结果保持 UNKNOWN，禁止盲目重发 |
| 继续出发 | `POST /vehicle/go` | `vehicleName` | 车辆在停靠点驶向下一个点 |
| 打开格口 | `POST /vehicle/open_box`（文档换行显示为 `ope` + `n box`） | `vehicleName, gridNos?` | 空数组/缺省表示全部格口；Task Hub 必须传已授权格口集合 |

文档中测试环境 URL 存在断行和个别拼写展示，正式接入前必须用九识提供的完整 URL/环境清单核对，不能直接把转换后的文字上线。普通派发不得混入联系人或订单隐私字段。

## MQTT 连接与主题

GeoBridge 使用 Paho MQTT v5：用户名/密码来自受控凭证，`automaticReconnect=true`、`cleanStart=false`，连接成功和重连完成后重新订阅；失败按固定延迟重试。业务消息通过有界单线程队列串行处理，实时消息快速更新。

九识提供企业 MQTT server 的 host、用户名、密码和 `organizationCode`。订阅主题固定为：

```text
{organizationCode}/vehicle/+/realtime/push
{organizationCode}/vehicle/+/business/push
```

实际实现可按启用车辆收窄为具体车辆主题。收到 JSON 后按主题分流；坏消息记录有限错误并丢弃，不能阻塞 MQTT 回调线程。断线恢复后重新订阅，重复与乱序消息由事件幂等键和上报时间判序处理。

## 消息到 Task Hub 事件的映射

实时推送约 1Hz，包含 `vehicleName`、`gcj02Lon/gcj02Lat`、`speed`、`heading`、`power`、`vehicleState`、`vehicleBusinessStatus`、`dispatchId`、`timestamp`、`doorStatus` 等。业务推送只在业务状态变化时发送，包含 `vehicleBusinessStatus`、`currentStopId/currentStopName`、`lastStopId/lastStopName`、`dispatchId`、`timestamp` 及可选订单列表。

- `timestamp` 按毫秒时间戳解析为 `reportedAt`；每类消息独立判序，旧消息不能覆盖新快照。
- 实时消息更新遥测和门快照；业务消息更新业务状态和停靠点。缺少某类字段不清空另一类已知值。
- `doorStatus` 的值 `DOOR_CLOSE/DOOR_OPEN` 映射为 `CLOSED/OPEN`；缺失门字段不能刷新门状态。
- 九识业务状态 `IDLE/ROUTING/ONWAY/ATSTOP/BACK/ARRIVEHOME` 映射到 Task Hub 的车辆/任务状态，映射不确定时保留原始码并进入人工核对，不强行标记完成。
- MQTT 事件先持久化 `integration_event(provider=JIUSHI,event_key=...)`，再更新 `vehicle_snapshot`、`vehicle_door_snapshot` 和活动任务；同一事件内容改变返回冲突。

## 安全边界与当前状态

凭证、token、MQTT 密码只能进入受控秘密存储，设置读取接口只返回是否配置、连接状态、最后消息时间和错误类别。当前分支继续以 `SIMULATOR` 完成闭环；`JIUSHI` 适配器在 S20 通过 HTTP stub/MQTT fixture 验证后才能进入可配置状态。没有真实凭证和车辆时，readiness 必须标记为“依赖阻塞”，不能把模拟结果描述为九识验收。
