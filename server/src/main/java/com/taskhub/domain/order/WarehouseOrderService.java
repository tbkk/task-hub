package com.taskhub.domain.order;

import com.taskhub.api.ApiException;
import com.taskhub.domain.audit.AuditService;
import com.taskhub.domain.identity.*;
import com.taskhub.domain.identity.IdentityModels.Actor;
import com.taskhub.domain.notification.BusinessEventService;
import com.taskhub.infrastructure.idempotency.IdempotencyService;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class WarehouseOrderService {
  public record Review(Integer expectedVersion, String decision, String reason) {}

  private final com.taskhub.domain.batch.BatchService batches;
  private final JdbcTemplate db;
  private final AuthorizationService auth;
  private final OrderService orders;
  private final IdempotencyService idem;
  private final AuditService audit;
  private final BusinessEventService events;

  public WarehouseOrderService(
      JdbcTemplate db,
      AuthorizationService auth,
      OrderService orders,
      IdempotencyService idem,
      AuditService audit,
      BusinessEventService events,
      com.taskhub.domain.batch.BatchService batches) {
    this.batches = batches;
    this.db = db;
    this.auth = auth;
    this.orders = orders;
    this.idem = idem;
    this.audit = audit;
    this.events = events;
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> review(Actor actor, String id, String key, Review input) {
    auth.requireWorkspace(actor, "warehouse");
    orders.get(actor, id);
    return idem.execute(
        actor,
        "POST /orders/" + id + "/review",
        key,
        input,
        Map.class,
        () -> {
          lock(id);
          var before = orders.get(actor, id);
          checkVersion(input, before);
          if (!"PENDING".equals(before.get("status"))) throw conflict("订单已处理，请重新读取");
          if (!Set.of("ACCEPT", "REJECT").contains(String.valueOf(input.decision())))
            throw bad("受理决定无效");
          String reason =
              "REJECT".equals(input.decision())
                  ? IdentityValidation.required(input.reason(), 1000, "驳回原因")
                  : null;
          String status = input.decision().equals("ACCEPT") ? "ACCEPTED" : "REJECTED";
          db.update(
              "UPDATE delivery_order SET status=?,version=version+1,updated_at=UTC_TIMESTAMP(3)"
                  + " WHERE id=?",
              status,
              id);
          if (status.equals("REJECTED")) release(id, (String) before.get("slotId"));
          var after = orders.get(actor, id);
          audit.record(actor, "ORDER", id, "ORDER_" + input.decision(), before, after, reason);
          events.append("ORDER_REVIEWED", id, Map.of("orderId", id, "status", status));
          return after;
        });
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> cancellation(
      Actor actor, String id, String cancellationId, String key, Review input) {
    auth.requireWorkspace(actor, "warehouse");
    orders.get(actor, id);
    return idem.execute(
        actor,
        "POST /orders/" + id + "/cancellations/" + cancellationId + "/review",
        key,
        input,
        Map.class,
        () -> {
          String lockedBatch = batches.lockForCancellation(id);
          lock(id);
          var before = orders.get(actor, id);
          checkVersion(input, before);
          if (!(before.get("cancellation") instanceof Map<?, ?> current)
              || !cancellationId.equals(current.get("id"))
              || !"PENDING".equals(current.get("status"))) throw conflict("取消申请已处理或不存在");
          if (!Set.of("APPROVE", "REJECT").contains(String.valueOf(input.decision())))
            throw bad("处理决定无效");
          String reason = IdentityValidation.required(input.reason(), 1000, "处理原因");
          boolean approve = input.decision().equals("APPROVE");
          if (approve && !Set.of("PENDING", "ACCEPTED", "READY").contains(before.get("status")))
            throw conflict("订单已进入配送准备或运输，需要先核对批次和车辆状态");
          if (approve) batches.removeForCancellation(actor, id, lockedBatch);
          db.update(
              "UPDATE order_cancellation SET status=?,result=? WHERE id=?",
              approve ? "APPROVED" : "REJECTED",
              reason,
              cancellationId);
          db.update(
              "UPDATE delivery_order SET status=?,version=version+1,updated_at=UTC_TIMESTAMP(3)"
                  + " WHERE id=?",
              approve ? "CANCELLED" : before.get("status"),
              id);
          if (approve) release(id, (String) before.get("slotId"));
          var after = orders.get(actor, id);
          audit.record(
              actor, "ORDER", id, "CANCELLATION_" + input.decision(), before, after, reason);
          events.append("CANCELLATION_REVIEWED", id, Map.of("orderId", id, "approved", approve));
          return after;
        });
  }

  private void lock(String id) {
    if (db.queryForList("SELECT id FROM delivery_order WHERE id=? FOR UPDATE", id).isEmpty())
      throw new ApiException(404, 40400, "订单不存在");
  }

  private void checkVersion(Review input, Map<String, Object> order) {
    if (input.expectedVersion() == null) throw bad("expectedVersion必填");
    if (input.expectedVersion().intValue() != ((Number) order.get("version")).intValue())
      throw new ApiException(409, 40902, "订单已更新，请重新读取");
  }

  private void release(String id, String slot) {
    if (db.update(
                "UPDATE delivery_order SET reservation_released=true WHERE id=? AND"
                    + " reservation_released=false",
                id)
            == 1
        && db.update("UPDATE reservation_slot SET used=used-1 WHERE id=? AND used>0", slot) != 1)
      throw new IllegalStateException("预约容量记录不一致");
  }

  private static ApiException bad(String message) {
    return new ApiException(400, 40000, message);
  }

  private static ApiException conflict(String message) {
    return new ApiException(409, 40903, message);
  }
}
