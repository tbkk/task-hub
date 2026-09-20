# Task Hub 管理平台

面向系统管理与业务支撑的 Vue 3 管理端骨架。当前仅提供可构建工程、模块导航和请求基础设施；所有业务模块均为“尚未实现”状态。

## 环境要求

- Node.js 20.19+ 或 22.12+
- npm 10+

## 本地开发

```bash
npm ci
cp .env.example .env.local
npm run dev
```

开发服务器默认将 `/api` 原样代理到 `http://localhost:8080`。可通过 `VITE_API_PROXY_TARGET` 修改代理目标；`VITE_API_BASE_URL` 用于配置浏览器请求前缀。

## 校验与构建

```bash
npm test
npm run typecheck
npm run build
npm run preview
```

## 当前边界

管理平台仅承载账户、人员与权限、基础资料、规则配置、既有第三方接入、管理报表和审计运行维护。审批、合单、装货、派车、车辆控制、订单处理和工单处理等日常业务属于小程序，不在本工程提供入口。

接口响应约定为 `{ code: number, message: string, data: T }`，成功业务码为 `0`。业务 ID 必须作为字符串传输和使用。
