package com.taskhub.domain.dispatch;

import com.taskhub.api.*;
import com.taskhub.domain.audit.AuditService;
import com.taskhub.domain.batch.BatchService;
import com.taskhub.domain.identity.*;
import com.taskhub.domain.identity.IdentityModels.Actor;
import com.taskhub.domain.integration.VehicleGateway;
import com.taskhub.domain.integration.VehicleGateway.*;
import com.taskhub.domain.masterdata.*;
import com.taskhub.domain.notification.BusinessEventService;
import com.taskhub.domain.vehicle.VehicleQueryService;
import com.taskhub.infrastructure.idempotency.IdempotencyService;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

@Service
public class DispatchService {
  private final JdbcTemplate db;
  private final AuthorizationService auth;
  private final BatchService batches;
  private final LoadingStopService stops;
  private final RuleService rules;
  private final VehicleQueryService vehicles;
  private final IdempotencyService idem;
  private final AuditService audit;
  private final BusinessEventService events;
  private final VehicleGateway gateway;
  private final TransactionTemplate tx;
  private final String provider;

  public DispatchService(
      JdbcTemplate db,
      AuthorizationService auth,
      BatchService batches,
      LoadingStopService stops,
      RuleService rules,
      VehicleQueryService vehicles,
      IdempotencyService idem,
      AuditService audit,
      BusinessEventService events,
      VehicleGateway gateway,
      PlatformTransactionManager manager,
      @Value("${taskhub.vehicle.simulator-enabled:false}") boolean simulator) {
    this.db = db;
    this.auth = auth;
    this.batches = batches;
    this.stops = stops;
    this.rules = rules;
    this.vehicles = vehicles;
    this.idem = idem;
    this.audit = audit;
    this.events = events;
    this.gateway = gateway;
    this.provider = simulator ? "SIMULATOR" : "JIUSHI";
    tx = new TransactionTemplate(manager);
    tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  private ApiException invalid(String m) {
    return new ApiException(422, 42204, m);
  }

  private Map<String, Object> row(String id, boolean lock) {
    var rows =
        db.queryForList(
            "SELECT * FROM dispatch_request WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
    if (rows.isEmpty()) throw new ApiException(404, 40400, "派发请求不存在");
    return rows.get(0);
  }

  public Map<String, Object> get(Actor actor, String id) {
    var row = row(id, false);
    var batch = batches.row((String) row.get("batch_id"), false);
    vehicles.requireRead(actor, (String) batch.get("warehouse_id"));
    if ("PENDING".equals(row.get("status"))
        && com.taskhub.infrastructure.DatabaseTime.instant(row.get("created_at"))
            .isBefore(java.time.Instant.now().minusSeconds(60))) {
      recoverPending();
      row = row(id, false);
    }
    return view(row);
  }

  private Map<String, Object> view(Map<String, Object> row) {
    var result = new LinkedHashMap<String, Object>();
    for (String f : List.of("id", "status", "message")) result.put(f, row.get(f));
    result.put("batchId", row.get("batch_id"));
    result.put("vehicleId", row.get("vehicle_id"));
    result.put("taskId", row.get("task_id"));
    result.put("dispatchId", row.get("dispatch_id"));
    result.put("createdAt", VehicleQueryService.time(row.get("created_at")));
    return result;
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> dispatch(
      Actor actor, String batchId, String key, DispatchModels.Input input) {
    var batch = batches.row(batchId, false);
    auth.require(actor, "DISPATCH", (String) batch.get("warehouse_id"));
    var created = new AtomicBoolean(false);
    Map<String, Object> prepared =
        idem.execute(
            actor,
            "POST /batches/" + batchId + "/dispatch",
            key,
            input,
            Map.class,
            () -> {
              var current = batches.row(batchId, true);
              auth.require(actor, "DISPATCH", (String) current.get("warehouse_id"));
              if (input.expectedVersion() == null)
                throw new ApiException(400, 40000, "expectedVersion必填");
              if (((Number) current.get("version")).intValue() != input.expectedVersion())
                throw new ApiException(409, 40902, "批次版本已更新");
              if (!"READY".equals(current.get("status"))) throw BatchService.conflict("批次未就绪或已锁定");
              if (!"CONFIRMED".equals(current.get("load_status"))
                  || current.get("confirmed_version") == null
                  || ((Number) current.get("confirmed_version")).intValue()
                      != input.expectedVersion()) throw invalid("请重新确认完整装货");
              var binding = stops.binding((String) current.get("warehouse_id"));
              if (binding == null) throw invalid("仓库尚未绑定装货点");
              String vehicle = (String) current.get("vehicle_id");
              if (vehicle == null) throw invalid("未分配车辆");
              var members = batches.members(batchId);
              if (members.isEmpty()) throw invalid("批次没有订单");
              for (String order : members) {
                var o =
                    db.queryForMap(
                        "SELECT status FROM delivery_order WHERE id=? FOR UPDATE", order);
                if (!"READY".equals(o.get("status"))) throw invalid("批次成员未全部装货就绪");
                if (db.queryForObject(
                        "SELECT COUNT(*) FROM order_cancellation WHERE order_id=? AND"
                            + " status='PENDING'",
                        Integer.class,
                        order)
                    > 0) throw BatchService.conflict("存在待审核取消申请");
              }
              var vehicleRow =
                  db.queryForMap("SELECT * FROM vehicle WHERE id=? FOR UPDATE", vehicle);
              if (!Boolean.TRUE.equals(vehicleRow.get("enabled"))
                  || !current.get("warehouse_id").equals(vehicleRow.get("warehouse_id")))
                throw invalid("车辆不可用或所属仓库改变");
              if (!db.queryForList(
                      "SELECT vehicle_id FROM active_vehicle_task WHERE vehicle_id=?", vehicle)
                  .isEmpty()) throw BatchService.conflict("车辆已有派发请求或活动任务");
              for (String stop : List.of(binding.stopId(), (String) current.get("stop_id")))
                if (db.queryForObject(
                        "SELECT COUNT(*) FROM vehicle_stop vs JOIN stop s ON s.id=vs.stop_id WHERE"
                            + " vs.vehicle_id=? AND vs.stop_id=? AND s.enabled=true",
                        Integer.class,
                        vehicle,
                        stop)
                    != 1) throw invalid("车辆装货点或目的地不可达");
              var snapshotRows =
                  db.queryForList("SELECT * FROM vehicle_snapshot WHERE vehicle_id=?", vehicle);
              if (snapshotRows.isEmpty()) throw invalid("车辆状态未知");
              var snapshot = snapshotRows.get(0);
              if (snapshot.get("dispatch_id") != null) throw invalid("车辆存在尚未核对结束的外部任务");
              var config = rules.read((String) current.get("warehouse_id"));
              if (!Boolean.TRUE.equals(snapshot.get("online"))
                  || !VehicleQueryService.fresh(
                      snapshot.get("reported_at"), config.telemetryMaxAgeSeconds())
                  || !VehicleQueryService.fresh(
                      snapshot.get("business_reported_at"), config.telemetryMaxAgeSeconds())
                  || Boolean.TRUE.equals(snapshot.get("reconciliation")))
                throw invalid("车辆离线、状态过期或待核对");
              if (!(snapshot.get("speed") instanceof Number speed)
                  || speed.doubleValue() != 0
                  || !binding.stopId().equals(snapshot.get("current_stop_id"))
                  || !Set.of("IDLE", "ATSTOP")
                      .contains(String.valueOf(snapshot.get("business_status"))))
                throw invalid("车辆必须停在已绑定装货点且速度明确为零");
              var doors =
                  db.queryForList(
                      "SELECT c.id,d.status,d.reported_at FROM compartment c LEFT JOIN"
                          + " vehicle_door_snapshot d ON d.compartment_id=c.id WHERE c.vehicle_id=?"
                          + " AND c.enabled=true ORDER BY c.id",
                      vehicle);
              if (doors.isEmpty()) throw invalid("没有可核对的格口");
              for (var door : doors)
                if (!"CLOSED".equals(door.get("status"))
                    || !VehicleQueryService.fresh(
                        door.get("reported_at"), config.doorMaxAgeSeconds()))
                  throw invalid("所有格口门必须关闭且状态新鲜");
              for (String order : members)
                if (db.queryForObject(
                        "SELECT COUNT(*) FROM batch_assignment a JOIN active_compartment ac ON"
                            + " ac.compartment_id=a.compartment_id AND ac.batch_id=a.batch_id JOIN"
                            + " compartment c ON c.id=a.compartment_id WHERE a.batch_id=? AND"
                            + " a.order_id=? AND c.vehicle_id=? AND c.enabled=true",
                        Integer.class,
                        batchId,
                        order,
                        vehicle)
                    < 1) throw invalid("订单格口占用不完整");
              String id = UUID.randomUUID().toString();
              db.update(
                  "INSERT INTO"
                      + " dispatch_request(id,batch_id,vehicle_id,from_stop_id,goal_stop_id,status,message)"
                      + " VALUES(?,?,?,?,?,'PENDING','派发请求已保存，等待提供者结果')",
                  id,
                  batchId,
                  vehicle,
                  binding.stopId(),
                  current.get("stop_id"));
              db.update(
                  "INSERT INTO active_vehicle_task(vehicle_id,dispatch_request_id) VALUES(?,?)",
                  vehicle,
                  id);
              db.update(
                  "UPDATE batch SET"
                      + " status='LOCKED',dispatch_request_id=?,version=version+1,updated_at=UTC_TIMESTAMP(3)"
                      + " WHERE id=?",
                  id,
                  batchId);
              var result = view(row(id, false));
              audit.record(actor, "DISPATCH", id, "DISPATCH_REQUEST", null, result, null);
              events.append("DISPATCH_PENDING", id, Map.of("requestId", id, "batchId", batchId));
              created.set(true);
              return result;
            });
    String request = (String) prepared.get("id");
    if (created.get()) {
      var r = row(request, false);
      GatewayResult result;
      try {
        if (TransactionSynchronizationManager.isActualTransactionActive())
          throw new IllegalStateException("车辆调用不得位于业务事务");
        result =
            gateway.dispatch(
                new DispatchCommand(
                    request,
                    (String) r.get("vehicle_id"),
                    (String) r.get("from_stop_id"),
                    List.of((String) r.get("goal_stop_id"))));
      } catch (RuntimeException e) {
        result = new GatewayResult(GatewayStatus.UNKNOWN, null, "提供者结果未知，请核对；不会自动重发");
      }
      finish(actor, request, result);
    }
    return get(actor, request);
  }

  private void finish(Actor actor, String id, GatewayResult result) {
    tx.executeWithoutResult(
        status -> {
          var initial = row(id, false);
          var batch = batches.row((String) initial.get("batch_id"), true);
          db.queryForMap("SELECT id FROM vehicle WHERE id=? FOR UPDATE", initial.get("vehicle_id"));
          var r = row(id, true);
          if (!Set.of("PENDING", "UNKNOWN").contains(r.get("status"))) return;
          var safe =
              result == null ? new GatewayResult(GatewayStatus.UNKNOWN, null, "结果未知") : result;
          if (safe.status() == GatewayStatus.ACCEPTED
              && (safe.dispatchId() == null
                  || safe.dispatchId().isBlank()
                  || safe.dispatchId().length() > 64))
            safe = new GatewayResult(GatewayStatus.UNKNOWN, null, "受理响应缺少有效任务标识，请核对");
          if ("UNKNOWN".equals(r.get("status")) && safe.status() == GatewayStatus.UNKNOWN) return;
          String task = null;
          String state = safe.status().name();
          if (safe.status() == GatewayStatus.ACCEPTED) {
            if (!db.queryForList(
                    "SELECT id FROM vehicle_task WHERE provider=? AND dispatch_id=?",
                    provider,
                    safe.dispatchId())
                .isEmpty()) {
              state = "UNKNOWN";
              safe = new GatewayResult(GatewayStatus.UNKNOWN, null, "外部任务标识已关联其他请求，请核对");
            } else {
              task = UUID.randomUUID().toString();
              db.update(
                  "INSERT INTO"
                      + " vehicle_task(id,vehicle_id,batch_id,dispatch_request_id,provider,dispatch_id,state,current_stop_id,next_stop_id)"
                      + " VALUES(?,?,?,?,?,?,'PLANNING',?,?)",
                  task,
                  r.get("vehicle_id"),
                  r.get("batch_id"),
                  id,
                  provider,
                  safe.dispatchId(),
                  r.get("from_stop_id"),
                  r.get("goal_stop_id"));
              db.update(
                  "UPDATE active_vehicle_task SET task_id=? WHERE vehicle_id=? AND"
                      + " dispatch_request_id=?",
                  task,
                  r.get("vehicle_id"),
                  id);
              db.update("UPDATE batch SET task_id=? WHERE id=?", task, r.get("batch_id"));
            }
          }
          if (safe.status() == GatewayStatus.FAILED) {
            db.update(
                "DELETE FROM active_vehicle_task WHERE vehicle_id=? AND dispatch_request_id=?",
                r.get("vehicle_id"),
                id);
            db.update(
                "UPDATE batch SET"
                    + " status='READY',dispatch_request_id=NULL,confirmed_version=version+1,version=version+1,updated_at=UTC_TIMESTAMP(3)"
                    + " WHERE id=?",
                r.get("batch_id"));
          }
          String message = safe.message() == null ? "派发请求待核对" : safe.message();
          if (message.length() > 500) message = message.substring(0, 500);
          db.update(
              "UPDATE dispatch_request SET"
                  + " status=?,task_id=?,dispatch_id=?,message=?,updated_at=UTC_TIMESTAMP(3) WHERE"
                  + " id=?",
              state,
              task,
              safe.dispatchId(),
              message,
              id);
          var after = view(row(id, false));
          audit.record(actor, "DISPATCH", id, "DISPATCH_RESULT", view(r), after, null);
          events.append(
              "DISPATCH_" + state,
              id,
              Map.of("requestId", id, "batchId", r.get("batch_id"), "status", state));
        });
  }

  public Map<String, Object> reconcile(Actor actor, String id) {
    var r = row(id, false);
    var batch = batches.row((String) r.get("batch_id"), false);
    auth.require(actor, "DISPATCH", (String) batch.get("warehouse_id"));
    if (!Set.of("PENDING", "UNKNOWN").contains(r.get("status"))) return get(actor, id);
    // 精确请求标识核对；只查询，从不重发。不凭“车有任务”推断本次成功。
    GatewayResult result;
    try {
      result =
          gateway.queryDispatch(
              new DispatchCommand(
                  id,
                  (String) r.get("vehicle_id"),
                  (String) r.get("from_stop_id"),
                  List.of((String) r.get("goal_stop_id"))),
              com.taskhub.infrastructure.DatabaseTime.instant(r.get("created_at")));
    } catch (RuntimeException e) {
      result = new GatewayResult(GatewayStatus.UNKNOWN, null, "提供者核对暂不可用");
    }
    finish(actor, id, result);
    return get(actor, id);
  }

  /** 启动/查询恢复只标记待核对，绝不调用gateway重发。 */
  public int recoverPending() {
    return tx.execute(
        status -> {
          var pending =
              db.queryForList(
                  "SELECT * FROM dispatch_request WHERE status='PENDING' AND"
                      + " created_at<DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 60 SECOND) ORDER BY id FOR"
                      + " UPDATE");
          for (var request : pending) {
            db.update(
                "UPDATE dispatch_request SET"
                    + " status='UNKNOWN',message='执行中断或响应逾时，需要核对，不自动重发',updated_at=UTC_TIMESTAMP(3)"
                    + " WHERE id=?",
                request.get("id"));
            events.append(
                "DISPATCH_UNKNOWN",
                (String) request.get("id"),
                Map.of(
                    "requestId",
                    request.get("id"),
                    "batchId",
                    request.get("batch_id"),
                    "status",
                    "UNKNOWN"));
          }
          return pending.size();
        });
  }
}
