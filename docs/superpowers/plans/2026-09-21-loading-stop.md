# 仓库装货点绑定实施计划

> 使用 executing-plans / TDD 执行；补齐既有S13“仓库已绑定装货点”前置数据。

**目标：** 给每个仓库明确配置唯一既有装货点，派发不得猜测出发地。
**架构：** 新增V8 warehouse_loading_stop（warehouse_id PK,stop_id FK,version）；独立管理GET/PUT接口，避免目的地关联被误认为装货点。管理平台选择已绑定本仓且启用的点位。派发事务复核实际车辆所在点与绑定一致。
**技术栈：** Java17 / Spring Boot / JdbcTemplate / MySQL。

- [ ] LoadingStopIT：未配置GET返回null；PUT需MASTERDATA_MANAGE本仓、expectedVersion，未知/未关联/停用点422、旧版本409、跨仓403；HTTP404先红灯。
- [ ] 新建LoadingStopService/Controller与V8；只读响应{id:warehouseId,stopId,version}；初次expectedVersion=0，保存后version=1。后续每次加1。
- [ ] 事务先锁操作者、仓库、点位；占用活动批次时拒绝更换。写审计同事务。
- [ ] 点位停用/取消仓库关联时MasterdataUsage阻断仍用作装货点；防并发用stop行锁，关联校验在锁后。
- [ ] 补API契约与管理端配置入口；真实MySQL定向验证与独立审查。当前不提交推送。
