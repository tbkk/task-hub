# 完全移除短信认证实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**目标：** 删除小程序短信认证能力，改用内部账号密码完成首次微信绑定，并让运行代码和配置不再依赖短信。

**架构：** 微信未绑定时服务端创建短期一次性绑定记录，返回 `CREDENTIALS_REQUIRED`；小程序提交绑定令牌与内部账号密码到 `/api/mini/auth/bind`，服务端复用 BCrypt 凭证校验，在事务内完成员工与微信关系绑定并签发小程序会话。删除短信控制器、provider、配置、挑战表及前端手机号页。

**技术栈：** Spring Boot、MyBatis、Flyway、JUnit MockMvc、Vue 3 + TypeScript、uni-app、Vitest。

## 全局约束

- 文档和错误提示使用中文。
- 不记录密码、密码哈希、微信 openid 或绑定令牌到普通日志。
- 旧数据库通过有序 migration 删除 `sms_challenge`，不能重建数据库。
- 绑定令牌有效期 10 分钟、单次消费；账号仍需启用且已完成首次改密。
- 必须运行后端身份测试、小程序测试、类型检查、H5 构建和微信构建。

### 任务 1：后端账号密码微信绑定

**文件：**
- 修改：`server/src/main/java/com/taskhub/domain/identity/MiniAuthModels.java`
- 修改：`server/src/main/java/com/taskhub/domain/identity/MiniAuthService.java`
- 修改：`server/src/main/java/com/taskhub/domain/identity/MiniAuthController.java`
- 修改：`server/src/main/java/com/taskhub/domain/identity/CredentialMapper.java`
- 修改：`server/src/main/java/com/taskhub/config/SecurityConfig.java`
- 修改：`server/src/main/java/com/taskhub/config/BearerFilter.java`
- 测试：`server/src/test/java/com/taskhub/identity/MiniAuthIT.java`

- [ ] 将微信未绑定状态改为 `CREDENTIALS_REQUIRED`，新增 `BindInput(bindingToken, username, password)`。
- [ ] 在 MiniAuthService 中注入 CredentialMapper 与 BCryptPasswordEncoder；新增 `bind(BindInput)`，校验令牌、凭证、启用状态、`must_change_password=false`、绑定冲突，事务内绑定并消费令牌后签发 MINI 会话。
- [ ] 增加 `/api/mini/auth/bind`，删除 sms/verify controller 方法及短信依赖。
- [ ] 仅将 wechat 和 bind 作为公开认证入口，移除 sms/verify 白名单。
- [ ] 测试绑定成功、错误密码、临时密码、过期/重复令牌、微信绑定冲突。

### 任务 2：删除后端短信基础设施

**文件：**
- 删除：`server/src/main/java/com/taskhub/domain/integration/SmsProvider.java`
- 删除：`server/src/main/java/com/taskhub/domain/identity/MiniAuthMapper.java` 中短信挑战方法与 SQL
- 修改：`server/src/main/java/com/taskhub/domain/identity/MiniAuthModels.java`
- 修改：`server/src/main/java/com/taskhub/domain/integration/AuthProviderConfiguration.java`
- 修改：`server/src/main/java/com/taskhub/config/*` 中短信配置引用
- 新增：`server/src/main/resources/db/migration/V7__remove_sms_challenge.sql`
- 修改：`server/src/test/java/com/taskhub/identity/MiniAuthIT.java`、`AuthProviderTest.java`

- [ ] 删除短信 challenge 模型、mapper SQL、provider bean、限频/HMAC/TTL 配置和测试。
- [ ] 新增 `DROP TABLE IF EXISTS sms_challenge` migration，并更新 migration 测试。
- [ ] 更新 `server/README.md`，删除短信配置和认证说明。

### 任务 3：小程序首次微信绑定改为账号密码

**文件：**
- 修改：`miniprogram/src/features/auth/model.ts`
- 修改：`miniprogram/src/features/auth/api.ts`
- 修改：`miniprogram/src/features/auth/session.ts`
- 修改：`miniprogram/src/pages/login/index.vue`
- 新增：`miniprogram/src/pages/wechat-bind/index.vue`
- 修改：`miniprogram/src/pages.json`
- 删除：`miniprogram/src/pages/phone/index.vue`
- 修改：`miniprogram/src/features/auth/mock.ts`、相关 fixtures/tests

- [ ] 删除短信类型、API、状态和 mock；新增 `bindWechat(bindingToken, username, password)` API。
- [ ] 将微信未绑定路由改为 `/pages/wechat-bind/index`，页面只显示账号、密码输入和绑定按钮，不显示令牌。
- [ ] 绑定成功建立会话并进入首页；失败显示服务端错误；临时密码提示先完成首次改密。
- [ ] 从 pages.json 删除 phone 路由，新增 wechat-bind 路由。

### 任务 4：契约、文档与全量验证

**文件：**
- 修改：`docs/api/delivery-contract.md`
- 修改：`docs/architecture/identity-baseline.md`
- 修改：`docs/integration/provider-readiness.md`
- 修改：`docs/development/acceptance-matrix.md`
- 修改：`deploy/local.env.example`、`deploy/README.md`
- 修改：小程序测试与后端测试

- [ ] 将认证契约改为账号密码登录和账号密码微信绑定，移除短信 endpoint/config。
- [ ] `rg` 检查运行代码和配置不再出现短信认证入口或 `TASKHUB_AUTH_SMS_*`。
- [ ] 运行后端身份测试、默认测试、管理端测试；运行小程序测试、类型检查、H5 构建、微信构建。
- [ ] 提交功能分支，合并到 `dev1.0`，在合并后的分支复验并推送远程。
