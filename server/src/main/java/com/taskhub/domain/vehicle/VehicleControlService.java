package com.taskhub.domain.vehicle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.api.ApiException;
import com.taskhub.domain.identity.AuthorizationService;
import com.taskhub.domain.identity.IdentityModels.Actor;
import com.taskhub.domain.integration.VehicleGateway;
import com.taskhub.domain.ticket.TicketBlocker;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class VehicleControlService {
  private final JdbcTemplate db; private final AuthorizationService auth; private final VehicleGateway gateway; private final TicketBlocker tickets; private final ObjectMapper json;
  public VehicleControlService(JdbcTemplate db, AuthorizationService auth, VehicleGateway gateway, TicketBlocker tickets, ObjectMapper json){this.db=db;this.auth=auth;this.gateway=gateway;this.tickets=tickets;this.json=json;}
  public Map<String,Object> go(Actor actor,String taskId,String key,ControlModels.Input input){return execute(actor,taskId,key,"GO",input);}
  public Map<String,Object> cancel(Actor actor,String taskId,String key,ControlModels.Input input){return execute(actor,taskId,key,"CANCEL_TASK",input);}
  private Map<String,Object> execute(Actor actor,String taskId,String key,String type,ControlModels.Input input){
    auth.requireWorkspace(actor,"dispatch"); if(key==null||key.isBlank()) throw new ApiException(400,40000,"Idempotency-Key必填");
    var rows=db.queryForList("SELECT t.*,b.warehouse_id,b.stop_id,r.goal_stop_id,b.version batch_version,vs.* FROM vehicle_task t JOIN batch b ON b.id=t.batch_id JOIN dispatch_request r ON r.id=t.dispatch_request_id LEFT JOIN vehicle_snapshot vs ON vs.vehicle_id=t.vehicle_id WHERE t.id=? FOR UPDATE",taskId);
    if(rows.isEmpty()) throw new ApiException(404,40400,"车辆任务不存在"); var r=rows.get(0); auth.require(actor,"DISPATCH",(String)r.get("warehouse_id"));
    var doors=db.queryForList("SELECT d.status,d.reported_at FROM compartment c LEFT JOIN vehicle_door_snapshot d ON d.compartment_id=c.id WHERE c.vehicle_id=? AND c.enabled=true",r.get("vehicle_id"));
    var statuses=db.queryForList("SELECT o.status FROM batch_order bo JOIN delivery_order o ON o.id=bo.order_id WHERE bo.batch_id=? AND bo.removed_at IS NULL",String.class,r.get("batch_id"));
    var cfg=new ControlPolicy.Context((String)r.get("state"),(String)r.get("business_status"),(String)r.get("current_stop_id"),(String)r.get("next_stop_id"),(String)r.get("stop_id"), (Boolean)r.get("online"), VehicleQueryService.fresh(r.get("reported_at"),30), r.get("speed") instanceof Number n?new java.math.BigDecimal(n.toString()):null,statuses,doors.stream().map(x->x.get("status") == null ? "UNKNOWN" : String.valueOf(x.get("status"))).toList(),doors.stream().map(x->VehicleQueryService.fresh(x.get("reported_at"),30)).toList(),tickets.hasActiveForTask(taskId),!db.queryForList("SELECT id FROM control_request WHERE task_id=? AND status='UNKNOWN'",taskId).isEmpty());
    var blockers=ControlPolicy.blockers(cfg,"GO".equals(type)); if(!blockers.isEmpty()) throw new ApiException(422,42204, json.valueToTree(Map.of("blockers",blockers)).toString());
    var old=db.queryForList("SELECT * FROM control_request WHERE actor_id=? AND request_key=?",actor.employeeId(),key); if(!old.isEmpty()) return old.get(0);
    String id=UUID.randomUUID().toString();
    try { db.update("INSERT INTO control_request(id,request_key,actor_id,request_hash,type,vehicle_id,task_id,compartment_ids,status,message) VALUES(?,?,?,?,?,?,?,JSON_ARRAY(),'PENDING','控制请求已保存')",id,key,actor.employeeId(),type+":"+taskId,type,r.get("vehicle_id"),taskId); }
    catch (org.springframework.dao.DuplicateKeyException e) { return db.queryForMap("SELECT * FROM control_request WHERE actor_id=? AND request_key=?",actor.employeeId(),key); }
    VehicleGateway.GatewayResult result="GO".equals(type)?gateway.go(new VehicleGateway.TaskCommand(id,(String)r.get("vehicle_id"),(String)r.get("dispatch_id"))):gateway.cancel(new VehicleGateway.TaskCommand(id,(String)r.get("vehicle_id"),(String)r.get("dispatch_id")));
    db.update("UPDATE control_request SET status=?,message=?,updated_at=UTC_TIMESTAMP(3) WHERE id=?",result.status().name(),result.message(),id);
    return db.queryForMap("SELECT * FROM control_request WHERE id=?",id);
  }
}
