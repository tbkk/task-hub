# Task Hub 后续交付实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有身份、订单、批次、派发、工单和消息基础上，完成取货与车辆控制闭环，再补齐小程序、管理端收尾、接入契约验证和最终交付验收。

**Architecture:** 后端继续以 MySQL 持久化状态，控制请求、车辆任务、门状态和订单取货状态分离；所有外部动作通过 `VehicleGateway`，当前只允许本地 `SIMULATOR`。小程序仅负责扫码、展示和动作编排，权限、接收人、任务、停靠点、门状态和并发校验由后端决定。管理端只做配置、报表、审计和运行查询。

**Tech Stack:** Java 17、Spring Boot 3.5、MyBatis/JdbcTemplate、MySQL/Flyway、Vue 3、TypeScript、uni-app、Vitest、Paho MQTT v5（真实接入阶段）。

## Global Constraints

- 继续在 `feature/base` 开发；不使用 Docker，不覆盖其他未提交修改。
- 所有 ID（包括九识 `dispatchId`）以字符串传输；动作请求使用 `Idempotency-Key`，更新使用 `expectedVersion`。
- 业务日期使用 `Asia/Shanghai`，数据库时间使用 UTC；错误沿用 `{code,message,data}` 和现有错误码。
- 取货只允许指定接收人；扫码只解析对象，不能授权；客户端不能自行选择格口。
- `ACCEPTED` 只表示外部请求受理；`UNKNOWN` 必须保留并核对，禁止盲目重发。
- 不新增地图、ETA、远程关门、返航、自动派车、业务短信或订阅推送。
- 当前微信、短信、九识凭证和车辆资源未具备时，只验证模拟 provider；不得宣称真实联调完成。
- 每个任务按 TDD：先写失败测试、确认 RED，再实现、复验、独立 review。

## 文件结构与边界

- 取货与控制后端：`server/src/main/java/com/taskhub/domain/order/PickupService.java`、`PickupController.java`、`server/src/main/java/com/taskhub/domain/vehicle/ControlPolicy.java`、`VehicleControlService.java`、`VehicleControlController.java`、`ControlModels.java`。
- 取货/控制迁移和测试：`server/src/main/resources/db/migration/V12__pickup_control.sql`、`server/src/test/java/com/taskhub/order/PickupIT.java`、`server/src/test/java/com/taskhub/vehicle/ControlIT.java`。
- 小程序业务适配：`miniprogram/src/features/{pickup,control,dispatch,ticket,message,overview}` 及对应 `subpackages` 页面。
- 管理端收尾：`admin-web/src/features/{integration,reports,audit}`、`admin-web/src/api/`。
- 接入与最终交付：`server/src/main/java/com/taskhub/domain/integration/`、`server/src/test/java/com/taskhub/integration/ProviderContractTest.java`、`docs/development/`、`scripts/`。

## Task 1：扫码取货和指定格口开门

**Files:**

- Create: `server/src/main/resources/db/migration/V12__pickup_control.sql`
- Create: `server/src/main/java/com/taskhub/domain/order/PickupService.java`
- Create: `server/src/main/java/com/taskhub/domain/order/PickupController.java`
- Create: `server/src/main/java/com/taskhub/domain/vehicle/ControlModels.java`
- Test: `server/src/test/java/com/taskhub/order/PickupIT.java`

**Interfaces:**

- `POST /api/pickup/scan` body `{qrText:string}` returns `{vehicle,orders,canContinue,blockers}` and only includes the current employee's orders.
- `POST /api/orders/{id}/pickup/open` body `{expectedVersion}` returns `ControlRequest` and derives compartment IDs from the order.
- `POST /api/orders/{id}/pickup/confirm` body `{expectedVersion}` returns the single completed order.
- QR format is `taskhub:vehicle:<vehicleId>`; server reloads vehicle task, current stop, receiver, order, door freshness and authorization on every action.

- [ ] Write RED tests for non-receiver, unbound receiver, malformed QR, another vehicle, non-`ATSTOP`, stale/unknown door, empty compartments, duplicate confirm, and two receivers completing separate orders.
- [ ] Implement transactional scan/open/confirm with order and task row locks; never accept client-provided compartment IDs; persist control request independently from actual door state.
- [ ] Map gateway `ACCEPTED|FAILED|UNKNOWN` to `ControlRequest`, keep order unchanged for open UNKNOWN, and make confirm idempotent without using a door-open event as proof of pickup.
- [ ] Run `set -a; source server/.env.test.local; set +a; mvn -Pmysql-it -Dtest=PickupIT test` and record the result.
- [ ] Run task review, fix Critical/Important findings, then commit `feat: add pickup identity and compartment control`.

## Task 2：继续出发、整车取消和控制并发

**Files:**

- Create: `server/src/main/java/com/taskhub/domain/vehicle/ControlPolicy.java`
- Create: `server/src/main/java/com/taskhub/domain/vehicle/VehicleControlService.java`
- Create: `server/src/main/java/com/taskhub/domain/vehicle/VehicleControlController.java`
- Create: `server/src/main/java/com/taskhub/domain/ticket/TicketBlocker.java`
- Modify: `server/src/main/java/com/taskhub/domain/ticket/SqlTicketBlocker.java`
- Create: `server/src/test/java/com/taskhub/vehicle/ControlIT.java`

**Interfaces:**

- `POST /api/tasks/{id}/go` and `POST /api/tasks/{id}/cancel` accept `{expectedVersion}` and return `ControlRequest`.
- `ControlPolicy.blockers(ControlContext)` returns blockers with codes `DOOR_UNKNOWN`, `DOOR_OPEN`, `VEHICLE_STALE`, `NO_NEXT_STOP`, `ORDER_NOT_PICKED`, `ACTIVE_TICKET`, `TASK_MISMATCH`, or `UNKNOWN_CONTROL`.
- `TicketBlocker.hasActiveForTask(String taskId):boolean` is the shared SQL blocker.

- [ ] Write RED policy tests for every blocker, including null speed refusing cancel, unknown door refusing go, no next stop, old vehicle snapshot, active ticket, task mismatch and existing UNKNOWN request.
- [ ] Implement lock order `batch → vehicle → task → orders`, unique active control `(vehicle_id,type)`, and no external gateway call when any blocker exists.
- [ ] Implement go/cancel state transitions: accepted request remains separate from task execution; cancel success does not cancel orders; UNKNOWN retains the task lock and requires reconciliation.
- [ ] Add race tests for ticket close/create versus go and pickup confirm versus go; run `mvn -Pmysql-it -Dtest=PickupIT,ControlIT,TicketIT test`.
- [ ] Complete review and commit `feat: add pickup and vehicle control policies`.

## Task 3：小程序装货、调度、取货、工单和消息页面

**Files:**

- Create: `miniprogram/src/features/pickup/{model,api}.ts`, `miniprogram/src/features/control/{model,api}.ts`
- Modify: `miniprogram/src/subpackages/warehouse/batch-edit.vue`, `miniprogram/src/subpackages/dispatch/index.vue`
- Create: `miniprogram/src/subpackages/dispatch/{task,vehicle}.vue`
- Create: `miniprogram/src/subpackages/worker/{scan-pickup,pickup-confirm,tickets,ticket-detail}.vue`
- Modify: `miniprogram/src/pages/messages/index.vue`, `miniprogram/src/subpackages/overview/index.vue`, `miniprogram/src/pages.json`
- Test: `miniprogram/tests/pickup.test.ts`, `control.test.ts`, `messages.test.ts`

- [ ] Add failing pure tests for QR parsing, server-derived compartments, control blocker presentation, UNKNOWN retry/reconcile, pagination and 320/390px no-overflow states.
- [ ] Implement loading selection and confirmation using existing batch APIs; after save, reload the server batch and display vehicle/compartment assignments.
- [ ] Implement dispatch console/task detail, scan result, pickup open/confirm, ticket lifecycle and message detail using `request`, `PagedQuery`, `expectedVersion`, and idempotency helpers.
- [ ] Cover loading/content/empty/error/busy/repeated-click states; preserve input after failure and do not expose other recipients' personal data.
- [ ] Run `npm test`, `npm run typecheck`, `npm run build:h5`, `npm run build:mp-weixin`, then browser-check the demo simulator flow.
- [ ] Review and commit `feat(mini): complete loading dispatch pickup and messages`.

## Task 4：管理端接入、报表和审计

**Files:**

- Create: `server/src/main/java/com/taskhub/domain/report/{ReportModels,ReportService,ReportController,ExcelExportService}.java`
- Create: `server/src/main/java/com/taskhub/domain/audit/{HistoryController,CorrectionService}.java`
- Create: `server/src/main/java/com/taskhub/domain/integration/{IntegrationSettingsService,IntegrationController}.java`
- Create: `admin-web/src/features/{integration,reports,audit}/{api,*.vue}`
- Test: `server/src/test/java/com/taskhub/report/ReportIT.java`, `admin-web/src/features/*/*.test.ts`

- [ ] Write RED tests for Shanghai date boundaries, scope isolation, order/batch/task counts, 10,000-row export limit, formula-safe Excel text, versioned corrections and secret redaction.
- [ ] Implement one shared `ReportScope.from(Actor, ReportFilter):ScopedFilter` for summary, detail and export; use prepared SQL and allowlisted sorting.
- [ ] Implement read-only integration settings: GET never returns credentials; writable fields are provider/base URL/org/app ID/enabled; production refuses simulator and arbitrary URLs.
- [ ] Add audit/history filters, correction append-only records, Excel MIME type and safe filenames; no correction may change receiver, status or pickup time.
- [ ] Run server report tests, admin tests/build, and browser-check permission boundaries; review and commit `feat: add reports audit and integration settings`.

## Task 5：九识协议适配和本地 fixture

**Files:**

- Create: `server/src/main/java/com/taskhub/domain/integration/JiushiTokenService.java`
- Create: `server/src/main/java/com/taskhub/domain/integration/JiushiVehicleGateway.java`
- Create: `server/src/main/java/com/taskhub/domain/integration/JiushiMqttAdapter.java`
- Create: `server/src/test/java/com/taskhub/integration/ProviderContractTest.java`
- Modify: `docs/integration/provider-readiness.md`, `docs/superpowers/plans/2026-09-20-server-delivery.md`

- [ ] Write HTTP stub RED tests for token cache/expiry, one token-invalid retry, envelope errors, timeout→UNKNOWN, one-vehicle-per-second detail limit, and dispatch body without contacts for ordinary dispatch.
- [ ] Write MQTT fixture RED tests for MQTT v5 `cleanStart=false`, reconnect resubscription, realtime/business topic routing, duplicate/older event rejection, same-timestamp conflict, missing speed/door preservation and `ATSTOP` destination matching.
- [ ] Implement only the documented boundary from `docs/integration/jiushi-geobridge-contract.md`; keep credentials out of logs and settings responses; use `integration_event` and existing snapshots for durable state.
- [ ] Keep real provider disabled until broker, organization code, credentials, complete endpoint paths, source MD5 and test vehicle are confirmed; no real command is sent by tests.
- [ ] Run `mvn -Dtest=ProviderContractTest test`, record verified fixture fields and unresolved vendor fields, review, and commit `feat: add jiushi provider contract adapter`.

## Task 6：全链路模拟、恢复和交付

**Files:**

- Create: `server/src/test/java/com/taskhub/DeliveryFlowIT.java`, `RetentionPolicyTest.java`
- Create: `server/src/main/java/com/taskhub/domain/audit/RetentionPolicy.java`
- Create: `scripts/verify-delivery.sh`
- Create: `docs/development/local-delivery.md`, `docs/development/backup-restore.md`, `docs/reviews/2026-09-22-next-delivery.md`
- Modify: `docs/development/acceptance-matrix.md`, `docs/superpowers/plans/2026-09-20-development-roadmap.md`

- [ ] Write RED full-flow tests for two receivers: admin setup → sessions → order → warehouse accept → batch/loading → simulator dispatch → ATSTOP → separate pickups → doors closed → go → ticket/message/report checks.
- [ ] Implement dry-run retention rule `eligible(false, old, now)=false`, terminal records under 31 days false, terminal records over 31 days true; production cleanup remains disabled until retention start is approved.
- [ ] Run service restart recovery, MySQL backup to a fresh `_restore_test` database, row-count/foreign-key/maximum-update checks, and all three application verification commands.
- [ ] Update acceptance matrix with `通过/未执行/依赖阻塞`; explicitly keep real WeChat, real SMS, real MQTT/vehicle, target MySQL 8.4 and production recovery as blocked where resources are absent.
- [ ] Complete whole-branch security/concurrency review, fix findings, rerun affected tests, and commit `test: verify simulated delivery and recovery`.

## Self-review checklist

- [ ] All contract endpoints `/pickup/*`, `/orders/*/pickup/*`, `/tasks/*/go`, `/tasks/*/cancel`, reports, audit and settings have one owning task.
- [ ] Every new action has a RED test, MySQL integration test where locking/persistence matters, and a frontend state test where UI changes.
- [ ] No task claims real九识/微信/短信 completion without credentials and external evidence.
- [ ] 计划执行完成后运行文档占位词检查和 `git diff --check`，确认没有遗留占位内容或格式错误。

Plan complete and saved to `docs/superpowers/plans/2026-09-22-next-delivery.md`. Two execution options:

1. **Subagent-Driven（推荐）**：按任务逐项派发实现，并在每项后执行 review。
2. **Inline Execution**：在当前会话使用 executing-plans 分批执行。

等待选择执行方式。
