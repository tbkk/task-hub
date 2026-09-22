package com.taskhub.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.domain.identity.SessionService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** 控制接口的数据库锁/阻塞回归；需要显式 MYSQL_TEST_URL 专用库。 */
@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.sql.init.mode=never", "taskhub.vehicle.simulator-enabled=true"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ControlIT {
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
  private final ObjectMapper json = new ObjectMapper();

  private String id() { return UUID.randomUUID().toString(); }

  private record Fixture(String token, String task, String batch, String vehicle) {}

  private Fixture fixture(boolean door, boolean speed) {
    String w = id(), stop = id(), vehicle = id(), comp = id(), batch = id(), request = id(), task = id();
    String employee = id();
    db.update("INSERT INTO warehouse(id,name,code,enabled) VALUES(?,?,?,true)", w, "控制测试仓", w);
    db.update("INSERT INTO warehouse_rule(warehouse_id,config) VALUES(?,?)", w,
        "{\"businessHours\":[],\"slotCapacity\":20,\"bookingDays\":7,\"descriptionMaxLength\":200,\"sizeMaxLength\":80,\"remarkMaxLength\":200,\"telemetryMaxAgeSeconds\":30,\"doorMaxAgeSeconds\":30}");
    db.update("INSERT INTO stop(id,name,external_stop_id,enabled) VALUES(?,?,?,true)", stop, "控制站", stop);
    db.update("INSERT INTO stop_warehouse(stop_id,warehouse_id) VALUES(?,?)", stop, w);
    db.update("INSERT INTO vehicle(id,name,external_vehicle_name,warehouse_id,enabled) VALUES(?,?,?,?,true)", vehicle, "控制车", vehicle, w);
    db.update("INSERT INTO vehicle_stop(vehicle_id,stop_id) VALUES(?,?)", vehicle, stop);
    db.update("INSERT INTO compartment(id,vehicle_id,hardware_no,label,enabled) VALUES(?,?,?,?,true)", comp, vehicle, "HW-" + comp, "格口");
    db.update("INSERT INTO employee(id,name,phone,enabled,home_warehouse_id) VALUES(?,?,?,true,?)", employee, "调度员", "139" + Math.floorMod(employee.hashCode(), 100000000), w);
    String grant = id();
    db.update("INSERT INTO role_grant(id,employee_id,role,scope) VALUES(?,?,?,?)", grant, employee, "dispatch", "WAREHOUSES");
    db.update("INSERT INTO grant_warehouse(grant_id,warehouse_id) VALUES(?,?)", grant, w);
    String slot = id();
    db.update("INSERT INTO reservation_slot(id,warehouse_id,start_at,end_at) VALUES(?,?,?,?)", slot, w, "2030-01-01 00:00:00", "2030-01-01 01:00:00");
    String order = id();
    db.update("INSERT INTO delivery_order(id,number,warehouse_id,stop_id,slot_id,applicant_id,receiver_name,receiver_phone,description,size,remark,status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
        order, "TH" + order.replace("-", ""), w, stop, slot, employee, "接收人", "13912345678", "货物", "小件", "", "COMPLETED");
    db.update("INSERT INTO batch(id,number,warehouse_id,stop_id,slot_id,status,load_status,vehicle_id) VALUES(?,?,?,?,?,'LOCKED','CONFIRMED',?)", batch, "B" + batch.replace("-", ""), w, stop, slot, vehicle);
    db.update("INSERT INTO batch_order(id,batch_id,order_id) VALUES(?,?,?)", id(), batch, order);
    db.update("INSERT INTO active_order_batch(order_id,batch_id) VALUES(?,?)", order, batch);
    db.update("INSERT INTO batch_assignment(batch_id,order_id,compartment_id) VALUES(?,?,?)", batch, order, comp);
    db.update("INSERT INTO active_compartment(compartment_id,batch_id) VALUES(?,?)", comp, batch);
    db.update("INSERT INTO dispatch_request(id,batch_id,vehicle_id,from_stop_id,goal_stop_id,status,message,dispatch_id) VALUES(?,?,?,?,?,'ACCEPTED','已受理',?)", request, batch, vehicle, stop, stop, "dispatch-" + request);
    db.update("INSERT INTO vehicle_task(id,vehicle_id,batch_id,dispatch_request_id,provider,dispatch_id,state,current_stop_id,next_stop_id) VALUES(?,?,?,?,?,?,?,?,?)", task, vehicle, batch, request, "SIMULATOR", "dispatch-" + request, "AT_STOP", stop, stop);
    db.update("INSERT INTO active_vehicle_task(vehicle_id,dispatch_request_id,task_id) VALUES(?,?,?)", vehicle, request, task);
    db.update("INSERT INTO vehicle_snapshot(vehicle_id,online,speed,business_status,dispatch_id,current_stop_id,next_stop_id,reported_at,received_at,business_reported_at) VALUES(?,true,?,?,?,?,?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))", vehicle, speed ? 0 : null, "ATSTOP", "dispatch-" + request, stop, stop);
    if (door) db.update("INSERT INTO vehicle_door_snapshot(compartment_id,vehicle_id,status,reported_at,received_at) VALUES(?,?, 'CLOSED',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))", comp, vehicle);
    return new Fixture(sessions.issue(employee, "MINI").token(), task, batch, vehicle);
  }

  @Test
  void unknownDoorBlocksGoBeforeGatewayAndRequestWrite() throws Exception {
    Fixture f = fixture(false, true);
    mvc.perform(post("/api/tasks/" + f.task + "/go").header("Authorization", "Bearer " + f.token)
            .header("X-Workspace", "dispatch").header("Idempotency-Key", id())
            .contentType("application/json").content(json.writeValueAsString(java.util.Map.of("expectedVersion", 0))))
        .andExpect(status().isUnprocessableEntity());
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM control_request WHERE task_id=?", Integer.class, f.task));
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM simulator_command WHERE operation='GO' AND command_json LIKE CONCAT('%',?,'%')", Integer.class, f.vehicle));
  }

  @Test
  void nullSpeedBlocksCancelInsteadOfTreatingItAsZero() throws Exception {
    Fixture f = fixture(true, false);
    mvc.perform(post("/api/tasks/" + f.task + "/cancel").header("Authorization", "Bearer " + f.token)
            .header("X-Workspace", "dispatch").header("Idempotency-Key", id())
            .contentType("application/json").content(json.writeValueAsString(java.util.Map.of("expectedVersion", 0))))
        .andExpect(status().isUnprocessableEntity());
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM control_request WHERE task_id=?", Integer.class, f.task));
  }

  @Test
  void acceptedGoIsSeparateFromTaskExecutionAndIdempotent() throws Exception {
    Fixture f = fixture(true, true);
    String key = id();
    var body = json.writeValueAsString(java.util.Map.of("expectedVersion", 0));
    var first = mvc.perform(post("/api/tasks/" + f.task + "/go").header("Authorization", "Bearer " + f.token)
            .header("X-Workspace", "dispatch").header("Idempotency-Key", key)
            .contentType("application/json").content(body)).andExpect(status().isOk()).andReturn();
    var second = mvc.perform(post("/api/tasks/" + f.task + "/go").header("Authorization", "Bearer " + f.token)
            .header("X-Workspace", "dispatch").header("Idempotency-Key", key)
            .contentType("application/json").content(body)).andExpect(status().isOk()).andReturn();
    var firstData = json.readTree(first.getResponse().getContentAsString()).path("data");
    var secondData = json.readTree(second.getResponse().getContentAsString()).path("data");
    assertEquals("ACCEPTED", firstData.path("status").asText());
    assertEquals(firstData.path("id").asText(), secondData.path("id").asText());
    assertEquals("AT_STOP", db.queryForObject("SELECT state FROM vehicle_task WHERE id=?", String.class, f.task));
    assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM control_request WHERE task_id=? AND type='GO'", Integer.class, f.task));
  }

  @Test
  void unknownGoRemainsActiveAndIsNotRetriedBySameRequest() throws Exception {
    db.update("INSERT INTO simulator_scenario(operation,outcome) VALUES('GO','TIMEOUT') ON DUPLICATE KEY UPDATE outcome='TIMEOUT'");
    try {
      Fixture f = fixture(true, true);
      String key = id();
      var body = json.writeValueAsString(java.util.Map.of("expectedVersion", 0));
      var first = mvc.perform(post("/api/tasks/" + f.task + "/go").header("Authorization", "Bearer " + f.token)
              .header("X-Workspace", "dispatch").header("Idempotency-Key", key)
              .contentType("application/json").content(body)).andExpect(status().isOk()).andReturn();
      var second = mvc.perform(post("/api/tasks/" + f.task + "/go").header("Authorization", "Bearer " + f.token)
              .header("X-Workspace", "dispatch").header("Idempotency-Key", key)
              .contentType("application/json").content(body)).andExpect(status().isOk()).andReturn();
      var firstData = json.readTree(first.getResponse().getContentAsString()).path("data");
      var secondData = json.readTree(second.getResponse().getContentAsString()).path("data");
      assertEquals("UNKNOWN", firstData.path("status").asText());
      assertEquals(firstData.path("id").asText(), secondData.path("id").asText());
      assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM control_request WHERE task_id=? AND status='UNKNOWN'", Integer.class, f.task));
    } finally {
      db.update("DELETE FROM simulator_scenario WHERE operation='GO'");
    }
  }
}
