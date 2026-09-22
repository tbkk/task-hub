package com.taskhub.domain.vehicle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.api.ApiException;
import com.taskhub.domain.identity.AuthorizationService;
import com.taskhub.domain.identity.IdentityModels.Actor;
import com.taskhub.domain.integration.VehicleGateway;
import com.taskhub.domain.ticket.TicketBlocker;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 车辆控制请求：事务内锁定并写入请求，事务外调用提供者，再用独立事务落结果。 */
@Service
public class VehicleControlService {
  private final JdbcTemplate db;
  private final AuthorizationService auth;
  private final VehicleGateway gateway;
  private final TicketBlocker tickets;
  private final ObjectMapper json;
  private final TransactionTemplate tx;

  public VehicleControlService(JdbcTemplate db, AuthorizationService auth, VehicleGateway gateway,
      TicketBlocker tickets, ObjectMapper json, PlatformTransactionManager manager) {
    this.db = db; this.auth = auth; this.gateway = gateway; this.tickets = tickets; this.json = json;
    this.tx = new TransactionTemplate(manager);
  }

  public Map<String, Object> go(Actor actor, String taskId, String key, ControlModels.Input input) {
    return execute(actor, taskId, key, "GO", input);
  }

  public Map<String, Object> cancel(Actor actor, String taskId, String key, ControlModels.Input input) {
    return execute(actor, taskId, key, "CANCEL_TASK", input);
  }

  private record Prepared(String id, String type, String vehicleId, String dispatchId,
      boolean created, Map<String, Object> existing) {}

  private Map<String, Object> execute(Actor actor, String taskId, String key, String type,
      ControlModels.Input input) {
    auth.requireWorkspace(actor, "dispatch");
    if (taskId == null || taskId.isBlank()) throw new ApiException(400, 40000, "任务标识必填");
    if (key == null || key.isBlank() || key.length() > 128)
      throw new ApiException(400, 40000, "Idempotency-Key必填");
    if (input == null || input.expectedVersion() == null)
      throw new ApiException(400, 40000, "expectedVersion必填");
    Prepared prepared = tx.execute(s -> prepare(actor, taskId, key, type, input.expectedVersion()));
    if (prepared == null) throw new IllegalStateException("控制请求准备失败");
    if (!prepared.created()) return view(prepared.existing());

    VehicleGateway.GatewayResult result;
    try {
      var command = new VehicleGateway.TaskCommand(prepared.id(), prepared.vehicleId(), prepared.dispatchId());
      result = "GO".equals(type) ? gateway.go(command) : gateway.cancel(command);
    } catch (RuntimeException e) {
      result = new VehicleGateway.GatewayResult(VehicleGateway.GatewayStatus.UNKNOWN, null,
          "提供者响应未知，请核对；不会自动重发");
    }
    if (result == null)
      result = new VehicleGateway.GatewayResult(VehicleGateway.GatewayStatus.UNKNOWN, null, "提供者响应未知，请核对");
    finish(prepared.id(), result);
    return view(db.queryForMap("SELECT * FROM control_request WHERE id=?", prepared.id()));
  }

  /** 固定锁顺序：batch → vehicle → task → orders。 */
  private Prepared prepare(Actor actor, String taskId, String key, String type, int expectedVersion) {
    var refs = db.queryForList("SELECT batch_id,vehicle_id FROM vehicle_task WHERE id=?", taskId);
    if (refs.isEmpty()) throw new ApiException(404, 40400, "车辆任务不存在");
    String batchId = String.valueOf(refs.get(0).get("batch_id"));
    String vehicleId = String.valueOf(refs.get(0).get("vehicle_id"));
    Map<String, Object> batch = one("SELECT * FROM batch WHERE id=? FOR UPDATE", batchId, "批次不存在");
    one("SELECT * FROM vehicle WHERE id=? FOR UPDATE", vehicleId, "车辆不存在");
    Map<String, Object> task = one("SELECT t.*,r.dispatch_id request_dispatch_id FROM vehicle_task t "
        + "JOIN dispatch_request r ON r.id=t.dispatch_request_id WHERE t.id=? FOR UPDATE", taskId, "车辆任务不存在");
    if (!batchId.equals(task.get("batch_id")) || !vehicleId.equals(task.get("vehicle_id")))
      throw new ApiException(409, 40903, "任务关联已变化，请重新读取");
    boolean active = !db.queryForList("SELECT task_id FROM active_vehicle_task WHERE vehicle_id=? AND task_id=? FOR UPDATE",
        vehicleId, taskId).isEmpty();
    List<Map<String, Object>> orders = db.queryForList("SELECT o.id,o.status FROM batch_order bo "
        + "JOIN delivery_order o ON o.id=bo.order_id WHERE bo.batch_id=? AND bo.removed_at IS NULL "
        + "ORDER BY o.id FOR UPDATE", batchId);
    auth.require(actor, "VEHICLE_CONTROL", String.valueOf(batch.get("warehouse_id")));
    if (((Number) batch.get("version")).intValue() != expectedVersion)
      throw new ApiException(409, 40902, "批次版本已更新，请重新读取");

    String hash = hash(type + ":" + taskId + ":" + expectedVersion);
    var old = db.queryForList("SELECT * FROM control_request WHERE actor_id=? AND request_key=? FOR UPDATE",
        actor.employeeId(), key);
    if (!old.isEmpty()) {
      Map<String, Object> row = old.get(0);
      if (!type.equals(row.get("type")) || !taskId.equals(row.get("task_id"))
          || (row.get("request_hash") != null && !hash.equals(row.get("request_hash"))))
        throw new ApiException(409, 40901, "同一请求标识的内容不一致");
      return new Prepared(String.valueOf(row.get("id")), type, vehicleId,
          String.valueOf(task.get("dispatch_id")), false, row);
    }

    var snapshotRows = db.queryForList("SELECT * FROM vehicle_snapshot WHERE vehicle_id=?", vehicleId);
    Map<String, Object> snapshot = snapshotRows.isEmpty() ? Map.of() : snapshotRows.get(0);
    var doors = db.queryForList("SELECT c.id,d.status,d.reported_at FROM compartment c LEFT JOIN vehicle_door_snapshot d "
        + "ON d.compartment_id=c.id WHERE c.vehicle_id=? AND c.enabled=true ORDER BY c.id", vehicleId);
    var doorStatuses = doors.stream().map(d -> d.get("status") == null ? "UNKNOWN" : String.valueOf(d.get("status"))).toList();
    var doorFresh = doors.stream().map(d -> VehicleQueryService.fresh(d.get("reported_at"), 30)).toList();
    var orderStatuses = orders.stream().map(o -> String.valueOf(o.get("status"))).toList();
    boolean unknown = !db.queryForList("SELECT id FROM control_request WHERE task_id=? AND status='UNKNOWN' LIMIT 1", taskId).isEmpty();
    String taskState = active ? String.valueOf(task.get("state")) : "INACTIVE";
    var context = new ControlPolicy.Context(taskState,
        snapshot.get("business_status") == null ? null : String.valueOf(snapshot.get("business_status")),
        snapshot.get("current_stop_id") == null ? null : String.valueOf(snapshot.get("current_stop_id")),
        snapshot.get("next_stop_id") == null ? task.get("next_stop_id") == null ? null : String.valueOf(task.get("next_stop_id")) : String.valueOf(snapshot.get("next_stop_id")),
        batch.get("stop_id") == null ? null : String.valueOf(batch.get("stop_id")),
        (Boolean) snapshot.get("online"), VehicleQueryService.fresh(snapshot.get("reported_at"), 30),
        snapshot.get("speed") instanceof Number n ? new java.math.BigDecimal(n.toString()) : null,
        orderStatuses, doorStatuses, doorFresh, tickets.hasActiveForTask(taskId), unknown);
    var blockers = ControlPolicy.blockers(context, "GO".equals(type));
    if (!blockers.isEmpty())
      throw new ApiException(422, 42204, json.valueToTree(Map.of("blockers", blockers)).toString());

    String id = UUID.randomUUID().toString();
    try {
      db.update("INSERT INTO control_request(id,request_key,actor_id,request_hash,type,vehicle_id,task_id,compartment_ids,status,message) "
          + "VALUES(?,?,?,?,?,?,?,JSON_ARRAY(),'PENDING','控制请求已保存，等待提供者结果')",
          id, key, actor.employeeId(), hash, type, vehicleId, taskId);
    } catch (DuplicateKeyException e) {
      var duplicate = db.queryForList("SELECT * FROM control_request WHERE actor_id=? AND request_key=? FOR UPDATE",
          actor.employeeId(), key);
      if (!duplicate.isEmpty()) return new Prepared(String.valueOf(duplicate.get(0).get("id")), type, vehicleId,
          String.valueOf(task.get("dispatch_id")), false, duplicate.get(0));
      throw e;
    }
    String dispatchId = task.get("dispatch_id") == null ? String.valueOf(task.get("request_dispatch_id")) : String.valueOf(task.get("dispatch_id"));
    return new Prepared(id, type, vehicleId, dispatchId, true, null);
  }

  private void finish(String id, VehicleGateway.GatewayResult result) {
    tx.executeWithoutResult(s -> {
      var rows = db.queryForList("SELECT status FROM control_request WHERE id=? FOR UPDATE", id);
      if (rows.isEmpty() || !"PENDING".equals(rows.get(0).get("status"))) return;
      String message = result.message() == null ? "控制请求结果待核对" : result.message();
      if (message.length() > 500) message = message.substring(0, 500);
      db.update("UPDATE control_request SET status=?,message=?,updated_at=UTC_TIMESTAMP(3) WHERE id=? AND status='PENDING'",
          result.status().name(), message, id);
    });
  }

  private Map<String, Object> one(String sql, Object arg, String message) {
    var rows = db.queryForList(sql, arg);
    if (rows.isEmpty()) throw new ApiException(404, 40400, message);
    return rows.get(0);
  }

  private Map<String, Object> view(Map<String, Object> row) {
    var out = new LinkedHashMap<String, Object>();
    out.put("id", row.get("id")); out.put("type", row.get("type")); out.put("status", row.get("status"));
    out.put("vehicleId", row.get("vehicle_id")); out.put("taskId", row.get("task_id")); out.put("orderId", row.get("order_id"));
    try { out.put("compartmentIds", json.readValue(String.valueOf(row.get("compartment_ids")), List.class)); }
    catch (Exception e) { out.put("compartmentIds", List.of()); }
    out.put("message", row.get("message"));
    return out;
  }

  private String hash(String value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    catch (Exception e) { throw new IllegalStateException(e); }
  }
}
