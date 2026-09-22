# 外部接入准备度

更新时间：2026-09-22

| 接入 | 当前实现 | 已验证 | 依赖阻塞 |
| --- | --- | --- | --- |
| 九识控制 | `SIMULATOR` 网关覆盖派发、开门、出发、取消和 UNKNOWN | 本地数据库幂等、冲突和超时测试 | 九识 appId/appKey、完整环境 URL、测试车辆及控制授权 |
| 九识 MQTT | 已记录 GeoBridge 的 MQTT v5 连接、主题和事件映射约定 | 模拟事件入口、重复/乱序/门状态判序 | MQTT host、organizationCode、用户名/密码、可接收的测试车辆 |
| 微信 | Mock provider | 本地会话与权限测试 | AppID、合法域名和真机环境 |
| 短信 | Mock provider | 验证码限频、消费和会话测试 | 服务商、签名、模板和发送凭证 |

真实九识适配启用前需要完成以下检查：

1. 用九识提供的完整 URL 校对文档中换行损坏的 `go`、`open_box` 等路径。
2. 用 HTTP stub 验证 token 过期只重试一次、超时映射 UNKNOWN、请求体不泄露联系人字段。
3. 用 MQTT fixture 验证 `realtime/push` 与 `business/push` 的重连重订阅、重复/乱序、缺失门字段和事件冲突。
4. 在得到书面授权、测试车辆和回滚窗口前，不发送真实车辆控制命令。
