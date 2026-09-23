# 工人申请与订单切片实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: 使用 executing-plans 按任务执行；本切片已授权直接实施，不提交推送。

**Goal:** 完成 P03–P09/P19 的真实 API 页面与可复核模拟界面验证。

**Architecture:** 目录与订单分别放在 features/catalog、features/order；页面只编排交互，共享 request 注入内存会话和 worker 工作区。所有写入等待服务端结果，网络失败保留相同正文及幂等键。

**Tech Stack:** Vue 3、uni-app、TypeScript、SCSS、Node test/tsx。

## 全局约束

- 引用 2026-09-20-mini-delivery.md Task 3/4 与 delivery-contract.md，不改变既有需求。
- 分支 feature/base；只修改小程序和本计划；不得修改其他工作区逻辑。
- 仓库以 GET /catalog/warehouses 的唯一归属为准，空目录提示联系管理员；不从 grants 推导。
- 先读 Figma 18:123/171/221、27:240/265/287/305、39:281，再转换为项目 Vue 组件。
- 手机号必填；未绑定接收人可保存但不能取货；仅申请人且服务端 allowedActions 含 ORDER_CANCEL 时显示取消。
- 窄屏 320/390 验证；HTTP 拦截只用于测试，真实 MySQL 联调与微信真机单独列明。

## 任务 1：目录与订单规则、接口

文件：新增 `miniprogram/src/features/catalog/{model,api}.ts`、`features/order/{model,api,submission}.ts`；测试 `tests/worker.test.ts`。

- [x] RED：以 `node --import tsx --test tests/worker.test.ts` 验证模块缺失。断言未来半小时/服务端 available/remaining、字段长度和手机号、取消独立状态、仅申请人动作、网络重试正文与 key 一致。
- [x] CODE：`canSelectSlot(slot, now)` 同时判断未来、可用、容量和 30 分钟；`validateOrderDraft(draft,rules)` 返回字段错误；`Submission<T>.run(input,send)` 在未知结果时锁定 input/key，成功或明确业务拒绝才释放。
- [x] GREEN：目录 API 使用字符串参数编码，订单 API 使用 POST /orders 与 POST /orders/{id}/cancellations，测试完整通过。

## 任务 2：目录、时段与常用点页面

文件：新增 `subpackages/worker/{destination,slot,favorites}.vue`、`styles/worker.scss`。

- [x] CODE：目的地页按归属仓库加载，名称搜索；event channel 回传停靠点，时段页以服务端规则 bookingDays 和 Asia/Shanghai 日期加载；不可用时段禁选，选择时再次验证；收藏通过 PUT/DELETE 请求并显示 busy/error。
- [x] GREEN：测试停用收藏不可选但可移除，切换目的地清除原时段，加载/空/失败可重试。

## 任务 3：申请、列表、详情与取消

文件：替换 `subpackages/worker/index.vue`；新增 `{result,orders,order-detail,cancellation}.vue`；注册 pages.json、个人中心收藏入口。

- [x] CODE：表单包含 description/size/receiverName/receiverPhone/remark/stop/slot；服务端规则验证，42201 清除过期时段；未知提交只允许原样重试，草稿标记 dirty。
- [x] CODE：secondary tab 重定向 orders；状态精确筛选、关键字、分页与刷新；详情仅读取本订单及 scope 裁剪的 history，不请求批次成员。
- [x] CODE：取消页读取最新详情、原因和二次确认，提交 expectedVersion；40902 刷新后重新确认；原订单状态与 cancellation 独立展示。
- [x] GREEN：创建结果仅由服务端详情呈现，未匹配接收人提示、详情历史与取消状态检查。

## 任务 4：验证与 review

- [x] `npm test`、`npm run typecheck`、`npm run build:h5`、`npm run build:mp-weixin` 全部通过。
- [x] 在独立 5176 端口 HTTP 拦截验证完整申请、收藏、时段、分页、失败重试、取消；保存 320/390 截图及控制台/横向布局检查。
- [x] `git diff --check`，检查权限、幂等、数据隐私、未知结果不假成功；报告写 /tmp/task-hub-mini-worker-report.md，交 root review。

## 执行记录

- 2026-09-21：已按 skill 读取全部八个 Figma 节点；采用现有组件与 tokens，设计没有额外图像资产。

- 2026-09-21 最终验证：29 项测试、typecheck、H5/微信构建通过；Chrome HTTP 拦截闭环及 320/390 布局通过。真实 MySQL 工人业务联调、微信真机待依赖就绪；root 独立 review 已请求。
