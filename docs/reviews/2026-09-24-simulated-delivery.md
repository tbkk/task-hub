# 完整模拟配送闭环验收

日期：2026-09-24。基线 `dev1.0/a25ead8`，验收分支 `feature/simulated-delivery-acceptance`。
范围引用[后续交付计划 Task 6](../superpowers/plans/2026-09-22-next-delivery.md)。本次不连接真实车辆、不使用演示库、不重建数据库。

## 环境与执行

- 本机 MySQL 9.6.0，显式专用库 `task_hub_delivery_test`；凭证来自未纳入版本控制的 `server/.env.test.local`。
- 业务接口验证使用 Spring Boot MockMvc、真实服务和 MySQL，车辆请求由 SIMULATOR 接收，遥测通过模拟事件端点输入。
- H5 使用 Chrome 无头浏览器，页面接口使用桩；与数据库业务测试分别记录。
- 进程恢复使用生产 JAR 的 local profile，回环地址和随机端口；独立启动、终止、再次启动 Java，不影响已有部署实例。

## 已通过

1. MigrationIT、PickupIT、ControlIT：15 项通过。迁移增量应用和重放检查通过，无清库。
2. DeliveryFlowIT：同一申请人通过 API 下单给两个接收人、仓库受理、合批、分配两个格口、装货确认、派发、ONWAY 和 ATSTOP、两个接收人分别开门并确认取货。
3. 每位接收人扫码只显示本人的订单；跨接收人确认返回 404。未全取、门未关、活动工单分别阻断 go，阻断时不产生 GO 请求。
4. 工单受理、关闭后 go 被受理；受理本身不改变 AT_STOP，后续事件推进 RUNNING、FINISHED 和批次 CLOSED。
5. 派发、开门、确认取货和 go 使用相同 key 重放，模拟器只收到一次派发、两次开门和一次 go。
6. 真实 outbox 投递器生成工单关闭消息；跨用户读取被拒绝、已读状态落库；报表与闭环数据一致：2 单、1 批、1 任务。
7. H5 任务详情：网络失败后重试保留相同 key 和请求体；刷新恢复登录态；320/390px 页面无横向溢出，未捕获页面 JavaScript 异常。
8. 两个独立 Java 进程：首次通过模拟短信 API 登录，第二次启动仍能使用相同 token 读取相同身份与 FINISHED 任务，重启没有新增模拟车辆命令。

## 可复现命令

```bash
# 项目根目录；source 不打印凭证
set -a
source server/.env.test.local
set +a
(cd server && mvn -Dtest=MigrationIT,PickupIT,ControlIT,DeliveryFlowIT test)
(cd server && mvn -Dspring.test.context.cache.maxSize=4 '-Dtest=*Test,*IT' test)
(cd server && mvn -DskipTests package)
python3 harness/tests/delivery_restart.py

# 单独终端启动 H5
(cd miniprogram && VITE_AUTH_MOCK=false npm run dev:h5 -- --host 127.0.0.1 --port 18191)
# Node 环境需可解析 playwright，并安装 Chrome；必要时用 NODE_PATH 指向已安装包
node harness/tests/delivery_ui.cjs
```

实际 Maven 工程不存在 `mysql-it` profile；选择集成测试用 `-Dtest`，不能把普通 `mvn test` 当作业务 IT 全套。

全套首次执行为 127 项、0 断言失败、1 个环境错误：多个测试上下文保留连接池使 MySQL 返回 `Too many connections`。复跑命令限制 Spring 测试上下文缓存为 4，不修改数据库全局连接上限；复跑 127 项全部通过，0 失败、0 错误、0 跳过。

## Review 记录

独立审查未发现 Critical 问题。审查提出的申请人范围已与用户确认的“两个不同接收人”核对，测试为同一申请人给两人下单，不宣称覆盖两个申请人的隔离。已加强已读时间字段存在性和时间格式断言；H5 刷新后等待任务正文，并核对任务请求携带原 token。两项修改均定向复验通过。未修改业务生产代码。

## 未覆盖范围

- 业务基础资料和身份权限由 fixture 初始化，会话由 SessionService 签发；完整管理端主数据录入与真实微信注册不在这条测试内。
- 模拟下一停靠点由事件明确指定；不代表已确认实车路线和自动离站策略。
- 消息投递同步调用真实处理器，未测定时投递延迟。
- H5 只验证任务详情的重试、刷新和窄屏；完整三端 UI 手工闭环、微信开发者工具和真机尚未执行。
- 重启验证覆盖已完成任务与登录会话；在途任务、PENDING/UNKNOWN 控制恢复、断电中断事务未由此次重启测试覆盖。
- MySQL 8.4、独立备份恢复、真实短信/九识 MQTT/车辆和目标服务器仍未验收。
