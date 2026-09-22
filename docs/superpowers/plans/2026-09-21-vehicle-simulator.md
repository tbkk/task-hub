# 车辆模拟提供者实施计划

> 使用 executing-plans 持续执行；沿用服务端交付 S12/S14 需求。

**目标：** 用真实 MySQL 保存模拟场景与车辆命令结果，为派发/控制提供可重查边界；模拟受理不等于车辆动作。
**架构：** VehicleGateway 命令接口、SimulatorVehicleGateway 持久化模拟实现、VehicleProviderConfiguration 生产防护、SimulatorController 管理场景；事件融合随 S14 使用相同入口。
**技术栈：** Java17 / Spring Boot / JdbcTemplate / MySQL / Flyway。

## 全局约束

- 当前 feature/base；不提交、不推送、不合并。新增 V7，不修改历史迁移。
- 模拟仅 local/test 且显式 SIMULATOR_ENABLED=true；生产与混合生产 profile 拒绝启用。
- 模拟管理要求 INTEGRATION_MANAGE ALL；admin 显示字段不能作为权限边界。
- 接口 requestId 唯一；同请求内容返回原结果，冲突40901；TIMEOUT持久化UNKNOWN；不自动改门状态。

## 任务

- [ ] SimulatorIT：场景接口不存在先404红灯；场景持久化、非管理权限403。
- [ ] V7：simulator_scenario(operation PK,outcome) 和 simulator_command(request_id PK,operation,command_json,result_json)。默认未配置场景返回ACCEPTED。
- [ ] VehicleGateway：dispatch/open/go/cancel/queryCommand；请求id与车辆必填，开门hardwareNos非空且唯一。响应status/dispatchId/message。
- [ ] SimulatorVehicleGateway：事务中INSERT IGNORE请求记录后FOR UPDATE；同id比较规范化正文，既有结果重放；仅新请求消费当前场景。
- [ ] 提供者配置拒绝生产模拟；未配置真实接口返回503，不假装车辆成功。
- [ ] 测试ACCEPTED/FAILED/TIMEOUT及重建服务后结果保留；空开门拒绝；生产保护；定向真实库验证与独立审查。
