package com.taskhub.domain.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.api.ApiException;
import com.taskhub.domain.identity.AuthorizationService;
import com.taskhub.domain.identity.IdentityModels.Actor;
import com.taskhub.domain.integration.VehicleGateway;
import com.taskhub.domain.vehicle.ControlModels;
import com.taskhub.domain.vehicle.VehicleQueryService;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;
import com.taskhub.infrastructure.idempotency.IdempotencyService;

@Service
public class PickupService {
  private final JdbcTemplate db;
  private final AuthorizationService auth;
  private final VehicleGateway gateway;
  private final ObjectMapper json;
  private final IdempotencyService idem;
  private final TransactionTemplate tx;
  private final VehicleQueryService vehicles;

  public PickupService(JdbcTemplate db, AuthorizationService auth, VehicleGateway gateway, ObjectMapper json,
                       IdempotencyService idem, PlatformTransactionManager manager, VehicleQueryService vehicles) {
    this.db = db; this.auth = auth; this.gateway = gateway; this.json = json; this.idem = idem;
    this.tx = new TransactionTemplate(manager);
    this.vehicles = vehicles;
  }

  public Map<String,Object> scan(Actor actor, ControlModels.Scan input) {
    auth.requireWorkspace(actor, "worker");
    String prefix = "taskhub:vehicle:";
    if (input == null || input.qrText() == null || !input.qrText().startsWith(prefix)) throw bad("二维码无效");
    String vehicle = input.qrText().substring(prefix.length());
    if (vehicle.isBlank() || vehicle.length() > 36) throw bad("二维码无效");
    var v = db.queryForList("SELECT * FROM vehicle WHERE id=? AND enabled=true", vehicle);
    if (v.isEmpty()) throw new ApiException(404,40400,"车辆不存在");
    var task = db.queryForList("SELECT t.*,b.stop_id FROM vehicle_task t JOIN batch b ON b.id=t.batch_id JOIN active_vehicle_task a ON a.task_id=t.id AND a.vehicle_id=t.vehicle_id WHERE t.vehicle_id=?", vehicle);
    if (task.isEmpty()) throw bad("车辆没有活动任务");
    var t = task.get(0);
    var orders = db.queryForList("SELECT o.id,o.number,o.receiver_name,o.status,o.version,ba.compartment_id FROM delivery_order o JOIN active_order_batch ab ON ab.order_id=o.id JOIN batch_assignment ba ON ba.order_id=o.id AND ba.batch_id=ab.batch_id WHERE ab.batch_id=? AND COALESCE(o.receiver_id,(SELECT p.employee_id FROM verified_phone p WHERE p.phone=o.receiver_phone))=? AND o.status='AWAITING_PICKUP'", t.get("batch_id"), actor.employeeId());
    Map<String,Object> out = new LinkedHashMap<>();
    Map<String,Object> vehicleView = new LinkedHashMap<>(vehicles.snapshot(vehicle));
    var blockers = new ArrayList<Map<String,String>>();
    if (!"AT_STOP".equals(t.get("state"))) blockers.add(Map.of("code","NOT_AT_STOP","message","车辆尚未到达停靠点"));
    if (!"ATSTOP".equals(vehicleView.get("businessStatus"))) blockers.add(Map.of("code","NOT_ATSTOP","message","车辆业务状态不是等待取货"));
    if (!Objects.equals(vehicleView.get("currentStopId"), t.get("stop_id"))) blockers.add(Map.of("code","WRONG_STOP","message","车辆当前停靠点与任务不一致"));
    if (!Boolean.TRUE.equals(vehicleView.get("fresh"))) blockers.add(Map.of("code","VEHICLE_STALE","message","车辆状态已过期"));
    out.put("vehicle", vehicleView); out.put("taskId", t.get("id")); out.put("businessStatus", "AT_STOP".equals(t.get("state")) ? "ATSTOP" : t.get("state")); out.put("orders", orders); out.put("canContinue", !orders.isEmpty() && blockers.isEmpty()); out.put("blockers", blockers);
    return out;
  }

  private record OpenPreparation(String id, boolean created) {}

  public Map<String,Object> open(Actor actor, String orderId, ControlModels.Input input, String key) {
    auth.requireWorkspace(actor, "worker");
    if (key == null || key.isBlank() || key.length() > 128) throw bad("Idempotency-Key必填");
    if (input == null || input.expectedVersion() == null) throw new ApiException(400,40000,"expectedVersion必填");
    OpenPreparation prepared = tx.execute(status -> prepareOpen(actor, orderId, input.expectedVersion(), key));
    if (!prepared.created()) return view(db.queryForMap("SELECT * FROM control_request WHERE id=?", prepared.id()));
    Map<String,Object> row = db.queryForMap("SELECT * FROM control_request WHERE id=?", prepared.id());
    VehicleGateway.GatewayResult result;
    try {
      result = gateway.open(new VehicleGateway.OpenCommand(prepared.id(), (String) row.get("vehicle_id"),
          dispatchId(prepared.id()), hardwareNos(prepared.id())));
    } catch (RuntimeException e) {
      result = new VehicleGateway.GatewayResult(VehicleGateway.GatewayStatus.UNKNOWN, null, "提供者结果未知，请核对；不会自动重发");
    }
    final VehicleGateway.GatewayResult finalResult = result;
    tx.executeWithoutResult(status -> db.update("UPDATE control_request SET status=?,message=?,updated_at=UTC_TIMESTAMP(3) WHERE id=? AND status='PENDING'", finalResult.status().name(), finalResult.message(), prepared.id()));
    return view(db.queryForMap("SELECT * FROM control_request WHERE id=?", prepared.id()));
  }

  public Map<String,Object> confirm(Actor actor,String orderId,ControlModels.Input input,String key){
    auth.requireWorkspace(actor,"worker");
    if(key==null||key.isBlank()||key.length()>128) throw bad("Idempotency-Key必填");
    if (input == null || input.expectedVersion() == null) throw new ApiException(400,40000,"expectedVersion必填");
    return idem.execute(actor, "POST /orders/" + orderId + "/pickup/confirm", key, input, Map.class,
        () -> confirmInTransaction(actor, orderId, input.expectedVersion()));
  }

  private Map<String,Object> confirmInTransaction(Actor actor, String orderId, int expectedVersion) {
    var rows=db.queryForList("SELECT o.*,vs.business_status,vs.current_stop_id,b.stop_id,b.vehicle_id,t.id task_id,t.dispatch_id,t.state,vs.reported_at FROM delivery_order o JOIN active_order_batch ab ON ab.order_id=o.id JOIN batch b ON b.id=ab.batch_id JOIN active_vehicle_task av ON av.vehicle_id=b.vehicle_id JOIN vehicle_task t ON t.id=av.task_id AND t.batch_id=ab.batch_id LEFT JOIN vehicle_snapshot vs ON vs.vehicle_id=b.vehicle_id WHERE o.id=? AND COALESCE(o.receiver_id,(SELECT p.employee_id FROM verified_phone p WHERE p.phone=o.receiver_phone))=? FOR UPDATE",orderId,actor.employeeId());
    if(rows.isEmpty()) throw new ApiException(404,40400,"订单不存在或不在权限范围");
    var o=rows.get(0);
    if("COMPLETED".equals(o.get("status"))) return o;
    if(((Number)o.get("version")).intValue()!=expectedVersion) throw new ApiException(409,40902,"订单版本已更新");
    if (!"AWAITING_PICKUP".equals(o.get("status")) || !"ATSTOP".equals(o.get("business_status")) || !Objects.equals(o.get("current_stop_id"), o.get("stop_id")) || !"AT_STOP".equals(o.get("state")) || !VehicleQueryService.fresh(o.get("reported_at"), 60)) throw new ApiException(422,42204,"车辆未在新鲜的目标停靠点等待取货");
    if (db.queryForObject("SELECT COUNT(*) FROM control_request WHERE order_id=? AND type='PICKUP_OPEN' AND status='UNKNOWN'", Integer.class, orderId) > 0) throw new ApiException(422,42204,"开门请求结果未知，请先核对");
    db.update("UPDATE delivery_order SET status='COMPLETED',pickup_employee_id=?,picked_up_at=UTC_TIMESTAMP(3),version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE id=? AND version=?",actor.employeeId(),orderId,expectedVersion);
    return db.queryForMap("SELECT * FROM delivery_order WHERE id=?",orderId);
  }

  private OpenPreparation prepareOpen(Actor actor, String orderId, int expectedVersion, String key) {
    var existing=db.queryForList("SELECT * FROM control_request WHERE actor_id=? AND request_key=? FOR UPDATE",actor.employeeId(),key);
    if(!existing.isEmpty()) {
      var old=existing.get(0);
      if (!"PICKUP_OPEN".equals(old.get("type")) || !orderId.equals(old.get("order_id"))) throw new ApiException(409,40901,"同一请求标识的内容不一致");
      if (old.get("request_hash") != null && !hash(orderId+":"+expectedVersion).equals(old.get("request_hash"))) throw new ApiException(409,40901,"同一请求标识的内容不一致");
      if (db.queryForList("SELECT o.id FROM delivery_order o JOIN active_order_batch ab ON ab.order_id=o.id JOIN batch b ON b.id=ab.batch_id JOIN active_vehicle_task av ON av.vehicle_id=b.vehicle_id JOIN vehicle_task t ON t.id=av.task_id AND t.batch_id=ab.batch_id WHERE o.id=? AND COALESCE(o.receiver_id,(SELECT p.employee_id FROM verified_phone p WHERE p.phone=o.receiver_phone))=?", orderId, actor.employeeId()).isEmpty()) throw new ApiException(404,40400,"订单不存在或不在权限范围");
      return new OpenPreparation((String)old.get("id"), false);
    }
    var legacy=db.queryForList("SELECT * FROM control_request WHERE actor_id IS NULL AND request_key=? FOR UPDATE",key);
    if(!legacy.isEmpty()) {
      var old=legacy.get(0);
      if (!"PICKUP_OPEN".equals(old.get("type")) || !orderId.equals(old.get("order_id"))) throw new ApiException(409,40901,"同一请求标识的内容不一致");
      if (db.queryForList("SELECT o.id FROM delivery_order o JOIN active_order_batch ab ON ab.order_id=o.id JOIN batch b ON b.id=ab.batch_id JOIN active_vehicle_task av ON av.vehicle_id=b.vehicle_id JOIN vehicle_task t ON t.id=av.task_id AND t.batch_id=ab.batch_id WHERE o.id=? AND COALESCE(o.receiver_id,(SELECT p.employee_id FROM verified_phone p WHERE p.phone=o.receiver_phone))=?", orderId, actor.employeeId()).isEmpty()) throw new ApiException(404,40400,"订单不存在或不在权限范围");
      return new OpenPreparation((String)old.get("id"), false);
    }
    var o = db.queryForList("SELECT o.*,ab.batch_id,b.vehicle_id,t.id task_id,t.dispatch_id,t.state,vs.business_status,vs.current_stop_id,vs.reported_at FROM delivery_order o JOIN active_order_batch ab ON ab.order_id=o.id JOIN batch b ON b.id=ab.batch_id JOIN active_vehicle_task av ON av.vehicle_id=b.vehicle_id JOIN vehicle_task t ON t.id=av.task_id AND t.batch_id=ab.batch_id LEFT JOIN vehicle_snapshot vs ON vs.vehicle_id=b.vehicle_id WHERE o.id=? AND COALESCE(o.receiver_id,(SELECT p.employee_id FROM verified_phone p WHERE p.phone=o.receiver_phone))=? FOR UPDATE",orderId,actor.employeeId());
    if (o.isEmpty()) throw new ApiException(404,40400,"订单不存在或不在权限范围");
    var row=o.get(0);
    var active=db.queryForList("SELECT id FROM control_request WHERE vehicle_id=? AND type='PICKUP_OPEN' AND status IN ('PENDING','UNKNOWN') FOR UPDATE",row.get("vehicle_id"));
    if (!active.isEmpty()) throw new ApiException(409,40903,"车辆已有待核对开门请求");
    if (((Number)row.get("version")).intValue()!=expectedVersion) throw new ApiException(409,40902,"订单版本已更新");
    if (!"AWAITING_PICKUP".equals(row.get("status")) || !"ATSTOP".equals(row.get("business_status")) || !Objects.equals(row.get("current_stop_id"), row.get("stop_id")) || !"AT_STOP".equals(row.get("state")) || !VehicleQueryService.fresh(row.get("reported_at"),60)) throw new ApiException(422,42204,"车辆未在新鲜的目标停靠点等待取货");
    var comps=db.queryForList("SELECT c.id,c.hardware_no,d.status,d.reported_at FROM batch_assignment a JOIN compartment c ON c.id=a.compartment_id LEFT JOIN vehicle_door_snapshot d ON d.compartment_id=c.id WHERE a.batch_id=? AND a.order_id=? AND c.enabled=true FOR UPDATE",row.get("batch_id"),orderId);
    if(comps.isEmpty()) throw new ApiException(422,42204,"订单未分配格口");
    for (var c : comps) if (!"CLOSED".equals(c.get("status")) || !VehicleQueryService.fresh(c.get("reported_at"), 30)) throw new ApiException(422,42204,"格口门状态未知或已过期");
    List<String> ids=comps.stream().map(x->(String)x.get("id")).toList();
    String id=UUID.randomUUID().toString();
    db.update("INSERT INTO control_request(id,request_key,actor_id,request_hash,type,vehicle_id,task_id,order_id,compartment_ids,status,message) VALUES(?,?,?,?,?,?,?,?,?,'PENDING','开门请求已保存')",id,key,actor.employeeId(),hash(orderId+":"+expectedVersion),"PICKUP_OPEN",row.get("vehicle_id"),row.get("task_id"),orderId,write(ids));
    return new OpenPreparation(id, true);
  }

  private String dispatchId(String requestId) { return db.queryForObject("SELECT dispatch_id FROM vehicle_task t JOIN control_request c ON c.task_id=t.id WHERE c.id=?",String.class,requestId); }
  private List<String> hardwareNos(String requestId) {
    String raw = db.queryForObject("SELECT compartment_ids FROM control_request WHERE id=?", String.class, requestId);
    try {
      List<String> ids = json.readValue(raw, List.class);
      if (ids.isEmpty()) throw bad("格口为空");
      return db.queryForList("SELECT hardware_no FROM compartment WHERE id IN ("+String.join(",", Collections.nCopies(ids.size(), "?"))+") ORDER BY id", String.class, ids.toArray());
    } catch (Exception e) { throw new IllegalStateException(e); }
  }
  private String hash(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
  private Map<String,Object> view(Map<String,Object> row){Map<String,Object> m=new LinkedHashMap<>(); m.put("id",row.get("id"));m.put("type",row.get("type"));m.put("status",row.get("status"));m.put("vehicleId",row.get("vehicle_id"));m.put("taskId",row.get("task_id"));m.put("orderId",row.get("order_id"));try{m.put("compartmentIds",json.readValue(String.valueOf(row.get("compartment_ids")),List.class));}catch(Exception e){m.put("compartmentIds",List.of());}m.put("message",row.get("message"));return m;}
  private String write(Object v){try{return json.writeValueAsString(v);}catch(Exception e){throw new IllegalStateException(e);}}
  private static ApiException bad(String s){return new ApiException(422,42204,s);}
}
