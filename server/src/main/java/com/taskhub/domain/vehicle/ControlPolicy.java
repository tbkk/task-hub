package com.taskhub.domain.vehicle;

import java.util.*;

public final class ControlPolicy {
  private ControlPolicy() {}
  public record Blocker(String code, String message) {}
  public record Context(String taskState, String businessStatus, String currentStopId, String nextStopId,
                        String expectedStopId, Boolean online, Boolean fresh, java.math.BigDecimal speed,
                        List<String> orderStatuses, List<String> doorStatuses, List<Boolean> doorFresh,
                        boolean activeTicket, boolean unknownControl) {}
  public static List<Blocker> blockers(Context c, boolean go) {
    var out = new ArrayList<Blocker>();
    if (!Boolean.TRUE.equals(c.online()) || !Boolean.TRUE.equals(c.fresh())) out.add(new Blocker("VEHICLE_STALE", "车辆在线或状态已过期"));
    if (go && (!Objects.equals(c.currentStopId(), c.expectedStopId()) || !"ATSTOP".equals(c.businessStatus()))) out.add(new Blocker("TASK_MISMATCH", "车辆未在当前任务停靠点"));
    if (go && (c.nextStopId() == null || c.nextStopId().isBlank())) out.add(new Blocker("NO_NEXT_STOP", "没有下一停靠点"));
    if (go) for (int i=0;i<c.doorStatuses().size();i++) {
      if (!Boolean.TRUE.equals(c.doorFresh().get(i)) || c.doorStatuses().get(i) == null || "UNKNOWN".equals(c.doorStatuses().get(i))) out.add(new Blocker("DOOR_UNKNOWN", "存在状态未知或过期格口门"));
      else if ("OPEN".equals(c.doorStatuses().get(i))) out.add(new Blocker("DOOR_OPEN", "存在未关闭格口门"));
      else if (!"CLOSED".equals(c.doorStatuses().get(i))) out.add(new Blocker("DOOR_UNKNOWN", "存在状态未知格口门"));
    }
    if (go && c.orderStatuses().stream().anyMatch(s -> !"COMPLETED".equals(s))) out.add(new Blocker("ORDER_NOT_PICKED", "当前停靠点订单尚未全部取货"));
    if (c.activeTicket()) out.add(new Blocker("ACTIVE_TICKET", "存在活动工单"));
    if (c.unknownControl()) out.add(new Blocker("UNKNOWN_CONTROL", "已有未知控制请求，请先核对"));
    if (!go && (c.speed() == null || c.speed().signum() != 0)) out.add(new Blocker("VEHICLE_MOVING", "车辆速度未知或不为零"));
    return out;
  }
}
