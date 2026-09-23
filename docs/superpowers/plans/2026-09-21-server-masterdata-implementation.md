# 主数据 S05 实施计划

> 执行方式：当前已授权任务内逐项实施，采用 executing-plans 与 TDD；不提交推送。

**目标：** 实现版本化仓库、既有外部点位/车辆/硬件绑定、规则和按当前授权过滤的业务目录。
**架构：** MyBatis 持久化、薄控制器；资源服务、规则服务、目录服务分别承担业务。外部目录只在 local/test 读取模拟数据库；生产返回 503。
**技术栈：** Java 17、Spring Boot、MyBatis、Flyway、MySQL。

## 约束

- 沿用 delivery-contract；ID 字符串、version 整数、PageResponse.total 整数。
- 只编辑主数据代码、V3、主数据测试与本计划；不改身份 Java、V2 和前端。
- V3 增加 employee.home_warehouse_id 可空外键；worker SELF 不推导所属仓库。
- 真实专用测试库，UUID 隔离数据，不清库。外部连接及后续订单容量不得声称已验收。

## 任务 1：持久化资源和范围

文件：V3__masterdata.sql、MasterdataModels/Mapper/Service/Controller.java、MasterdataIT.java。
- [x] 先写 HTTP 创建仓库、重复 code、旧版本更新、跨范围列表及对象访问测试。
- [x] 执行 `set -a; source .env.test.local; set +a; mvn -Dtest=MasterdataIT test`；确认缺失 API 返回非 200。
- [x] 建立 warehouse/stop/vehicle/compartment 与绑定关系表，所有外部标识唯一；员工所属仓库外键引用 warehouse。
- [x] 写操作先 `authorization.lockActor(actor)`，按相同 capability 验证旧/新全部仓库，再锁行检查 expectedVersion；重复资源返回 409。
- [x] 对绑定仓库和车辆可达点位做现存/启用校验；停用只更新 enabled，保留关联。

## 任务 2：外部目录与硬件同步

文件：ExternalCatalog.java、CompartmentService.java、MasterdataIT.java。
- [x] 写不存在外部资源拒绝、重复车辆、跨车格口、同步保留标签与旧版本冲突测试。
- [x] local/test 使用 external_catalog_resource、external_catalog_compartment 中真实存在的模拟定义；其他 profile 返回 503。
- [x] 同步新增硬件、已有定义不改号，消失硬件停用而不删除；车辆版本递增，格口更新只允许 label/enabled。
- [x] 提供 MasterdataUsage 查询扩展接口：停用资源、移除绑定、停用硬件前调用；订单阶段接入真实占用查询。

## 任务 3：规则与目录

文件：RuleService/RuleController/CatalogService/CatalogController.java、MasterdataIT.java。
- [x] 写重叠时段、非 30 分钟、非法容量/长度/鲜度、共享格口 true 拒绝测试。
- [x] 规则输入全量校验；按 warehouseId+version 更新，未配置规则返回 404，不注入生产演示默认值。
- [x] worker 目录从 employee.home_warehouse_id 读最多一个启用仓库；其他工作区仅选中角色 scope 生效。
- [x] 点位目录要求仓库/点位/绑定车辆启用且车辆绑定到该点；公共规则不返回接入参数。
- [x] 预约 slots 和 favorites 属后续 S07，本切片不假造容量。

## 验证与交付

- [x] MasterdataIT 真实 MySQL 红绿；`mvn test` 常规回归，不与其他身份数据库测试并跑。
- [x] 检查 git diff 与代码 review；报告测试数字、命令、迁移环境、未完成占用查询边界。
- [x] V3 仅向前迁移；恢复使用迁移前备份，不能清库/回改 V2。

## 2026-09-21 执行证据

- 专用 MySQL `task_hub_delivery_test`，Flyway 从 V2 正常升级 V3；原表数据保留，新增 home_warehouse_id 默认 NULL。未修改 V2、未清库。
- 首次红灯：`/tmp/masterdata-red.log`，仓库 POST 404；扩展红灯 `/tmp/masterdata-red2.log`，4 项缺少接口；外部资源移除后本地点位停用红灯 `/tmp/masterdata-extra-red.log`，预期 200 实际 422。
- `mvn -q -f server/pom.xml -Dtest=MasterdataIT test`：9 项通过，0 失败/错误；真实 HTTP 安全链（MockMvc）与 MySQL。包含并发两个请求同版本仅一个成功、旧版本 409、重复绑定/硬件唯一键、跨车格口、工人所属仓库、逐 capability/逐工作区范围、不可达/停用目录、规则粒度/重叠/数值/共享限制、外部缺失与生产拒绝模拟目录。
- `mvn -q -f server/pom.xml test`：退出 0；常规回归通过。该命令默认不包含 IT，因此单列专用数据库测试结果。
- `git diff --check`：通过。Java 新增文件使用项目已有 google-java-format 工具格式化。
- 自查修复：跨服务直接访问 Spring 代理字段导致同步 NPE，改为构造器显式注入；车辆获得行锁后再次按最新 warehouseId 授权；主数据写事务 READ_COMMITTED，点位先锁行再校验当前绑定范围；停用不依赖外部目录在线。
- `MasterdataUsage.requireChangeAllowed(resource,id)` 是订单/批次占用的扩展边界，当前没有订单模块实现；不宣称活动业务停用阻断完成。S07 的容量抢占、slots、收藏未在此切片实现。
- 模拟外部目录是数据库表，测试按 UUID 写入隔离目录；未预置伪真实连接或生产候选。真实九识尚未接入，非 local/test 返回 503。
- 恢复方式：部署前备份目标库；迁移失败保留证据并从备份恢复到独立环境核验，不删除原库、不回改已应用 V2/V3；当前仅在专用测试环境执行迁移。
- 资源列表当前在服务端按同项授权过滤后分页；数据规模增长时应下推过滤/分页到 SQL，不能当作已完成大数据首屏性能验收。
- 尚未提交/推送。主任务独立 review 后再决定 S05 最终验收状态。
