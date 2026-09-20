# 工程初始化实施计划

**目标：** 根据最新标准版端划分，建立可构建、可继续开发的三端工程及部署基础。

**架构：** 独立双前端、按业务域分包的单体 Java 后端，统一 MySQL 数据源；开发请求代理与部署反向代理均保留 `/api` 前缀。

**约束：** Java 17、Spring Boot 3、MyBatis、Vue 3、TypeScript、Element Plus、uni-app、MySQL 8.4；不实现额外功能或虚假车辆控制。依赖版本固定并生成锁文件。

- [x] 初始化管理端：Vite、Vue Router、Element Plus、管理模块路由、请求封装及环境示例；类型检查、构建和 3 项测试通过。
- [x] 初始化小程序：同版本 DCloud Vue3 依赖、manifest/pages、业务分包、请求封装及微信配置；类型检查、微信与 H5 构建通过。
- [x] 初始化后端：Maven、启动类、配置、业务包、MyBatis 元数据查询、统一响应及默认拒绝；6 项测试与打包通过，并实际启动检查接口。
- [x] 初始化数据库与部署：SQL、Compose、Dockerfile、Nginx、环境示例；完成静态检查。Docker 不可用，容器及 MySQL 联调待验证。
- [x] 完成启动指南、模块说明、根 README、Figma 82 个画板索引与忽略规则，记录构建结果和 DCloud 依赖风险。

执行在当前会话和当前工作目录完成，保留用户已有资料，不自动提交或发布。
