# 订单与批次切片复核

2026-09-21，当前feature/base未提交源码。

## 订单S06–S09

原审查 `/tmp/task-hub-orders-current-review.md` 指出履历日期筛选及权限/审计测试缺失。修复由plan_mini完成，根执行者复核OrderController.history参数、OrderService.history上海日期半开区间、OrderIT新增真实数据库断言。15项定向通过；后续100项全套通过。原Important已闭合，运输取消仍按后续切片追踪。

## 批次S10/S11

原审查 `/tmp/task-hub-batch-review.md` 指出LOCKED直接编辑拒绝及确认后换车失效回归缺失。实施者补BatchIT场景，根复核实际断言：HTTP409且成员/分配不变；换车后DRAFT/UNCONFIRMED、清空confirmed字段/哈希、只有新格口占用、订单ACCEPTED。BatchService.replace已移除重复锁查询。BatchIT8项及全套100项通过。原Important已闭合。

## 小程序仓库Task5

根独立检查发现：已有取消记录时受理按钮按cancellationId错误路由；Submission非响应式；未知结果可被刷新/新编辑破坏重试正文。已补reviewIntent回归（RED缺导出→GREEN），明确订单与取消操作，响应式busy/保留原请求快照，未知结果禁止改写并给重试入口；批次成员候选增加后续页入口。窄屏将订单号放独立行，状态中文展示。

真实浏览器脚本 `/tmp/task-hub-browser-check/warehouse-real.cjs` 验证：短信模拟提供者真实登录→warehouse工作区→有取消申请的订单仍正确受理→拒绝取消→组批落库，390/320布局检查通过。未使用接口拦截。

## 门禁边界

此文件仅覆盖上述订单/批次/仓库审批范围；S12–S14、装货点配置、工单和消息新增代码尚需独立审查；车辆控制/取货、全链路验收仍未完成。MySQL实测9.6，目标8.4与微信真机/实车未验。不提交推送。
