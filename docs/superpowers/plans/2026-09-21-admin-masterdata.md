# 主数据管理页面实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans。当前分支实施，不提交推送。

**Goal:** 管理仓库、外部点位绑定、车辆绑定、硬件格口和仓库业务规则。
**Architecture:** 复用 Element Plus 表格/抽屉/确认、统一 request；资源类型和转换集中 model/api。外部ID只选服务端目录，硬件只同步既有定义，不手工造号。
**Tech Stack:** Vue3、TypeScript、Vitest。

## Global Constraints

- 管理平台不提供日常审批、派车、开门、取货操作。
- 更新发送 expectedVersion；冲突后重新读取，未知结果查询后核对。
- 目录来自模拟提供者时明确说明，不写实际车辆接入成功。

## 实施任务

- [ ] `features/masterdata/api.test.ts` 验证写body只含资源可编辑字段、version映射、车辆externalVehicleName不可替换、格口无hardwareNo写入口。
- [ ] `features/masterdata/api.ts` 明确 Warehouse/Stop/Vehicle/Compartment/Rule 类型和各自API，分页参数URLSearchParams。
- [ ] `MasterdataPage.vue` 根据路由资源加载列表，查询详情后编辑；仓库创建name/code，点位外部ID+仓库多选，车辆外部目录+归属仓库+停点多选；停用明确确认且保留历史。
- [ ] `CompartmentPage.vue` 选车→只读hardwareNo列表→标签/启停更新，同步发送vehicle.expectedVersion后刷新车辆版本，不开放自定义硬件号。
- [ ] `RulesPage.vue` 选仓库读取规则，用weekday/start/end行编辑，不允许共享格口true，前端基本检查，后端完整校验。
- [ ] 路由替换相应占位页，单测/build后真实浏览器CRUD及版本冲突验证，失败可重试、窄屏抽屉不溢出。
