package com.taskhub.domain.notification;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BusinessEventDelivery {
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(BusinessEventDelivery.class);
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final TransactionTemplate tx;

  record Target(String type, String id, String warehouse, String worker) {}

  public BusinessEventDelivery(
      JdbcTemplate db, ObjectMapper json, PlatformTransactionManager manager) {
    this.db = db;
    this.json = json;
    this.tx = new TransactionTemplate(manager);
  }

  public int deliverBatch(int limit) {
    if (limit < 1 || limit > 100) throw new IllegalArgumentException("投递批量范围1至100");
    int count = 0;
    for (int i = 0; i < limit; i++) {
      String[] current = {null};
      try {
        Boolean found =
            tx.execute(
                status -> {
                  var rows =
                      db.queryForList(
                          "SELECT * FROM business_event WHERE processed_at IS NULL AND"
                              + " (next_attempt_at IS NULL OR next_attempt_at<=UTC_TIMESTAMP(3))"
                              + " ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED");
                  if (rows.isEmpty()) return false;
                  var event = rows.get(0);
                  String eventId = (String) event.get("id");
                  current[0] = eventId;
                  deliver(event);
                  db.update(
                      "INSERT INTO event_delivery(event_id) VALUES(?) ON DUPLICATE KEY UPDATE"
                          + " event_id=event_id",
                      eventId);
                  db.update(
                      "UPDATE business_event SET processed_at=UTC_TIMESTAMP(3) WHERE id=?",
                      eventId);
                  return true;
                });
        if (!Boolean.TRUE.equals(found)) break;
        count++;
      } catch (RuntimeException failure) {
        if (current[0] == null) throw failure;
        log.warn(
            "业务事件投递失败，保留重试：eventId={}, errorType={}",
            current[0],
            failure.getClass().getSimpleName());
        tx.executeWithoutResult(
            status ->
                db.update(
                    "UPDATE business_event SET"
                        + " retry_count=retry_count+1,next_attempt_at=DATE_ADD(UTC_TIMESTAMP(3),INTERVAL"
                        + " 30 SECOND) WHERE id=? AND processed_at IS NULL",
                    current[0]));
      }
    }
    return count;
  }

  private JsonNode payload(Object value) {
    try {
      return json.readTree((String) value);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalArgumentException("事件正文无效", e);
    }
  }

  private Target target(String eventType, String aggregate, JsonNode payload) {
    String order = payload.path("orderId").asText(null),
        ticket = payload.path("ticketId").asText(null),
        batch = payload.path("batchId").asText(null),
        task = payload.path("taskId").asText(null);
    if (ticket != null || eventType.startsWith("TICKET_")) {
      String id = ticket == null ? aggregate : ticket;
      var rows = db.queryForList("SELECT warehouse_id,reporter_id FROM ticket WHERE id=?", id);
      if (rows.isEmpty()) return null;
      return new Target(
          "TICKET",
          id,
          (String) rows.get(0).get("warehouse_id"),
          (String) rows.get(0).get("reporter_id"));
    }
    if (order != null
        || eventType.startsWith("ORDER_")
        || eventType.equals("CANCELLATION_REVIEWED")) {
      String id = order == null ? aggregate : order;
      var rows =
          db.queryForList("SELECT warehouse_id,applicant_id FROM delivery_order WHERE id=?", id);
      if (rows.isEmpty()) return null;
      return new Target(
          "ORDER",
          id,
          (String) rows.get(0).get("warehouse_id"),
          (String) rows.get(0).get("applicant_id"));
    }
    if (task != null) {
      var rows =
          db.queryForList(
              "SELECT v.warehouse_id FROM vehicle_task t JOIN vehicle v ON v.id=t.vehicle_id WHERE"
                  + " t.id=?",
              task);
      if (!rows.isEmpty())
        return new Target("TASK", task, (String) rows.get(0).get("warehouse_id"), null);
    }
    if (batch != null) {
      var rows = db.queryForList("SELECT warehouse_id FROM batch WHERE id=?", batch);
      if (!rows.isEmpty())
        return new Target("BATCH", batch, (String) rows.get(0).get("warehouse_id"), null);
    }
    return null;
  }

  private void deliver(Map<String, Object> event) {
    String type = (String) event.get("event_type");
    JsonNode payload = payload(event.get("payload"));
    Target target = target(type, (String) event.get("aggregate_id"), payload);
    if (target == null) return;
    Map<String, String> recipients = new LinkedHashMap<>();
    boolean operational = type.startsWith("DISPATCH_") || type.startsWith("CONTROL_");
    if (target.worker() != null && !operational) recipients.put(target.worker(), "worker");
    if (target.type().equals("ORDER") && !operational) {
      var receivers =
          db.queryForList(
              "SELECT COALESCE(o.receiver_id,(SELECT p.employee_id FROM verified_phone p WHERE"
                  + " p.phone=o.receiver_phone)) FROM delivery_order o WHERE o.id=?",
              String.class,
              target.id());
      receivers.stream()
          .filter(Objects::nonNull)
          .forEach(id -> recipients.putIfAbsent(id, "worker"));
    }
    String role = operational || target.type().equals("TASK") ? "dispatch" : "warehouse";
    var staff =
        db.queryForList(
            "SELECT DISTINCT g.employee_id FROM role_grant g JOIN employee e ON e.id=g.employee_id"
                + " LEFT JOIN grant_warehouse w ON w.grant_id=g.id WHERE e.enabled=true AND"
                + " g.role=? AND (g.scope='ALL' OR (g.scope='WAREHOUSES' AND w.warehouse_id=?))",
            String.class,
            role,
            target.warehouse());
    staff.forEach(id -> recipients.putIfAbsent(id, role));
    String title = title(type, payload);
    for (var entry : recipients.entrySet()) {
      if (db.queryForObject(
              "SELECT COUNT(*) FROM employee WHERE id=? AND enabled=true",
              Integer.class,
              entry.getKey())
          == 0) continue;
      db.update(
          "INSERT INTO"
              + " notification(id,event_id,employee_id,title,body,target_type,target_id,target_workspace)"
              + " VALUES(?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE event_id=event_id",
          UUID.randomUUID().toString(),
          event.get("id"),
          entry.getKey(),
          title,
          "业务记录有更新，请进入详情查看。",
          target.type(),
          target.id(),
          entry.getValue());
    }
  }

  private String title(String type, JsonNode payload) {
    if (type.contains("UNKNOWN") || type.contains("RECONCILIATION")) return "车辆请求结果待核对";
    return switch (type) {
      case "ORDER_CREATED" -> "新配送申请";
      case "ORDER_REVIEWED" ->
          payload.path("status").asText().equals("REJECTED") ? "配送申请已驳回" : "配送申请已受理";
      case "ORDER_CANCEL_REQUESTED" -> "订单申请取消";
      case "CANCELLATION_REVIEWED" -> "取消申请已有处理结果";
      case "TICKET_CREATED" -> "新的异常反馈";
      case "TICKET_CLOSE" -> "异常工单已关闭";
      case "TICKET_ACCEPT" -> "异常工单已受理";
      case "TICKET_NOTE" -> "工单处理说明已更新";
      case "DISPATCH_ACCEPTED" -> "派发请求已受理";
      case "DISPATCH_FAILED" -> "派发请求失败";
      default -> "业务状态已更新";
    };
  }
}
