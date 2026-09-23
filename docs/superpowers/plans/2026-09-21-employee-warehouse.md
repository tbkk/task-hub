# 员工归属与查询补全实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans；在当前 feature/base 执行，完成验证与审查后记录。

**Goal:** 员工管理持久化所属仓库、提供完整服务端过滤与绑定信息，人员管理员可读取授权配置所需仓库。
**Architecture:** 复用员工 mapper/service/controller，V3 由主数据切片创建 warehouse 与 employee.home_warehouse_id。角色 SELF 与所属仓库保持独立。仓库选项通过员工管理授权查询，不借用主数据管理权限。
**Tech Stack:** Java17、Spring Boot、MyBatis、MySQL、Vue3。

## Global Constraints

- 人员维护要求 EMPLOYEE_MANAGE ALL，日常角色不因此获得业务权限。
- 手机号录入不等于验证，绑定状态只读。
- 数据库变化使用 V3；不可编辑 V2。
- 员工可暂不设归属仓库，未设工人不允许申请；无效或停用仓库不能新分配。

## 任务

文件：identity/IdentityModels.java、EmployeeMapper.java、EmployeeService.java、EmployeeController.java；identity/EmployeeDirectoryIT.java；admin-web/src/features/people/*。

- [ ] 编写 HTTP/MySQL 测试：创建 homeWarehouseId 后 GET 回显；不存在/停用仓库返回400；编辑版本冲突409；worker归属仓库仅通过管理接口改变。
- [ ] 编写筛选测试：role/warehouseId/phoneVerified/wechatBound 在服务端过滤，总数与分页一致，多角色员工不重复。
- [ ] GET /admin/employees/warehouse-options 使用 EMPLOYEE_MANAGE ALL，分页返回仓库 id/name/enabled（用于保留旧停用标签），没有 MASTERDATA_MANAGE 仍可读；无人员管理权限403。
- [ ] 为输入输出添加 homeWarehouseId，mapper独立读取/更新，写前锁定有效仓库；新增 wechatBound 查询 EXISTS，禁止返回 openid。
- [ ] 前端添加相应筛选与只读绑定信息、仓库下拉及摘要；完整分页加载仓库选项，保留已授范围。
- [ ] 执行 EmployeeDirectoryIT 后执行受影响身份测试；前端 test/build 和真实浏览器增改查筛选，记录版本与环境。

## 验证记录

- EmployeeDirectoryIT 未知仓库 RED：400 预期实际200；角色筛选 RED：总数0预期实际1。实现后 EmployeeDirectoryIT 3 项通过（含旧停用归属可保留、新分配拒绝），相关 IdentityIT/AuthorizationIT 合计16项通过。
- 主数据与员工目录合跑12项通过，日志 /tmp/task-hub-combined-masterdata.log。
- 当前本地演示库已应用V3且后台重启，浏览器真实创建仓库、修改员工所属仓库、服务端角色筛选和重置通过；凭据只存/tmp受限文件。
- 人员UI需独立review；工作区代码尚未提交。
