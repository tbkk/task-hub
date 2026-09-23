package com.taskhub.domain.masterdata;

/** 订单/批次模块实现此接口，在资源事务和对象锁下阻止活动业务引用的停用或重绑定。 */
public interface MasterdataUsage {
  void requireChangeAllowed(String resource, String id);
}
