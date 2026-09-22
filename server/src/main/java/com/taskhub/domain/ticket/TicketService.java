package com.taskhub.domain.ticket;

import com.taskhub.api.*;
import com.taskhub.domain.audit.AuditService;
import com.taskhub.domain.identity.*;
import com.taskhub.domain.identity.IdentityModels.Actor;
import com.taskhub.domain.notification.BusinessEventService;
import com.taskhub.domain.order.OrderService;
import com.taskhub.infrastructure.DatabaseTime;
import com.taskhub.infrastructure.idempotency.IdempotencyService;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TicketService {
  private final JdbcTemplate db;
  private final AuthorizationService auth;
  private final OrderService orders;
  private final IdempotencyService idem;
  private final AuditService audit;
  private final BusinessEventService events;

  public TicketService(
      JdbcTemplate db,
      AuthorizationService auth,
      OrderService orders,
      IdempotencyService idem,
      AuditService audit,
      BusinessEventService events) {
    this.db = db;
    this.auth = auth;
    this.orders = orders;
    this.idem = idem;
    this.audit = audit;
    this.events = events;
  }

  private ApiException missing() {
    return new ApiException(404, 40400, "工单不存在或无权查看");
  }

  private Map<String, Object> row(String id, boolean lock) {
    var rows = db.queryForList("SELECT * FROM ticket WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
    if (rows.isEmpty()) throw missing();
    return rows.get(0);
  }

  private void scope(Actor actor, Map<String, Object> row) {
    if ("worker".equals(actor.workspace())) {
      auth.requireWorkspace(actor, "worker");
      if (!actor.employeeId().equals(row.get("reporter_id"))) throw missing();
    } else if ("warehouse".equals(actor.workspace())) {
      var grant = auth.requireWorkspace(actor, "warehouse");
      if (!AuthorizationService.covers(
          grant.scope(), grant.warehouseIds(), (String) row.get("warehouse_id"))) throw missing();
    } else throw AuthorizationService.denied();
  }

  public Map<String, Object> get(Actor actor, String id) {
    var row = row(id, false);
    scope(actor, row);
    var out = new LinkedHashMap<String, Object>();
    for (String name : List.of("id", "number", "version", "description", "state", "result"))
      out.put(name, row.get(name));
    out.put("orderId", row.get("order_id"));
    out.put("warehouseId", row.get("warehouse_id"));
    out.put("reporterId", row.get("reporter_id"));
    out.put("createdAt", DatabaseTime.instant(row.get("created_at")).toString());
    out.put("updatedAt", DatabaseTime.instant(row.get("updated_at")).toString());
    out.put(
        "notes",
        db
            .queryForList(
                "SELECT id,author_name,text,created_at FROM ticket_note WHERE ticket_id=? ORDER BY"
                    + " created_at,id",
                id)
            .stream()
            .map(
                n ->
                    Map.of(
                        "id",
                        n.get("id"),
                        "authorName",
                        n.get("author_name"),
                        "text",
                        n.get("text"),
                        "createdAt",
                        DatabaseTime.instant(n.get("created_at")).toString()))
            .toList());
    out.put(
        "events",
        db
            .queryForList(
                "SELECT id,action,actor_name,workspace,occurred_at,reason FROM audit_event WHERE"
                    + " object_type='TICKET' AND object_id=? ORDER BY occurred_at,id",
                id)
            .stream()
            .map(
                n -> {
                  var item = new LinkedHashMap<String, Object>();
                  item.put("id", n.get("id"));
                  item.put("action", n.get("action"));
                  item.put("actorName", n.get("actor_name"));
                  item.put("workspace", n.get("workspace"));
                  item.put("occurredAt", DatabaseTime.instant(n.get("occurred_at")).toString());
                  item.put("reason", n.get("reason"));
                  item.put(
                      "summary",
                      switch ((String) n.get("action")) {
                        case "TICKET_CREATE" -> "提交反馈";
                        case "TICKET_ACCEPT" -> "仓库已受理";
                        case "TICKET_NOTE" -> "补充处理说明";
                        case "TICKET_CLOSE" -> "工单已关闭";
                        default -> "工单更新";
                      });
                  return item;
                })
            .toList());
    out.put(
        "allowedActions",
        "warehouse".equals(actor.workspace()) && !"CLOSED".equals(row.get("state"))
            ? ("OPEN".equals(row.get("state"))
                ? List.of("TICKET_ACCEPT", "TICKET_NOTE", "TICKET_CLOSE")
                : List.of("TICKET_NOTE", "TICKET_CLOSE"))
            : List.of());
    return out;
  }

  public PageResponse<Map<String, Object>> list(
      Actor actor, String state, String warehouse, int page, int size) {
    if (page < 1 || page > 100000 || size < 1 || size > 100)
      throw new ApiException(400, 40000, "分页参数无效");
    var args = new ArrayList<Object>();
    String where;
    if ("worker".equals(actor.workspace())) {
      auth.requireWorkspace(actor, "worker");
      where = "reporter_id=?";
      args.add(actor.employeeId());
    } else {
      var grant = auth.requireWorkspace(actor, "warehouse");
      where = "1=1";
      if (!"ALL".equals(grant.scope())) {
        if (grant.warehouseIds().isEmpty()) return new PageResponse<>(List.of(), 0, page, size);
        where +=
            " AND warehouse_id IN ("
                + String.join(",", Collections.nCopies(grant.warehouseIds().size(), "?"))
                + ")";
        args.addAll(grant.warehouseIds());
      }
    }
    if (state != null && !state.isBlank()) {
      if (!Set.of("OPEN", "PROCESSING", "CLOSED").contains(state))
        throw new ApiException(400, 40000, "工单状态无效");
      where += " AND state=?";
      args.add(state);
    }
    if (warehouse != null && !warehouse.isBlank()) {
      where += " AND warehouse_id=?";
      args.add(warehouse);
    }
    int total =
        db.queryForObject(
            "SELECT COUNT(*) FROM ticket WHERE " + where, Integer.class, args.toArray());
    args.add(size);
    args.add((page - 1) * size);
    var ids =
        db.queryForList(
            "SELECT id FROM ticket WHERE "
                + where
                + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
            String.class,
            args.toArray());
    return new PageResponse<>(ids.stream().map(id -> get(actor, id)).toList(), total, page, size);
  }

  private String vehicleForOrder(String order) {
    var rows =
        db.queryForList(
            "SELECT b.vehicle_id FROM active_order_batch a JOIN batch b ON b.id=a.batch_id WHERE"
                + " a.order_id=?",
            order);
    return rows.isEmpty() ? null : (String) rows.get(0).get("vehicle_id");
  }

  private void lockOrderContext(String order) {
    String vehicle = vehicleForOrder(order);
    if (vehicle != null) {
      db.queryForList("SELECT id FROM vehicle WHERE id=? FOR UPDATE", vehicle);
      db.queryForList(
          "SELECT vt.id FROM vehicle_task vt JOIN active_vehicle_task a ON a.task_id=vt.id WHERE"
              + " a.vehicle_id=? FOR UPDATE",
          vehicle);
    }
    db.queryForList("SELECT id FROM delivery_order WHERE id=? FOR UPDATE", order);
    if (!Objects.equals(vehicle, vehicleForOrder(order)))
      throw new ApiException(409, 40903, "订单车辆关联已更新，请重新读取");
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> create(Actor actor, String key, TicketModels.Create input) {
    auth.requireWorkspace(actor, "worker");
    if (input == null || input.orderId() == null) throw new ApiException(400, 40000, "关联订单必填");
    orders.get(actor, input.orderId());
    String description = IdentityValidation.required(input.description(), 2000, "反馈说明");
    return idem.execute(
        actor,
        "POST /tickets",
        key,
        input,
        Map.class,
        () -> {
          lockOrderContext(input.orderId());
          var order = orders.get(actor, input.orderId());
          var active =
              db.queryForList(
                  "SELECT ticket_id FROM active_order_ticket WHERE order_id=?",
                  String.class,
                  input.orderId());
          if (!active.isEmpty()) {
            if (!actor.employeeId().equals(row(active.get(0), false).get("reporter_id")))
              throw new ApiException(409, 40903, "该订单已有活动工单，请联系仓库处理");
            return get(actor, active.get(0));
          }
          String id = UUID.randomUUID().toString();
          db.update(
              "INSERT INTO ticket(id,number,order_id,warehouse_id,reporter_id,description)"
                  + " VALUES(?,?,?,?,?,?)",
              id,
              "T" + id.replace("-", ""),
              input.orderId(),
              order.get("warehouseId"),
              actor.employeeId(),
              description);
          db.update(
              "INSERT INTO active_order_ticket(order_id,ticket_id) VALUES(?,?)",
              input.orderId(),
              id);
          audit.record(actor, "TICKET", id, "TICKET_CREATE", null, get(actor, id), null);
          events.append("TICKET_CREATED", id, Map.of("ticketId", id, "orderId", input.orderId()));
          return get(actor, id);
        });
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> action(
      Actor actor, String id, String key, String action, TicketModels.Action input) {
    auth.requireWorkspace(actor, "warehouse");
    get(actor, id);
    return idem.execute(
        actor,
        "POST /tickets/" + id + "/" + action,
        key,
        input,
        Map.class,
        () -> {
          var current = row(id, true);
          scope(actor, current);
          if (input.expectedVersion() == null)
            throw new ApiException(400, 40000, "expectedVersion必填");
          if (input.expectedVersion().intValue() != ((Number) current.get("version")).intValue())
            throw new ApiException(409, 40902, "工单已更新，请重新读取");
          if ("CLOSED".equals(current.get("state"))) throw new ApiException(409, 40903, "工单已关闭");
          String reason = null, next = (String) current.get("state");
          var before = get(actor, id);
          switch (action) {
            case "accept" -> {
              if (!"OPEN".equals(next)) throw new ApiException(409, 40903, "工单已受理");
              next = "PROCESSING";
            }
            case "notes" -> {
              reason = IdentityValidation.required(input.text(), 1000, "处理说明");
              db.update(
                  "INSERT INTO ticket_note(id,ticket_id,author_id,author_name,text)"
                      + " VALUES(?,?,?,?,?)",
                  UUID.randomUUID().toString(),
                  id,
                  actor.employeeId(),
                  actor.identity().name(),
                  reason);
            }
            case "close" -> {
              reason = IdentityValidation.required(input.result(), 1000, "处理结果");
              next = "CLOSED";
              db.update("DELETE FROM active_order_ticket WHERE ticket_id=?", id);
            }
            default -> throw new ApiException(400, 40000, "工单动作无效");
          }
          db.update(
              "UPDATE ticket SET state=?,result=?,version=version+1,updated_at=UTC_TIMESTAMP(3)"
                  + " WHERE id=?",
              next,
              action.equals("close") ? reason : current.get("result"),
              id);
          String event = action.equals("notes") ? "NOTE" : action.toUpperCase(Locale.ROOT);
          audit.record(actor, "TICKET", id, "TICKET_" + event, before, get(actor, id), reason);
          events.append(
              "TICKET_" + event, id, Map.of("ticketId", id, "orderId", current.get("order_id")));
          return get(actor, id);
        });
  }
}
