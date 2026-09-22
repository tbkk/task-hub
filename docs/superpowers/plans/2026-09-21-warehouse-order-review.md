# 仓库订单受理与取消处理实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans，当前分支执行。

**Goal:** 仓库按授权范围受理/驳回订单和处理尚未组批订单的取消申请。
**Architecture:** 复用幂等服务，在同事务内锁订单、复核仓库角色与version、更新状态、释放预约容量并记录审计事件。已组批/已派发的安全处理在批次模块接入后扩展，当前拒绝超出已实现状态的动作。
**Tech Stack:** Java17、Spring Boot、JdbcTemplate、MySQL。

## Global Constraints

- 只有warehouse工作区可审批；跨仓不返回订单内容。
- 驳回/取消处理必须理由，订单容量至多释放一次。
- 取消拒绝保持原订单主状态；取消批准不能代替取消车辆任务。

- [ ] 测试PENDING受理→ACCEPTED，重复版本409；另一仓库404/403。
- [ ] 测试驳回释放容量used=0，重试同key不二次释放；无原因400。
- [ ] 测试取消拒绝保留PENDING/ACCEPTED；批准取消→CANCELLED，审计与事件同事务。
- [ ] WarehouseOrderService与Controller实现；状态变更条件原子校验。不得编造车辆控制成功。
- [ ] 真实MySQL定向通过后接入仓库小程序界面；当前切片不包含装载/组批。
