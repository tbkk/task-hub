# S10/S11 组批装货实施计划

> 执行方式：已有全计划授权，使用 executing-plans 与 TDD 在当前 feature/base 执行，不提交推送。

**目标：** 真实 MySQL 保存同仓同点同时段批次、独立格口占用及装货确认，取消安全移出。
**架构：** 复用 JdbcTemplate、IdempotencyService 与现有授权/审计。V6 新建 batch、batch_order、active_order_batch、batch_assignment、active_compartment；批次先锁，订单排序锁；取消先锁批次再锁订单，避免反向锁。
**技术：** Java 17、Spring Boot、MySQL、Flyway、MockMvc。

## 约束
中文文档；模拟资源不等于实车；不读取输出密钥；所有写接口幂等；仓库写、仓库与调度范围读；不开发车辆任务控制。

## 切片 1：批次
- [x] 新建 BatchIT，HTTP 创建后断言 DRAFT，混点/重复/已占用失败，过期版本失败，两个操作员抢同单仅一次成功。
- [x] 执行 `cd server && mvn -Pmysql-it -Dtest=BatchIT test`，记录接口不存在红灯。
- [x] 新建 V6__batches.sql 与 domain/batch/BatchModels.java、BatchService.java、BatchController.java。输入同契约，创建锁订单排序校验 ACCEPTED，再插活动关联；replaceOrders 批次锁、版本检查、成员校验、保留历史并重置分配。
- [x] 同命令绿灯。

## 切片 2：装货
- [x] BatchIT 增加完整分配、空/遗漏/跨车/重复/禁用/他批占用拒绝与容量确认测试。
- [x] 同命令确认红灯；BatchService 实现 assign/confirm，车辆格口锁与唯一占用；确认保存操作者时间版本及哈希，订单变 READY。
- [x] 同命令绿灯。

## 切片 3：取消和回归
- [x] BatchIT 覆盖 READY 取消释放与最后成员 CLOSED、LOCKED 保留取消待处理。
- [x] WarehouseOrderService 接入批次先锁和安全移出，OrderService 查询关联；同命令绿灯。
- [x] 执行 BatchIT、OrderIT 和编译；review 权限、锁顺序、历史和占用；写 /tmp/task-hub-batch-loading-report.md。

## 验证记录
2026-09-21：真实 MySQL 9.6 专用库原位迁移 V6 通过；BatchIT 6项、OrderIT 5项、MasterdataIT 10项共21项通过。批次/装货接口缺失及 LOCKED 取消绕过均先观察红灯后修复。loading/open与派发后人工核对留待车辆任务切片；目标MySQL 8.4和实车未验证。
