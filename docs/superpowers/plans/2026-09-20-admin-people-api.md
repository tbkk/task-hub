# 人员管理数据适配实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans。按步骤测试并执行，不提交推送。

**Goal:** 提供分页员工查询、详情、编辑与内部凭据维护的统一适配，编辑草稿不修改现有授权。
**Architecture:** 复用 request 和身份类型；页面只使用 people/api.ts。服务端是权限与数据真实性边界。
**Tech Stack:** Vue 3、TypeScript、Vitest。

## Global Constraints

- token 只在内存；业务数据不进入客户端 mock 仓库。
- 多角色编辑保留每个角色的数据范围；expectedVersion 来自读取版本。
- 页面不回显密码，不用手机号录入冒充验证。

## 任务：人员 API 与独立草稿

文件：`admin-web/src/features/people/api.ts`、`api.test.ts`。
接口：`listEmployees(query):Promise<Page<Employee>>`、`getEmployee(id)`、`saveEmployee(draft,id?)`、`resetCredentials(id,username,temporaryPassword)`、`employeeDraft(employee?)`。

- [ ] 增加回归：分页编码、更新 expectedVersion、多角色深拷贝不污染原对象、凭据不进入员工数据。
- [ ] `cd admin-web && npm test -- src/features/people/api.test.ts`：首次缺少模块失败。
- [ ] 实现集中 API：GET /admin/employees 与 /{id}，POST 新建，PUT 带版本更新，POST /{id}/credentials；所有 body 使用 JSON.stringify，路径参数 encodeURIComponent。
- [ ] 新增草稿默认 enabled=true/grants=[]/platformGrants=[]；已有数据只复制可编辑字段与 version，授权数组嵌套复制。
- [ ] 同一命令通过，再 npm run build；后续页面任务接入浏览器验收。

本文件为本切片接口与测试步骤，页面布局和联调单独记录。

## 人员列表与编辑接入

文件 `PeoplePage.vue` 与 router/index.ts。复用 Element Plus 卡片、表格、分页、抽屉与确认弹窗，保持当前浅灰背景、蓝色主操作。

- [ ] 列表查询在服务端分页；旧请求迟到不覆盖最新筛选；加载失败保留错误并可重试。
- [ ] 查看/编辑打开独立草稿，逐角色选择范围与仓库，平台能力只可勾选当前用户可委派项。
- [ ] 写前展示变更摘要，停用说明撤销访问并保留历史，冲突展示重新读取按钮；凭据单独弹窗不回显。
- [ ] 输入手机号只表示准入，验证状态只读。修改失败保留草稿，不吞掉错误。
- [ ] 浏览器验证新增、查询、编辑、取消、停用确认及多角色保留。仓库可选数据等待主数据 API，不构造本地仓库。
