# 微信小程序

基于 uni-app、Vue 3 和 TypeScript 的小程序工程骨架，统一调用 `../server/` 提供的后端接口。当前仅包含可编译的入口和模块占位页，不包含登录、业务流程或车辆控制实现。

## 环境要求

- Node.js 22.12+（根目录 `.nvmrc` 指定 22）
- npm 10+
- 微信开发者工具（预览微信小程序时需要）

## 启动与构建

```bash
npm ci
npm run dev:h5
npm run dev:mp-weixin
npm run typecheck
npm run build:h5
npm run build:mp-weixin
```

H5 开发服务默认由 Vite 输出本地访问地址。微信小程序开发构建输出到 `dist/dev/mp-weixin`，生产构建输出到 `dist/build/mp-weixin`。

## 本地配置

1. 复制 `.env.example` 为 `.env.local`。H5 开发使用 `/api` 代理到 `http://localhost:8080`；微信小程序必须将 `VITE_API_BASE_URL` 改为完整 HTTPS 地址并包含 `/api`，在微信后台配置合法请求域名。真机不能使用开发电脑的 `localhost`。
2. 在 `src/manifest.json` 的 `mp-weixin.appid` 和 `project.config.json` 的 `appid` 填入自己的微信小程序 AppID。仓库不保存真实 AppID。
3. 使用微信开发者工具导入本目录；开发者工具会按 `project.config.json` 读取 `dist/dev/mp-weixin`。

请求层约定响应为 `{ code, message, data }`，仅 `code === 0` 视为成功。服务端 ID 均按字符串使用，不转换成 JavaScript `number`。

## 设计基线

占位界面引用 Figma P01（node `27:210`）的基础视觉 token：背景 `#f6f8fb`、主色 `#1677ff`、正文 `#1c2433`、次要文字 `#667387`、白色卡片及 `8px/12px` 圆角。

完整设计稿：[task-hub 小程序](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?m=dev)。画板和模块映射见 [Figma 索引](../docs/design/figma-miniprogram.md)。

主包放公共入口、登录、消息和个人中心；`subpackages/` 按工人、仓库、调度、概览分包。各入口仅用于工程构建，未实现角色授权或业务流程。后续工作区切换必须使用服务端返回的授权，不能仅靠路由判断权限。

## 验证与依赖限制

2026-09-20 已通过类型检查、微信小程序生产构建及 H5 生产构建。尚未进行微信开发者工具或真机验证。

DCloud Vue3 工具链版本统一为 `3.0.0-5020620260917001`，其 Vite peer dependency 精确要求 `5.2.8`。本轮在线 `npm audit` 报告依赖链中 40 项问题（15 low、12 moderate、13 high），尚未解决；不使用 `audit fix --force` 自动降级 DCloud 包或替换编译器。开发服务器仅用于可信本机环境，后续需跟随兼容的 DCloud 版本升级并复验。
