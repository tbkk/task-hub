package com.taskhub.domain.order;

import com.taskhub.api.*;
import com.taskhub.domain.audit.AuditService;
import com.taskhub.domain.identity.*;
import com.taskhub.domain.identity.IdentityModels.*;
import com.taskhub.domain.masterdata.*;
import com.taskhub.domain.notification.BusinessEventService;
import com.taskhub.infrastructure.idempotency.IdempotencyService;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderService implements MasterdataUsage {
  private final JdbcTemplate db;
  private final AuthorizationService auth;
  private final ReservationService reservations;
  private final CatalogService catalog;
  private final IdempotencyService idem;
  private final AuditService audit;
  private final BusinessEventService events;

  public OrderService(
      JdbcTemplate db,
      AuthorizationService auth,
      ReservationService reservations,
      CatalogService catalog,
      IdempotencyService idem,
      AuditService audit,
      BusinessEventService events) {
    this.db = db;
    this.auth = auth;
    this.reservations = reservations;
    this.catalog = catalog;
    this.idem = idem;
    this.audit = audit;
    this.events = events;
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> create(Actor actor, String key, OrderModels.Create v) {
    auth.requireWorkspace(actor, "worker");
    return idem.execute(
        actor,
        "POST /orders",
        key,
        v,
        Map.class,
        () -> {
          // 仓库锁使配置停用/规则变更与预约创建串行；当前读后再校验目录。
          if (db.queryForList("SELECT id FROM warehouse WHERE id=? FOR UPDATE", v.warehouseId())
              .isEmpty()) throw bad("所属仓库不存在");
          var rules = catalog.rules(actor, v.warehouseId());
          String description = text(v.description(), rules.descriptionMaxLength()),
              size = text(v.size(), rules.sizeMaxLength()),
              name = text(v.receiverName(), 80),
              phone = IdentityValidation.phone(v.receiverPhone());
          String remark = v.remark() == null ? "" : v.remark().trim();
          if (remark.length() > rules.remarkMaxLength()) throw bad("备注过长");
          reservations.reserve(actor, v.warehouseId(), v.stopId(), v.slotId());
          var owners =
              db.queryForList(
                  "SELECT p.employee_id FROM verified_phone p JOIN employee e ON e.id=p.employee_id"
                      + " WHERE p.phone=? AND e.enabled=true",
                  phone);
          String receiver = owners.isEmpty() ? null : (String) owners.get(0).get("employee_id"),
              id = UUID.randomUUID().toString();
          String number =
              "TH"
                  + java.time.LocalDate.now(ReservationService.ZONE).toString().replace("-", "")
                  + id.replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
          db.update(
              "INSERT INTO"
                  + " delivery_order(id,number,warehouse_id,stop_id,slot_id,applicant_id,receiver_id,receiver_name,receiver_phone,description,size,remark,status)"
                  + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,'PENDING')",
              id,
              number,
              v.warehouseId(),
              v.stopId(),
              v.slotId(),
              actor.employeeId(),
              receiver,
              name,
              phone,
              description,
              size,
              remark);
          var result = get(actor, id);
          audit.record(actor, "ORDER", id, "ORDER_CREATE", null, result, null);
          events.append("ORDER_CREATED", id, Map.of("orderId", id, "warehouseId", v.warehouseId()));
          return result;
        });
  }

  private static ApiException bad(String text) {
    return new ApiException(400, 40000, text);
  }

  private static String text(String value, int max) {
    return IdentityValidation.required(value, max, "申请内容");
  }

  private String scope(Actor actor, List<Object> args) {
    var grant = auth.requireWorkspace(actor, actor.workspace());
    if (grant.role().equals("worker")) {
      args.add(actor.employeeId());
      args.add(actor.employeeId());
      return "(o.applicant_id=? OR COALESCE(o.receiver_id,(SELECT p.employee_id FROM verified_phone"
          + " p WHERE p.phone=o.receiver_phone))=?)";
    }
    if (grant.scope().equals("ALL")) return "1=1";
    if (grant.warehouseIds().isEmpty()) return "1=0";
    args.addAll(grant.warehouseIds());
    return "o.warehouse_id IN ("
        + String.join(",", Collections.nCopies(grant.warehouseIds().size(), "?"))
        + ")";
  }

  public Map<String, Object> get(Actor actor, String id) {
    var args = new ArrayList<Object>();
    String where = scope(actor, args);
    args.add(id);
    var rows =
        db.queryForList(
            "SELECT o.*,s.start_at,s.end_at,w.name warehouse_name,t.name stop_name,e.name"
                + " applicant_name,COALESCE(o.receiver_id,(SELECT p.employee_id FROM verified_phone"
                + " p WHERE p.phone=o.receiver_phone)) resolved_receiver FROM delivery_order o JOIN"
                + " reservation_slot s ON s.id=o.slot_id JOIN warehouse w ON w.id=o.warehouse_id"
                + " JOIN stop t ON t.id=o.stop_id JOIN employee e ON e.id=o.applicant_id WHERE "
                + where
                + " AND o.id=?",
            args.toArray());
    if (rows.isEmpty()) throw new ApiException(404, 40400, "订单不存在或不在权限范围");
    return view(actor, rows.get(0));
  }

  private Map<String, Object> view(Actor actor, Map<String, Object> row) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (String field :
        List.of("id", "number", "version", "description", "size", "remark", "status"))
      result.put(field, row.get(field));
    for (String field :
        List.of(
            "warehouse_id",
            "stop_id",
            "slot_id",
            "applicant_id",
            "receiver_name",
            "receiver_phone",
            "warehouse_name",
            "stop_name",
            "applicant_name")) {
      String[] pieces = field.split("_");
      result.put(
          pieces[0] + pieces[1].substring(0, 1).toUpperCase(Locale.ROOT) + pieces[1].substring(1),
          row.get(field));
    }
    result.put(
        "slotStart",
        com.taskhub.infrastructure.DatabaseTime.instant(row.get("start_at")).toString());
    result.put(
        "slotEnd", com.taskhub.infrastructure.DatabaseTime.instant(row.get("end_at")).toString());
    result.put(
        "createdAt",
        com.taskhub.infrastructure.DatabaseTime.instant(row.get("created_at")).toString());
    result.put(
        "updatedAt",
        com.taskhub.infrastructure.DatabaseTime.instant(row.get("updated_at")).toString());
    boolean applicant = actor.employeeId().equals(row.get("applicant_id")),
        receiver = actor.employeeId().equals(row.get("resolved_receiver"));
    result.put("receiverBound", row.get("resolved_receiver") != null);
    result.put(
        "relation",
        applicant ? (receiver ? "BOTH" : "APPLICANT") : receiver ? "RECEIVER" : "STAFF");
    var cancellations =
        db.queryForList(
            "SELECT id,status,reason,result FROM order_cancellation WHERE order_id=? ORDER BY"
                + " created_at DESC,id DESC LIMIT 1",
            row.get("id"));
    var cancellation = cancellations.isEmpty() ? null : cancellations.get(0);
    result.put("cancellation", cancellation);
    boolean terminal = List.of("COMPLETED", "REJECTED", "CANCELLED").contains(row.get("status"));
    result.put(
        "allowedActions",
        actor.workspace().equals("worker")
                && applicant
                && !terminal
                && (cancellation == null || !"PENDING".equals(cancellation.get("status")))
            ? List.of("ORDER_CANCEL")
            : List.of());
    if ("warehouse".equals(actor.workspace())) {
      var actions = new ArrayList<String>();
      if ("PENDING".equals(row.get("status"))) actions.add("ORDER_REVIEW");
      if (cancellation != null && "PENDING".equals(cancellation.get("status")))
        actions.add("CANCELLATION_REVIEW");
      result.put("allowedActions", actions);
    }
    var batches =
        db.queryForList(
            "SELECT b.id,b.vehicle_id,b.status FROM active_order_batch a JOIN batch b ON"
                + " b.id=a.batch_id WHERE a.order_id=?",
            row.get("id"));
    var batch = batches.isEmpty() ? null : batches.get(0);
    result.put("batchId", batch == null ? null : batch.get("id"));
    result.put("vehicleId", batch == null ? null : batch.get("vehicle_id"));
    result.put(
        "compartmentIds",
        batch == null
            ? List.of()
            : db.queryForList(
                "SELECT compartment_id FROM batch_assignment WHERE batch_id=? AND order_id=? ORDER"
                    + " BY compartment_id",
                String.class,
                batch.get("id"),
                row.get("id")));
    result.put("activeTicketId", null);
    result.put("dispatchPendingReview", batch != null && "LOCKED".equals(batch.get("status")));
    return result;
  }

  public PageResponse<Map<String, Object>> list(
      Actor actor,
      String status,
      String warehouse,
      String keyword,
      String from,
      String to,
      int page,
      int size) {
    if (page < 1 || page > 100000 || size < 1 || size > 100) throw bad("分页参数无效");
    var args = new ArrayList<Object>();
    String where = scope(actor, args);
    if (status != null && !status.isBlank()) {
      where += " AND o.status=?";
      args.add(status);
    }
    if (warehouse != null && !warehouse.isBlank()) {
      where += " AND o.warehouse_id=?";
      args.add(warehouse);
    }
    if (keyword != null && !keyword.isBlank()) {
      where += " AND (LOCATE(?,o.number)>0 OR LOCATE(?,o.description)>0)";
      args.add(keyword);
      args.add(keyword);
    }
    try {
      if (from != null) {
        where += " AND o.created_at>=?";
        args.add(
            Timestamp.from(
                java.time.LocalDate.parse(from).atStartOfDay(ReservationService.ZONE).toInstant()));
      }
      if (to != null) {
        where += " AND o.created_at<?";
        args.add(
            Timestamp.from(
                java.time.LocalDate.parse(to)
                    .plusDays(1)
                    .atStartOfDay(ReservationService.ZONE)
                    .toInstant()));
      }
    } catch (java.time.DateTimeException e) {
      throw bad("日期格式无效");
    }
    int count =
        db.queryForObject(
            "SELECT COUNT(*) FROM delivery_order o WHERE " + where, Integer.class, args.toArray());
    args.add(size);
    args.add((page - 1) * size);
    var ids =
        db.queryForList(
            "SELECT o.id FROM delivery_order o JOIN reservation_slot s ON s.id=o.slot_id WHERE "
                + where
                + " ORDER BY s.start_at,o.created_at,o.id LIMIT ? OFFSET ?",
            String.class,
            args.toArray());
    return new PageResponse<>(ids.stream().map(id -> get(actor, id)).toList(), count, page, size);
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> cancel(
      Actor actor, String id, String key, OrderModels.Cancellation input) {
    auth.requireWorkspace(actor, "worker");
    get(actor, id);
    return idem.execute(
        actor,
        "POST /orders/" + id + "/cancellations",
        key,
        input,
        Map.class,
        () -> {
          var rows = db.queryForList("SELECT id FROM delivery_order WHERE id=? FOR UPDATE", id);
          if (rows.isEmpty()) throw new ApiException(404, 40400, "订单不存在");
          var before = get(actor, id);
          if (!actor.employeeId().equals(before.get("applicantId")))
            throw AuthorizationService.denied();
          if (input.expectedVersion() == null
              || input.expectedVersion().intValue() != ((Number) before.get("version")).intValue())
            throw new ApiException(409, 40902, "版本已变化，请重新读取");
          if (before.get("cancellation") instanceof Map<?, ?> cancellation
              && "PENDING".equals(cancellation.get("status"))) return before;
          if (!((List<?>) before.get("allowedActions")).contains("ORDER_CANCEL"))
            throw new ApiException(409, 40903, "当前不能申请取消");
          String reason = text(input.reason(), 1000);
          db.update(
              "INSERT INTO order_cancellation(id,order_id,status,reason) VALUES(?,?,'PENDING',?)",
              UUID.randomUUID().toString(),
              id,
              reason);
          db.update(
              "UPDATE delivery_order SET version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE id=?",
              id);
          var result = get(actor, id);
          audit.record(actor, "ORDER", id, "ORDER_CANCEL_REQUEST", before, result, reason);
          events.append("ORDER_CANCEL_REQUESTED", id, Map.of("orderId", id));
          return result;
        });
  }

  public PageResponse<Map<String, Object>> history(
      Actor actor, String id, String from, String to, int page, int size) {
    get(actor, id);
    if (page < 1 || page > 100000 || size < 1 || size > 100) throw bad("分页参数无效");
    String where = "object_type='ORDER' AND object_id=?";
    var args = new ArrayList<Object>();
    args.add(id);
    try {
      java.time.LocalDate fromDate =
          from == null || from.isBlank() ? null : java.time.LocalDate.parse(from);
      java.time.LocalDate toDate =
          to == null || to.isBlank() ? null : java.time.LocalDate.parse(to);
      if (fromDate != null && toDate != null && fromDate.isAfter(toDate))
        throw bad("开始日期不能晚于结束日期");
      if (fromDate != null) {
        where += " AND occurred_at>=?";
        args.add(Timestamp.from(fromDate.atStartOfDay(ReservationService.ZONE).toInstant()));
      }
      if (toDate != null) {
        where += " AND occurred_at<?";
        args.add(
            Timestamp.from(
                toDate.plusDays(1).atStartOfDay(ReservationService.ZONE).toInstant()));
      }
    } catch (java.time.DateTimeException e) {
      throw bad("日期格式无效");
    }
    int count =
        db.queryForObject(
            "SELECT COUNT(*) FROM audit_event WHERE " + where,
            Integer.class,
            args.toArray());
    args.add(size);
    args.add((page - 1) * size);
    var rows =
        db.queryForList(
            "SELECT id,action,actor_name,occurred_at,reason FROM audit_event WHERE"
                + " "
                + where
                + " ORDER BY occurred_at DESC,id DESC LIMIT ?"
                + " OFFSET ?",
            args.toArray());
    return new PageResponse<>(
        rows.stream()
            .map(
                row -> {
                  Map<String, Object> result = new LinkedHashMap<>();
                  result.put("id", row.get("id"));
                  result.put("action", row.get("action"));
                  result.put("actorName", row.get("actor_name"));
                  result.put(
                      "occurredAt",
                      com.taskhub.infrastructure.DatabaseTime.instant(row.get("occurred_at"))
                          .toString());
                  result.put(
                      "summary",
                      Map.of(
                              "ORDER_CREATE",
                              "申请已提交",
                              "ORDER_CANCEL_REQUEST",
                              "取消申请已提交",
                              "ORDER_ACCEPT",
                              "仓库已受理",
                              "ORDER_REJECT",
                              "仓库已驳回",
                              "CANCELLATION_APPROVE",
                              "取消申请已批准",
                              "CANCELLATION_REJECT",
                              "取消申请已拒绝")
                          .getOrDefault(String.valueOf(row.get("action")), "业务记录已更新"));
                  result.put("reason", row.get("reason"));
                  return result;
                })
            .toList(),
        count,
        page,
        size);
  }

  @Override
  public void requireChangeAllowed(String resource, String id) {
    if (!Set.of("warehouse", "stop").contains(resource)) return;
    String column = resource.equals("warehouse") ? "warehouse_id" : "stop_id";
    if (db.queryForObject(
            "SELECT COUNT(*) FROM delivery_order WHERE "
                + column
                + "=? AND status NOT IN ('COMPLETED','REJECTED','CANCELLED')",
            Integer.class,
            id)
        > 0) throw new ApiException(409, 40903, "存在活动订单，不能停用或改变关联");
  }
}
