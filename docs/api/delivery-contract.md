# 全阶段交付 API 契约

日期：2026-09-20。状态：实施基线，非已实现接口声明。依据功能端划分 V2.2、原需求 A01–A15、身份基线和服务端交付设计。路径均为自有服务；管理端不提供日常审批、派发或控制入口。

## 公共协议

- 前缀 `/api`；JSON 响应 `{code:number,message:string,data:T|null}`，成功 `code=0`。HTTP 状态与失败一致；`400` 参数、`401` 会话失效、`403` 当前授权不足、`404` 对象不存在或不在范围、`409` 状态/版本/幂等冲突、`422` 业务前置条件不足、`429` 限频、`503` 外部服务不可用。
- 分页查询 `page=1&pageSize=20`，最大 100；返回 `{items:T[],total:number,page:number,pageSize:number}`。白名单排序；默认订单 `slotStart ASC,createdAt ASC,id ASC`，其他列表 `createdAt DESC,id DESC`。
- 所有 ID 均为字符串，包括九识 `dispatchId`；版本为非负整数。日期 `YYYY-MM-DD`，时间 ISO-8601 带偏移（如 `2026-09-21T08:00:00+08:00`），业务日期使用 `Asia/Shanghai`，数据库 UTC。
- 两端均发送 `Authorization: Bearer <opaqueToken>`；服务端只存 token SHA-256，32 字节安全随机值，默认会话 8 小时且可配置。不使用 Cookie 认证。浏览器 token 只放内存，刷新重新登录；小程序只放会话内存，启动重新认证。响应禁止缓存。退出后 token 立即失效。
- 日常业务请求发送 `X-Workspace: worker|warehouse|dispatch|overview`；这只是角色选择，不是授权。后端重新读取该角色授权及数据范围。管理接口独立校验匹配的 platformGrant，不借用其他 grant 的范围。`admin` 仅表示可进入平台，不代表所有管理权限。
- 创建及控制发送 `Idempotency-Key`（客户端生成 UUID，一次确认重试沿用）；服务端按账号、路由、key 唯一，记录规范化请求哈希和结果。同 key 不同正文返回 `40901`，同正文返回原业务结果。业务幂等记录至少保留 31 天；结果未知不允许靠换 key 绕过对象锁。
- 更新/动作正文带 `expectedVersion`；冲突 `40902`，客户端重新读取详情后再次确认。所有写操作返回最新详情（控制返回独立请求详情）。
- 细分 code：`40101` 会话失效、`40301` 账号停用、`40302` 授权变化、`40901` 幂等冲突、`40902` 版本冲突、`40903` 对象占用、`42201` 时段不可用、`42202` 取货身份不符、`42203` 车辆状态过期、`42204` 控制前置条件不足、`42901` 限频；错误 `data` 可包含 `{fieldErrors:{字段:中文说明},blockers:[{code,message}],currentVersion:number}`，未授权失败不返回对象细节。

## 身份

```ts
type Role = 'worker'|'warehouse'|'dispatch'|'overview';
type Grant = {role:Role; scope:'SELF'|'WAREHOUSES'|'ALL'; warehouseIds:string[]};
type PlatformCapability = 'EMPLOYEE_MANAGE'|'MASTERDATA_MANAGE'|'RULE_MANAGE'|'INTEGRATION_MANAGE'|'REPORT_VIEW'|'REPORT_EXPORT'|'AUDIT_VIEW';
type PlatformGrant = {capability:PlatformCapability;scope:'WAREHOUSES'|'ALL';warehouseIds:string[]};
type Identity = {id:string;name:string;verifiedPhone:string|null;grants:Grant[];platformGrants:PlatformGrant[];admin:boolean;mustChangePassword:boolean};
type Session = {token:string;expiresAt:string;user:Identity};
type WechatExchange = {status:'AUTHENTICATED';session:Session}|{status:'PHONE_REQUIRED';bindingToken:string;expiresAt:string};
```

| 方法/路径 | 请求 | 成功 data / 约束 |
| --- | --- | --- |
| POST `/admin/auth/login` | `{username,password}` | `Session`；统一账号或密码错误，账号/IP 双限频 |
| GET `/identity/me` | 无 | `Identity`；每次验证停用、过期及实时授权 |
| POST `/identity/logout` | 无 | `null`；撤销当前会话，重复退出安全 |
| POST `/identity/password` | `{currentPassword,newPassword}` | `null`；修改后撤销全部会话，重新登录 |
| POST `/mini/auth/wechat` | `{code}` | `WechatExchange`；模拟 code 仅 local/test 提供者接受 |
| POST `/mini/auth/sms` | `{phone,purpose:'LOGIN'|'BIND',bindingToken?:string}` | `{challengeId,retryAfterSeconds,expiresAt}`；不返回验证码 |
| POST `/mini/auth/verify` | `{challengeId,phone,code,bindingToken?:string}` | `Session`；验证码单次消费，BIND 必须验证绑定 token |

短信登录不要求已有微信会话；绑定微信则必须具有服务端生成的短期绑定 token。手机号由管理员录入不代表验证；只有验证码成功后才建立本人验证关系；冲突拒绝自动合并。模拟验证码从本地环境配置加载，不写到版本库或普通日志。

员工 `homeWarehouseId` 为管理员配置的所属仓库，迁移期可空；工人申请只允许该启用仓库，未配置返回空目录并提示联系管理员。`worker` 的 SELF 与空 `warehouseIds` 表示本人订单范围，不承担仓库归属。

## 员工授权委派

`EMPLOYEE_MANAGE` 允许在自己的人员管理范围内给员工授予或移除既有业务角色及对应仓库范围；该管理能力本身不授予操作者日常审批、派车或取货权限。平台能力只能委派操作者已拥有的同项能力及其范围，不能通过员工编辑为自己扩大权限。首管理员由受控初始化授予全厂平台能力，再开通业务员工。

## 管理与主数据

管理资源 CRUD：GET 列表、POST 创建、GET `/{id}`、PUT `/{id}` 更新；停用通过 PUT `enabled:false`，不删除被业务引用的对象。下面所有模型均含 `{id,version,createdAt,updatedAt}`（创建输入不含这些字段，更新输入使用 `expectedVersion`）。

| 路径 | 资源字段 / 查询 |
| --- | --- |
| `/admin/employees` | `{name,phone,homeWarehouseId:string|null,enabled,grants:Grant[],platformGrants:PlatformGrant[]}`；查询 `keyword,enabled,warehouseId,role,phoneVerified,wechatBound`；手机号为准入信息，GET 另返回 `verifiedPhone,wechatBound` |
| GET `/admin/employees/warehouse-options` | `page,pageSize` | `Page<{id,name,enabled}>`；人员管理 ALL 可读，供归属与授权选择，不授予资料写权限 |
| POST `/admin/employees/{id}/credentials` | `{username,temporaryPassword}`；仅重设内部密码，`mustChangePassword=true`，撤销该员工所有管理会话；不回显密码 |
| `/admin/warehouses` | `{name,code,enabled}` |
| `/admin/stops` | `{name,externalStopId,warehouseIds:string[],enabled}`；外部点位只能绑定已有点位 |
| `/admin/vehicles` | `{name,externalVehicleName,warehouseId,enabled,boundStopIds:string[]}` |
| GET `/admin/vehicles/{vehicleId}/compartments` | `{hardwareNo:string,label:string,enabled}`；只读已同步硬件定义，不任意创建格口 |
| PUT `/admin/vehicles/{vehicleId}/compartments/{id}` | `{expectedVersion,label,enabled}`；仅本地标签和启停，不改 hardwareNo |
| GET `/admin/integration-catalog/{resource}` | resource 为 `vehicles` 或 `stops`；分页外部已有资源 `{id,name}` |
| POST `/admin/vehicles/{vehicleId}/compartments/sync` | `{expectedVersion}`；同步已有硬件定义，返回格口列表；活动占用中不删除/改号，冲突 409 |
| GET `/admin/rules/warehouses` | `page,pageSize` | 当前 RULE_MANAGE 范围的仓库选项 |
| GET/PUT `/admin/rules/{warehouseId}` | `{version,businessHours:[{weekday:1,start:'08:00',end:'17:00'}],slotCapacity:number,bookingDays:number,descriptionMaxLength:number,sizeMaxLength:number,remarkMaxLength:number,telemetryMaxAgeSeconds:number,doorMaxAgeSeconds:number,pickupTimeoutMinutes:number,sharedCompartmentEnabled:false}`；PUT 带 `expectedVersion` |
| GET/PUT `/admin/integration-settings` | `{version,provider:'simulator'|'jiushi',baseUrl,organizationCode,miniAppId,enabled,credentialConfigured:boolean}`；写入可带 `credential`，读取永不回显；生产秘密通过环境/秘密文件引用，普通数据库仅保存引用 |
| GET `/admin/integration-status` | `{provider,connected,lastMessageAt,lastErrorCode,checkedAt}`；不暴露地址凭证和内部异常堆栈 |
| GET `/catalog/warehouses` | 当前可申请/有权操作仓库列表 |
| GET `/catalog/stops?warehouseId=…` | 启用、已有绑定且至少一台启用车辆可达的点位列表 |
| GET `/catalog/slots?warehouseId=…&stopId=…&date=…` | `[{id,start,end,remaining,available}]`；半小时未来档，提交仍重新校验 |
| GET `/catalog/rules?warehouseId=…` | 公共文字长度和预约规则，不含接入秘密 |
| GET/PUT/DELETE `/favorites` / `/favorites/{stopId}` | GET 返回点位列表；PUT/DELETE 无正文，幂等；只能收藏已有可用点位 |
| GET `/integration/mini-entry` | `{appId:string|null,available:boolean,mode:'real'|'simulator'}`；限仓库、调度或管理员 |

## 订单、批次、装货

平台权限映射：员工和凭据维护 `EMPLOYEE_MANAGE`，主数据及硬件同步 `MASTERDATA_MANAGE`，规则 `RULE_MANAGE`，接入参数/目录 `INTEGRATION_MANAGE`，报告列表/汇总 `REPORT_VIEW`，Excel `REPORT_EXPORT`，审计 `AUDIT_VIEW`。查看报告与导出分别校验各自 grant 的仓库范围；禁止借用查看 ALL 扩大导出 WAREHOUSES。员工/接入等全局管理要求该能力 scope=ALL；授权编辑者不能授予超出自己能力与范围的授权。首次管理员由受控 CLI 授予全部能力 ALL，其他账号按需授予。

```ts
type OrderStatus = 'PENDING'|'ACCEPTED'|'READY'|'IN_TRANSIT'|'AWAITING_PICKUP'|'COMPLETED'|'REJECTED'|'CANCELLED';
type Cancellation = {id:string;status:'PENDING'|'APPROVED'|'REJECTED';reason:string;result:string|null};
type Order = {id:string;number:string;version:number;warehouseId:string;stopId:string;slotId:string;slotStart:string;slotEnd:string;description:string;size:string;receiverName:string;receiverPhone:string;receiverBound:boolean;remark:string;applicantId:string;status:OrderStatus;batchId:string|null;vehicleId:string|null;compartmentIds:string[];cancellation:Cancellation|null;dispatchPendingReview:boolean;createdAt:string;updatedAt:string;allowedActions:string[]};
type Batch = {id:string;number:string;version:number;warehouseId:string;stopId:string;slotId:string;orderIds:string[];status:'DRAFT'|'READY'|'LOCKED'|'CLOSED';loadStatus:'UNCONFIRMED'|'CONFIRMED';vehicleId:string|null;assignments:{orderId:string;compartmentIds:string[]}[];dispatchRequestId:string|null;taskId:string|null;allowedActions:string[]};
```

工人订单详情仅返回本人申请/接收订单；批次引用不意味着可读取批次其他订单。`allowedActions` 方便呈现，但写入仍实时重新鉴权。

详情聚合补充字段（列表可省略）：Order 增加 `warehouseName,stopName,applicantName,relation:'APPLICANT'|'RECEIVER'|'BOTH'|'STAFF',events:History[],activeTicketId:string|null`；Batch 增加 `warehouseName,stopName,orders:Order[]`（仅仓库/调度范围可读）；VehicleTask 增加 `goals:{index:number,stopId:string,stopName:string,arrivalAt:string|null,departureAt:string|null}[],requests:ControlRequest[],events:History[]`；Ticket 增加 `notes:{id,authorName,text,createdAt}[],events:History[]`。Vehicle 增加 `allowedActions:string[]`。姓名/手机号按当前数据关系脱敏，工人不可通过 batchId 读取同批他人 Order。详情聚合使用同一个作用域，不能绕过单对象权限。

| 方法/路径 | 请求/查询 | 成功 data |
| --- | --- | --- |
| POST `/orders` | `{warehouseId,stopId,slotId,description,size,receiverName,receiverPhone,remark}` | `Order`；接收人账号由服务器按验证手机号解析 |
| GET `/orders` | `status,warehouseId,from,to,keyword,page,pageSize` | `Page<Order>`；工人仅本人申请/接收，仓库/调度按当前角色范围 |
| GET `/orders/{id}` | 无 | `Order` |
| POST `/orders/{id}/cancellations` | `{expectedVersion,reason}` | `Order`；仅申请人，保存请求不直接改变状态 |
| POST `/orders/{id}/review` | `{expectedVersion,decision:'ACCEPT'|'REJECT',reason?:string}` | `Order`；仓库，驳回必须原因 |
| POST `/orders/{id}/cancellations/{cancellationId}/review` | `{expectedVersion,decision:'APPROVE'|'REJECT',reason}` | `Order`；派发前先安全移出并使原批次装货失效；派发后批准需现场核对且不隐式取消车辆任务 |
| GET/POST `/batches` | GET `warehouseId,status,page,pageSize`；POST `{orderIds:string[]}` | `Page<Batch>` / `Batch` |
| GET `/batches/{id}` | 无 | `Batch` |
| PUT `/batches/{id}/orders` | `{expectedVersion,orderIds:string[]}` | `Batch`；全量成员更新，至少一个；锁定后禁止 |
| PUT `/batches/{id}/loading` | `{expectedVersion,vehicleId,assignments:[{orderId,compartmentIds}]}` | `Batch`；完整覆盖订单，全部格口属于该车 |
| POST `/batches/{id}/loading/confirm` | `{expectedVersion,capacityConfirmed:true}` | `Batch`；人工装载确认 |
| POST `/batches/{id}/loading/open` | `{expectedVersion,compartmentIds:string[]}` | `ControlRequest`；只能已分配且非空格口 |

## 派发、状态、取货、车辆控制

```ts
type RequestStatus = 'PENDING'|'ACCEPTED'|'FAILED'|'UNKNOWN';
type ControlRequest = {id:string;type:'LOAD_OPEN'|'PICKUP_OPEN'|'DISPATCH_OPEN'|'GO'|'CANCEL_TASK';status:RequestStatus;vehicleId:string;taskId:string|null;compartmentIds:string[];message:string;createdAt:string;updatedAt:string};
type DispatchRequest = {id:string;batchId:string;vehicleId:string;status:RequestStatus;taskId:string|null;dispatchId:string|null;message:string;createdAt:string};
type Vehicle = {id:string;name:string;warehouseId:string;version:number;online:boolean|null;batteryPercent:number|null;speed:number|null;businessStatus:string|null;taskId:string|null;dispatchId:string|null;currentStopId:string|null;nextStopId:string|null;reportedAt:string|null;receivedAt:string|null;fresh:boolean;doors:{compartmentId:string;status:'OPEN'|'CLOSED'|'UNKNOWN';reportedAt:string|null}[];longitude:number|null;latitude:number|null;distanceToNextStopMeters:number|null;historicalLegMinutes:number|null;blockers:{code:string;message:string}[]};
type VehicleTask = {id:string;vehicleId:string;batchId:string;dispatchId:string;state:'PLANNING'|'RUNNING'|'AT_STOP'|'FINISHED'|'CANCELLED'|'RECONCILIATION';currentStopId:string|null;nextStopId:string|null;createdAt:string;updatedAt:string};
```

| 方法/路径 | 输入 | 成功 data / 规则 |
| --- | --- | --- |
| GET `/vehicles`、GET `/vehicles/{id}` | `warehouseId,page,pageSize` | `Page<Vehicle>` / `Vehicle`；刷新返回持久化快照，外部详情每车最多 1Hz |
| POST `/batches/{id}/dispatch` | `{expectedVersion}` | `DispatchRequest`；采用已装货车辆，不在此静默换车 |
| GET `/dispatch-requests/{id}` | 无 | `DispatchRequest` |
| POST `/dispatch-requests/{id}/reconcile` | 无 | `DispatchRequest`；只读核对外部实际任务，不重发派发 |
| GET `/tasks`、GET `/tasks/{id}` | `warehouseId,state,page,pageSize` | `Page<VehicleTask>` / `VehicleTask` |
| POST `/pickup/scan` | `{qrText:string}` | `{vehicle:Vehicle,orders:Order[],canContinue:boolean,blockers:[{code,message}]}`；仅返回本人可取订单 |
| POST `/orders/{id}/pickup/open` | `{expectedVersion}` | `ControlRequest`；后台推导格口，不接受自由格口 |
| POST `/orders/{id}/pickup/confirm` | `{expectedVersion}` | `Order`；本人接收、匹配新鲜 ATSTOP/任务/目的地，单单完成 |
| POST `/vehicles/{id}/controls/open` | `{taskId,compartmentIds:string[]}` | `ControlRequest`；调度范围和当前任务格口校验 |
| POST `/vehicles/{id}/controls/go` | `{taskId}` | `ControlRequest`；工人本人当前站已完成订单关系或调度授权，全站已取、门关、鲜度、无活动工单、下一点明确 |
| POST `/vehicles/{id}/controls/cancel` | `{taskId,reason}` | `ControlRequest`；仅调度，速度明确为 0，在线新鲜；成功只更新任务，不替代单单取消 |
| GET `/control-requests/{id}` | 无 | `ControlRequest`；按关联仓库/订单授权 |
| POST `/control-requests/{id}/reconcile` | 无 | `ControlRequest`；只读核对，不盲重发 |

受理响应不意味着门已开或车辆已出发；实际状态来自 `Vehicle`。超时返回已保存的 `UNKNOWN` 请求（HTTP 200），客户端展示“待核对”。同车同类未决控制不能通过新 key 重复发送。二维码格式 `taskhub:vehicle:<id>`，只识别对象，不授权。

## 工单、消息、概览、审计

```ts
type Ticket = {id:string;number:string;version:number;orderId:string;warehouseId:string;reporterId:string;description:string;state:'OPEN'|'PROCESSING'|'CLOSED';result:string|null;createdAt:string;updatedAt:string};
type Message = {id:string;title:string;body:string;readAt:string|null;createdAt:string;target:{type:'ORDER'|'BATCH'|'TASK'|'TICKET';id:string;workspace:Role}|null};
type History = {id:string;objectType:string;objectId:string;action:string;actorName:string;workspace:string;occurredAt:string;summary:string;before:object|null;after:object|null;reason:string|null};
```

| 方法/路径 | 输入 | data |
| --- | --- | --- |
| POST `/tickets` | `{orderId,description}` | `Ticket`；同单已有活动工单返回现有工单 |
| GET `/tickets`、GET `/tickets/{id}` | `state,warehouseId,page,pageSize` | `Page<Ticket>` / `Ticket`；工人仅本人提交 |
| POST `/tickets/{id}/accept` | `{expectedVersion}` | `Ticket` |
| POST `/tickets/{id}/notes` | `{expectedVersion,text}` | `Ticket`；仓库处理说明写履历 |
| POST `/tickets/{id}/close` | `{expectedVersion,result}` | `Ticket`；非空结果，不修改订单/车辆 |
| GET `/messages` | `unreadOnly,page,pageSize` | `Page<Message>` |
| GET `/messages/unread-count` | 无 | `{count:number}` |
| GET `/messages/{id}` | 无 | `Message`；账号归属校验；关联对象失权时 target=null |
| PUT `/messages/{id}/read` | 无 | `Message`；幂等 |
| GET `/overview` | `date,warehouseId` | `{date,orderCounts:Record<OrderStatus,number>,batchCounts:Record<string,number>,taskCounts:Record<string,number>,openTickets:number}` |
| GET `/history` | `objectType,objectId,from,to,page,pageSize` | `Page<History>`；按业务范围、工人隐私裁剪 |
| POST `/history/corrections` | `{objectType:'ORDER',objectId,expectedVersion,field:'description'|'size'|'remark',value,reason}` | `History`；仓库范围，保留旧值；禁止修改接收关系、状态、任务和取货事实 |
| GET `/admin/reports/orders` | `from,to,warehouseId,vehicleId,status,page,pageSize` | `Page<Order>`；只读、管理授权 |
| GET `/admin/reports/summary` | `from,to,warehouseId,vehicleId` | `{orderCounts,batchCounts,taskCounts,openTickets}`；与明细相同过滤口径 |
| GET `/admin/reports/orders/export` | 同明细过滤 | Excel 文件，最多 10000 行，超出 422 提示缩小区间；记录导出审计，文本单元格防公式注入 |
| GET `/admin/audits` | `from,to,actorId,action,page,pageSize` | `Page<History>`；配置、登录、授权、导出、接入变更 |

## 本地模拟器与生产边界

`POST /api/dev/simulator/events` 仅 local/test、ADMIN、显式 `SIMULATOR_ENABLED=true`：`{eventId,vehicleId,dispatchId,reportedAt,businessStatus,currentStopId,nextStopId,online,speed,doors}`；走相同持久化事件处理器，支持重复、乱序、过期、门未知。`PUT /api/dev/simulator/scenario` 输入 `{operation:'DISPATCH'|'OPEN'|'GO'|'CANCEL',outcome:'ACCEPTED'|'FAILED'|'TIMEOUT'}`；场景保存在 MySQL，不用内存业务仓库。生产 profile 不注册上述 controller，启动检查拒绝 mock 身份、短信和车辆模式。实际微信/短信/车辆凭证未到位仅完成适配器边界和模拟验证，不标记真实接入通过。

## 仓库装货点绑定补充（S13派发前置）

GET/PUT `/admin/warehouses/{id}/loading-stop` 独立校验 `MASTERDATA_MANAGE` 对应仓库范围。GET 未配置返回 `data:null`；PUT `{stopId,expectedVersion}`，初次版本为0，成功返回 `{id:warehouseId,stopId,version}`，首次保存版本1。点位必须已关联本仓且启用；存在活动批次禁止更换既有装货点。绑定点位停用或变更关联须先解除其使用（改绑）。派发必须明确读取该绑定，并复核车辆新鲜位置；不能从目的地列表猜测装货点。

工单字段长度：description最多2000字；处理说明text及关闭result最多1000字，与审计原因字段一致。已有活动工单仅在当前账号可见时返回原工单，其他提交者收到409提示联系仓库，不泄漏他人反馈正文。
