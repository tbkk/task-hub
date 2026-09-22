# Task Hub 服务端

Java 17、Spring Boot 3.5.6、MyBatis 3.0.5、MySQL Connector/J 9.4.0。Maven 3.6.3 及以上；H2 仅用于测试，不替代 MySQL 验收。

## 启动与数据库

```bash
mvn spring-boot:run
mvn package
java -jar target/task-hub-server-0.0.1-SNAPSHOT.jar
```

数据库环境变量：`MYSQL_HOST`（localhost）、`MYSQL_PORT`（3306）、`MYSQL_DATABASE`（task_hub）、`MYSQL_USER`（taskhub）、`MYSQL_PASSWORD`（无默认密码）。HTTP 端口 `SERVER_PORT` 默认为 8080。连接详情和秘密不应进入普通日志。

Flyway 通过 `TASKHUB_MIGRATIONS_ENABLED=true` 显式启用；local profile 开启迁移。V1 兼容元数据表，V2 新建身份表；没有自动 baseline 或 clean。已有非空库应先核对结构并受控 baseline，不重建数据库代替升级。默认不执行迁移时可以无数据库启动，ready 会报告依赖不可用。

`GET /api/health` 不访问数据库；`GET /api/ready` 检查 `application_metadata.schema_version`。受保护接口缺少/失效 Bearer 返回 40101；停用返回 40301；权限不足返回 40302。API 响应统一 `{code,message,data}`。ID 为字符串，版本为整数。

## 首管理员受控初始化

没有公开注册接口、默认用户名或默认密码。先设置 `BOOTSTRAP_ADMIN_USERNAME`、`BOOTSTRAP_ADMIN_PASSWORD`、`BOOTSTRAP_ADMIN_NAME`、`BOOTSTRAP_ADMIN_PHONE` 和数据库环境变量。密码至少12字符且UTF-8不超过72字节；通过安全输入或部署秘密注入，不把真实值写入命令历史/文档。

```bash
TASKHUB_MIGRATIONS_ENABLED=true java \
  -Dloader.main=com.taskhub.domain.identity.BootstrapAdminCommand \
  -cp target/task-hub-server-0.0.1-SNAPSHOT.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher
```

CLI 使用 `WebApplicationType.NONE`，不监听 HTTP。事务锁定初始化记录；已有管理员或已初始化标记时拒绝重跑。创建员工、BCrypt 凭据和全部平台能力 ALL；不自动授予日常业务角色。首次密码必须修改，CLI 不输出密码或 token。成功后移除初始化秘密。数据库备份和应用兼容回退是迁移恢复方式，不修改已应用迁移。

## 身份与授权

- 管理登录 `POST /api/admin/auth/login`；`GET /api/identity/me`；`POST /api/identity/logout`；`POST /api/identity/password`。
- token 为32字节随机值，数据库仅存 SHA-256；默认8小时，无自动续期。退出撤销当前会话；改密撤销所有会话；临时密码仅允许 me/password/logout。
- 员工 CRUD `/api/admin/employees` 及 `/{id}/credentials` 需要 EMPLOYEE_MANAGE ALL。账号停用保留历史授权但拒绝访问。设置临时密码撤销管理会话；授权修改对已登录 token 实时生效。
- `X-Workspace` 仅选择已有角色，不授予角色。平台能力及各自范围独立，REPORT_VIEW ALL 不扩大 REPORT_EXPORT 的仓库范围；admin 仅派生展示字段。
- 管理员录入 `phone` 是准入信息；`verifiedPhone` 仅验证码验证后建立，改准入手机号不迁移已验证关系。冲突拒绝自动合并。
- 登录账号默认5次/15分钟、IP100次/15分钟限频；账号成功登录重置账号计数，IP保留请求计数。短信每号码60秒间隔、10次/小时、每IP30次/小时；验证码5分钟有效、最多5次失败、单次消费。

## 外部身份模拟边界

未配置真实微信/短信适配器时返回503，不自动降级为模拟。启用模拟必须同时满足 local/test profile 和显式 `taskhub.auth.mock-enabled=true`。prod/production profile 即使同时指定 local 仍拒绝模拟启动。

以下属性通过环境/秘密配置注入，不在仓库写固定码：

| 属性 | 用途 |
| --- | --- |
| `taskhub.auth.mock-enabled` | 显式启用模拟，默认 false |
| `taskhub.auth.mock-wechat-code` | 本地交换入口唯一接受的模拟 code |
| `taskhub.auth.mock-wechat-openid` | 模拟微信身份，不能由客户端自选员工 ID |
| `taskhub.auth.mock-wechat-app-id` | 模拟 appId，默认 local-simulator |
| `taskhub.auth.mock-sms-code` | 开发配置的六位模拟码，API 不返回 |
| `taskhub.auth.sms-hmac-secret` | 至少32字符的服务端挑战HMAC秘密；需跨重启保留 |
| `taskhub.auth.session-seconds` | 默认28800 |
| `taskhub.auth.login-attempts` | 默认5 |
| `taskhub.auth.sms-ttl-seconds` | 默认300 |
| `taskhub.auth.sms-interval-seconds` | 默认60 |
| `taskhub.auth.sms-phone-hour-limit` | 默认10 |
| `taskhub.auth.sms-ip-hour-limit` | 默认30 |
| `taskhub.auth.sms-max-attempts` | 默认5 |

可通过 Spring 参数指定非秘密开关，例如 `--spring.profiles.active=local --taskhub.auth.mock-enabled=true`；秘密从环境或私有配置读取。手机号支持大陆11位及 `+86` 前缀并统一规范化。短信 LOGIN 不要求微信；BIND 要求服务器签发的10分钟绑定 token，首次发送即限定手机号，验证成功单次消费。微信/短信 provider 是外部适配接口，实际会话、准入、挑战和绑定均存 MySQL。

## 验证

```bash
mvn test
mvn package
# 先显式设置 MYSQL_TEST_URL、MYSQL_TEST_USER、MYSQL_TEST_PASSWORD：
mvn -Dtest=MigrationIT,IdentityIT,AuthorizationIT,MiniAuthIT test
```

集成测试拒绝非 `task_hub_*_test` 专用库；测试使用UUID/独立IP数据，不清库。单测覆盖认证边界和生产模拟禁用；真实MySQL测试覆盖HTTP会话、密码、停用、员工版本、能力范围、挑战过期/次数/消费、并发消费、绑定与验证冲突。

本轮实际数据库为本机 MySQL 9.6，目标8.4兼容性尚未实测。微信、短信真实服务和真机验证未执行；模拟通过不代表真实接入完成。后续订单/主数据/审计仍按交付计划实现，本文件不宣称全部业务接口完成。

### 车辆模拟与站内消息投递

仅 `local` / `test` 可显式配置 `SIMULATOR_ENABLED=true` 使用持久化车辆模拟提供者；生产（包括混合生产 profile）拒绝启用。模拟场景管理要求 `INTEGRATION_MANAGE` 全范围权限，超时记录保持 UNKNOWN，受理不代表门已开或车辆已运行。

`NOTIFICATIONS_ENABLED=true` 启用每3秒一次的持久化事件投递（每批最多100条），默认关闭。失败事件保留并延迟重试，成功通知和投递状态同事务；此项只提供站内消息，不发送业务短信。启动前必须启用并完成数据库迁移。
