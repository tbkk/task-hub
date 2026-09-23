# 九识车辆集成契约（GeoBridge 参考）

本文档把当前可从九识 V2.9 文档和 `/Users/qin/Projects/GeoBridge` 源码确认的约定记录下来，作为 Task Hub 后续真实适配器的边界。当前环境继续使用模拟车辆网关；本文不包含任何真实凭据，也不表示已经完成九识或实车验收。

## 1. 资料和适用范围

- 九识开放 API：`docs/design/九识开放api文档V2.9.md`。
- 九识通用推送：`docs/design/九识通用推送文档V2.9.md`。
- 参考实现：`/Users/qin/Projects/GeoBridge/src/main/java/com/geobridge/jiushi`、`.../mqtt`。
- 本文仅约定认证、车辆任务/控制 HTTP 调用和 MQTT 入站状态；短信、九识订单通知和车辆实际安全策略仍由九识平台负责。

## 2. HTTP 认证和通用响应

### 2.1 获取 Token

九识认证接口为 `POST /app/accessToken`，请求头 `Content-Type: application/json`，请求体为：

```json
{"appId":"<由九识提供>","appKey":"<由九识提供>"}
```

环境地址（文档给出的地址）：

- 测试：`http://auth-uat.zelostech.com.cn/app/accessToken`
- 正式：`https://auth.zelostech.com.cn/app/accessToken`

响应外层是 `{success,errorCode,message,data}`；成功时 `data.token` 用作后续请求的 `token` 请求头，`data.expiresAfter` 是分钟数，`data.id` 是应用主键。GeoBridge 对 token 做进程内缓存，在过期前减去安全提前量；上游返回 token 失效、过期或认证失败时清缓存并只重试一次，不能无限重试。token、appKey 不得写入日志。

### 2.2 API 基址和查询

文档给出的网关基址是：

- 测试：`http://gateway-uat.zelostech.com.cn/business-proxy/open-apis`
- 正式：`https://gateway.zelostech.com.cn/business-proxy/open-apis`

除 token 接口外均携带 `token` header，响应沿用上述外层结构。GeoBridge 已按此方式实现：

| 能力 | 方法和路径 | 关键参数 |
| --- | --- | --- |
| 公司列表 | `GET /organizations` | `pageNumber`、`pageSize` |
| 站点列表 | `GET /stations` | `pageNumber`、`pageSize`、可选 `organizationId` |
| 停靠点列表 | `GET /stops` | `pageNumber`、`pageSize`、可选 `stationId`、`includingPassBy` |
| 车辆列表 | `GET /vehicles` | `pageNumber`、`pageSize`、可选 `stationId` |
| 车辆详情 | `GET /vehicle?vehicleName=...` | 车辆名称；文档要求同一车辆约 1 秒最多查询一次 |

车辆详情可包含业务状态、任务 `dispatchId`、当前位置、在线/电量、绑定停靠点和 `goals`。Task Hub 应把九识的 `vehicleName`、停靠点 id、`dispatchId` 作为外部映射字段保存，不能用本地 UUID 直接替代九识 id。

## 3. 车辆控制 HTTP 方式

控制全部通过九识开放 API 的 HTTP 网关完成；MQTT 只接收推送，不用于发送开门或行驶命令。每次调用都要记录本地请求 id、外部动作、车辆、脱敏后的请求摘要、响应状态和原始外部错误分类；不得记录 token、appKey 或完整手机号。

### 3.1 派送任务并出发

对于带订单和格口的业务，优先使用 `POST /vehicle/add_dispatch_order_and_go`（正式文档 URL 为 `https://gateway.zelostech.com.cn/business-proxy/open-apis/vehicle/add_dispatch_order_and_go`；测试为对应 `gateway-uat` 地址）。请求体核心结构：

```json
{
  "vehicleName":"ZL00106",
  "fromStopId":2852,
  "speedLimit":4,
  "toStops":[
    {"stopId":2891,"loadContact":"<手机号>","loadUserName":"装货人",
     "orders":[{"gridNos":[1],"contacts":[{"contact":"<手机号>","userName":"取货人"}]}]}
  ]
}
```

字段是否可省略、订单完整字段以九识文档和实际租户开通能力为准。无订单的普通任务使用 `POST /vehicle/add_dispatch`，体为 `vehicleName`、可选 `fromStopId`、必填 `toStopIds` 数组和可选 `speedLimit`。成功响应包含 `vehicleId`、`vehicleName`、业务状态和 `dispatchId`；常见失败包括车辆已有任务、车辆自动驾驶中、停靠点不存在或未绑定。

### 3.2 出发、开格口、取消

文档定义的动作和请求体如下：

| 动作 | 方法和路径 | 请求体 | 业务前置条件（以九识返回为准） |
| --- | --- | --- | --- |
| 车辆出发 | `POST /vehicle/go` | `{"vehicleName":"..."}` | 车辆在停靠点且有下一目的地 |
| 开格口 | `POST /vehicle/open_box` | `vehicleName`，可选 `gridNos`；空数组/缺省表示全部格口 | 车辆在线；具体安全约束由九识返回 |
| 取消任务 | `POST /vehicle/cancel_dispatch` | `{"vehicleName":"..."}` | 有速度时不允许取消 |

任务取消或控制失败不能按 HTTP 200 推断成功，必须同时检查九识外层 `success`。Task Hub 的 `UNKNOWN` 结果必须保留并通过查询/推送进行对账，不能自动重复发送可能产生实际动作的命令。

### 3.3 通用指令

`POST /vehicle/command` 下发常用指令，参数包括 `vehicleName`、`commandType`、必填操作人 `userId`/`userName`；特殊指令还要传九识提供的、全小写 MD5 形式的 `source`。文档列出的指令包括：

- `BUSINESS_SHUTDOWN`、`RESTART_HARD`、`RESTART_SOFT`；通常要求在线、车速为 0，且不在遥控/升级中。
- `EMERGENCY_STOP`、`RECOVERY`；急停会立即刹车，恢复用于继续自动驾驶，属于高风险动作。
- `BUSINESS_TBOX_SET_UP`、`BUSINESS_ARRIVE`、`BUSINESS_ONE_KEY_SIDE`、`BUSINESS_TASK_RECOVERY`。

Task Hub 第一阶段只把派送、出发、开格口、取消纳入 `VehicleGateway`；通用指令需单独权限、审计和九识提供的 `source` 常量后再开放，不能用模拟 command 直接替代真实安全控制。

## 4. MQTT 入站

### 4.1 连接、认证和订阅

九识通用推送文档要求企业向九识 MQTT server 提供 host、用户名和密码，九识提供 `organizationCode`。文档没有给出统一 broker URI、TLS 端口、证书、客户端 id、保活、QoS 或遗嘱参数；这些必须在租户开通资料中确认，不能从文档猜测。

GeoBridge 的可复用连接策略：Paho MQTT v5 异步客户端；用户名/密码认证；`automaticReconnect=true`、`cleanStart=false`；连接成功（包括重连回调）重新订阅；初次连接失败按固定退避再次尝试；关闭时停止重试并释放客户端。Task Hub 适配器应把 broker、clientId、organizationCode、QoS、重连间隔配置化，凭据放密钥存储或受限配置文件。

订阅主题：

```text
{organizationCode}/vehicle/+/realtime/push
{organizationCode}/vehicle/+/business/push
```

GeoBridge 当前按启用规则中的车辆展开为单车主题，并在规则变化后增删订阅；也可以在确认 ACL 允许后使用 `+` 通配订阅。建议 Task Hub 先按已绑定车辆订阅，避免接收无关车辆数据。

### 4.2 实时推送（1Hz）

实时推送在车辆开机且正常联网时发送，频率约 1Hz。文档字段较多，Task Hub 至少解析并持久化：

| 字段 | 含义/映射 |
| --- | --- |
| `vehicleName`、`vehicleNumber`、`vehicleVin` | 外部车辆标识和车辆资料 |
| `lon`/`lat`、`gcj02Lon`/`gcj02Lat` | WGS84 与 GCJ02 坐标；地图展示使用 GCJ02，不能混用 |
| `heading`、`speed` | 弧度朝向、米/秒速度 |
| `power`、`temperature`、`chargeStatus` | 电量、电池温度、充电状态 |
| `vehicleState`、`chassisState` | 车辆健康/驾驶模式 |
| `vehicleBusinessStatus`、`dispatchId` | 配送状态和九识任务 |
| `timestamp` | Unix 毫秒上报时间 |
| `doorStatus` | 格口号到 `DOOR_CLOSE`/`DOOR_OPEN` 的映射 |
| `currentToTargetMileage`、`globalMileage`、`traveledMileage` | 里程遥测 |

其余灯光、挡位、胎压、冷机和货箱温度字段采用可选字段保存；缺失字段不能被当作零值或关闭状态。

### 4.3 业务状态推送

业务推送只在业务状态变化时发送，主题为 `.../business/push`。核心字段：`vehicleName`、`vehicleBusinessStatus`、`timestamp`、`currentStopId/currentStopName`、`lastStopId/lastStopName`、`dispatchId`。可选 `orderList` 可能包含九识订单号、客户订单号、联系人、停靠点、格口、验证码和订单状态。

状态至少覆盖 `IDLE`、`WITHORDERS`、`ROUTING`、`ONWAY`、`ATSTOP`、`BACK`、`ARRIVEHOME`。`ATSTOP` 表示车辆停在当前停靠点等待用户，不能仅凭实时坐标推断到站。

## 5. 乱序、重复和状态映射

GeoBridge 的处理规则应迁移到 Task Hub 的持久化事件处理器：

1. 以 `(provider, eventKey)` 做幂等去重；若上游没有事件 id，使用规范化主题、车辆、消息类型、`timestamp` 和 payload hash 生成稳定 key，并保留原 payload hash 以识别同 key 冲突。
2. 实时流和业务流分别维护最新时间戳；只有严格更新的时间戳才覆盖对应快照，迟到或重复消息记为已接收但不回滚新状态。
3. 两种消息可乱序到达：实时字段只更新位置、速度、驾驶模式、门状态等遥测；业务字段只更新业务状态、任务和停靠点。合并时不得用实时消息中的旧业务状态覆盖业务流，也不得用业务消息中的缺失遥测清空实时值。
4. `timestamp` 按 Unix 毫秒解析；缺失、非数值、未来异常过大的时间戳进入隔离/告警，不作为新鲜快照。缺失门状态不刷新“门已关闭”判断，缺失速度不改写为 0。
5. 推送到 `ATSTOP` 且 `currentStopId` 与当前任务目的地一致时，才可推进 Task Hub 的到站/待取货状态；`ONWAY`、`BACK` 等状态映射到运输中，具体订单状态仍以业务推送中的 `orderList` 和本地订单为准。
6. 连接中断期间不补造车辆状态；重连后等待新的实时消息，并通过受限频率的车辆详情查询做对账。详情接口同一车辆约 1 秒一次，不能在每条 MQTT 消息后调用。

## 6. 需要九识/租户确认的内容

以下内容在当前两份文档或 GeoBridge 中没有足够依据，适配器上线前必须补齐：

- 实际 MQTT broker URI、端口、TLS/证书、协议版本、ACL、QoS、保活、客户端 id 规则和是否允许通配订阅。
- MQTT 用户名/密码的申请、轮换和失效通知流程。
- `source` 的 MD5 常量值，以及哪些通用指令必须携带它。
- `open_box`、`go`、`cancel_dispatch` 的正式 URL；原文存在换行导致的路径断裂，取消任务正式地址甚至未填写。
- 订单任务 `toStops.orders` 的完整字段、多个格口/多个联系人的约束，以及 Task Hub 订单号与九识 `orderNo` 的映射方式。
- 推送消息是否有 envelope、消息 id、QoS/retain 约定；文档示例是裸 JSON，GeoBridge 也按裸 JSON 反序列化。
- 实时/业务状态的时钟来源、允许时钟偏差、重复消息保留周期及离线判定阈值。
- 九识接口错误码、限流和幂等语义；HTTP 成功是否代表命令已执行，还是仅代表已受理。
- 车辆门状态 key（示例为字符串格口号）与 Task Hub 硬件/格口 id 的绑定规则。

在上述资料确认前，生产配置应拒绝启用真实 provider；只能使用 Task Hub simulator 验证数据库事务、幂等、UNKNOWN 和事件乱序流程。

## 7. 参考实现对照

GeoBridge 的 `JiushiTokenService`、`JiushiVehicleClient`、`MqttVehicleSubscriber`、`VehicleStateStore`、`VehicleRealtimeHandler` 和 `VehicleBusinessHandler` 分别对应本文的认证、查询、连接订阅、双流状态合并和到站去重职责。Task Hub 不复制 GeoBridge 的凭据文件或运行配置；只复用协议边界和验证策略，并把内存状态升级为已有 `integration_event`、`vehicle_snapshot`、`vehicle_door_snapshot` 等持久化模型。
