# S13/S14 派发与遥测实施计划

> 既有授权 inline 执行，使用 executing-plans/TDD，当前 feature/base 不提交推送。

**目标：** 持久化派发请求、占用、车辆任务及有序遥测，模拟走统一入口。
**架构：** V9 新表；JdbcTemplate 与 TransactionTemplate 复用现有事务模式；幂等准备事务与外部gateway严格分离；VehicleEventService唯一状态更新入口。
**技术：** Java17、Spring Boot、真实MySQL、MockMvc。

## 约束
权限按当次dispatch/warehouse范围；派发仅dispatch；UNKNOWN不重发，PENDING遗留只核对；真实车辆未验收；保持V1–V8不变。

## 1 派发准备与结果
- [x] DispatchIT 写HTTP派发成功PLANNING/订单仍READY；缺绑定、旧快照、不确认、并发同车、TIMEOUT保留锁、不重发、FAILED释放测试。
- [x] 执行 `mvn -Dtest=DispatchIT test` 观察缺接口红灯。
- [x] 新建 V9__dispatch.sql、domain/dispatch/DispatchModels.java、DispatchService.java、DispatchController.java，按 /tmp/task-hub-dispatch-contract.md 实现 prepare→gateway→finish；精确requestId核对，另车任务不充当匹配结果。
- [x] 同命令绿灯，验证gateway发生时没有业务事务，重启/PENDING不会自动发送。

## 2 遥测与查询
- [x] VehicleEventIT 写重复/旧时间/旧任务/同时间冲突/缺门/正确ONWAY和ATSTOP/错误点测试。
- [x] 执行 `mvn -Dtest=VehicleEventIT test` 观察缺接口红灯。
- [x] 新建 integration/VehicleEvent.java、VehicleEventService.java、SimulatorEventController.java 与 vehicle/VehicleQueryService.java、VehicleController.java，独立流判序，任务、订单与business_event同事务。
- [x] 同命令绿灯；GET车辆读本地快照无外部调用，任何外部详情刷新须每车1Hz。

## 3 回归与review
- [x] 执行 BatchIT、DispatchIT、VehicleEventIT、SimulatorIT、OrderIT、MasterdataIT；编译格式和diff检查。
- [x] 检查资源锁、角色、旧任务污染、未知结果；报告 /tmp/task-hub-dispatch-report.md，根独立review。

## 验证记录
2026-09-21：MySQL9.6专用库V8原位升级V9；6个测试类43项通过，授权锁收尾后DispatchIT+VehicleEventIT12项复验通过。外部任务错误放行、PENDING未转换、错误车辆核对证据均先红灯再修复。真实提供者详情刷新/1Hz限流和冲突核对解除留后续接入；GET当前仅读持久化快照。任务详情时间/履历聚合与派发后取消现场核对未实现，不能宣称整体配送闭环完成。根执行者独立review待进行。
