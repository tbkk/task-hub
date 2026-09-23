# 事务事件与站内消息实施计划

> 执行既有S18，使用executing-plans与TDD。

**目标：** 从真实业务事件生成账号唯一消息，支持分页、未读、幂等已读与失权目标裁剪。
**架构：** V11 notification/event_delivery及business_event重试字段；BusinessEventDelivery短事务SKIP LOCKED按事件投递；NotificationService当前账号查询；可配置后台调度。
**技术栈：** Spring Boot、JdbcTemplate、MySQL。

- [ ] NotificationIT先POST订单后调用投递→账号一条/陌生账号404、幂等read时间不变/失权target=null测试RED。
- [ ] V11唯一(event_id,employee_id)，event_delivery保存结果；失败事务回滚后单独增加retry_count及next_attempt_at。成功投递与processed_at同事务。
- [ ] 收件人按事件对象所属订单申请人/验证收货人及仓库或调度角色范围计算，同账号多角色去重；工单只通知提交者与仓库，不泄漏正文。
- [ ] 消息标题/摘要按事件生成，不把UNKNOWN/ACCEPTED当实际出发；目标返回前当前权限再次校验，失权只留中性通知和target=null。
- [ ] GET/messages及unread-count、详情、PUT/read；无跨账号读取。后台调度显式配置，测试直接调用deliverBatch，所有业务存MySQL。
- [ ] 定向测试、失败恢复/去重回归、独立review及小程序消息联调。
