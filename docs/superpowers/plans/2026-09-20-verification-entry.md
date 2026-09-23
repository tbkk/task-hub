# 统一验证入口 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 提供从任意目录执行三端验证的稳定入口，不启动或停止已有服务。

**Architecture:** Bash 脚本定位仓库根目录，按参数顺序调用已有工程命令，遇失败立即退出。测试、类型检查和两类小程序构建均保留。

**Tech Stack:** Bash、npm、Maven、Git。

## Global Constraints

- 当前继续 `feature/base`，不创建或切换分支。
- 当前不使用 Docker。
- 不覆盖用户及其他协作者的未提交修改。
- 编译通过不等于真实接口、微信真机或实车验收通过。

---

### Task 1: 模块验证入口

**Files:**
- Create: `harness/scripts/verify.sh`
- Create: `harness/README.md`

**Interfaces:**
- Consumes: `miniprogram/package.json`、`admin-web/package.json` 的既有 scripts 和 `server/pom.xml`。
- Produces: `bash harness/scripts/verify.sh [all|mini|admin|server]`，成功 0，命令失败传播非零，非法模式 2。

- [ ] **Step 1: 验证既有模块基线**

```bash
cd miniprogram && npm test && npm run typecheck
cd ../admin-web && npm test && npm run build
cd ../server && mvn test
```

预期：分别 9、3、6 项现有测试通过。当前基线在 2026-09-20 验证通过；后续测试数量随功能增长。

- [ ] **Step 2: 写入入口代码**

```bash
#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"
case "${1:-all}" in
  mini) (cd miniprogram && npm test && npm run typecheck && npm run build:h5 && npm run build:mp-weixin) ;;
  admin) (cd admin-web && npm test && npm run build) ;;
  server) (cd server && mvn test && mvn package -DskipTests) ;;
  all)
    bash "$repo_root/harness/scripts/verify.sh" mini
    bash "$repo_root/harness/scripts/verify.sh" admin
    bash "$repo_root/harness/scripts/verify.sh" server
    git diff --check
    ;;
  *) printf '%s\n' '用法: harness/scripts/verify.sh [all|mini|admin|server]' >&2; exit 2 ;;
esac
```

- [ ] **Step 3: 写入入口说明**

`harness/README.md` 内容：

```markdown
# 验证工具

从仓库根目录运行 `bash harness/scripts/verify.sh` 验证三端。参数 `mini`、`admin`、`server` 只验证对应模块；脚本支持从任意目录用绝对路径运行。

脚本遇失败立即停止，不安装依赖、不启动或停止服务、不改数据库。先分别在前端运行 `npm ci`，并准备 Java 17、Maven 和项目要求的 Node。

这些命令覆盖自动测试、类型检查和构建。数据库迁移、浏览器、微信真机、车辆和备份恢复需要各自的验证记录，不能由构建结果替代。
```

- [ ] **Step 4: 验证语法、非法参数与工作目录无关性**

```bash
bash -n harness/scripts/verify.sh
bash harness/scripts/verify.sh invalid
cd /tmp && bash /Users/qin/Projects/task-hub/harness/scripts/verify.sh admin
```

预期：语法成功；非法参数显示用法并退出 2；从 /tmp 成功完成管理端验证。脚本为既有命令薄封装，不额外编写镜像实现的测试。

- [ ] **Step 5: Review 并记录验证**

核对 `set -euo pipefail`、根目录定位、参数白名单和递归绝对路径；记录结果到执行进度。提交/推送遵循当前用户授权，不因脚本完成自动合并。
