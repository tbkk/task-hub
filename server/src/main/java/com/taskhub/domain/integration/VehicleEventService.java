package com.taskhub.domain.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.api.ApiException;
import com.taskhub.domain.notification.BusinessEventService;
import com.taskhub.infrastructure.DatabaseTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class VehicleEventService {
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final BusinessEventService events;
  private final com.taskhub.domain.identity.AuthorizationService auth;

  public VehicleEventService(
      JdbcTemplate db,
      ObjectMapper json,
      BusinessEventService events,
      com.taskhub.domain.identity.AuthorizationService auth) {
    this.db = db;
    this.json = json;
    this.events = events;
    this.auth = auth;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Map<String, Object> acceptSimulator(
      com.taskhub.domain.identity.IdentityModels.Actor actor, VehicleEvent event) {
    auth.lockActor(actor);
    auth.requirePlatform(actor, "INTEGRATION_MANAGE", null);
    return accept("SIMULATOR", event);
  }

  private ApiException bad(String message) {
    return new ApiException(400, 40000, message);
  }

  private String text(String value, int max) {
    if (value == null || value.isBlank() || value.length() > max) throw bad("事件标识无效");
    return value;
  }

  private static int compare(Instant incoming, Object old) {
    return old == null ? 1 : incoming.compareTo(DatabaseTime.instant(old));
  }

  private static boolean same(Object a, Object b) {
    if (a instanceof Number x && b instanceof Number y)
      return new java.math.BigDecimal(x.toString())
              .compareTo(new java.math.BigDecimal(y.toString()))
          == 0;
    return Objects.equals(a, b);
  }

  private static boolean differs(Object incoming, Object previous) {
    return incoming != null && !same(incoming, previous);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Map<String, Object> accept(String provider, VehicleEvent e) {
    if (!Set.of("SIMULATOR", "JIUSHI").contains(provider)) throw bad("事件来源无效");
    text(e.eventId(), 128);
    text(e.vehicleId(), 36);
    if (e.dispatchId() != null) text(e.dispatchId(), 64);
    if (e.reportedAt() == null || e.reportedAt().isAfter(Instant.now().plusSeconds(30)))
      throw bad("事件时间无效或超前");
    if (e.businessStatus() != null
        && !Set.of("IDLE", "PLANNING", "ONWAY", "ATSTOP", "FINISHED", "CANCELLED", "ERROR")
            .contains(e.businessStatus())) throw bad("业务状态无效");
    if (e.speed() != null && e.speed().signum() < 0) throw bad("速度不能为负");
    if (e.batteryPercent() != null
        && (e.batteryPercent().signum() < 0
            || e.batteryPercent().compareTo(java.math.BigDecimal.valueOf(100)) > 0))
      throw bad("电量无效");
    // 与派发一致先锁批次再锁车辆，避免任务结束关闭批次时形成反向锁。
    var activeBefore =
        db.queryForList(
            "SELECT r.batch_id FROM active_vehicle_task a JOIN dispatch_request r ON"
                + " r.id=a.dispatch_request_id WHERE a.vehicle_id=?",
            String.class,
            e.vehicleId());
    if (!activeBefore.isEmpty())
      db.queryForMap("SELECT id FROM batch WHERE id=? FOR UPDATE", activeBefore.get(0));
    var vehicle = db.queryForList("SELECT id FROM vehicle WHERE id=? FOR UPDATE", e.vehicleId());
    if (vehicle.isEmpty()) throw new ApiException(404, 40400, "车辆不存在");
    var activeAfter =
        db.queryForList(
            "SELECT r.batch_id FROM active_vehicle_task a JOIN dispatch_request r ON"
                + " r.id=a.dispatch_request_id WHERE a.vehicle_id=?",
            String.class,
            e.vehicleId());
    if (!activeBefore.equals(activeAfter)) throw new ApiException(409, 40903, "车辆活动任务正在变化，请重试同一事件");
    var doorIds = new HashSet<String>();
    if (e.doors() != null) {
      if (e.doors().size() > 100) throw bad("门数量超限");
      for (var door : e.doors()) {
        if (door == null
            || !doorIds.add(door.compartmentId())
            || !Set.of("OPEN", "CLOSED", "UNKNOWN").contains(String.valueOf(door.status())))
          throw bad("门事件无效或重复");
        if (db.queryForObject(
                "SELECT COUNT(*) FROM compartment WHERE id=? AND vehicle_id=?",
                Integer.class,
                door.compartmentId(),
                e.vehicleId())
            != 1) throw bad("格口不属于该车辆");
      }
    }
    String payload;
    String hash;
    try {
      payload = json.writeValueAsString(e);
      hash =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
    var duplicate =
        db.queryForList(
            "SELECT id,payload_hash,disposition FROM integration_event WHERE provider=? AND"
                + " event_key=?",
            provider,
            e.eventId());
    if (!duplicate.isEmpty()) {
      if (!hash.equals(duplicate.get(0).get("payload_hash")))
        throw new ApiException(409, 40901, "同一事件标识内容不一致");
      return Map.of("id", duplicate.get(0).get("id"), "disposition", "DUPLICATE");
    }
    Instant time = e.reportedAt().truncatedTo(ChronoUnit.MILLIS);
    String id = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO"
            + " integration_event(id,provider,event_key,vehicle_id,dispatch_id,reported_at,payload,payload_hash,disposition)"
            + " VALUES(?,?,?,?,?,?,?,?,'RECEIVED')",
        id,
        provider,
        e.eventId(),
        e.vehicleId(),
        e.dispatchId(),
        Timestamp.from(time),
        payload,
        hash);
    db.update("INSERT IGNORE INTO vehicle_snapshot(vehicle_id) VALUES(?)", e.vehicleId());
    var old = db.queryForMap("SELECT * FROM vehicle_snapshot WHERE vehicle_id=?", e.vehicleId());
    var tasks =
        db.queryForList(
            "SELECT t.* FROM active_vehicle_task a JOIN vehicle_task t ON t.id=a.task_id WHERE"
                + " a.vehicle_id=? FOR UPDATE",
            e.vehicleId());
    var task = tasks.isEmpty() ? null : tasks.get(0);
    // 有当前任务时，缺任务号与旧任务号均不能污染当前快照（包括门）。
    boolean mismatch =
        task != null
            && (!provider.equals(task.get("provider"))
                || !Objects.equals(e.dispatchId(), task.get("dispatch_id")));
    if (task == null && e.dispatchId() != null) {
      var known =
          db.queryForList(
              "SELECT vehicle_id,state FROM vehicle_task WHERE provider=? AND dispatch_id=?",
              provider,
              e.dispatchId());
      mismatch = !known.isEmpty();
    }
    if (mismatch) {
      disposition(id, "TASK_MISMATCH");
      return Map.of("id", id, "disposition", "TASK_MISMATCH");
    }
    boolean telemetry =
        e.online() != null
            || e.speed() != null
            || e.batteryPercent() != null
            || e.longitude() != null
            || e.latitude() != null
            || e.distanceToNextStopMeters() != null
            || e.historicalLegMinutes() != null;
    int telemetryOrder = compare(time, old.get("reported_at")),
        businessOrder = compare(time, old.get("business_reported_at"));
    boolean conflict = false, changed = false;
    if (telemetry && telemetryOrder == 0)
      conflict =
          differs(e.online(), old.get("online"))
              || differs(e.speed(), old.get("speed"))
              || differs(e.batteryPercent(), old.get("battery_percent"))
              || differs(e.longitude(), old.get("longitude"))
              || differs(e.latitude(), old.get("latitude"))
              || differs(e.distanceToNextStopMeters(), old.get("distance_to_next_stop_meters"))
              || differs(e.historicalLegMinutes(), old.get("historical_leg_minutes"));
    if (e.businessStatus() != null && businessOrder == 0)
      conflict |=
          !same(e.businessStatus(), old.get("business_status"))
              || !same(e.dispatchId(), old.get("dispatch_id"))
              || !same(e.currentStopId(), old.get("current_stop_id"))
              || !same(e.nextStopId(), old.get("next_stop_id"));
    if (e.doors() != null)
      for (var door : e.doors()) {
        var prior =
            db.queryForList(
                "SELECT status,reported_at FROM vehicle_door_snapshot WHERE compartment_id=?",
                door.compartmentId());
        if (!prior.isEmpty()
            && compare(time, prior.get(0).get("reported_at")) == 0
            && !same(door.status(), prior.get(0).get("status"))) conflict = true;
      }
    if (conflict) {
      db.update(
          "UPDATE vehicle_snapshot SET reconciliation=true WHERE vehicle_id=?", e.vehicleId());
      if (task != null)
        db.update(
            "UPDATE vehicle_task SET state='RECONCILIATION',updated_at=UTC_TIMESTAMP(3) WHERE id=?",
            task.get("id"));
      disposition(id, "CONFLICT");
      events.append(
          "VEHICLE_RECONCILIATION",
          e.vehicleId(),
          Map.of("vehicleId", e.vehicleId(), "eventId", id));
      return Map.of("id", id, "disposition", "CONFLICT");
    }
    if (telemetry && telemetryOrder > 0) {
      // 新的车辆消息缺少速度/在线值时保留unknown，不使用旧速度制造“当前已停”。
      db.update(
          "UPDATE vehicle_snapshot SET"
              + " online=?,speed=?,battery_percent=?,longitude=?,latitude=?,distance_to_next_stop_meters=?,historical_leg_minutes=?,reported_at=?,received_at=UTC_TIMESTAMP(3)"
              + " WHERE vehicle_id=?",
          e.online(),
          e.speed(),
          e.batteryPercent(),
          e.longitude(),
          e.latitude(),
          e.distanceToNextStopMeters(),
          e.historicalLegMinutes(),
          Timestamp.from(time),
          e.vehicleId());
      changed = true;
    }
    if (e.businessStatus() != null && businessOrder > 0) {
      db.update(
          "UPDATE vehicle_snapshot SET"
              + " business_status=?,dispatch_id=?,current_stop_id=?,next_stop_id=?,business_reported_at=?,received_at=UTC_TIMESTAMP(3)"
              + " WHERE vehicle_id=?",
          e.businessStatus(),
          e.dispatchId(),
          e.currentStopId(),
          e.nextStopId(),
          Timestamp.from(time),
          e.vehicleId());
      changed = true;
      if (task != null && !Boolean.TRUE.equals(old.get("reconciliation"))) advance(task, e);
    }
    if (e.doors() != null)
      for (var door : e.doors()) {
        var prior =
            db.queryForList(
                "SELECT reported_at FROM vehicle_door_snapshot WHERE compartment_id=?",
                door.compartmentId());
        if (prior.isEmpty() || compare(time, prior.get(0).get("reported_at")) > 0) {
          db.update(
              "INSERT INTO"
                  + " vehicle_door_snapshot(compartment_id,vehicle_id,status,reported_at,received_at)"
                  + " VALUES(?,?,?,?,UTC_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE"
                  + " status=VALUES(status),reported_at=VALUES(reported_at),received_at=VALUES(received_at)",
              door.compartmentId(),
              e.vehicleId(),
              door.status(),
              Timestamp.from(time));
          changed = true;
        }
      }
    String result = changed ? "APPLIED" : "STALE";
    disposition(id, result);
    return Map.of("id", id, "disposition", result);
  }

  private void disposition(String id, String state) {
    db.update("UPDATE integration_event SET disposition=? WHERE id=?", state, id);
  }

  private void advance(Map<String, Object> task, VehicleEvent event) {
    if (Set.of("FINISHED", "CANCELLED", "RECONCILIATION").contains(task.get("state"))) return;
    var request =
        db.queryForMap(
            "SELECT goal_stop_id FROM dispatch_request WHERE id=?",
            task.get("dispatch_request_id"));
    String state = (String) task.get("state");
    String next = event.nextStopId();
    if ("ONWAY".equals(event.businessStatus())) state = "RUNNING";
    else if ("ATSTOP".equals(event.businessStatus())) {
      if (!Objects.equals(request.get("goal_stop_id"), event.currentStopId())) {
        state = "RECONCILIATION";
        db.update(
            "UPDATE vehicle_snapshot SET reconciliation=true WHERE vehicle_id=?",
            event.vehicleId());
      } else state = "AT_STOP";
    } else if ("FINISHED".equals(event.businessStatus())) state = "FINISHED";
    else if ("CANCELLED".equals(event.businessStatus())) state = "CANCELLED";
    else if ("ERROR".equals(event.businessStatus())) {
      state = "RECONCILIATION";
      db.update(
          "UPDATE vehicle_snapshot SET reconciliation=true WHERE vehicle_id=?", event.vehicleId());
    }
    db.update(
        "UPDATE vehicle_task SET"
            + " state=?,current_stop_id=?,next_stop_id=?,updated_at=UTC_TIMESTAMP(3) WHERE id=?",
        state,
        event.currentStopId(),
        next,
        task.get("id"));
    var orders =
        db.queryForList(
            "SELECT o.id,o.status FROM delivery_order o JOIN active_order_batch a ON"
                + " a.order_id=o.id WHERE a.batch_id=? ORDER BY o.id FOR UPDATE",
            task.get("batch_id"));
    for (var order : orders) {
      String target = null;
      if ("RUNNING".equals(state) && "READY".equals(order.get("status"))) target = "IN_TRANSIT";
      if ("AT_STOP".equals(state) && Set.of("READY", "IN_TRANSIT").contains(order.get("status")))
        target = "AWAITING_PICKUP";
      if (target != null) {
        db.update(
            "UPDATE delivery_order SET status=?,version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE"
                + " id=?",
            target,
            order.get("id"));
        events.append(
            target.equals("IN_TRANSIT") ? "ORDER_IN_TRANSIT" : "ORDER_ARRIVED",
            (String) order.get("id"),
            Map.of("orderId", order.get("id"), "taskId", task.get("id"), "status", target));
      }
    }
    if (!state.equals(task.get("state")))
      events.append(
          "TASK_STATE", (String) task.get("id"), Map.of("taskId", task.get("id"), "state", state));
    // 任务结束不等于货物已取；仅全单终态才释放活动资源，避免遗留货物失去追踪。
    if (Set.of("FINISHED", "CANCELLED").contains(state)
        && orders.stream()
            .allMatch(
                o -> Set.of("COMPLETED", "CANCELLED", "REJECTED").contains(o.get("status")))) {
      db.update("DELETE FROM active_vehicle_task WHERE task_id=?", task.get("id"));
      db.update("DELETE FROM active_compartment WHERE batch_id=?", task.get("batch_id"));
      db.update(
          "UPDATE batch SET status='CLOSED',version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE"
              + " id=?",
          task.get("batch_id"));
    }
  }
}
