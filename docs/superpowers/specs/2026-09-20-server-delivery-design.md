# 阶段 2—8 服务端交付设计

2026-09-20。本文承接整体路线图、身份基线和 V2.2 端划分；原需求中日常业务的 PC 位置由 V2.2 覆盖。前后端字段与路径唯一基线为 `docs/api/delivery-contract.md`。本轮只写设计和计划，未实现声明不得写成验证结果。

## 目标、非目标与结构

在现有 Java 17 / Spring Boot 3.5.6 / MyBatis 3.0.5 模块化单体实现真实 MySQL 业务闭环。微信、短信、九识 API/MQTT 可替换模拟；身份会话、准入、预约、订单、批次、控制请求、事件、消息、配置和报表全部持久化。保留现有 health、ready、ApiResponse、Jackson 长整型字符串规则。无 Docker、Redis、微服务、自动派车、远程关门、返航、异步导出中心或独立告警产品。

每个 domain 下 `Controller` 处理输入、`Service` 编排事务、`Mapper` SQL、`Models` DTO/实体、纯规则类负责状态条件。禁止用一个通用 JSON CRUD 服务代替业务约束。MyBatis SQL 放同域 Mapper 注解或对应 XML，只选择一种并保持一致；多表范围查询使用 XML。配置和持久化工具放 config/infrastructure，不让 Controller 直接调用 Mapper。

## 身份、会话和权限

两端采用 opaque Bearer，随机 token 明文只返回一次，数据库 SHA-256 唯一索引；默认 8 小时可配置、无隐式续期。密码 BCrypt，自建 Spring Security filter 每次验证 session、employee.enabled 和当前 grants；不使用默认内存账号或 cookie。密码变更撤销会话；临时密码账号仅允许 me/password/logout。管理员最后一名保护使用数据库锁，避免并发撤销最后可用管理员。

首次管理员 CLI profile `bootstrap-admin`，从环境读取姓名、用户名和初始密码，密码强度至少 12 字符，受控执行，成功不输出密码/令牌；已有管理员时拒绝重复初始化。CLI 不监听 HTTP，事务创建员工、管理员能力、密码哈希，随后退出。管理员给员工配置临时密码必须强制改密。

授权函数 `require(actor, capability, warehouseId)` 选择当次 workspace 对应的 grant；禁止将概览 ALL 与仓库写权限合并。工人查询为 applicantId 或当前已验证 receiverEmployeeId；取货仅 receiverEmployeeId，申请人无默认取货权。管理配置能力独立，管理员无隐式车辆控制。权限与账户停用变更需和敏感业务命令取得相容行锁，保证已经提交的撤权在后续控制前检查生效。

平台身份额外保存 `platform_grant(employee_id,capability,scope)` 和 `platform_grant_warehouse`（V2）。`admin` 是存在平台授权的派生布尔值，不是超级用户绕过开关。人员、主数据、规则、接入、报告查看、报告导出、审计分别授权；每项能力及范围使用同一 grant。全局人员/接入管理要求 ALL，查看报告 ALL 不能扩大导出 A 仓。授权编辑不得提升至操作者不具有的能力或更大范围。硬件格口从既有外部资源同步，管理仅修改本地标签与启停，禁止人为伪造格口号；模拟目录用于本地完整联调。

验证码默认有效 5 分钟、60 秒发送间隔、5 次错误锁定、每号码小时 10 次、IP 小时 30 次；参数配置化。hash 与服务器秘密 HMAC，challenge 单次事务消费，绑定 token 同样短期、单次消费。验证码失败计数事务单独提交，不能随认证异常回滚。绑定手机号唯一；员工准入 phone 与 verifiedPhone 分离，管理员改 phone 不能直接迁移验证权。固定模拟码只有 local/test 且显式环境值，生产启动 fail-fast。

## MySQL 表与迁移

目标 MySQL 8.4，本机若为 9.6 如实记录且目标兼容验证未完成。新建独立数据库不触碰其他项目；沿用既有 `application_metadata`。Flyway V1 使用 `CREATE TABLE IF NOT EXISTS application_metadata` 与幂等初始元数据，现有非空库先核对结构再显式 baseline 0；禁止自动 baseline-on-migrate 掩盖未知表。

| 迁移 | 表、主约束与索引 |
| --- | --- |
| V1__application_metadata.sql | 既有元数据兼容，不改变用户数据 |
| V2__identity.sql | employee、admin_credential(unique username)、verified_phone(unique phone,employee_id)、wechat_binding(unique app_id,openid)、role_grant、grant_warehouse、auth_session(unique token_hash)、sms_challenge、auth_rate_limit、wechat_binding_challenge |
| V3__masterdata.sql | warehouse、stop、warehouse_stop、vehicle(unique external_vehicle_name)、vehicle_stop、compartment(unique vehicle_id,hardware_no)、business_rule(version)、integration_setting(秘密引用) |
| V4__orders.sql | reservation_slot(unique warehouse_id,start_at,end_at)、delivery_order、order_cancellation、favorite_stop(unique employee_id,stop_id)、idempotency_record(unique actor_id,route,key)、business_event、audit_event |
| V5__batches.sql | delivery_batch、batch_order历史、active_order_batch(order_id PK)、order_compartment、active_compartment(compartment_id PK)、load_confirmation |
| V6__dispatch.sql | dispatch_request、vehicle_task(unique provider,dispatch_id)、active_vehicle_task(vehicle_id PK)、control_request、active_vehicle_control(vehicle_id,type PK)、vehicle_snapshot、vehicle_door_snapshot、integration_event(unique provider,event_key)、simulator_scenario |
| V7__tickets_messages.sql | ticket、active_order_ticket(order_id PK)、ticket_note、notification(unique event_id,employee_id)、event_delivery(unique event_id,consumer) |
| V8__report_indexes.sql | orders(warehouse_id,created_at,status)、batch(warehouse_id,created_at)、task(vehicle_id,created_at)、audit(actor_id,occurred_at)、消息(employee_id,read_at,created_at) |

业务主键 VARCHAR(36) UUID，外部 dispatchId VARCHAR(64)，展示订单号单独唯一字段。时间 DATETIME(3) UTC，version BIGINT，状态 VARCHAR 并在 Java 枚举校验；核心关系外键 RESTRICT，不级联删除履历。所有敏感命令一个事务包含业务更新、业务事件和审计。外部网络调用不持有长数据库事务：先创建 PENDING+资源占用，提交，再发送，短事务保存结果；进程在发送前后崩溃均进入待核对，不自动重发。

## 独立状态机

| 对象 | 状态与转换 |
| --- | --- |
| 订单 | PENDING→ACCEPTED/REJECTED；ACCEPTED→READY（装货确认）；READY→IN_TRANSIT（匹配行驶）；IN_TRANSIT→AWAITING_PICKUP（匹配 ATSTOP）；AWAITING_PICKUP→COMPLETED（本人确认）；安全取消审核→CANCELLED，未决取消不改原状态 |
| 取消申请 | PENDING→APPROVED/REJECTED；不映射为车辆任务取消 |
| 批次 | DRAFT→READY→LOCKED→CLOSED；成员/格口改变 READY→DRAFT 且 loadStatus=UNCONFIRMED；LOCKED 禁止拆批 |
| 装货 | UNCONFIRMED→CONFIRMED；订单/车/格口变更强制失效 |
| 派发请求 | PENDING→ACCEPTED/FAILED/UNKNOWN；UNKNOWN 只经核对落定；明确失败才可释放资源并重新确认 |
| 车辆任务 | PLANNING→RUNNING→AT_STOP→RUNNING/FINISHED；明确取消→CANCELLED；不匹配/冲突→RECONCILIATION |
| 控制请求 | PENDING→ACCEPTED/FAILED/UNKNOWN；ACCEPTED 只表达请求受理，实际门/车状态另存 |
| 工单 | OPEN→PROCESSING→CLOSED；关闭结果必填，完全不触发订单和车辆动作 |

已取货订单仍可作为接收人继续出发的关系依据；不能因为完成就丢失当前站控制资格。批次订单不全完成不关闭，任务结束不自动完成订单。发车结果未知锁定批次和车辆；任务不明确时禁止单单取消导致剩余货物无法追踪。

## 并发、幂等和容量

预约先锁 slot 行，条件更新 `used < capacity`，与创建订单同事务；取消/驳回释放容量必须记录已释放标志，保证只释放一次。不存在时段行先 INSERT IGNORE 再 SELECT FOR UPDATE；日期、营业、未来半小时由服务器计算，不信任客户端时间。

乐观版本控制处理受理/工单等普通更新，受影响行数 0 返回 409。组批按排序后的订单 ID 取得行锁，再插 active_order_batch；冲突事务整体回滚。格口占用、同车任务、同车同类控制用唯一活动表保护，不能只依赖 JVM synchronized。幂等表在业务事务首次落库，重复同内容恢复结果，未决请求保持其独立状态。

共享格口默认禁用，配置字段保留但未获得业务确认前服务端拒绝启用；独立格口演示不代表业务最终规则。取消派发后订单需要仓库核对并记录原因；绝不隐式调用整车取消。安全不能判断时保留原状态和未决标记。

## 外部适配及状态融合

接口 `WechatProvider.exchange(code)`、`SmsProvider.send(phone,code)`、`VehicleGateway.dispatch/open/go/cancel/query`。模拟器与真实适配器共用 DTO 与业务入口。真实适配器仅连接配置的预期主机，TLS、连接/读取超时、token 到期缓存、不记录密钥、读接口退避和每车 1 秒限流；写接口超时不重发。九识普通任务接口不传 contacts，避免触发本期未包含的业务短信。

`VehicleEventService.accept(event)` 验证车辆/厂商/任务归属，按独立遥测和业务流 timestamp 去重与判序，保留 receivedAt；旧事件只记履历不覆盖快照。同时间不同内容进入 RECONCILIATION；旧任务不影响新任务。缺少 doorStatus 不刷新门时间，不把 null 视为 CLOSED；MQTT 在线状态与连接状态分离。重连后每车限流查询详情，不能声称补齐丢失历史。

继续出发需要当前任务 ATSTOP、当前点匹配、全站本系统相关订单已取、全部相关门 CLOSED 且新鲜、无活动阻断工单、下一点明确、无未决 GO。与工单创建和取货确认共享当前任务锁防止检查竞态。开门要求明确非空格口与有效接收关系；取消要求明确 speed=0，不把缺失值视为 0。现场自动离站策略不受本系统按钮约束，必须单列实车阻塞。

## 事件、通知、查询与留存

业务事件与业务事务一起提交，持久化投递器使用锁/lease 分批处理；event_delivery 去重，通知按事件+员工去重，多个角色不会重复。收件人必须有当前数据权；消息关联失权返回不可跳转，不泄露对象详情。重启继续投递未完成事件。

概览按订单、批次、任务分别计数，Asia/Shanghai 日期转 UTC `[start,end)`，明细/统计/导出复用同一个权限及条件构造器。Excel 使用 POI 真正 xlsx、字符串单元格、不执行用户公式；同步最多 10000 行。审计记录 actor、workspace、grantId、requestId、before/after、reason、occurredAt，隐私裁剪且不记录密码、验证码、token。

至少保留 31 天；起算点未冻结前禁用自动业务清理并保留所有记录。实现可测试 retention eligibility 纯函数与 dry-run，未结束业务永不候选；正式启用删除须业务确认并完成独立备份恢复。原始遥测留存独立，不删除必要业务事件。

## 验证与交付门槛

单元测试覆盖权限与状态纯函数；MockMvc 验证真实 HTTP 错误和凭据；MySQL 集成测试验证迁移升级、FK、唯一约束、事务回滚和双连接并发，不把 H2 当作数据库验收。MySQL 测试显式 profile `mysql-it` 及专用环境库，目标数据库名必须以 `_test` 结尾，禁止清理普通库。

本地 E2E 通过真实 HTTP+MySQL，外部提供者模拟：创建员工→真实会话→申请→审批→组批→装货→派发→注入到达→两人分别取货→关闭现场门事件→继续出发→工单闭环→消息/报表核对；覆盖撤权、重复、超时、乱序和重启持久化。真实微信、短信、MQTT、现场车辆动作、A13 跳转及目标服务器恢复必须标依赖阻塞，不用模拟替代。

验收矩阵应覆盖原需求 A01–A15（路线图 A01–A13 是缩写，不能漏 A14 消息报表和 A15 性能）。性能条件未约定时记录实际样本，不承诺未测的 3 秒指标。迁移回退以应用兼容回退和备份恢复为主，不修改已应用 migration，不重建业务数据库。
