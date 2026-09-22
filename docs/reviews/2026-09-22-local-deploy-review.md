# 本地部署脚本验证与审查

日期：2026-09-22。基线：`feature/base` / `8f65de8` 上的未提交部署脚本；保留此前文档修改。本次不提交、推送或合并。

## 实现

新增根 `deploy.sh`、`deploy/local.nginx.conf`、`deploy/local.env.example`、`harness/tests/test_local_deploy.py`。管理端 18090、H5 18091、后端 18092，均监听本机回环；运行目录 `/private/tmp/task-hub-local-deploy`。根 `.env.local` 为忽略文件，权限实测 600，秘密不写入此记录。

## 已执行验证

| 项目 | 命令或操作 | 实际结果 |
| --- | --- | --- |
| 脚本边界 | `python3 -m unittest discover -s harness/tests -p 'test_local_deploy.py' -v` | 10 项通过：非法命令/配置/端口/运行目录、配置不执行命令替换、锁、伪造 PID、端口冲突、真实 Nginx 停止、丢失 PID 拒绝接管 |
| 语法与格式 | `bash -n deploy.sh`、`git diff --check` | 通过 |
| 管理端 | deploy 内执行 `npm test`、`npm run build` | 15 测试通过，类型检查/生产构建通过 |
| 小程序 H5 | deploy 内执行 `npm test`、`npm run typecheck`、`npm run build:h5` | 35 测试通过，类型检查/H5生产构建通过 |
| 后端 | deploy 内执行 `mvn package` | 默认 19 项测试通过，JAR 打包成功；未执行业务 `*IT` 全套 |
| Nginx | 独立配置 `nginx -t` | 通过，未修改全局配置 |
| 真实部署 | `./deploy.sh deploy`，修复后最终完整重跑 | 退出 0，两个站点与 API 探测通过 |
| HTTP | 首页、首页引用的管理端 2 个/H5 3 个 JS/CSS、管理端深层路由、两站 `/api/ready` 和 `/api/identity/me` | 页面/资源 200，资源 MIME 正确，深层路由返回 HTML，ready code=0，无认证接口 401 |
| 浏览器 | Codex 浏览器打开管理端和 H5 | 显示内部账号登录及小程序登录页；不是仅检查 HTTP 200 |
| 生命周期 | status、restart、stop、再 restart，最后 deploy | 运行时状态正常；停止后 status 非零；后端独立会话在执行命令结束后继续存活；最终保持部署运行 |
| 数据保留 | 重启前后只读统计员工/订单 | 均为 3 名员工、6 条订单；MySQL PID 824 保持运行 |
| 迁移 | 演示库启动日志 | MySQL 9.6，V1–V11 校验通过；已有版本 11，无需新增迁移；无清库/重建 |
| 日志与配置 | logs 命令、忽略规则与文件权限检查 | 命令成功；`.env.local` 被忽略且权限 600 |

环境：macOS，本机 Node 25.8.2、npm 11.11.1、Java 17.0.18、Maven 3.6.3、Homebrew Nginx、MySQL 9.6。最终构建日志 `/tmp/task-hub-local-deploy-final.log`，运行日志见部署目录；临时文件可能被系统清理。

## 问题、修复与复审

1. 初始边界测试在脚本尚不存在时 6 项失败；实现后通过。随后增加真实 Nginx 与端口测试，将边界覆盖扩展为 10 项。
2. 本机 Nginx master 进程标题末尾填充空格导致归属误判，已去除尾部空白并增加真实 Nginx 停止回归。监听检查同时核对 worker 的 master，避免同一实例被当作端口冲突。
3. 初始后台 Java 在工具执行会话结束后退出，改为 Python Popen 独立会话且标准输入断开；跨工具调用状态检查确认仍运行。
4. Bash 中文紧邻变量名导致参数解析失败，已对 PID 使用花括号；端口冲突回归及 status 实测通过。
5. 独立 review 指出 PID 文件丢失时旧后端可能为新发布提供健康结果（P2）。已要求监听进程匹配登记 PID，并在 ready 后及最终验收核对进程归属；新增丢失 PID 的真实隔离 Nginx 回归。独立只读复审确认闭合。

## 尚未执行与边界

本次不等于业务全链路验收：未重新运行 MySQL 业务 IT 全套、微信构建/真机、实车、完整业务角色交互、性能、备份恢复、目标 MySQL 8.4 或生产 HTTPS。Flyway 对 MySQL 9.6 的兼容提示和前端包体积提示仍存在。部署启用本地模拟和站内消息调度，不使用真实微信/短信/车辆提供者。

历史版本产物保留；启动阶段失败会停止新进程，不自动回退数据库或保证自动恢复旧服务。临时目录被清理后需重新 deploy。强制终止遗留锁需要人工核对后清理。

## 后续 --force 变更验证

用户要求参考 Puck 的 `--force` 后端强制部署行为。新增标志仅用于 deploy，省略动作等同 deploy；普通命令保持归属检查，Nginx/前端端口/锁不绕过。构建与 Nginx 配置预检通过后才可能终止后端 PID 记录或后端端口监听者（包含非托管进程），先 TERM，15 秒未退出则 KILL。

新增测试先因参数尚不支持而失败，随后使用独立临时端口和真实隔离子进程验证终止及忽略 TERM 的 KILL 升级。构建/预检测试仅替换构建和检查结果，不替换真实终止逻辑。补充拒绝其他命令、前端冲突、锁及失败保留后端检查；最终完整测试记录见 `/tmp/task-hub-force-tests-final.log`。

独立只读审查未发现高/中影响问题，并提示预检失败测试缺口，已补齐。实际 `./deploy.sh --force deploy` 退出 0，重新执行前端测试/构建和后端默认测试/打包；`./deploy.sh status` 成功，保持两站及 API 可用。部署日志 `/tmp/task-hub-force-deploy.log`；本次未对真实无关应用执行强制终止，非托管场景使用测试子进程验证。
