package com.taskhub.domain.notification;

import com.taskhub.api.*;
import com.taskhub.domain.batch.BatchService;
import com.taskhub.domain.identity.*;
import com.taskhub.domain.identity.IdentityModels.Actor;
import com.taskhub.domain.order.OrderService;
import com.taskhub.domain.ticket.TicketService;
import com.taskhub.domain.vehicle.VehicleQueryService;
import com.taskhub.infrastructure.DatabaseTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
  private final JdbcTemplate db;
  private final AuthorizationService auth;
  private final OrderService orders;
  private final BatchService batches;
  private final TicketService tickets;
  private final VehicleQueryService vehicles;

  public NotificationService(
      JdbcTemplate db,
      AuthorizationService auth,
      OrderService orders,
      BatchService batches,
      TicketService tickets,
      VehicleQueryService vehicles) {
    this.db = db;
    this.auth = auth;
    this.orders = orders;
    this.batches = batches;
    this.tickets = tickets;
    this.vehicles = vehicles;
  }

  public Map<String, Object> get(Actor actor, String id) {
    auth.refresh(actor);
    var rows =
        db.queryForList(
            "SELECT * FROM notification WHERE id=? AND employee_id=?", id, actor.employeeId());
    if (rows.isEmpty()) throw new ApiException(404, 40400, "消息不存在");
    var row = rows.get(0);
    var out = new LinkedHashMap<String, Object>();
    for (String name : List.of("id", "title", "body")) out.put(name, row.get(name));
    out.put("createdAt", DatabaseTime.instant(row.get("created_at")).toString());
    out.put(
        "readAt",
        row.get("read_at") == null ? null : DatabaseTime.instant(row.get("read_at")).toString());
    out.put("target", target(actor, row));
    return out;
  }

  private Object target(Actor actor, Map<String, Object> row) {
    String type = (String) row.get("target_type"),
        id = (String) row.get("target_id"),
        workspace = (String) row.get("target_workspace");
    try {
      Actor targetActor = actor.withWorkspace(workspace);
      auth.requireWorkspace(targetActor, workspace);
      switch (type) {
        case "ORDER" -> orders.get(targetActor, id);
        case "BATCH" -> batches.get(targetActor, id);
        case "TICKET" -> tickets.get(targetActor, id);
        case "TASK" -> vehicles.task(targetActor, id);
        default -> {
          return null;
        }
      }
      return Map.of("type", type, "id", id, "workspace", workspace);
    } catch (ApiException e) {
      if (e.status() == 403 || e.status() == 404) return null;
      throw e;
    }
  }

  public PageResponse<Map<String, Object>> list(Actor actor, boolean unread, int page, int size) {
    auth.refresh(actor);
    if (page < 1 || page > 100000 || size < 1 || size > 100)
      throw new ApiException(400, 40000, "分页参数无效");
    String where = "employee_id=?" + (unread ? " AND read_at IS NULL" : "");
    int total =
        db.queryForObject(
            "SELECT COUNT(*) FROM notification WHERE " + where, Integer.class, actor.employeeId());
    var ids =
        db.queryForList(
            "SELECT id FROM notification WHERE "
                + where
                + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
            String.class,
            actor.employeeId(),
            size,
            (page - 1) * size);
    return new PageResponse<>(ids.stream().map(id -> get(actor, id)).toList(), total, page, size);
  }

  public Map<String, Integer> count(Actor actor) {
    auth.refresh(actor);
    return Map.of(
        "count",
        db.queryForObject(
            "SELECT COUNT(*) FROM notification WHERE employee_id=? AND read_at IS NULL",
            Integer.class,
            actor.employeeId()));
  }

  @Transactional
  public Map<String, Object> read(Actor actor, String id) {
    auth.lockActor(actor);
    get(actor, id);
    db.update(
        "UPDATE notification SET read_at=COALESCE(read_at,UTC_TIMESTAMP(3)) WHERE id=? AND"
            + " employee_id=?",
        id,
        actor.employeeId());
    return get(actor, id);
  }
}
