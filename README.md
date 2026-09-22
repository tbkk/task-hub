# Task Hub

## 项目结构

| 目录 | 技术栈 | 用途 |
| --- | --- | --- |
| [miniprogram/](miniprogram/) | uni-app + Vue 3 + TypeScript | 微信小程序（用户端） |
| [admin-web/](admin-web/) | Vue 3 + TypeScript + Element Plus | 网页管理后台（管理员端） |
| [server/](server/) | Spring Boot + MyBatis | Java 后端（统一服务） |
| [database/](database/) | MySQL | 数据库脚本 |
| [docs/](docs/) | Markdown | 项目文档 |
| [deploy/](deploy/) | Docker + Nginx | 部署配置 |

小程序和管理后台统一调用 `server/` 提供的后端接口，后端访问 MySQL 数据库。

## 现有资料

- `docs/design/`：需求文档、功能说明、第三方接口文档及准备工作资料。
- `ui/images/`：界面参考图片。

## 当前状态

已初始化双前端、Java 后端、数据库元数据脚本和可选容器配置；当前采用本地开发。小程序按工人、仓库、调度和概览分包，已在 `feature/base` 实现 P01/P02 页面及 mock 登录，详见小程序 README。管理平台按管理职责提供模块导航。真实认证、角色授权、订单及车辆控制尚未实现。

## 本地开发

环境：Node.js 22.12+（见 `.nvmrc`）、npm 10+、Java 17、Maven 3.6.3+；数据联调使用 MySQL 8.4。

在三个终端中分别启动：

```sh
cd admin-web
npm ci
cp .env.example .env.local
npm run dev
```

```sh
cd miniprogram
npm ci
cp .env.example .env.local
npm run dev:mp-weixin
```

```sh
cd server
mvn spring-boot:run
```

微信开发者工具导入 `miniprogram/`，开发产物位于 `dist/dev/mp-weixin`；需配置自己的小程序 AppID 和 HTTPS 合法请求域名。浏览器预览可用 `npm run dev:h5`。两端 H5 开发代理均保留 `/api` 前缀，目标默认 `http://localhost:8080`。

后端不依赖数据库即可启动并响应 `/api/health`；`/api/ready` 需数据库元数据表可用。其他接口默认拒绝访问。数据库配置见 [后端说明](server/README.md)，SQL 导入见 [数据库说明](database/README.md)，容器联调见 [部署说明](deploy/README.md)。

## 设计与后续开发

小程序设计源为 [Figma task-hub](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?m=dev)，已核实的 82 个顶层画板见 [Figma 索引](docs/design/figma-miniprogram.md)。需求边界以 [端划分 V2.2](docs/design/中集系统功能端划分建议.md) 为准：日常业务在小程序，系统管理与支撑在管理平台。

## 初始化验证（2026-09-20）

- 管理端：类型检查、生产构建、3 项请求测试通过；Element Plus 全量引入导致构建体积提示，后续业务开发按需引入。
- 小程序：类型检查、微信生产构建、H5 生产构建通过；DCloud 依赖风险及兼容性约束见小程序 README。
- 后端：6 项测试、Maven 打包通过；实际运行验证存活 200、数据库不可用时就绪 503、业务接口 403。
- 部署：YAML 解析及配置路径核对完成；本机无 Docker，未验证容器构建、Compose 启动及真实 MySQL 联调。小程序未进行真机验证。
