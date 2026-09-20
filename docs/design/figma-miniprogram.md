# 小程序 Figma 页面索引

设计源：[task-hub](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?m=dev)。

2026-09-20 通过 Figma MCP 读取。业务画布 `7:2`（01 小程序 · 标准版业务），基础组件画布 `15:2`（00 基础 UI 2.0）。以下是业务画布的顶层画板快照，包含主页面、弹层与状态，不代表每个画板都应单独注册路由。

## 工程映射

- 主包：P01–P02 登录与验证、P16–P18 消息与个人中心、P21 工作区。
- 工人分包：P03–P15 申请／订单／取货／反馈，P19 常用停靠点。
- 仓库分包：P22–P25 工作台／审批／批次／装货，P33 异常处理。
- 调度分包：P26–P29 选车／调度／车辆控制。
- 概览分包：P34、P40 授权范围内的日常业务和记录查询。
- P20 九识跳转为共用能力，后续按入口归属实现；初始化不接入外部小程序。

这是模块归属规划，当前只注册各分包入口以验证构建；详细页面随功能开发逐步增加。

## 实现约定

1. 功能范围以 `中集系统功能端划分建议.md` V2.2 和现行小程序功能说明为准。设计中示例姓名、车辆、订单和时间不作为真实业务数据。
2. 每次实现画板时重新读取对应 `get_design_context`，结合截图、组件和变量还原到 Vue／uni-app；不直接复制 React/Tailwind 参考代码。
3. 复用基础 UI 组件，页面支持安全区、内容滚动、不同屏宽；390 × 844 是设计基准而非固定设备大小。
4. 已读取 P01 `27:210`：主色 `#1677ff`、页面底色 `#f6f8fb`、表面 `#ffffff`、主文字 `#1c2433`、辅助文字 `#667387`；按钮圆角 8px、卡片 12px。这些是局部已核实值，其余组件应读取后再扩展。
5. 图中若出现超出标准版的能力（例如 P08 的参考耗时），先依据需求核对，不仅凭画稿新增功能。
6. 本轮只初始化结构及设计变量，尚未交付这些业务页面或鉴权；客户端路由和工作区不作为权限边界。

## 画板索引

| 名称 | Figma 节点 |
| --- | --- |
| P03 申请首页 / 企业微信风格 | [18:123](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=18-123) |
| P07 我的订单 / 企业微信风格 | [18:171](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=18-171) |
| P08 订单详情与配送进度 / 企业微信风格 | [18:221](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=18-221) |
| P01 登录与员工准入 / 企业微信风格 | [27:210](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=27-210) |
| P02 手机号验证与绑定 / 企业微信风格 | [27:221](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=27-221) |
| P04 目的地选择 / 企业微信风格 | [27:240](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=27-240) |
| P05 预约日期与时段选择 / 企业微信风格 | [27:265](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=27-265) |
| P06 申请提交结果 / 企业微信风格 | [27:287](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=27-287) |
| P09 取消申请确认与结果 / 企业微信风格 | [27:305](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=27-305) |
| P10 扫码结果与可操作订单 / 企业微信风格 | [27:320](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=27-320) |
| P11 开门与确认取货 / 企业微信风格 | [27:346](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=27-346) |
| P12 继续出发条件与确认 / 企业微信风格 | [27:365](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=27-365) |
| P13 未收货反馈 / 企业微信风格 | [27:386](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=27-386) |
| P14 我的异常工单 / 企业微信风格 | [39:144](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=39-144) |
| P15 异常工单详情 / 企业微信风格 | [39:180](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=39-180) |
| P16 消息列表 / 企业微信风格 | [39:200](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=39-200) |
| P17 消息详情 / 企业微信风格 | [39:231](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=39-231) |
| P18 个人中心 / 企业微信风格 | [39:246](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=39-246) |
| P19 常用停靠点管理 / 企业微信风格 | [39:281](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=39-281) |
| P20 九识小程序跳转确认 / 企业微信风格 | [39:302](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=39-302) |
| P21 角色工作区切换 / 企业微信风格 | [39:318](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=39-318) |
| P18 退出登录确认 | [42:225](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=42-225) |
| P22 / 仓库工作台 | [46:212](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=46-212) |
| P23 / 申请审批 | [46:239](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=46-239) |
| P24 / 合单与批次编辑 | [46:263](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=46-263) |
| P25 / 装货与格口分配 | [46:286](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=46-286) |
| P26 / 选择车辆 | [46:312](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=46-312) |
| P23 / 已受理 | [47:219](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=47-219) |
| P23 / 驳回原因 | [47:236](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=47-236) |
| P23 / 已驳回 | [47:252](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=47-252) |
| P23 / 取消申请处理 | [47:266](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=47-266) |
| P23 / 取消已处理 | [47:287](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=47-287) |
| P25 / 开门请求待核对 | [47:302](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=47-302) |
| P25 / 箱门已打开 | [47:319](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=47-319) |
| P26 / 派车信息核对 | [47:341](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=47-341) |
| P26 / 派车二次确认 | [47:364](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=47-364) |
| P26 / 任务规划中 | [47:381](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=47-381) |
| P26 / 派发待核对 | [47:397](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=47-397) |
| P24 / 配送批次列表 | [47:414](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=47-414) |
| P27 / 调度台 | [51:241](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=51-241) |
| P27 / 任务详情 | [51:273](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=51-273) |
| P28 / 车辆列表 | [51:299](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=51-299) |
| P28 / 车辆详情 | [51:330](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=51-330) |
| P29 / 车辆控制 | [51:353](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=51-353) |
| P33 / 仓库异常工单 | [51:423](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=51-423) |
| P33 / 工单受理 | [51:443](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=51-443) |
| P28 / 空闲车辆 | [52:250](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=52-250) |
| P28 / 车辆离线 | [52:267](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=52-267) |
| P28 / 状态过期 | [52:283](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=52-283) |
| P29 / 开门确认 | [52:299](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=52-299) |
| P29 / 开门请求中 | [52:315](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=52-315) |
| P29 / 箱门已打开 | [52:331](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=52-331) |
| P29 / 操作失败 | [52:346](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=52-346) |
| P29 / 控制结果待核对 | [52:362](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=52-362) |
| P29 / 任务取消条件 | [52:378](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=52-378) |
| P29 / 取消任务确认 | [52:394](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=52-394) |
| P29 / 取消请求中 | [52:410](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=52-410) |
| P29 / 任务已取消 | [52:425](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=52-425) |
| P33 / 填写处理结果 | [52:626](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=52-626) |
| P33 / 关闭工单确认 | [52:643](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=52-643) |
| P33 / 工单已关闭 | [52:659](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=52-659) |
| P33 / 工单已被他人处理 | [52:675](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=52-675) |
| P33 / 处理结果提交失败 | [52:690](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=52-690) |
| 本期设计评审 / 调度与异常状态索引 | [54:433](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=54-433) |
| P29 / 开门条件不满足 | [55:321](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=55-321) |
| P29 / 继续出发条件不满足 | [56:326](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=56-326) |
| P34 / 日常业务概览 | [57:325](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=57-325) |
| P40 / 业务记录 | [57:421](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=57-421) |
| P34 / 日期与授权范围 | [58:332](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=58-332) |
| P34 / 配送中订单 | [58:348](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=58-348) |
| P34 / 暂无业务数据 | [58:366](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=58-366) |
| P20 / 打开九识首页中 | [58:571](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=58-571) |
| P20 / 返回后核对状态 | [58:587](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=58-587) |
| P20 / 打开失败 | [58:662](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=58-662) |
| P40 / 筛选业务记录 | [58:677](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=58-677) |
| P40 / 业务记录详情 | [58:694](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=58-694) |
| P40 / 更正记录详情 | [58:712](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=58-712) |
| P40 / 暂无匹配记录 | [58:730](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=58-730) |
| P34 / 待受理订单 | [60:387](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=60-387) |
| P34 / 待装货订单 | [60:406](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=60-406) |
| P34 / 待发车订单 | [60:425](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=60-425) |
| P34 / 待取货订单 | [60:443](https://www.figma.com/design/i9zjE4RdghEoK0u5T4YM33/task-hub?node-id=60-443) |
