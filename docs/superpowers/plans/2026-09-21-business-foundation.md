# 业务幂等与事件基础实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans；当前分支持续实施。

**Goal:** 为订单创建和后续动作提供持久化幂等及同事务审计事件。
**Architecture:** MySQL唯一(账号、工作区、路由、key)，事务内锁账号并刷新权限，规范化请求JSON计算hash，成功结果JSON同事务保存。审计和业务事件不得独立提交。
**Tech Stack:** Spring TransactionTemplate、MyBatis/JdbcTemplate、Jackson、MySQL。

## Global Constraints

- 业务数据落MySQL，不使用内存仓库；token不写入幂等或审计。
- 同key不同内容409，失败回滚不留下成功结果；重复请求不执行业务第二次。
- 业务记录至少31天，本阶段无自动删除。
- 本任务迁移使用下一版本V4__business_foundation.sql，订单后续V5；原索引迁移文件名为建议，已执行的V1–V3不改。

## 实施步骤

- [ ] IdempotencyIT测试同key重放/正文冲突/回滚/两个并发请求只一次、审计脱敏且失败回滚。
- [ ] 首次运行因缺少幂等服务失败后新增V4三表：idempotency_record、business_event、audit_event。
- [ ] execute(Actor,route,key,Object request,Class<T>,Supplier<T>)：校验key，锁actor并刷新会话，按scope读取已有结果，hash不符409，否则读保存response；首次调用supplier后写JSON。
- [ ] append/record 使用MANDATORY传播，事务外拒绝调用；脱敏password/token/code/credential/secret字段但保留业务状态code可由上层单独文案。
- [ ] 专用MySQL并发测试通过，新增迁移重放验证，不将基础接口当作订单流程已完成。
