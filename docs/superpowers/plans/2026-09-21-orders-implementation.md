# 预约与工人订单实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans；当前 feature/base 实施。

**Goal:** 工人唯一归属仓库真实申请、预约容量、收藏、本人订单与独立取消状态。
**Architecture:** V5建立预约/订单/收藏；复用V4幂等与审计事件，订单创建同事务锁仓库与预约行，受理后的业务为后续阶段。
**Tech Stack:** Java17、Spring Boot、MySQL、JdbcTemplate。

## Global Constraints

- UTC存储、Asia/Shanghai业务日期；半小时未来时段，提交重新检查规则/可达/容量。
- 身份由服务端验证手机号关系确定，二维码和姓名不授权。
- 幂等、审计、业务事件同事务；取消申请不修改主订单状态。

## 任务

- [ ] OrderIT：真实MySQL建立仓库/点位/车辆/规则和工人，GET slots→POST orders→本人详情，第三人404；请求重试一单、容量1并发仅一单、取消保留PENDING。
- [ ] V5__orders.sql：reservation_slot唯一warehouse/start，delivery_order保存slot/receiver/applicant，order_cancellation独立表，favorite_stop唯一employee/stop。
- [ ] ReservationService.list(actor,warehouse,stop,date)只返回营业/未来/范围内时段，创建时段稳定字符串ID，容量仅按数据库有效订单计数。提交加锁slot并更新used，order携带reservationReleased供后续释放。
- [ ] OrderService创建校验文字上限/有效手机号，receiver_id从verified_phone查。接口列表/详情按workspace与本人/授权warehouse过滤，安全字段聚合。
- [ ] FavoriteController PUT/DELETE幂等并从catalog只返回可用点位。
- [ ] 取消POST expectedVersion与key，申请人校验后创建PENDING取消记录、订单version递增；不做订单自动取消。
- [ ] GET history只返回本人订单范围内的审计安全摘要；后续仓库操作复用。
- [ ] 实际并发/越权/重复提交验证，再接入已实现小程序页面，重启确认持久化。
