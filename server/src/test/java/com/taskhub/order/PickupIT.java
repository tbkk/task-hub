package com.taskhub.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.taskhub.domain.identity.SessionService;
import java.util.UUID;
import java.util.*;
import java.time.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.sql.init.mode=never", "taskhub.vehicle.simulator-enabled=true"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PickupIT {
  @DynamicPropertySource
  static void db(DynamicPropertyRegistry r) {
    String url = System.getenv("MYSQL_TEST_URL");
    if (url == null || !url.matches("jdbc:mysql://[^/]+/task_hub_[a-zA-Z0-9_]*_test(?:\\?.*)?"))
      throw new IllegalStateException("显式专用库必填");
    r.add("spring.datasource.url", () -> url + (url.contains("?") ? "&" : "?") + "connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
    r.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    r.add("spring.datasource.username", () -> System.getenv().getOrDefault("MYSQL_TEST_USER", "taskhub"));
    r.add("spring.datasource.password", () -> System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", ""));
  }

  @Autowired MockMvc mvc;
  @Autowired SessionService sessions;
  @Autowired JdbcTemplate db;

  private String id() { return UUID.randomUUID().toString(); }
  private String phone() { return "139" + String.format("%08d", Math.floorMod(UUID.randomUUID().hashCode(), 100000000)); }

  private record Fixture(String warehouse, String stop, String vehicle, String task, String order,
                         String receiver, String receiverToken, String workerToken, String compartment) {}

  private Fixture fixture(boolean staleDoor, boolean unboundReceiver, boolean atStop) throws Exception {
    String w=id(), stop=id(), vehicle=id(), task=id(), request=id(), order=id(), receiver=id(), worker=id(), comp=id();
    String workerPhone=phone(), receiverPhone=phone();
    db.update("INSERT INTO warehouse(id,name,code,enabled) VALUES(?,?,?,true)",w,"取货仓",w);
    db.update("INSERT INTO warehouse_rule(warehouse_id,config) VALUES(?,?)",w,"{\"businessHours\":[],\"slotCapacity\":20,\"bookingDays\":7,\"descriptionMaxLength\":200,\"sizeMaxLength\":80,\"remarkMaxLength\":200,\"telemetryMaxAgeSeconds\":30,\"doorMaxAgeSeconds\":30,\"pickupTimeoutMinutes\":15,\"sharedCompartmentEnabled\":false}");
    db.update("INSERT INTO stop(id,name,external_stop_id,enabled) VALUES(?,?,?,true)",stop,"取货站",stop);
    db.update("INSERT INTO stop_warehouse(stop_id,warehouse_id) VALUES(?,?)",stop,w);
    db.update("INSERT INTO vehicle(id,name,external_vehicle_name,warehouse_id,enabled) VALUES(?,?,?,?,true)",vehicle,"取货车",vehicle,w);
    db.update("INSERT INTO vehicle_stop(vehicle_id,stop_id) VALUES(?,?)",vehicle,stop);
    db.update("INSERT INTO compartment(id,vehicle_id,hardware_no,label,enabled) VALUES(?,?,?,?,true)",comp,vehicle,"HW-"+comp,"格口");
    db.update("INSERT INTO employee(id,name,phone,enabled,home_warehouse_id) VALUES(?,?,?,true,?)",worker,"操作员",workerPhone,w);
    db.update("INSERT INTO role_grant(id,employee_id,role,scope) VALUES(?,?,'worker','SELF')",id(),worker);
    db.update("INSERT INTO employee(id,name,phone,enabled,home_warehouse_id) VALUES(?,?,?,true,?)",receiver,"接收人",receiverPhone,w);
    db.update("INSERT INTO role_grant(id,employee_id,role,scope) VALUES(?,?,'worker','SELF')",id(),receiver);
    if (!unboundReceiver) db.update("INSERT INTO verified_phone(employee_id,phone) VALUES(?,?)",receiver,receiverPhone);
    db.update("INSERT INTO reservation_slot(id,warehouse_id,start_at,end_at) VALUES(?,?,?,?)",id(),w,"2030-01-01 00:00:00","2030-01-01 01:00:00");
    String slot=db.queryForObject("SELECT id FROM reservation_slot WHERE warehouse_id=?",String.class,w);
    db.update("INSERT INTO delivery_order(id,number,warehouse_id,stop_id,slot_id,applicant_id,receiver_id,receiver_name,receiver_phone,description,size,remark,status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",order,"TH"+order.replace("-",""),w,stop,slot,worker,unboundReceiver?null:receiver,"接收人",receiverPhone,"货物","小件","","AWAITING_PICKUP");
    String batch=id();
    db.update("INSERT INTO batch(id,number,warehouse_id,stop_id,slot_id,status,load_status,vehicle_id) VALUES(?,?,?,?,?,'LOCKED','CONFIRMED',?)",batch,"B"+batch.replace("-",""),w,stop,slot,vehicle);
    db.update("INSERT INTO batch_order(id,batch_id,order_id) VALUES(?,?,?)",id(),batch,order);
    db.update("INSERT INTO active_order_batch(order_id,batch_id) VALUES(?,?)",order,batch);
    db.update("INSERT INTO batch_assignment(batch_id,order_id,compartment_id) VALUES(?,?,?)",batch,order,comp);
    db.update("INSERT INTO active_compartment(compartment_id,batch_id) VALUES(?,?)",comp,batch);
    db.update("INSERT INTO dispatch_request(id,batch_id,vehicle_id,from_stop_id,goal_stop_id,status,message,dispatch_id) VALUES(?,?,?,?,?,'ACCEPTED','已受理',?)",request,batch,vehicle,stop,stop,"dispatch-"+request);
    db.update("INSERT INTO vehicle_task(id,vehicle_id,batch_id,dispatch_request_id,provider,dispatch_id,state,current_stop_id,next_stop_id) VALUES(?,?,?,?,?,?,?,?,?)",task,vehicle,batch,request,"SIMULATOR","dispatch-"+request,"AT_STOP",stop,stop);
    db.update("INSERT INTO active_vehicle_task(vehicle_id,dispatch_request_id,task_id) VALUES(?,?,?)",vehicle,request,task);
    String business=atStop?"ATSTOP":"ONWAY";
    db.update("INSERT INTO vehicle_snapshot(vehicle_id,online,speed,business_status,dispatch_id,current_stop_id,reported_at,received_at,business_reported_at) VALUES(?,true,0,?,?,?,?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",vehicle,business,"dispatch-"+request,atStop?stop:null,Instant.now());
    if (!staleDoor) db.update("INSERT INTO vehicle_door_snapshot(compartment_id,vehicle_id,status,reported_at,received_at) VALUES(?,?, 'CLOSED',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",comp,vehicle);
    String workerToken=sessions.issue(worker,"MINI").token(), receiverToken=sessions.issue(receiver,"MINI").token();
    return new Fixture(w,stop,vehicle,task,order,receiver,receiverToken,workerToken,comp);
  }

  private JsonNode request(String token, String path, String key, Object body, int expected) throws Exception {
    var result=mvc.perform(post(path).header("Authorization","Bearer "+token).header("X-Workspace","worker").header("Idempotency-Key",key).contentType("application/json").content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body))).andExpect(status().is(expected)).andReturn();
    return new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).path("data");
  }

  @Test
  void malformedQrIsRejectedBeforeVehicleLookup() throws Exception {
    String employee = UUID.randomUUID().toString();
    String warehouse = UUID.randomUUID().toString();
    db.update("INSERT INTO warehouse(id,name,code,enabled) VALUES(?,?,?,true)", warehouse, "扫码测试仓", warehouse);
    db.update("INSERT INTO employee(id,name,phone,enabled,home_warehouse_id) VALUES(?,?,?,true,?)", employee, "扫码测试员", "139" + String.format("%08d", Math.abs(employee.hashCode())), warehouse);
    db.update("INSERT INTO role_grant(id,employee_id,role,scope) VALUES(?,?,'worker','SELF')", UUID.randomUUID().toString(), employee);
    var token = sessions.issue(employee, "MINI").token();
    mvc.perform(post("/api/pickup/scan")
            .header("Authorization", "Bearer " + token)
            .header("X-Workspace", "worker")
            .contentType("application/json")
            .content("{\"qrText\":\"https://example.invalid\"}"))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test void unboundReceiverIsNotReturnedByScan() throws Exception {
    Fixture f=fixture(false,true,true);
    JsonNode data=request(f.receiverToken,"/api/pickup/scan","scan-"+id(),Map.of("qrText","taskhub:vehicle:"+f.vehicle),200);
    org.junit.jupiter.api.Assertions.assertEquals(0,data.path("orders").size());
  }

  @Test void anotherEmployeeCannotScanOrConfirmReceiverOrder() throws Exception {
    Fixture f=fixture(false,false,true);
    JsonNode scan=request(f.workerToken,"/api/pickup/scan","scan-"+id(),Map.of("qrText","taskhub:vehicle:"+f.vehicle),200);
    org.junit.jupiter.api.Assertions.assertEquals(0,scan.path("orders").size());
    request(f.workerToken,"/api/orders/"+f.order+"/pickup/confirm","confirm-"+id(),Map.of("expectedVersion",0),404);
  }

  @Test void anotherVehicleQrCannotExposeTask() throws Exception {
    Fixture f=fixture(false,false,true);
    request(f.receiverToken,"/api/pickup/scan","scan-"+id(),Map.of("qrText","taskhub:vehicle:"+id()),404);
  }

  @Test void openRejectsOrderWithoutAssignedCompartment() throws Exception {
    Fixture f=fixture(false,false,true);
    db.update("DELETE FROM active_compartment WHERE compartment_id=?",f.compartment);
    db.update("DELETE FROM batch_assignment WHERE compartment_id=?",f.compartment);
    request(f.receiverToken,"/api/orders/"+f.order+"/pickup/open","open-"+id(),Map.of("expectedVersion",0),422);
  }

  @Test void openRejectsMissingDoorSnapshot() throws Exception {
    Fixture f=fixture(true,false,true);
    request(f.receiverToken,"/api/orders/"+f.order+"/pickup/open","open-"+id(),Map.of("expectedVersion",0),422);
  }

  @Test void openRejectsStaleDoorSnapshot() throws Exception {
    Fixture f=fixture(false,false,true);
    db.update("UPDATE vehicle_door_snapshot SET reported_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 90 SECOND) WHERE compartment_id=?",f.compartment);
    request(f.receiverToken,"/api/orders/"+f.order+"/pickup/open","open-"+id(),Map.of("expectedVersion",0),422);
  }

  @Test void confirmRejectsNonAtStopAndStaleTelemetry() throws Exception {
    Fixture f=fixture(false,false,false);
    request(f.receiverToken,"/api/orders/"+f.order+"/pickup/confirm","confirm-"+id(),Map.of("expectedVersion",0),422);
    db.update("UPDATE vehicle_snapshot SET business_status='ATSTOP',current_stop_id=?,reported_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 90 SECOND) WHERE vehicle_id=?",f.stop,f.vehicle);
    request(f.receiverToken,"/api/orders/"+f.order+"/pickup/confirm","confirm-"+id(),Map.of("expectedVersion",0),422);
  }

  @Test void confirmIsIdempotentAndPersistsPickupActor() throws Exception {
    Fixture f=fixture(false,false,true);
    String key="confirm-"+id();
    JsonNode first=request(f.receiverToken,"/api/orders/"+f.order+"/pickup/confirm",key,Map.of("expectedVersion",0),200);
    JsonNode second=request(f.receiverToken,"/api/orders/"+f.order+"/pickup/confirm",key,Map.of("expectedVersion",0),200);
    org.junit.jupiter.api.Assertions.assertEquals("COMPLETED",first.path("status").asText());
    org.junit.jupiter.api.Assertions.assertEquals(first.path("id").asText(),second.path("id").asText());
    org.junit.jupiter.api.Assertions.assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE idempotency_key=?",Integer.class,key));
  }

  @Test void twoReceiversCanCompleteTheirOwnOrders() throws Exception {
    Fixture f=fixture(false,false,true);
    String receiver2=id(), phone2=phone(), order2=id(), comp2=id();
    db.update("INSERT INTO employee(id,name,phone,enabled,home_warehouse_id) VALUES(?,?,?,true,?)",receiver2,"第二接收人",phone2,f.warehouse);
    db.update("INSERT INTO role_grant(id,employee_id,role,scope) VALUES(?,?,'worker','SELF')",id(),receiver2);
    db.update("INSERT INTO verified_phone(employee_id,phone) VALUES(?,?)",receiver2,phone2);
    db.update("INSERT INTO compartment(id,vehicle_id,hardware_no,label,enabled) VALUES(?,?,?,?,true)",comp2,f.vehicle,"HW-"+comp2,"格口2");
    String slot=db.queryForObject("SELECT id FROM reservation_slot WHERE warehouse_id=?",String.class,f.warehouse);
    db.update("INSERT INTO delivery_order(id,number,warehouse_id,stop_id,slot_id,applicant_id,receiver_id,receiver_name,receiver_phone,description,size,remark,status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",order2,"TH"+order2.replace("-",""),f.warehouse,f.stop,slot,f.receiver,receiver2,"第二接收人",phone2,"货物2","小件","","AWAITING_PICKUP");
    String batch=db.queryForObject("SELECT batch_id FROM active_order_batch WHERE order_id=?",String.class,f.order);
    db.update("INSERT INTO batch_order(id,batch_id,order_id) VALUES(?,?,?)",id(),batch,order2);
    db.update("INSERT INTO active_order_batch(order_id,batch_id) VALUES(?,?)",order2,batch);
    db.update("INSERT INTO batch_assignment(batch_id,order_id,compartment_id) VALUES(?,?,?)",batch,order2,comp2);
    db.update("INSERT INTO active_compartment(compartment_id,batch_id) VALUES(?,?)",comp2,batch);
    db.update("INSERT INTO vehicle_door_snapshot(compartment_id,vehicle_id,status,reported_at,received_at) VALUES(?,?, 'CLOSED',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",comp2,f.vehicle);
    String receiver2Token=sessions.issue(receiver2,"MINI").token();
    JsonNode first=request(f.receiverToken,"/api/orders/"+f.order+"/pickup/confirm","confirm-"+id(),Map.of("expectedVersion",0),200);
    JsonNode second=request(receiver2Token,"/api/orders/"+order2+"/pickup/confirm","confirm-"+id(),Map.of("expectedVersion",0),200);
    org.junit.jupiter.api.Assertions.assertEquals("COMPLETED",first.path("status").asText());
    org.junit.jupiter.api.Assertions.assertEquals("COMPLETED",second.path("status").asText());
    org.junit.jupiter.api.Assertions.assertEquals(f.receiver,db.queryForObject("SELECT pickup_employee_id FROM delivery_order WHERE id=?",String.class,f.order));
    org.junit.jupiter.api.Assertions.assertEquals(receiver2,db.queryForObject("SELECT pickup_employee_id FROM delivery_order WHERE id=?",String.class,order2));
  }
}
