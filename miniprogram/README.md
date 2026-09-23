# 微信小程序

基于 uni-app、Vue 3 和 TypeScript 的小程序。已实现 Figma P01 登录页、P02 手机号验证页、P18 个人中心、P21 工作区切换、真实服务端会话基础、显式本地 mock 认证与四类角色导航；业务模块仍为占位。

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

默认 `VITE_AUTH_MOCK=false`，微信登录先调用 `uni.login`，再用临时 code 交换服务端会话；短信登录和绑定分别发送 `LOGIN`、`BIND` purpose，并使用服务端返回的 `challengeId` 验证。token 只保存在内存，业务请求发送 `Authorization` 与 `X-Workspace`。

请求层约定响应为 `{ code, message, data }`，仅 `code === 0` 视为成功。服务端 ID 均按字符串使用，不转换成 JavaScript `number`。

## 设计基线

占位界面引用 Figma P01（node `27:210`）的基础视觉 token：背景 `#f6f8fb`、主色 `#1677ff`、正文 `#1c2433`、次要文字 `#667387`、白色卡片及 `8px/12px` 圆角。

完整设计稿：[task-hub 小程序](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?m=dev)。画板和模块映射见 [Figma 索引](../docs/design/figma-miniprogram.md)。

主包放公共入口、登录、消息和个人中心；`subpackages/` 按工人、仓库、调度、概览分包。当前按 mock 授权控制工作区入口和逐角色仓库范围，切换清理旧工作区数据并确认未保存内容。此客户端演示不替代服务端鉴权；真实业务流程尚未实现。

## Mock 登录（feature/base）

本地基础演示需同时配置 `VITE_AUTH_MOCK=true` 与 `VITE_AUTH_MOCK_CODE`；微信开发者工具的导入能力仍受其 AppID/测试号配置约束。页面显示“演示模式”，不会发送短信或调用微信授权。验证码来自本地构建配置，认证 API 不返回验证码。

| 场景 | 手机号 | 验证码 |
| --- | --- | --- |
| 工人张三 | 13800000000 | 123456 |
| 多角色李明 | 13800000003 | 123456 |
| 仓库人员 | 13800000004 | 123456 |
| 调度人员 | 13800000005 | 123456 |
| 业务概览 | 13800000006 | 123456 |
| 未准入 | 13800000001（或其他有效号码） | 123456 |
| 账号停用 | 13800000002 | 123456 |

必须先点击“获取验证码”；模拟有效期 5 分钟，同号码发送间隔 60 秒，这些仅为 mock 参数。错误验证码可重试，成功后验证码消费失效。“微信登录”模拟获取待绑定身份，然后进入手机号验证；手机验证码入口直接验证并登录。

会话仅保留在内存，刷新即失效；单角色直接进入对应工作区，多角色首次选择，之后恢复仍有效的上次工作区。本地存储只保存角色偏好，不保存登录凭证；退出需要确认。Mock 不产生后端 token，也不能用于真实业务接口授权。测试命令为 `npm test`。

生产构建默认关闭 mock；演示构建可显式运行 `VITE_AUTH_MOCK=true VITE_AUTH_MOCK_CODE=123456 npm run build:h5` 或对应微信构建命令。`VITE_AUTH_MOCK=false` 使用 `/mini/auth/*` 与 `/identity/*` 真实接口，后端 local/test provider 可在没有真实微信、短信凭证时提供明确的服务端模拟交换。

登录页可展开模拟微信取消/网络失败，验证码页可模拟发送失败；返回再进入保留发送冷却时间。个人中心可模拟撤销当前角色或全部权限。多角色账号的仓库操作范围为 wh-1、调度范围为 wh-2，概览范围为两仓，互不扩大操作权限。

## 验证与依赖限制

2026-09-20 本切片已通过 20 项单元测试、类型检查、微信小程序生产构建及 H5 生产构建。阶段 1 的 H5 浏览器回归曾覆盖多角色导航、角色偏好、权限撤销、退出确认、越权路由、320px 窄屏、登录中返回及失败重试；本次真实 API 接入尚未执行浏览器联调、微信开发者工具或真机验证。

DCloud Vue3 工具链版本统一为 `3.0.0-5020620260917001`，其 Vite peer dependency 精确要求 `5.2.8`。本轮在线 `npm audit` 报告依赖链中 40 项问题（15 low、12 moderate、13 high），尚未解决；不使用 `audit fix --force` 自动降级 DCloud 包或替换编译器。开发服务器仅用于可信本机环境，后续需跟随兼容的 DCloud 版本升级并复验。
