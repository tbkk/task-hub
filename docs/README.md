# 项目文档

项目文档以 Markdown 为主，已有 Word 原始资料保留在 `design/` 中。

- `../AGENTS.md`：项目开发规则，参考 Puck 的需求、分支、验证和 review 规范。
- `development/workflow.md`：新功能、Bug 修复、数据库变更、验证与提交交付流程。
- `development/acceptance-matrix.md`：A01–A15 验收范围、已有验证记录、剩余实现与真实资源依赖；2026-09-22 已按代码核对状态。
- `../harness/README.md`：三端自动验证入口。
- `../deploy/README.md`：`deploy.sh` 本机 Nginx＋Java 实验部署与微信开发者工具接入。
- `reviews/2026-09-22-local-deploy-review.md`：本地部署验证与审查记录。
- `superpowers/specs/2026-09-20-delivery-scope.md`：当前分支完整开发的范围与模拟交付边界。
- `superpowers/plans/2026-09-20-delivery-progress.md`：切片状态与历史验证证据；历史“未提交”等文字按记录日期解读，不代表当前 Git 状态。

- `design/`：需求、功能范围、界面说明、第三方接口及开发准备资料。
- `integration/jiushi-geobridge-adaptation.md`、`integration/jiushi-geobridge-contract.md`：根据 GeoBridge 与九识 V2.9 文档整理的车辆控制、MQTT 入站和真实接入边界。
- `design/figma-miniprogram.md`：通过 MCP 核实的小程序 Figma 画板索引、设计变量及模块映射。
- `architecture/project-structure.md`：三端结构、权限边界与接口约定。
- `superpowers/plans/2026-09-20-project-bootstrap.md`：本轮初始化计划及验证记录。
- `superpowers/plans/2026-09-20-development-roadmap.md`：当前总体进度入口，第 2 节已于 2026-09-22 按 `8f65de8` 核对；包含各阶段剩余范围、验收标准和外部依赖。

各工程启动方式见对应 README；当前采用本机运行，不使用 Docker。后续接口说明、开发指南和运维文档统一放在本目录。
