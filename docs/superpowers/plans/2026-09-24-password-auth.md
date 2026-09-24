# 账号密码登录实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让小程序使用内部账号密码登录，并确保管理员重置密码后撤销员工全部旧会话、首次登录强制改密。

**Architecture:** 复用现有 `/api/admin/auth/login`、`/api/identity/password` 和 `mustChangePassword` 过滤器。新增小程序 auth API、登录表单和改密页；后端只补足“凭据重置撤销全部会话”的边界测试与必要实现。

**Tech Stack:** Spring Boot、MyBatis、MySQL、uni-app Vue 3、TypeScript、Vitest/Node test。

## Global Constraints

- 不存储明文密码、token 或验证码。
- 生产环境不启用 mock 登录。
- 临时密码会话只允许身份查询、改密和退出。
- 管理员重置密码撤销员工全部会话；员工主动改密也撤销全部会话。

### Task 1：后端密码会话回归

**Files:**

- Modify: `server/src/test/java/com/taskhub/identity/IdentityIT.java`
- Modify if needed: `server/src/main/java/com/taskhub/domain/identity/EmployeeService.java`, `SessionMapper.java`

- [ ] 写失败测试：同一员工创建 MINI 和 ADMIN 两个会话，管理员凭据重置后两个 token 都返回 401；临时密码旧会话仅可访问 me/password/logout。
- [ ] 运行 `set -a; source server/.env.test.local; set +a; mvn -Dtest=IdentityIT test` 确认 RED。
- [ ] 保持或补齐 `sessions.revokeAll(id)`，不得只撤销 ADMIN 会话。
- [ ] 复跑 IdentityIT，确认 GREEN，并运行 `git diff --check`。
- [ ] 提交 `test: cover password reset session revocation`。

### Task 2：小程序账号登录与首次改密

**Files:**

- Modify: `miniprogram/src/features/auth/api.ts`, `model.ts`, `session.ts`
- Modify: `miniprogram/src/pages/login/index.vue`, `pages.json`
- Create: `miniprogram/src/pages/password/index.vue`
- Test: `miniprogram/tests/password-auth.test.ts`

- [ ] 写失败测试：账号密码登录调用 `/admin/auth/login`；临时会话进入改密页；改密成功清理会话；登录失败保留账号。
- [ ] 运行 `cd miniprogram && npm test` 确认新增测试 RED。
- [ ] 实现 `loginWithPassword` 和 `changePassword` 适配，登录页默认显示账号密码表单，保留微信绑定入口。
- [ ] 在路由守卫和 App 启动路径识别 `mustChangePassword`，重定向 `/pages/password/index`；改密成功调用清理并回登录页。
- [ ] 运行小程序测试、类型检查、H5/微信构建。
- [ ] 提交 `feat(miniprogram): add password login and first password change`。

### Task 3：文档、集成验证与交付

**Files:**

- Modify: `docs/用户操作手册.md`, `docs/api/delivery-contract.md`, `docs/development/acceptance-matrix.md`
- Modify: `docs/superpowers/plans/2026-09-20-development-roadmap.md`

- [ ] 更新登录、管理员重置和首次改密步骤，明确短信不再是默认依赖。
- [ ] 运行后端 127 项集成测试、小程序测试与构建、管理端测试/构建。
- [ ] 做一次安全 review：无明文密码、无默认生产密码、重置撤销全部会话、临时会话边界正确。
- [ ] 合并到 `dev1.0`，在合并后的分支再次验证并推送。
