package com.taskhub.domain.vehicle;

import com.taskhub.api.*;
import com.taskhub.domain.identity.*;
import com.taskhub.domain.identity.IdentityModels.Actor;
import com.taskhub.domain.masterdata.RuleService;
import com.taskhub.infrastructure.DatabaseTime;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class VehicleQueryService {
  private final JdbcTemplate db;
  private final AuthorizationService auth;
  private final RuleService rules;

  public VehicleQueryService(JdbcTemplate db, AuthorizationService auth, RuleService rules) {
    this.db = db;
    this.auth = auth;
    this.rules = rules;
  }

  public void requireRead(Actor actor, String warehouse) {
    if (!Set.of("warehouse", "dispatch").contains(String.valueOf(actor.workspace())))
      throw AuthorizationService.denied();
    var grant = auth.requireWorkspace(actor, actor.workspace());
    if (!AuthorizationService.covers(grant.scope(), grant.warehouseIds(), warehouse))
      throw new ApiException(404, 40400, "对象不存在或不在权限范围");
  }

  public Map<String, Object> get(Actor actor, String id) {
    var vehicle = vehicle(id);
    requireRead(actor, (String) vehicle.get("warehouse_id"));
    return snapshot(id);
  }

  public Map<String, Object> vehicle(String id) {
    var rows = db.queryForList("SELECT * FROM vehicle WHERE id=?", id);
    if (rows.isEmpty()) throw new ApiException(404, 40400, "车辆不存在");
    return rows.get(0);
  }

  public static String time(Object value) {
    return value == null ? null : DatabaseTime.instant(value).toString();
  }

  public static boolean fresh(Object value, int seconds) {
    if (value == null) return false;
    Instant instant = DatabaseTime.instant(value), now = Instant.now();
    return !instant.isAfter(now.plusSeconds(5)) && !instant.isBefore(now.minusSeconds(seconds));
  }

  public Map<String, Object> snapshot(String id) {
    var vehicle = vehicle(id);
    var config = rules.read((String) vehicle.get("warehouse_id"));
    var rows = db.queryForList("SELECT * FROM vehicle_snapshot WHERE vehicle_id=?", id);
    var row = rows.isEmpty() ? Map.<String, Object>of() : rows.get(0);
    var active = db.queryForList("SELECT task_id FROM active_vehicle_task WHERE vehicle_id=?", id);
    var result = new LinkedHashMap<String, Object>();
    result.put("id", id);
    result.put("name", vehicle.get("name"));
    result.put("warehouseId", vehicle.get("warehouse_id"));
    result.put("version", vehicle.get("version"));
    for (String field :
        List.of(
            "online",
            "speed",
            "battery_percent",
            "business_status",
            "dispatch_id",
            "current_stop_id",
            "next_stop_id",
            "longitude",
            "latitude",
            "distance_to_next_stop_meters",
            "historical_leg_minutes")) result.put(camel(field), row.get(field));
    result.put("reportedAt", time(row.get("reported_at")));
    result.put("receivedAt", time(row.get("received_at")));
    result.put("businessReportedAt", time(row.get("business_reported_at")));
    boolean fresh = fresh(row.get("reported_at"), config.telemetryMaxAgeSeconds());
    result.put("fresh", fresh);
    result.put("taskId", active.isEmpty() ? null : active.get(0).get("task_id"));
    var doors =
        db
            .queryForList(
                "SELECT c.id,d.status,d.reported_at FROM compartment c LEFT JOIN"
                    + " vehicle_door_snapshot d ON d.compartment_id=c.id WHERE c.vehicle_id=? AND"
                    + " c.enabled=true ORDER BY c.id",
                id)
            .stream()
            .map(
                d -> {
                  var view = new LinkedHashMap<String, Object>();
                  view.put("compartmentId", d.get("id"));
                  view.put("status", d.get("status") == null ? "UNKNOWN" : d.get("status"));
                  view.put("reportedAt", time(d.get("reported_at")));
                  view.put("fresh", fresh(d.get("reported_at"), config.doorMaxAgeSeconds()));
                  return view;
                })
            .toList();
    result.put("doors", doors);
    var blockers = new ArrayList<Map<String, String>>();
    if (!fresh) blockers.add(Map.of("code", "STALE_VEHICLE", "message", "车辆状态已过期或未知"));
    if (!Boolean.TRUE.equals(row.get("online")))
      blockers.add(Map.of("code", "OFFLINE", "message", "车辆离线或在线状态未知"));
    if (Boolean.TRUE.equals(row.get("reconciliation")))
      blockers.add(Map.of("code", "RECONCILIATION", "message", "车辆状态冲突，待核对"));
    for (var door : doors)
      if (!"CLOSED".equals(door.get("status")) || !Boolean.TRUE.equals(door.get("fresh"))) {
        blockers.add(Map.of("code", "DOOR_UNSAFE", "message", "格口门未关或状态未知/过期"));
        break;
      }
    result.put("blockers", blockers);
    result.put("allowedActions", List.of());
    return result;
  }

  private static String camel(String field) {
    String[] p = field.split("_");
    String key = p[0];
    for (int i = 1; i < p.length; i++)
      key += Character.toUpperCase(p[i].charAt(0)) + p[i].substring(1);
    return key;
  }

  private String scope(Actor actor, List<Object> args, String prefix) {
    if (!Set.of("warehouse", "dispatch").contains(String.valueOf(actor.workspace())))
      throw AuthorizationService.denied();
    var g = auth.requireWorkspace(actor, actor.workspace());
    if ("ALL".equals(g.scope())) return "1=1";
    if (g.warehouseIds().isEmpty()) return "1=0";
    args.addAll(g.warehouseIds());
    return prefix
        + "warehouse_id IN ("
        + String.join(",", Collections.nCopies(g.warehouseIds().size(), "?"))
        + ")";
  }

  private void paging(int page, int size) {
    if (page < 1 || page > 100000 || size < 1 || size > 100)
      throw new ApiException(400, 40000, "分页参数无效");
  }

  public PageResponse<Map<String, Object>> list(Actor actor, String warehouse, int page, int size) {
    paging(page, size);
    var args = new ArrayList<Object>();
    String where = scope(actor, args, "");
    if (warehouse != null) {
      where += " AND warehouse_id=?";
      args.add(warehouse);
    }
    int count =
        db.queryForObject(
            "SELECT COUNT(*) FROM vehicle WHERE " + where, Integer.class, args.toArray());
    args.add(size);
    args.add((page - 1) * size);
    var ids =
        db.queryForList(
            "SELECT id FROM vehicle WHERE " + where + " ORDER BY id LIMIT ? OFFSET ?",
            String.class,
            args.toArray());
    return new PageResponse<>(ids.stream().map(id -> get(actor, id)).toList(), count, page, size);
  }

  public Map<String, Object> task(Actor actor, String id) {
    var rows =
        db.queryForList(
            "SELECT t.*,b.warehouse_id,b.stop_id,r.from_stop_id,r.goal_stop_id FROM vehicle_task t"
                + " JOIN batch b ON b.id=t.batch_id JOIN dispatch_request r ON"
                + " r.id=t.dispatch_request_id WHERE t.id=?",
            id);
    if (rows.isEmpty()) throw new ApiException(404, 40400, "任务不存在");
    var row = rows.get(0);
    requireRead(actor, (String) row.get("warehouse_id"));
    var result = new LinkedHashMap<String, Object>();
    for (String f :
        List.of(
            "id",
            "vehicle_id",
            "batch_id",
            "dispatch_id",
            "state",
            "current_stop_id",
            "next_stop_id")) result.put(camel(f), row.get(f));
    for (String f : List.of("created_at", "updated_at")) result.put(camel(f), time(row.get(f)));
    var goals = new ArrayList<Map<String, Object>>();
    for (String field : List.of("from_stop_id", "goal_stop_id")) {
      var goal = new LinkedHashMap<String, Object>();
      goal.put("index", goals.size());
      goal.put("stopId", row.get(field));
      goal.put(
          "stopName",
          db.queryForObject("SELECT name FROM stop WHERE id=?", String.class, row.get(field)));
      goal.put("arrivalAt", null);
      goal.put("departureAt", null);
      goals.add(goal);
    }
    result.put("goals", goals);
    result.put("requests", List.of());
    result.put("events", List.of());
    return result;
  }

  public PageResponse<Map<String, Object>> tasks(
      Actor actor, String warehouse, String state, int page, int size) {
    paging(page, size);
    var args = new ArrayList<Object>();
    String where = scope(actor, args, "b.");
    if (warehouse != null) {
      where += " AND b.warehouse_id=?";
      args.add(warehouse);
    }
    if (state != null) {
      where += " AND t.state=?";
      args.add(state);
    }
    String from = " FROM vehicle_task t JOIN batch b ON b.id=t.batch_id WHERE ";
    int total = db.queryForObject("SELECT COUNT(*)" + from + where, Integer.class, args.toArray());
    args.add(size);
    args.add((page - 1) * size);
    var ids =
        db.queryForList(
            "SELECT t.id" + from + where + " ORDER BY t.created_at DESC,t.id LIMIT ? OFFSET ?",
            String.class,
            args.toArray());
    return new PageResponse<>(ids.stream().map(id -> task(actor, id)).toList(), total, page, size);
  }
}
