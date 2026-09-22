# 服务端身份与权限实现计划

> 执行者：使用 executing-plans 在当前 feature/base 逐项实施。用户已授权编码，本文不是新的确认门槛；不提交或推送。

**目标：** S02–S04 提供真实数据库会话、逐角色/能力授权、员工维护及模拟微信短信适配。

**架构：** MyBatis 专属 Mapper 保存员工、凭据、会话、限频、绑定和挑战；服务封装事务，Controller 只映射契约。Bearer 每次读取员工状态及授权。身份认证模拟仅替换外部提供者，不替换准入或数据库。

**技术：** Java 17、Spring Boot 3.5.6、MyBatis、MySQL、BCrypt、Flyway。

## 约束及文件边界

- 复用 `ApiException(int,int,String)`、`ApiResponse`、`PageResponse<T>`；S01 的 POM、基础配置、公共错误处理由 root 维护。
- 只新增 `V2__identity.sql`，不重建数据库。测试库显式 `MYSQL_TEST_URL`、名称 `task_hub_*_test`；不清表。
- 所有 ID string，版本 int。手机准入与验证关系分离。平台 admin 为派生标识，不授权。
- 模拟提供者要求 local/test profile 加显式属性；prod 即使组合 local 也拒绝。秘密不得记录。
- 自定义属性以 `taskhub.auth.*` 命名；生产未接真实提供者时返回 503，不能静默模拟。

## 任务一：凭据与会话

文件：`domain/identity/IdentityModels.java`（DTO/Actor）、`EmployeeMapper.java`（员工/授权）、`CredentialMapper.java`（密码）、`SessionMapper.java`（会话）、`AuthRateLimiter.java` 与 `RateLimitMapper.java`（数据库限频）、`TokenCodec.java`（随机值/摘要）、`SessionService.java`、`AdminAuthService.java`、`IdentityController.java`；`config/BearerFilter.java`、`SecurityConfig.java`；`V2__identity.sql`；测试 `identity/IdentityIT.java`。

接口：`SessionService.authenticate(String):Actor`、`issue(String,String):Session`、`revoke(String)`；`Actor(String employeeId,String sessionHash,String workspace,Identity identity)`。身份读取从 EmployeeMapper 动态聚合，不缓存权限。

- [ ] 写 HTTP 测试并运行红灯：
```java
String token=login(username,password);
mvc.perform(get("/api/identity/me").header("Authorization","Bearer "+token))
 .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(employeeId));
assertNotEquals(token, jdbc.queryForObject("select token_hash from auth_session where employee_id=?",String.class,employeeId));
post("/api/identity/logout",token,Map.of()).andExpect(status().isOk());
me(token).andExpect(status().isUnauthorized());
```
- [ ] 创建迁移 employee/version/phone/enabled，凭据 username unique、verified_phone unique 两端、role/platform grant 和范围、会话 token_hash unique、bootstrap_lock、challenge、rate-limit。时间使用 Instant / UTC。
- [ ] BCrypt 密码比较（未知用户名执行虚拟 hash 比较），账号/IP 固定窗口限频由 upsert+行锁维护；随机 token 32 bytes URL-safe，只持久化 SHA256。
```java
byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
```
- [ ] 临时密码过滤仅开放 me/password/logout；修改当前密码必须匹配、至少12字符、撤销所有会话；退出重复安全。
- [ ] `mvn -Pmysql-it -Dtest=IdentityIT test`；验证错误密码/限频/过期/停用/密码重设。补首管理员 `BootstrapAdminService` 和 CLI `BootstrapAdminCommand`，行锁保护一次初始化、没有默认密码，CLI 非 Web 且成功退出。

## 任务二：独立能力、员工维护、撤权

文件：`AuthorizationService.java`、`EmployeeService.java`、`EmployeeController.java`，扩充上述 Mapper 和测试。

接口：`require(Actor,String,String):Grant`（业务能力映射到角色）；`requirePlatform(Actor,String,String):PlatformGrant`；`requireWorkspace(Actor,String):Grant`。返回授权用于后续模块数据过滤。所有方法重读员工和授权，敏感服务事务可调用 `lockActor` 取得员工行锁。

- [ ] 测试仓库 A + overview ALL 不可用仓库角色访问 B，查看 ALL + 导出 A 不可导出 B，非员工管理员访问员工接口 403。
```java
assertThrows(ApiException.class,()->authorization.require(actorWarehouseA,"ORDER_REVIEW","B"));
assertThrows(ApiException.class,()->authorization.requirePlatform(actorViewAll,"REPORT_EXPORT","B"));
```
- [ ] 列表分页过滤 keyword/enabled；详情、创建、更新和 credentials 仅 EMPLOYEE_MANAGE ALL。字段显式校验，重复准入手机号/用户名返回409；expectedVersion更新冲突40902。
- [ ] 平台 grant 不能超过操作者自己的对应能力和范围；EMPLOYEE_MANAGE ALL 可委派业务角色但不获得业务执行权；停用保留授权，所有入口拒绝。全局锁串行化授权编辑，最后可用 EMPLOYEE_MANAGE ALL 不可停用或撤销。
- [ ] 修改准入 phone 不改 verified_phone；credentials 临时密码必须改密并撤销管理会话，角色修改对现有 token 实时生效。
- [ ] 在 IdentityIT 通过 HTTP 创建、改权限、改手机号、停用、旧版本冲突、凭据重设；重复执行数据库保留历史测试数据。

## 任务三：微信交换、短信挑战

文件：`domain/integration/WechatProvider.java`、`SmsProvider.java`、`AuthProviderConfiguration.java`；`MiniAuthMapper.java`、`MiniAuthService.java`、`MiniAuthController.java`；测试 `identity/MiniAuthIT.java`、`identity/AuthProviderTest.java`。

接口：`WechatProvider.exchange(String):WechatIdentity`；`SmsProvider.send(String,String)`；`MiniAuthService.exchange(String)`、`send(SmsInput,String)`、`verify(VerifyInput)`。短信码 hash 为 HMAC-SHA256(server secret,challengeId+phone+code)，绑定 token SHA256。

- [ ] 写测试：未绑定微信返回 PHONE_REQUIRED；短信 LOGIN 直接登录准入员工；手机号录入不算验证；验证码消费后二次请求401/422；无 token 的 BIND 失败。
```java
String challenge=send(phone,"LOGIN",null);
verify(challenge,phone,configuredCode,null).andExpect(status().isOk());
verify(challenge,phone,configuredCode,null).andExpect(status().isUnauthorized());
```
- [ ] 发送手机号规范化（允许 +86），每号码60秒/每小时10次，IP每小时30次；挑战5分钟、错误5次，binding10分钟。参数可配置；失败计数在独立事务提交，不因异常回滚。
- [ ] 行锁消费 challenge 与 binding，微信唯一关系、手机号唯一关系不自动合并；binding 锁定首次手机号，不能切换号码。停用、准入变化均在验证时再检查。
- [ ] 测试直接 SQL 调整 expires_at 验证过期，错误次数达到上限，号码/IP限频，并发消费只有一个成功，已有微信/手机号冲突。
- [ ] 配置化 mock code / app id，不接受客户端自选员工ID，微信模拟 code 由明确配置匹配固定 openid；生产模拟启动抛错。真实提供者未配置返回503。

## 任务四：验证与交付

- [ ] `mvn test` 全部单元/契约测试；`MYSQL_TEST_URL=... mvn -Pmysql-it test` 真实MySQL运行身份/迁移测试；`mvn package` 构建。真实凭据从环境传入，不写计划或日志。
- [ ] `git diff --check`；逐项 review：无默认密码、随机凭据摘要存储、授权实时读取、事务/限频并发、临时密码、手机分离、prod模拟禁用。
- [ ] server README 添加初始化、环境属性与模拟边界；记录 /tmp/task-hub-server-auth-report.md 的实际结果/阻塞，不声称真实微信短信通过。

## 执行记录（2026-09-20）

- 已实现任务一至三的身份、会话、员工、逐角色/平台授权、挑战及CLI代码；代码均位于上列单职责文件，未提交/推送。
- 红灯已观察：原边界3项均403；微信短信端点缺失4项失败；退出后服务层旧Actor仍授权、密码空格被裁剪、停用再启用复活旧token、Bearer数据库异常未规范化，均有针对性回归后转绿。
- 完整命令 `MYSQL_TEST_URL='jdbc:mysql://127.0.0.1:3306/task_hub_delivery_test?serverTimezone=UTC' MYSQL_TEST_USER=root mvn '-Dtest=*Test,*IT' test`：36项通过，0失败/错误/跳过。测试库使用已有专用MySQL 9.6，V1→V2与重复迁移通过，未清库。
- `mvn package`：通过；`git diff --check`：通过。日志分别位于 `/tmp/task-hub-auth-final-tests.log`、`/tmp/task-hub-auth-package-final.log`。
- CLI 以非Web方式运行实际数据库，已有管理员时退出1并拒绝重复初始化；未打开HTTP监听。首管理员首次成功和两个CLI并发初始化未在真实MySQL执行（当前专用库已有测试管理员且不得清库）；对应服务单元测试覆盖无默认密码和重复初始化保护，不冒充真实并发验收。
- 临时密码、修改密码撤销所有会话、凭据重设撤销管理会话、停用后重新启用不能恢复旧会话均有验证。
- EMPLOYEE_MANAGE ALL 可委派业务角色以完成准入，但不因此获得日常执行权；平台能力仍逐项约束不可越级。此解释避免首管理员没有工人角色导致无法创建工人。
- 真实微信短信、目标MySQL 8.4、部署环境备份恢复未验证；主数据存在性引用和登录/员工变更审计随S05/S06集成，未把本切片当成完整路线图。
- 待root独立review；本地自审已处理会话撤销、密码空格、并发短信消费、手机号唯一冲突及错误脱敏问题。
