# 部署配置

## 推荐：本机实验部署

根目录 `deploy.sh` 使用独立 Nginx 实例、管理端/H5 静态构建和 Java JAR，复用本机 MySQL，无需 Docker。不修改全局 Nginx 配置；普通部署不强制终止其他进程，显式 `--force` 的后端终止范围见下文。

```bash
cd /Users/qin/Projects/task-hub
./deploy.sh deploy     # 测试、构建并部署；不带参数也执行 deploy
./deploy.sh --force deploy # 强制处理后端端口占用/后端 PID 异常；也可写 --force
./deploy.sh status     # 进程归属、两个页面及 API 检查，异常返回非零
./deploy.sh restart    # 读取配置，复用产物重启
./deploy.sh logs       # 最近后端和 Nginx 错误日志
./deploy.sh stop       # 停止本脚本托管服务，保留数据库与发布文件
```

| 入口 | 默认地址 |
| --- | --- |
| 管理平台 | http://127.0.0.1:18090/ |
| 小程序 H5 | http://127.0.0.1:18091/ |
| 后端 | http://127.0.0.1:18092/api/ready |

两个站点均将 `/api/` 保留前缀代理到后端。默认只监听本机回环。已有页面能力见总体路线图，部署不会补齐占位功能。

### 首次准备与配置

需要 Node.js 22.12+、npm、Java 17、Maven、Python 3、Nginx、curl、lsof 和可连接的 MySQL。脚本不自动安装依赖；首次分别在 `admin-web`、`miniprogram` 执行 `npm ci`。默认 Nginx MIME 文件为 `/opt/homebrew/etc/nginx/mime.types`，其他安装方式可用 `TASKHUB_DEPLOY_MIME_TYPES` 指定。

本机此次已经生成根 `.env.local`（权限 600），复用 `task_hub_demo_test`。新环境复制 `deploy/local.env.example` 到根 `.env.local`，填写数据库连接、六位 `TASKHUB_AUTH_MOCK_SMS_CODE`、`TASKHUB_AUTH_SMS_HMAC_SECRET`（可用 `openssl rand -hex 32` 生成）、本地 `TASKHUB_AUTH_MOCK_WECHAT_CODE` 和 `TASKHUB_AUTH_MOCK_WECHAT_OPENID`。示例不含实际验证码、密码或秘密。

配置只支持白名单 `KEY=VALUE` 和成对单/双引号；不支持 export、行尾注释、变量展开或命令替换。文件不存在时可完全通过环境传入；已经导出的同名环境变量优先。`TASKHUB_DEPLOY_ENV_FILE` 可指定其他配置文件。端口可通过示例中的三个 `TASKHUB_DEPLOY_*_PORT` 调整。改运行目录会建立另一个实例，应先停止原运行目录对应实例。

脚本固定后端 local profile，启用模拟身份、模拟车辆和站内消息投递，Java 端仅监听 127.0.0.1；两个前端固定 `/api`，H5 使用服务端认证。业务数据真实落库；模拟码不会发送短信。HMAC 秘密跨重启保留，修改会使旧挑战验证失效。此脚本只用于本地实验。

管理端使用已有内部账号密码，不自动重设或初始化管理员。H5 使用「手机号验证码登录」，输入已准入员工手机号，获取验证码后填写 `.env.local` 中的模拟码。微信真实登录 provider 尚未实现，不使用微信登录按钮验证真实身份。

### 产物、失败与恢复

默认运行目录 `/tmp/task-hub-local-deploy`（macOS 规范路径为 `/private/tmp/task-hub-local-deploy`），可用 `TASKHUB_DEPLOY_RUNTIME` 修改。发布按时间存入 `releases/`；`current` 指向当前产物，日志和 PID 分别保存。临时目录可能在系统清理时消失，届时重新 deploy；私有配置仍在仓库根。

所有构建成功及 Nginx 预检通过后才停旧服务。启动阶段失败时清理本次新进程，保留日志与产物，修复配置后重新 deploy/restart；不承诺自动回滚数据库。发布目录保留历史版本，不自动删除。后端日志追加保存，使用者可在停止后自行归档。

Flyway 在 local 启动时执行增量升级，不清库、不重建库、不注入演示数据。此次演示库已经是 V11，启动仅校验，无新增迁移。新的迁移部署前应先备份；回退程序不自动撤销迁移。

普通部署遇到端口冲突、PID 归属不符或 PID 丢失但监听仍存在时失败退出。显式 `--force deploy` 参考 Puck 行为：构建及 Nginx 预检成功后，终止后端 PID 文件记录的存活进程（包括归属不符者），并清理配置的后端端口监听者；先发送 TERM，15 秒未退出则发送 KILL。会打印目标 PID 和端口，可能终止其他应用，使用前核对后端端口及 PID 文件。前端端口和 Nginx 不强制接管，`--force restart/stop/status/logs` 返回用法错误；构建失败不提前杀进程。

`lock/` 阻止并发变更，--force 不绕过锁；命令正常退出或接收 INT/TERM 会释放锁，强制终止后需确认没有部署命令运行，再手动移除残留锁。运行目录只支持用户目录或 /tmp 下的不含空格路径。

### 微信开发者工具

此脚本不上传或部署微信包；要预览微信版本，在小程序目录另外运行：

```bash
VITE_AUTH_MOCK=false VITE_API_BASE_URL=http://127.0.0.1:18090/api npm run dev:mp-weixin
```

配置自己的 AppID 后，开发者工具导入 `miniprogram/`，指向 `dist/dev/mp-weixin/`；本机调试关闭合法域名校验，使用手机号验证码登录。此地址仅用于电脑模拟器，手机真机需要手机可达的接口地址及相应域名/HTTPS配置。

### 验证范围

`deploy` 运行管理端测试/类型检查/构建、小程序测试/类型检查/H5构建、后端 `mvn package` 默认单测。**默认 Maven 不运行 `*IT` 业务集成测试**，不得把它当作数据库业务全套验证；需另用独立 `task_hub_delivery_test` 运行集成测试，不能连接演示库执行。

部署验证记录见 `../docs/reviews/2026-09-22-local-deploy-review.md`。微信真机、实车、目标 MySQL 8.4、备份恢复和生产 TLS 尚未由此次部署验收。

## 可选：Docker Compose（历史配置）

以下为已有 Docker Compose 入口，本次没有执行或验证。生产域名、TLS、备份和真实鉴权在上线前另行配置。

## 启动

在项目根目录执行（需 Docker Engine／Docker Desktop 和 Compose v2）：

```sh
cp deploy/.env.example deploy/.env
# 编辑 deploy/.env，为应用用户和 root 设置不同密码
docker compose --env-file deploy/.env -f deploy/compose.yaml config --quiet
docker compose --env-file deploy/.env -f deploy/compose.yaml up --build -d
docker compose --env-file deploy/.env -f deploy/compose.yaml ps
```

打开 `http://localhost:8088`。`/api/` 由 Nginx 保留前缀转发给后端；数据库和 Java 端口默认不暴露到宿主机。入口仅绑定本机，当前配置用于本地联调。

```sh
curl -f http://localhost:8088/api/health
curl -f http://localhost:8088/api/ready
docker compose --env-file deploy/.env -f deploy/compose.yaml logs --tail=100 server
```

`health` 仅确认 Java 进程存活；`ready` 确认 MyBatis 能读取数据库元数据。Compose 按 MySQL → 后端就绪 → 管理端依次启动；业务接口默认拒绝访问。

## 数据与停止

```sh
docker compose --env-file deploy/.env -f deploy/compose.yaml down
```

停止不会删除命名数据卷。`database/init/` 仅在空数据目录初始化时运行；已有数据库需显式执行后续迁移。不要使用 `down -v` 清理有业务数据的环境。

`.env` 不提交，前端 `VITE_*` 变量会打包进客户端，不能存放微信、短信、九识凭证。小程序不是 Nginx 部署产物，应由微信开发者工具上传，并使用已配置合法域名的 HTTPS 后端地址。

## 验证边界

本轮执行环境未安装 Docker，容器构建、Compose 启动及真实 MySQL 联调需在具备 Docker 的环境按上述命令验证。源码构建及后端测试结果见根 README。
