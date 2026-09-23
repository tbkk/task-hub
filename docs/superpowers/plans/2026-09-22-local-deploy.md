# 本地实验部署实施计划

依据：`../specs/2026-09-22-local-deploy-design.md`，用户已确认。当前会话继续执行，不另行询问执行方式，不提交或合并。

目标：构建管理端/H5/Java，通过独立 Nginx 部署到 18090/18091，Java 使用 18092；复用演示库。

## 任务 1：命令与进程边界

文件：根 `deploy.sh`、`harness/tests/test_local_deploy.py`、`deploy/local.env.example`。

- [x] 编写并运行失败测试：非法命令返回 2；不安全运行目录/端口/配置拒绝；配置不执行命令替换；伪造 PID 不终止其他进程；部署锁阻止并发。
- [x] 实现 Bash 命令入口和受限配置解析，支持 deploy/restart/stop/status/logs；环境配置不打印秘密，PID 核对命令路径。
- [x] 执行 `python3 -m unittest discover -s harness/tests -p 'test_local_deploy.py' -v` 和 `bash -n deploy.sh`，确认边界通过。

## 任务 2：构建与本地发布

文件：`deploy.sh`、`deploy/local.nginx.conf`。

- [x] 实现依赖检查、独立发布目录、构建完成后切换；所有 npm 构建指定 `/api`，H5 指定服务端认证，避免本地配置改变部署目标。
- [x] 实现 Java local 启动、ready 检查、Nginx 配置测试与启动、两个站点/API验收；构建阶段失败不停止旧实例，启动阶段失败停止本次新进程。
- [x] 创建忽略的 `.env.local`，只用于本机演示；持久化随机挑战秘密及模拟码。执行 `./deploy.sh deploy`。
- [x] 检查首页/资源/深层路由/API，运行 restart/status/stop；验证 MySQL与其他监听未受影响，再启动实验环境。

## 任务 3：审查与交付

文件：`deploy/README.md`、根 `README.md`、`docs/README.md`、总体路线图、验证记录。

- [x] 审查配置白名单、端口/PID安全、锁清理、失败状态、真实服务边界；修复并重跑受影响检查。
- [x] 补齐操作步骤、依赖、登录与开发者工具访问说明；记录真实命令、环境、结果及未执行范围。
- [x] 执行 `git diff --check`；确认未添加秘密、构建产物，不提交或推送。

执行记录：`../../reviews/2026-09-22-local-deploy-review.md`。本机实验部署完成，目标 MySQL 8.4、微信真机、实车和备份恢复仍未验证。

## 后续变更：--force

用户明确要求参考 Puck 增加强制部署，按更新后的设计执行。

- [x] 增加 `--force deploy` 及 `--force` 默认部署，其他命令拒绝此标志。
- [x] 构建/Nginx预检完成后，对后端 PID 和端口监听者执行 TERM→15秒→KILL；保留普通部署、Nginx和锁检查。
- [x] 增加真实隔离进程终止、KILL升级、前端冲突、锁、构建与Nginx预检失败保留后端回归。
- [x] 独立代码审查；实际执行 `./deploy.sh --force deploy`，随后 status 验证两个入口和 API，保持本地服务运行。
