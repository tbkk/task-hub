# 阶段 2—8 服务端交付实施计划

> **面向 agent 执行者：** 必须使用 `superpowers:executing-plans` 逐任务执行；如用户选择多 agent 执行，则使用 `superpowers:subagent-driven-development`。步骤使用复选框跟踪。

**目标：** 以真实 MySQL 和服务端会话交付阶段 2—7 完整业务，完成阶段 8 可在本地验证的适配器、恢复及交付准备，真实外部验收单独记录阻塞。

**架构：** 沿用领域化模块单体、薄 Controller、事务 Service 和 MyBatis Mapper；外部微信、短信、车辆替换为可控模拟提供者，业务存储不可模拟。安全条件在服务端事务中重新校验，车辆请求受理与实际状态分离。

**技术栈：** Java 17、Spring Boot 3.5.6、MyBatis 3.0.5、MySQL 8.4、Flyway、JUnit 5、MockMvc、Apache POI。

## 全局约束

- 当前 `feature/base` 继续工作；用户未授权提交、推送、合并，不执行这些操作。每个任务结束做本地 diff review，获得提交授权后才分别提交。
- 中文文档；依据 `docs/superpowers/specs/2026-09-20-server-delivery-design.md` 与 `docs/api/delivery-contract.md`，接口命名以契约为准。
- 无 Docker；本机 9.6 测试须如实标记，发布前另验目标 8.4。现有用户改动不得覆盖。
- API ID 为字符串、时间 ISO-8601、Asia/Shanghai 业务日；跨角色范围不能混用。接口校验不得只依赖客户端。
- 原需求 A01–A15 全覆盖；真实微信/短信/车辆控制和实机跳转没有资源不得宣称通过。
- 下面代码块给出关键契约、失败断言与必须保留的 SQL/算法；每项实现按列出的文件拆分，禁止合并成一个通用业务 Controller。执行时测试先于对应生产实现。
- 每个任务步骤可继续拆成 2—5 分钟动作：先建单个测试方法→运行红灯→实现一个规则或查询→运行绿灯→检查该任务 diff；不一次编写全部模块再测试。

## 文件职责与依赖

`server/src/main/java/com/taskhub/` 为下表生产根；`server/src/test/java/com/taskhub/` 为测试根。任务内路径均从仓库根计算。

| 目录/文件 | 职责 |
| --- | --- |
| api/ApiException.java、ApiExceptionHandler.java、PageResponse.java | 统一真实 HTTP 错误、字段错误、分页 |
| config/SecurityConfig.java、BearerAuthenticationFilter.java | 仅白名单匿名、会话过滤、无 Cookie |
| domain/identity/ | 会话、密码、准入、逐角色授权、短信/微信绑定 |
| domain/masterdata/ | 员工外的仓库、点位、车辆格口档案与规则 |
| domain/order/、domain/batch/ | 预约、订单、取消、收藏、批次与装货 |
| domain/dispatch/、domain/vehicle/ | 请求、任务、控制前置规则、持久化快照 |
| domain/integration/ | 提供者接口、模拟器、真实协议适配、事件融合 |
| domain/ticket/、domain/notification/ | 独立工单与持久化消息投递 |
| domain/report/、domain/audit/ | 同范围报表、真实 Excel、不可覆盖的履历 |
| infrastructure/IdempotencyService.java | 请求哈希、幂等记录和重放 |
| resources/db/migration/ | V1—V8 有序迁移，不改已应用脚本 |
| test/support/MySqlIntegrationTest.java | 专用真实数据库测试基类，禁止非 `_test` 数据库 |

依赖顺序：S01→S02→S03→S04→S05→S06/S07→S08→S09→S10→S11→S12→S13→S14→S15→S16→S17→S18→S19→S20→S21。前后端按身份、主数据、订单、批次、派发、工单、概览各切片联调，不等所有后端写完。

## S01：真实 MySQL 迁移和 API 失败基础

**文件：** 修改 `server/pom.xml`、`server/src/main/resources/application.yml`、`server/src/test/resources/application-test.yml`；创建 `server/src/main/resources/db/migration/V1__application_metadata.sql`、`server/src/main/java/com/taskhub/api/ApiException.java`、`server/src/main/java/com/taskhub/api/ApiExceptionHandler.java`、`server/src/main/java/com/taskhub/api/PageResponse.java`、`server/src/test/java/com/taskhub/support/MySqlIntegrationTest.java`、`server/src/test/java/com/taskhub/MigrationIT.java`、`server/src/test/resources/application-mysql-it.yml`。

**接口：** 产出 `ApiException(int httpStatus,int code,String message)`、`PageResponse<T>(List<T> items,long total,int page,int pageSize)`；保留现有 `ApiResponse<T>`。

- [ ] 写 `MigrationIT`：在专用空库运行 Flyway 后断言 version=1 和 health/ready；关闭重连再迁移无重复数据；对已有元数据库走显式 baseline 0 升级，保留 sentinel 行。

```java
assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success=1", Integer.class)).isEqualTo(1);
assertThat(jdbc.queryForObject("select meta_value from application_metadata where meta_key='schema_version'", String.class)).isEqualTo("1");
```

- [ ] 从 `server/` 运行 `mvn -Pmysql-it -Dtest=MigrationIT test`，配置尚未增加时应失败，确认不是误连其他库。测试基类读取 JDBC URL 并拒绝数据库名不以 `_test` 结尾。
- [ ] 增加 Boot 管理版本的 `flyway-core`、`flyway-mysql`、validation starter；mysql-it profile 包含 `**/*IT.java` 并由环境 `MYSQL_TEST_URL/USER/PASSWORD` 提供连接。默认 test 禁用 Flyway 保留既有 H2 骨架测试；业务迁移验收只在 MySQL 执行。V1 复用 `database/init/001_app_metadata.sql` 的 meta_key/meta_value/updated_at 字段，保留原值，用 INSERT IGNORE 初始化。
- [ ] `mvn test`、`mvn -Pmysql-it -Dtest=MigrationIT test`；预期旧测试和真实迁移通过，记录实际 DB 版本与库名（不记密码）。
- [ ] 修改 `server/src/test/java/com/taskhub/DatabaseFreeContractTest.java` 的 SpringBootTest properties 增加 `spring.flyway.enabled=false`，保留数据库不可用 health/ready 测试；正常业务启动迁移失败应停止，不用此测试开关绕过生产迁移。
- [ ] 检查 diff，记录迁移升级与备份恢复入口，无提交授权不提交。

## S02：会话、密码登录和受控首管理员

**文件：** 创建 `server/src/main/resources/db/migration/V2__identity.sql`；`server/src/main/java/com/taskhub/domain/identity/IdentityModels.java`、`IdentityMapper.java`、`SessionService.java`、`AdminAuthService.java`、`IdentityController.java`、`AdminAuthController.java`、`BootstrapAdmin.java`；`server/src/main/java/com/taskhub/config/BearerAuthenticationFilter.java`；修改 `SecurityConfig.java`；测试 `server/src/test/java/com/taskhub/identity/AdminAuthIT.java`。

**接口：** `SessionService.issue(String employeeId,String client):Session`、`authenticate(String token):Actor`、`revoke(String token):void`；`Actor(employeeId,workspace,admin,mustChangePassword)`；登录输出契约 Session。数据库表按设计 V2 建立，用户名 unique、token_hash unique、session.expires_at/revoked_at。

- [ ] 写真实 HTTP 登录/退出测试与临时密码限制：

```java
mvc.perform(post("/api/admin/auth/login").contentType("application/json")
  .content("{\"username\":\"absent\",\"password\":\"invalid\"}"))
  .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.data").isEmpty());
```

- [ ] `mvn -Pmysql-it -Dtest=AdminAuthIT test`，预期新登录契约未实现失败。
- [ ] 实现 BCrypt 密码、随机 32-byte token、SHA-256 存储、8 小时默认 TTL、每次查员工启用状态。配置 `SessionCreationPolicy.STATELESS`，只放行 health/ready 和三条 mini、admin 登录接口，其余 authenticated；不接受 query token。

```java
byte[] raw = new byte[32];
new java.security.SecureRandom().nextBytes(raw);
String token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
String tokenHash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
```

- [ ] 增加 `bootstrap-admin` CLI：`WebApplicationType.NONE`、读取 `BOOTSTRAP_ADMIN_USERNAME/PASSWORD/NAME`；锁初始化标记，已有管理员拒绝；不在代码/日志记录密码。密码更换撤销会话，临时密码仅开放 me/password/logout。
- [ ] 运行 `mvn -Pmysql-it -Dtest=AdminAuthIT test`；验证正确密码、错误密码、停用、过期、退出重放、修改密码旧 token、并发初始化只成功一次；review 秘密日志与默认账号。

## S03：逐角色授权与员工管理

**文件：** 创建 `server/src/main/java/com/taskhub/domain/identity/AuthorizationService.java`、`EmployeeService.java`、`EmployeeController.java`；测试 `server/src/test/java/com/taskhub/identity/AuthorizationIT.java`。

**接口：** `AuthorizationService.require(Actor actor,String capability,String warehouseId):Grant`、`EmployeeService.update(String id,EmployeeUpdate input,Actor actor):Identity`；`EmployeeUpdate` 使用契约 name/phone/enabled/grants/platformGrants/expectedVersion。V2 追加 platform_grant 与 platform_grant_warehouse；admin 仅平台授权派生标记，报告查看/导出分别鉴权。

- [ ] 写“仓库 A 写权限 + overview ALL 仍不能受理 B”“撤角色保留另角色”“停用所有会话失效”“非管理员不可调用 admin API”用例。最关键断言：

```java
assertThatThrownBy(() -> authorization.require(actorWithWarehouseAAndOverviewAll, "ORDER_REVIEW", "warehouse-b"))
  .isInstanceOf(ApiException.class).hasMessageContaining("授权");
```

- [ ] `mvn -Pmysql-it -Dtest=AuthorizationIT test` 红灯。
- [ ] 授权 SQL 从当前角色 scope 联结 grant_warehouse，不拼接其他角色；员工更新使用 expectedVersion；管理员临时密码重设哈希并撤销管理会话；最后一个管理员保护锁定管理能力记录。

```sql
SELECT g.id FROM role_grant g LEFT JOIN grant_warehouse w ON w.grant_id=g.id
WHERE g.employee_id=#{employeeId} AND g.role=#{workspace}
AND (g.scope='ALL' OR w.warehouse_id=#{warehouseId})
```

- [ ] 运行同测试，另验证管理员改 phone 不修改 verified_phone；冲突绑定不自动合并。同步 me DTO 与两端授权适配器。
- [ ] 验证 REPORT_VIEW=ALL 与 REPORT_EXPORT=A 只能导出 A，只有查看能力不可导出；编辑授权不能提升到操作者没有的能力或范围。
- [ ] 本地 review 确认每个列表、详情和动作均使用当前角色而非前端隐藏按钮。

## S04：微信/短信提供者与真实会话绑定

**文件：** 创建 `server/src/main/java/com/taskhub/domain/integration/WechatProvider.java`、`SmsProvider.java`、`MockWechatProvider.java`、`MockSmsProvider.java`；`server/src/main/java/com/taskhub/domain/identity/MiniAuthService.java`、`MiniAuthController.java`、`SmsChallengeService.java`；测试 `server/src/test/java/com/taskhub/identity/MiniAuthIT.java`。

**接口：** `WechatProvider.exchange(String code):WechatIdentity(appId,openid)`；`SmsProvider.send(String phone,String code):void`；`MiniAuthService.exchange/verify` 输出契约 WechatExchange/Session。

- [ ] 测试相同手机号密码端和短信端落到同一 employee，验证码重放/过期/第 6 次失败被拒绝，绑定 token 不能跨手机号冒用，模拟提供者在 prod 无法启动。

```sql
UPDATE sms_challenge SET consumed_at=UTC_TIMESTAMP(3)
WHERE id=#{id} AND consumed_at IS NULL AND expires_at>UTC_TIMESTAMP(3) AND attempts<5
```

- [ ] `mvn -Pmysql-it -Dtest=MiniAuthIT test` 红灯；实现上述单次消费与发送限频行锁，失败次数独立事务提交。手机号按大陆手机号基线验证，区号规范化统一，不用不同字符串逃过唯一约束。
- [ ] 短信登录无 bindingToken；微信绑定必须匹配有效 token 和消费后的验证手机号。mock code 从 local 环境取，API 不回传，不使用前端自选员工 ID 来建立正式会话。
- [ ] 运行同测试，刷新服务进程后未过期挑战/会话仍可正确检查；review 确认没有内存身份库。

## S05：主数据、点位可达与版本化规则

**文件：** 创建 `server/src/main/resources/db/migration/V3__masterdata.sql`；`server/src/main/java/com/taskhub/domain/masterdata/MasterdataModels.java`、`MasterdataMapper.java`、`MasterdataService.java`、`MasterdataController.java`、`RuleService.java`、`CatalogController.java`；测试 `server/src/test/java/com/taskhub/masterdata/MasterdataIT.java`。

**接口：** 四类 `/admin/warehouses|stops|vehicles|vehicles/{id}/compartments` 与 `/admin/rules/{warehouseId}`；catalog 只返回已启用且已有车辆绑定的可达点位。

- [ ] 写重复车辆 external name、同车重复 hardwareNo、跨车格口、不可达点位不出现在 catalog、停用保留历史引用、旧版本更新 409 用例。

```java
mvc.perform(get("/api/catalog/stops").param("warehouseId", "warehouse-a")
  .header("Authorization", bearer).header("X-Workspace", "worker"))
  .andExpect(status().isOk()).andExpect(jsonPath("$.data[?(@.id=='unreachable')]").isEmpty());
```

- [ ] `mvn -Pmysql-it -Dtest=MasterdataIT test` 红灯；建立 V3 外键/unique，仓库停用不 cascade 删除业务；规则版本更新必须 `WHERE version=#{expectedVersion}`。
- [ ] 配置化营业时段、半小时档、容量、长度、鲜度，演示参数仅 local 配置；共享格口 false，在确认前拒绝 true；前端只读配置由 catalog 输出。
- [ ] 硬件格口从已有资源同步，增加契约 catalog/sync 接口；本地读取持久化模拟硬件目录，任意新增硬件号拒绝；PUT 仅 label/enabled，活动占用定义冲突返回 409。
- [ ] 同测试绿灯，review 真实控制凭证不得出现在档案模型。

## S06：业务事件、审计及通用幂等

**文件：** 创建 `server/src/main/resources/db/migration/V4__orders.sql`（同时建立该阶段订单、预约、收藏表）；`server/src/main/java/com/taskhub/infrastructure/IdempotencyService.java`、`IdempotencyMapper.java`；`server/src/main/java/com/taskhub/domain/audit/AuditService.java`、`AuditMapper.java`；`server/src/main/java/com/taskhub/domain/notification/BusinessEventService.java`、`BusinessEventMapper.java`；测试 `server/src/test/java/com/taskhub/IdempotencyIT.java`。

**接口：** `IdempotencyService.execute(Actor,String route,String key,String requestHash,Supplier<T>):T`；`AuditService.record(Actor,String objectType,String objectId,String action,Object before,Object after,String reason):void`；`BusinessEventService.append(String type,String aggregateId,Object payload):String`。

- [ ] 写两个事务同 key 只生成一次业务对象，不同 payload 返回 409，事务失败不留下已完成幂等结果，重启同 key 返回相同对象 ID。

```sql
INSERT INTO idempotency_record(id,actor_id,route,idempotency_key,request_hash,status)
VALUES(#{id},#{actorId},#{route},#{key},#{hash},'PENDING')
```

- [ ] `mvn -Pmysql-it -Dtest=IdempotencyIT test` 红灯；实现 unique(actor,route,key)、规范化 DTO 哈希、保存 response/ref ID；重复请求从持久化结果读取，未决控制不能重新调用外部。
- [ ] 业务事件、审计、主业务必须同事务；登录失败安全审计独立事务，审计过滤密码/token/code 字段。
- [ ] 同测试绿灯，review rollback 与并发唯一键异常转业务错误。

## S07：半小时预约与收藏

**文件：** 创建 `server/src/main/java/com/taskhub/domain/order/ReservationService.java`、`ReservationMapper.java`、`FavoriteService.java`、`FavoriteController.java`；测试 `server/src/test/java/com/taskhub/order/ReservationIT.java`。

**接口：** `ReservationService.listSlots(String warehouseId,String stopId,LocalDate date):List<Slot>`、`reserve(String slotId):void`、`release(String orderId):void`；`Slot(id,start,end,remaining,available)`。

- [ ] 固定 Clock 测试过去档、非半小时、非营业日、点位停用、容量 1 的两事务抢占只有一次成功；收藏 inactive 点位拒绝。

```sql
UPDATE reservation_slot SET used=used+1 WHERE id=#{id} AND used<capacity AND start_at>UTC_TIMESTAMP(3)
```

- [ ] `mvn -Pmysql-it -Dtest=ReservationIT test` 红灯；新档 INSERT IGNORE 后锁行，提交订单时重新判断规则/可达/容量；release 使用订单 reservation_released 标志保证只释放一次。
- [ ] 收藏 unique(employee,stop)，PUT/DELETE 幂等；无自由地址创建接口。
- [ ] 同测试绿灯，用两连接并发验证容量，不能用串行单测冒充。

## S08：申请、本人订单和独立取消申请

**文件：** 创建 `server/src/main/java/com/taskhub/domain/order/OrderModels.java`、`OrderMapper.java`、`OrderService.java`、`OrderController.java`、`OrderStateMachine.java`；测试 `server/src/test/java/com/taskhub/order/OrderIT.java`。

**接口：** 契约 POST/GET `/orders` 与取消申请；`OrderService.create(CreateOrder,Actor):Order`、`requestCancellation(String id,CancellationInput,Actor):Order`。

- [ ] 写本人申请/接收查询、第三人 404、未绑定接收人可保存但不能取货、空白描述、重复请求只一单、取消请求不改订单状态。

```java
assertThat(orderAfterCancelRequest.status()).isEqualTo("PENDING");
assertThat(orderAfterCancelRequest.cancellation().status()).isEqualTo("PENDING");
assertThat(orderAfterCancelRequest.id()).isEqualTo(createdOrder.id());
```

- [ ] `mvn -Pmysql-it -Dtest=OrderIT test` 红灯；创建事务校验长度、电话、时段、点位、容量，接收身份从 verified_phone 查，保存文字快照和关系；生成唯一可读 number。
- [ ] 所有对象查询加入本人关系/当前角色 warehouse predicate；工人 DTO 不包含同批其他人信息。取消仅申请人且未终态，已有活动取消返回原记录。
- [ ] 同测试绿灯，联调小程序真实 server mode 后重启服务器确认订单仍存在。

## S09：仓库受理、驳回与取消处理

**文件：** 创建 `server/src/main/java/com/taskhub/domain/order/WarehouseOrderService.java`、`WarehouseOrderController.java`；扩展 `OrderMapper.java`；测试 `server/src/test/java/com/taskhub/order/WarehouseOrderIT.java`。

**接口：** `/orders/{id}/review`、`/orders/{id}/cancellations/{id}/review`；消费 AuthorizationService 与事件服务，产出最新 Order。

- [ ] 测试两人同时审批只有一次成功、REJECT 原因空白 400、跨仓 404/403、驳回容量只释放一次、取消申请拒绝保留原业务状态。

```sql
UPDATE delivery_order SET status=#{newStatus},version=version+1
WHERE id=#{id} AND version=#{expectedVersion} AND status='PENDING'
```

- [ ] `mvn -Pmysql-it -Dtest=WarehouseOrderIT test` 红灯；行数 0 返回最新版本冲突，不能覆盖他人结果；取消批准事务调用批次安全移出规则，派发未决保留待处理而非强制取消。
- [ ] 已派发单单取消只做经核对的业务调整，不调用 VehicleGateway.cancel；记录原因、操作者、事件。
- [ ] 同测试绿灯，review 取消与整车取消无耦合。

## S10：同仓同点同时段组批

**文件：** 创建 `server/src/main/resources/db/migration/V5__batches.sql`；`server/src/main/java/com/taskhub/domain/batch/BatchModels.java`、`BatchMapper.java`、`BatchService.java`、`BatchController.java`；测试 `server/src/test/java/com/taskhub/batch/BatchIT.java`。

**接口：** `BatchService.create(List<String> orderIds,Actor):Batch`、`replaceOrders(String id,long version,List<String>,Actor):Batch`；订单状态 ACCEPTED 才可入批。

- [ ] 混仓/混点/混时段拒绝、重复 order ID 拒绝、两个批次同时抢同订单只成功一次、LOCKED 禁止编辑、移出后装货失效测试。

```sql
INSERT INTO active_order_batch(order_id,batch_id) VALUES(#{orderId},#{batchId})
```

- [ ] `mvn -Pmysql-it -Dtest=BatchIT test` 红灯；按订单 ID 排序锁定防死锁，再比较仓/点/时段、成员全部可用，active_order_batch PK 作为最后防线。
- [ ] 修改成员保留历史 batch_order，释放活动关联，状态回 DRAFT，清除确认并释放不再使用格口；至少一成员，全部成员移除需显式关闭空批处理且写审计，不静默留无效 READY。
- [ ] 同测试绿灯，review 历史关联仍可查询。

## S11：车辆格口分配与装货确认

**文件：** 创建 `server/src/main/java/com/taskhub/domain/batch/LoadingService.java`、`LoadingController.java`；扩展 `BatchMapper.java`；测试 `server/src/test/java/com/taskhub/batch/LoadingIT.java`。

**接口：** `assign(String batchId,LoadingInput,Actor):Batch`、`confirm(String batchId,long expectedVersion,boolean capacityConfirmed,Actor):Batch`；loading/open 下一任务消费 VehicleGateway。

- [ ] 测试空格口、跨车格口、遗漏订单、重复独立格口、他批占用、未人工确认容量、换车后确认失效。

```java
if (input.assignments().stream().anyMatch(a -> a.compartmentIds().isEmpty()))
    throw new ApiException(422, 42204, "每条订单必须明确格口");
```

- [ ] `mvn -Pmysql-it -Dtest=LoadingIT test` 红灯；同事务锁 batch/vehicle/compartment，active_compartment unique 防双占；硬件格口禁用不可分配。
- [ ] load confirmation 保存人员、时间、版本与 assignments 哈希；成员/车/格口改变使旧确认失效；确认后订单 READY，不代表已派发。
- [ ] 同测试绿灯，review 不从文字货物大小自动计算容量。

## S12：车辆接口与可控持久化模拟器

**文件：** 创建 `server/src/main/resources/db/migration/V6__dispatch.sql`；`server/src/main/java/com/taskhub/domain/integration/VehicleGateway.java`、`SimulatorVehicleGateway.java`、`SimulatorController.java`、`SimulatorMapper.java`；`server/src/main/java/com/taskhub/domain/vehicle/VehicleModels.java`；测试 `server/src/test/java/com/taskhub/integration/SimulatorIT.java`。

**接口：** `dispatch(DispatchCommand):GatewayResult`、`open(OpenCommand):GatewayResult`、`go(TaskCommand):GatewayResult`、`cancel(TaskCommand):GatewayResult`、`query(String vehicleId):VehicleSnapshot`；`GatewayResult(status,dispatchId,message)`。

```java
public enum GatewayStatus { ACCEPTED, FAILED, UNKNOWN }
public record OpenCommand(String requestId,String vehicleId,String dispatchId,List<String> hardwareNos) {}
public record TaskCommand(String requestId,String vehicleId,String dispatchId) {}
public record DispatchCommand(String requestId,String vehicleId,String fromStopId,List<String> goalStopIds) {}
```

- [ ] 写 scenario ACCEPTED/FAILED/TIMEOUT 三种行为；空 hardwareNos 无论 mock/real 都拒绝；prod 没有 dev endpoint；服务重启保留模拟事件/场景。
- [ ] `mvn -Pmysql-it -Dtest=SimulatorIT test` 红灯；模拟只替换外部结果，车辆快照/任务/订单仍由真实 Mapper 保存。TIMEOUT 映射 UNKNOWN，不自动标记实际开门。
- [ ] 实现 local/test 显式开关和 ADMIN 校验，生产启动拒绝模拟身份/车辆；模拟场景通过契约 dev endpoints 控制。
- [ ] 同测试绿灯，review 未调用真实车辆网络。

## S13：派发请求、任务与结果未知

**文件：** 创建 `server/src/main/java/com/taskhub/domain/dispatch/DispatchModels.java`、`DispatchMapper.java`、`DispatchService.java`、`DispatchController.java`、`DispatchReconciliationService.java`；测试 `server/src/test/java/com/taskhub/dispatch/DispatchIT.java`。

**接口：** `dispatch(String batchId,long version,Actor):DispatchRequest`、`reconcile(String requestId,Actor):DispatchRequest`；普通任务 fromStopId 从实际仓库已绑定装货点推导，若无法唯一识别则阻止派发；goalStopIds 必须包含批次 stop 且已绑定。

- [ ] 测试同车两个批次并发、旧快照、车已有任务、缺失装货点、未确认装货、TIMEOUT 不重发、PLANNING 不误写 IN_TRANSIT。

```sql
INSERT INTO active_vehicle_task(vehicle_id,dispatch_request_id) VALUES(#{vehicleId},#{requestId})
```

- [ ] `mvn -Pmysql-it -Dtest=DispatchIT test` 红灯；短事务保存 PENDING+batch LOCKED+车辆占用；提交后调用 gateway，再事务写 ACCEPTED/FAILED/UNKNOWN。进程恢复扫描 PENDING 标记需核对，不能自动补发。
- [ ] ACCEPTED 保存字符串 dispatchId 和独立 PLANNING task；UNKNOWN 锁批锁车；明确失败才释放并恢复 READY。核对需要任务目标、时间和车辆多条件，不仅“车有任务”；不确定保留 UNKNOWN。
- [ ] 同测试绿灯，加两线程不同 key 同车断言 gateway 仅调用一次；review 网络调用不在长事务内。

## S14：事件判序、快照及订单配送进度

**文件：** 创建 `server/src/main/java/com/taskhub/domain/integration/VehicleEvent.java`、`VehicleEventService.java`、`VehicleEventMapper.java`；`server/src/main/java/com/taskhub/domain/vehicle/VehicleQueryService.java`、`VehicleController.java`；测试 `server/src/test/java/com/taskhub/integration/VehicleEventIT.java`。

**接口：** `VehicleEventService.accept(VehicleEvent):void`；契约 GET vehicles、tasks；VehicleEvent 包含 provider/eventId/vehicleId/dispatchId/reportedAt/receivedAt/businessStatus/currentStopId/nextStopId/doors。

- [ ] 同事件两次只一条 event/通知源；旧时间不覆盖、旧 dispatch 不污染、同 timestamp 不同值核对、缺失门不刷新门时间、ONWAY 才进配送、正确 ATSTOP 才待取货测试。

```sql
UPDATE vehicle_snapshot SET business_status=#{status},reported_at=#{reportedAt}
WHERE vehicle_id=#{vehicleId} AND (reported_at IS NULL OR reported_at<#{reportedAt})
```

- [ ] `mvn -Pmysql-it -Dtest=VehicleEventIT test` 红灯；去重、业务流与遥测独立排序，订单转换事务与事件 append 原子；null speed/door 不作 0/关门。
- [ ] 当前 snapshot 持久化；查询返回 fresh 和具体 blocker；历史整段分钟字段 `historicalLegMinutes`，不叫 ETA。重连 query 受每车 1 秒限流。
- [ ] 同测试绿灯，review currentGoalIndex/stopId 对应厂商定义，不只凭经纬度判到达。

## S15：扫码身份、指定格口与单单取货

**文件：** 创建 `server/src/main/java/com/taskhub/domain/order/PickupService.java`、`PickupController.java`；`server/src/main/java/com/taskhub/domain/vehicle/ControlModels.java`、`ControlMapper.java`、`ControlRequestService.java`；测试 `server/src/test/java/com/taskhub/order/PickupIT.java`。

**接口：** `scan(String qrText,Actor):PickupScan`、`open(String orderId,long version,Actor):ControlRequest`、`confirm(String orderId,long version,Actor):Order`。

- [ ] 申请人非接收人拒绝、未绑定接收人拒绝、另一车拒绝、非 ATSTOP 拒绝、门未知展示不伪造、两人分别完成不整批完成。

```java
if (!actor.employeeId().equals(order.receiverEmployeeId()))
    throw new ApiException(422, 42202, "仅指定接收人可取货");
if (hardwareNos.isEmpty()) throw new ApiException(422, 42204, "未分配格口");
```

- [ ] `mvn -Pmysql-it -Dtest=PickupIT test` 红灯；QR 只解析 taskhub:vehicle:id；服务器从订单查硬件号，客户端不得自选任意号。当前 task/stop/鲜度每次复查。
- [ ] control request 独立保存，网络超时 UNKNOWN；confirm 只改单单 COMPLETED，写 pickup actor/time，重复确认幂等，不能以门开事件自动完成。
- [ ] 同测试绿灯，review scan 返回列表没有同批他人手机号。

## S16：继续出发、调度开门与整车取消

**文件：** 创建 `server/src/main/java/com/taskhub/domain/vehicle/ControlPolicy.java`、`VehicleControlService.java`、`VehicleControlController.java`；`server/src/main/java/com/taskhub/domain/ticket/TicketBlocker.java`；测试 `server/src/test/java/com/taskhub/vehicle/ControlIT.java`。

**接口：** `ControlPolicy.blockers(ControlContext):List<Blocker>`；`TicketBlocker.hasActiveForTask(String taskId):boolean`（S17 提供真实 SQL，当前先建立接口测试替身只限测试）；go/cancel/open 使用契约 DTO。

- [ ] 参数化测试未全取、门 OPEN/UNKNOWN/过期、车过期、工单活动、下一点 null、task mismatch、已有 UNKNOWN GO 均无 gateway 调用；速度 null/大于 0 不可取消。

```java
assertThat(controlPolicy.blockers(contextWithUnknownDoor)).extracting(Blocker::code).contains("DOOR_UNKNOWN");
assertThat(controlPolicy.blockers(contextWithoutNextStop)).extracting(Blocker::code).contains("NO_NEXT_STOP");
```

- [ ] `mvn -Pmysql-it -Dtest=ControlIT test` 红灯；go 检查同站全部关联订单，包括其他接收人；工人需要当前站本人完成关系或调度授权。任务行锁与工单/取货写入共用。
- [ ] active_vehicle_control unique(vehicle,type) 阻止换 key 绕过；受理与状态分开；取消明确成功更新 task 不自动取消各订单；不得增加远程关门或返航能力。
- [ ] 同测试绿灯；S17 后再跑工单竞态真实 SQL 回归，未完成该回归前阶段 5 不封板。

## S17：工单独立闭环与阻断查询

**文件：** 创建 `server/src/main/resources/db/migration/V7__tickets_messages.sql`；`server/src/main/java/com/taskhub/domain/ticket/TicketModels.java`、`TicketMapper.java`、`TicketService.java`、`TicketController.java`、`SqlTicketBlocker.java`；测试 `server/src/test/java/com/taskhub/ticket/TicketIT.java`。

**接口：** 创建/列表/详情/accept/notes/close 与契约一致；`SqlTicketBlocker` 实现 S16 接口。

- [ ] 活动同单重复返回现有、工人只看本人、仓库越权拒绝、两人同时关闭一成功、结果空白拒绝；关闭后订单状态/取货时间/dispatchId 完全不变。

```sql
SELECT EXISTS(SELECT 1 FROM ticket t JOIN delivery_order o ON o.id=t.order_id
JOIN batch_order bo ON bo.order_id=o.id JOIN vehicle_task vt ON vt.batch_id=bo.batch_id
WHERE vt.id=#{taskId} AND t.state IN ('OPEN','PROCESSING'))
```

- [ ] `mvn -Pmysql-it -Dtest=TicketIT,ControlIT test` 红灯；ticket 创建先锁关联任务再插 active_order_ticket，close 移除活动标记并写结果/事件；不调用外部 gateway。
- [ ] 测试并发创建工单与 go 共用任务锁：已提交工单阻止后续 go；已发出请求保留事件并提示仓库核对，不能承诺能撤销外部已执行指令。
- [ ] 同测试绿灯，review 工单正文不进入其他账号消息。

## S18：事务事件投递与账号去重站内消息

**文件：** 创建 `server/src/main/java/com/taskhub/domain/notification/NotificationModels.java`、`NotificationMapper.java`、`NotificationService.java`、`NotificationController.java`、`BusinessEventDelivery.java`；测试 `server/src/test/java/com/taskhub/notification/NotificationIT.java`。

**接口：** `/messages`、`/messages/{id}`、`/messages/unread-count`、`PUT /messages/{id}/read`；`BusinessEventDelivery.deliverBatch(int limit):int`。

- [ ] 同员工多角色只一条、投递事务中断重启可重试、读别人消息 404、授权撤销 target=null、重复 read 时间不反复变化。

```sql
INSERT INTO notification(id,event_id,employee_id,title,body,created_at)
VALUES(#{id},#{eventId},#{employeeId},#{title},#{body},UTC_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE event_id=VALUES(event_id)
```

- [ ] `mvn -Pmysql-it -Dtest=NotificationIT test` 红灯；business_event durable outbox 使用 lease 或 SKIP LOCKED，失败保留 retry_count/next_attempt_at；成功与 event_delivery 同事务。
- [ ] 受理/驳回/发车/到达/取消/工单处理确定事件生成消息；UNKNOWN 请求只通知需核对岗位，不冒充发车成功。查询消息和目标权限二次判断。
- [ ] 同测试绿灯，停服务重启复验未投递事件恢复；review 无业务短信与订阅推送扩展。

## S19：同口径概览、履历、更正与 Excel

**文件：** 创建 `server/src/main/resources/db/migration/V8__report_indexes.sql`；`server/src/main/java/com/taskhub/domain/report/ReportModels.java`、`ReportMapper.java`、`ReportScope.java`、`ReportService.java`、`ReportController.java`、`ExcelExportService.java`；`server/src/main/java/com/taskhub/domain/audit/HistoryController.java`、`CorrectionService.java`；测试 `server/src/test/java/com/taskhub/report/ReportIT.java`；修改 `server/pom.xml` 加 POI。

**接口：** overview/history/admin reports/audits 与契约一致；`ReportScope.from(Actor,ReportFilter):ScopedFilter` 所有统计/明细/导出共用。

- [ ] 上海零点/月底边界、订单/批次/任务分别计数、A 仓查询导出一致、Excel 文本 `=HYPERLINK(...)` 不变公式、超过 10000 行拒绝、更正保存前值原因且不能改状态/接收人测试。

```java
try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
    var cell = workbook.createSheet("订单").createRow(0).createCell(0);
    cell.setCellValue("=HYPERLINK(\"https://example.invalid\")");
    assertThat(cell.getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
}
```

- [ ] `mvn -Pmysql-it -Dtest=ReportIT test` 红灯；日期转 `[start,end)` UTC，prepared SQL，排序白名单；导出 application/vnd.openxmlformats-officedocument.spreadsheetml.sheet，安全 filename，记录操作者和过滤条件。
- [ ] correction 仅允许 description/size/remark，expectedVersion 和仓库权限，旧值/新值/原因写 append-only 履历；COMPLETED 不允许无痕替换。
- [ ] 同测试绿灯；用 SQL count 与 API/Excel 行数逐项核对，review 手机不新增导出入口、管理端不新增日常操作。

## S20：既有接入设置与真实适配器边界

**文件：** 创建 `server/src/main/java/com/taskhub/domain/integration/IntegrationSettingsService.java`、`IntegrationController.java`、`JiushiVehicleGateway.java`、`JiushiTokenService.java`、`JiushiMqttAdapter.java`、`RealWechatProvider.java`、`RealSmsProvider.java`；`server/src/test/java/com/taskhub/integration/ProviderContractTest.java`；文档 `docs/integration/provider-readiness.md`。

**接口：** 实现 S04/S12 已有接口，不改前端；设置 GET 永不返回 credential，输入 credential 或秘密引用写入受控秘密存储；状态页只输出连接/最后消息/错误码。

### 九识适配补充约定（2026-09-22）

车辆控制和 MQTT 消息接入以 `docs/integration/jiushi-geobridge-adaptation.md` 为准，参考 `/Users/qin/Projects/GeoBridge` 的实现：token 缓存提前失效且 token 错误只重试一次；MQTT 使用 MQTT v5、`cleanStart=false`、自动重连，重连后重新订阅 `organizationCode/vehicle/+/realtime/push` 与 `.../business/push`，业务消息通过有界串行队列处理。九识实时消息约 1Hz，业务消息按状态变化推送；两类消息按各自时间戳判序并持久化事件，门状态与业务状态不互相覆盖。

控制接口明确映射为 `add_dispatch`、`add_dispatch_order_and_go`、`cancel_dispatch`、`go` 和 `open_box`。九识返回受理/规划结果不等于车辆已出发或箱门已开；UNKNOWN 不自动重发。开放 API 文档中部分正式 URL 存在换行或拼写断裂，必须以九识提供的完整环境清单核对后才能启用真实适配器。

- [ ] 用本地 HTTP stub 测试微信错误码、九识 token 过期、请求超时 UNKNOWN、每车 1 秒限流、普通派发不带联系人；MQTT fixture 验证重复/乱序/断线重连。不能只 mock Service 来声称协议适配通过。

```java
assertThat(serializedDispatch).doesNotContain("contacts");
assertThat(serializedSettings).doesNotContain("credentialValue");
assertThat(resultAfterReadTimeout.status()).isEqualTo(GatewayStatus.UNKNOWN);
```

- [ ] `mvn -Dtest=ProviderContractTest test` 红灯；真实九识完整 URL 按原始 PDF/可靠资料核对，转换文档断行 URL 不直接上线。真实短信服务商未选定时实现明确 `ProviderUnavailableException` 的边界，不虚构通用发送协议；readiness 如实标阻塞。
- [ ] 接入管理只改允许的 provider/baseUrl/org/appId/enabled，不任意请求用户输入 URL；生产凭证不存普通配置表，不回显或记录。真实控制未获场地/车辆授权不执行。
- [ ] 同测试绿灯；记录可验证的本地协议 fixture 与仍缺真实字段/凭证/签名/模板/车辆条件，不将适配器骨架描述成真实接入完成。

## S21：全链路回归、留存与独立恢复交付

**文件：** 创建 `server/src/test/java/com/taskhub/DeliveryFlowIT.java`、`server/src/test/java/com/taskhub/RetentionPolicyTest.java`、`server/src/main/java/com/taskhub/domain/audit/RetentionPolicy.java`；`scripts/verify-delivery.sh`、`docs/development/local-delivery.md`、`docs/development/backup-restore.md`、`docs/reviews/2026-09-20-server-delivery.md`；修改 `server/README.md` 及总体路线图相应真实进度。

**接口：** `RetentionPolicy.eligible(boolean terminal,Instant lastHandledAt,Instant now):boolean` 仅 dry-run，不调度实际删除；脚本使用环境数据库和账号，不硬编码密码。

- [ ] 全 HTTP+MySQL 两接收人闭环测试，从 admin 建员工/档案→两用户真实会话→申请→受理→组批→装货→模拟派发→事件到达→分别取货→门关→go；再走工单、消息、报表核对。数据重启保留，进程退出不得丢单。

```java
assertThat(retention.eligible(false, oldInstant, now)).isFalse();
assertThat(retention.eligible(true, now.minus(java.time.Duration.ofDays(30)), now)).isFalse();
assertThat(retention.eligible(true, now.minus(java.time.Duration.ofDays(32)), now)).isTrue();
```

- [ ] `mvn -Pmysql-it -Dtest=DeliveryFlowIT test` 红灯；实现缺失接线，不以测试注入直接写最终状态代替公开业务命令。日期留存起算未确认前生产清理仍 disabled。
- [ ] 运行 `mvn test`、`mvn -Pmysql-it test`、`mvn package`；与两端执行 npm 测试/类型检查/构建及真实接口 H5 回归，记录数据库实际版本、测试数量、失败路径和命令结果。
- [ ] 使用本地 MySQL 官方工具备份专用应用库，恢复到全新 `_restore_test` 库，核对关键表数量、外键、最大更新时间与 API 查询；只写 dry-run 指南不等于恢复验收。凭证经权限受限选项文件传递，不放命令行/日志。
- [ ] 执行服务端 review：跨仓/IDOR、角色范围混用、单次验证码、SQL 并发、请求未知锁、事件时序、工单独立、导出秘密/公式检查；确认问题修复后重跑受影响用例。
- [ ] 文档写“通过/未执行/依赖阻塞”。A01 微信真实登录、A04–A08 实车、A12 实际 MQTT、A13 真机跳转、A15 约定性能、目标 MySQL 8.4 和生产恢复条件未具备时保持未完成。所有已授权本地实现完成后由根执行者继续交付，不把外部阻塞当作停止其余工作的理由。

## 自检及执行交接

- [ ] 覆盖：S02–S05 身份主数据；S07–S09 订单；S10–S11 仓库；S12–S16 调度取货；S17–S18 工单消息；S19 管理概览；S20–S21 接入交付。
- [ ] 类型/路径：逐项对照 `docs/api/delivery-contract.md`，前端不另造 endpoint，契约变更先同步三个计划。
- [ ] `rg -n 'TBD|TODO|implement later|fill in details' docs/superpowers/plans/2026-09-20-server-delivery.md` 预期无输出；`git diff --check` 预期无错误。
- [ ] 每项 review 通过才勾完成，外部依赖保持独立阻塞项。提交、推送、合并均等待用户相应授权。
