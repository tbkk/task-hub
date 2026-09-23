package com.taskhub.dispatch;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.taskhub.domain.identity.SessionService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.sql.init.mode=never",
      "taskhub.vehicle.simulator-enabled=true"
    })
@ActiveProfiles("test")
@AutoConfigureMockMvc
class DispatchIT {
  @DynamicPropertySource
  static void db(DynamicPropertyRegistry r) {
    String url = System.getenv("MYSQL_TEST_URL");
    if (url == null || !url.matches("jdbc:mysql://[^/]+/task_hub_[a-zA-Z0-9_]*_test(?:\\?.*)?"))
      throw new IllegalStateException("显式专用库必填");
    r.add(
        "spring.datasource.url",
        () ->
            url
                + (url.contains("?") ? "&" : "?")
                + "connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
    r.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    r.add(
        "spring.datasource.username",
        () -> System.getenv().getOrDefault("MYSQL_TEST_USER", "taskhub"));
    r.add(
        "spring.datasource.password",
        () -> System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", ""));
  }

  @Autowired JdbcTemplate db;
  @Autowired SessionService sessions;
  @Autowired ObjectMapper json;
  @Autowired MockMvc mvc;
  String warehouse, stop, worker, token, receiverPhone;

  String uuid() {
    return UUID.randomUUID().toString();
  }

  String phone() {
    return "136"
        + String.format(
            "%08d", Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100000000));
  }

  String employee() {
    String id = uuid();
    db.update(
        "INSERT INTO employee(id,name,phone,enabled,home_warehouse_id) VALUES(?,?,?,true,?)",
        id,
        "订单工人",
        phone(),
        warehouse);
    db.update(
        "INSERT INTO role_grant(id,employee_id,role,scope) VALUES(?,?,'worker','SELF')",
        uuid(),
        id);
    return id;
  }

  @BeforeEach
  void fixture() throws Exception {
    warehouse = uuid();
    stop = uuid();
    String vehicle = uuid();
    db.update(
        "INSERT INTO warehouse(id,name,code,enabled) VALUES(?,?,?,true)",
        warehouse,
        "订单仓库",
        warehouse);
    db.update(
        "INSERT INTO stop(id,name,external_stop_id,enabled) VALUES(?,?,?,true)",
        stop,
        "订单停点",
        stop);
    db.update("INSERT INTO stop_warehouse(stop_id,warehouse_id) VALUES(?,?)", stop, warehouse);
    db.update(
        "INSERT INTO vehicle(id,name,external_vehicle_name,warehouse_id,enabled)"
            + " VALUES(?,?,?,?,true)",
        vehicle,
        "订单车",
        vehicle,
        warehouse);
    db.update("INSERT INTO vehicle_stop(vehicle_id,stop_id) VALUES(?,?)", vehicle, stop);
    var config = new HashMap<String, Object>();
    config.put(
        "businessHours",
        java.util.stream.IntStream.rangeClosed(1, 7)
            .mapToObj(i -> Map.of("weekday", i, "start", "08:00", "end", "17:00"))
            .toList());
    config.putAll(
        Map.of(
            "slotCapacity",
            20,
            "bookingDays",
            7,
            "descriptionMaxLength",
            200,
            "sizeMaxLength",
            80,
            "remarkMaxLength",
            200,
            "telemetryMaxAgeSeconds",
            30,
            "doorMaxAgeSeconds",
            30,
            "pickupTimeoutMinutes",
            15,
            "sharedCompartmentEnabled",
            false));
    db.update(
        "INSERT INTO warehouse_rule(warehouse_id,config) VALUES(?,?)",
        warehouse,
        json.writeValueAsString(config));
    worker = employee();
    receiverPhone = phone();
    token = sessions.issue(worker, "MINI").token();
  }

  JsonNode slots() throws Exception {
    String date = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1).toString();
    var r =
        mvc.perform(
                get("/api/catalog/slots")
                    .param("warehouseId", warehouse)
                    .param("stopId", stop)
                    .param("date", date)
                    .header("Authorization", "Bearer " + token)
                    .header("X-Workspace", "worker"))
            .andExpect(status().isOk())
            .andReturn();
    return json.readTree(r.getResponse().getContentAsString()).path("data");
  }

  Map<String, Object> body(String slot) {
    return Map.of(
        "warehouseId",
        warehouse,
        "stopId",
        stop,
        "slotId",
        slot,
        "description",
        "物料",
        "size",
        "小件",
        "receiverName",
        "待验证接收人",
        "receiverPhone",
        receiverPhone,
        "remark",
        "");
  }

  String order() throws Exception {
    String slot = slots().get(0).path("id").asText();
    var result =
        mvc.perform(
                post("/api/orders")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Workspace", "worker")
                    .header("Idempotency-Key", uuid())
                    .contentType("application/json")
                    .content(json.writeValueAsString(body(slot))))
            .andExpect(status().isOk())
            .andReturn();
    String id =
        json.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText();
    db.update("UPDATE delivery_order SET status='ACCEPTED' WHERE id=?", id);
    return id;
  }

  void grant() {
    String id = uuid();
    db.update(
        "INSERT INTO role_grant(id,employee_id,role,scope) VALUES(?,?,'warehouse','WAREHOUSES')",
        id,
        worker);
    db.update("INSERT INTO grant_warehouse(grant_id,warehouse_id) VALUES(?,?)", id, warehouse);
  }

  JsonNode command(String method, String path, Object body, int expected) throws Exception {
    var request = method.equals("POST") ? post(path) : put(path);
    var result =
        mvc.perform(
                request
                    .header("Authorization", "Bearer " + token)
                    .header("X-Workspace", "warehouse")
                    .header("Idempotency-Key", uuid())
                    .contentType("application/json")
                    .content(json.writeValueAsString(body)))
            .andExpect(status().is(expected))
            .andReturn();
    return json.readTree(result.getResponse().getContentAsString()).path("data");
  }

  @Test
  void dispatchRequiresLoadingBinding() throws Exception {
    String a = order();
    grant();
    String id =
        command("POST", "/api/batches", Map.of("orderIds", List.of(a)), 200).path("id").asText();
    String vehicle =
        db.queryForObject("SELECT id FROM vehicle WHERE warehouse_id=?", String.class, warehouse);
    db.update(
        "UPDATE batch SET vehicle_id=?,status='READY',load_status='CONFIRMED',confirmed_version=0"
            + " WHERE id=?",
        vehicle,
        id);
    String g = uuid();
    db.update(
        "INSERT INTO role_grant(id,employee_id,role,scope) VALUES(?,?,'dispatch','WAREHOUSES')",
        g,
        worker);
    db.update("INSERT INTO grant_warehouse(grant_id,warehouse_id) VALUES(?,?)", g, warehouse);
    mvc.perform(
            post("/api/batches/" + id + "/dispatch")
                .header("Authorization", "Bearer " + token)
                .header("X-Workspace", "dispatch")
                .header("Idempotency-Key", uuid())
                .contentType("application/json")
                .content("{\"expectedVersion\":0}"))
        .andExpect(status().isUnprocessableEntity());
  }

  String ready() throws Exception {
    String a = order();
    grant();
    String id =
        command("POST", "/api/batches", Map.of("orderIds", List.of(a)), 200).path("id").asText();
    String
        vehicle =
            db.queryForObject(
                "SELECT id FROM vehicle WHERE warehouse_id=?", String.class, warehouse),
        c = uuid();
    db.update(
        "INSERT INTO compartment(id,vehicle_id,hardware_no,label,enabled) VALUES(?,?,?,?,true)",
        c,
        vehicle,
        c,
        "格口");
    command(
        "PUT",
        "/api/batches/" + id + "/loading",
        Map.of(
            "expectedVersion",
            0,
            "vehicleId",
            vehicle,
            "assignments",
            List.of(Map.of("orderId", a, "compartmentIds", List.of(c)))),
        200);
    command(
        "POST",
        "/api/batches/" + id + "/loading/confirm",
        Map.of("expectedVersion", 1, "capacityConfirmed", true),
        200);
    String g = uuid();
    db.update(
        "INSERT INTO role_grant(id,employee_id,role,scope) VALUES(?,?,'dispatch','WAREHOUSES')",
        g,
        worker);
    db.update("INSERT INTO grant_warehouse(grant_id,warehouse_id) VALUES(?,?)", g, warehouse);
    db.update(
        "INSERT INTO warehouse_loading_stop(warehouse_id,stop_id) VALUES(?,?)", warehouse, stop);
    db.update(
        "INSERT INTO"
            + " vehicle_snapshot(vehicle_id,online,speed,business_status,current_stop_id,reported_at,received_at,business_reported_at)"
            + " VALUES(?,true,0,'IDLE',?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
        vehicle,
        stop);
    db.update(
        "INSERT INTO"
            + " vehicle_door_snapshot(compartment_id,vehicle_id,status,reported_at,received_at)"
            + " VALUES(?,?,'CLOSED',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
        c,
        vehicle);
    db.update(
        "INSERT INTO simulator_scenario(operation,outcome) VALUES('DISPATCH','ACCEPTED') ON"
            + " DUPLICATE KEY UPDATE outcome='ACCEPTED'");
    return id;
  }

  JsonNode dispatch(String id, String key, int version, int expected) throws Exception {
    var response =
        mvc.perform(
                post("/api/batches/" + id + "/dispatch")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Workspace", "dispatch")
                    .header("Idempotency-Key", key)
                    .contentType("application/json")
                    .content(json.writeValueAsString(Map.of("expectedVersion", version))))
            .andExpect(status().is(expected))
            .andReturn();
    return json.readTree(response.getResponse().getContentAsString()).path("data");
  }

  @Test
  void acceptedIsPlanningAndReplayDoesNotResend() throws Exception {
    String id = ready(), key = uuid();
    var response = dispatch(id, key, 2, 200);
    assertEquals("ACCEPTED", response.path("status").asText());
    assertEquals(
        "PLANNING",
        db.queryForObject("SELECT state FROM vehicle_task WHERE batch_id=?", String.class, id));
    assertEquals(
        "READY",
        db.queryForObject(
            "SELECT o.status FROM delivery_order o JOIN active_order_batch a ON a.order_id=o.id"
                + " WHERE a.batch_id=?",
            String.class,
            id));
    assertEquals(response.path("id"), dispatch(id, key, 2, 200).path("id"));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM simulator_command WHERE request_id=?",
            Integer.class,
            response.path("id").asText()));
    dispatch(id, uuid(), 3, 409);
  }

  @Test
  void timeoutRemainsLockedAndReconciliationDoesNotResend() throws Exception {
    String id = ready();
    db.update("UPDATE simulator_scenario SET outcome='TIMEOUT' WHERE operation='DISPATCH'");
    var response = dispatch(id, uuid(), 2, 200);
    assertEquals("UNKNOWN", response.path("status").asText());
    assertEquals(
        "LOCKED", db.queryForObject("SELECT status FROM batch WHERE id=?", String.class, id));
    String request = response.path("id").asText();
    mvc.perform(
            post("/api/dispatch-requests/" + request + "/reconcile")
                .header("Authorization", "Bearer " + token)
                .header("X-Workspace", "dispatch"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("UNKNOWN"));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM simulator_command WHERE request_id=?", Integer.class, request));
    dispatch(id, uuid(), 3, 409);
  }

  @Test
  void failedReleasesButStaleAndUnknownDoorsNeverReachGateway() throws Exception {
    String id = ready(),
        vehicle = db.queryForObject("SELECT vehicle_id FROM batch WHERE id=?", String.class, id);
    db.update(
        "UPDATE vehicle_snapshot SET reported_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 2 MINUTE) WHERE"
            + " vehicle_id=?",
        vehicle);
    dispatch(id, uuid(), 2, 422);
    db.update(
        "UPDATE vehicle_snapshot SET reported_at=UTC_TIMESTAMP(3) WHERE vehicle_id=?", vehicle);
    db.update("UPDATE vehicle_door_snapshot SET status='UNKNOWN' WHERE vehicle_id=?", vehicle);
    dispatch(id, uuid(), 2, 422);
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM dispatch_request WHERE batch_id=?", Integer.class, id));
    db.update("UPDATE vehicle_door_snapshot SET status='CLOSED' WHERE vehicle_id=?", vehicle);
    db.update("UPDATE simulator_scenario SET outcome='FAILED' WHERE operation='DISPATCH'");
    assertEquals("FAILED", dispatch(id, uuid(), 2, 200).path("status").asText());
    assertEquals(
        "READY", db.queryForObject("SELECT status FROM batch WHERE id=?", String.class, id));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM active_vehicle_task WHERE vehicle_id=?", Integer.class, vehicle));
  }

  @Test
  void externalTaskAndUnconfirmedLoadBlockDispatch() throws Exception {
    String id = ready(),
        vehicle = db.queryForObject("SELECT vehicle_id FROM batch WHERE id=?", String.class, id);
    db.update(
        "UPDATE vehicle_snapshot SET dispatch_id='external-active' WHERE vehicle_id=?", vehicle);
    dispatch(id, uuid(), 2, 422);
    db.update("UPDATE vehicle_snapshot SET dispatch_id=NULL WHERE vehicle_id=?", vehicle);
    db.update("UPDATE batch SET load_status='UNCONFIRMED' WHERE id=?", id);
    dispatch(id, uuid(), 2, 422);
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM dispatch_request WHERE batch_id=?", Integer.class, id));
  }

  @Test
  void concurrentDifferentOperatorsCannotDispatchTwoBatchesOnOneVehicle() throws Exception {
    String first = ready(),
        a = order(),
        second =
            command("POST", "/api/batches", Map.of("orderIds", List.of(a)), 200)
                .path("id")
                .asText();
    String
        vehicle = db.queryForObject("SELECT vehicle_id FROM batch WHERE id=?", String.class, first),
        c = uuid();
    db.update(
        "INSERT INTO compartment(id,vehicle_id,hardware_no,label,enabled) VALUES(?,?,?,?,true)",
        c,
        vehicle,
        c,
        "第二格口");
    db.update(
        "INSERT INTO"
            + " vehicle_door_snapshot(compartment_id,vehicle_id,status,reported_at,received_at)"
            + " VALUES(?,?,'CLOSED',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
        c,
        vehicle);
    command(
        "PUT",
        "/api/batches/" + second + "/loading",
        Map.of(
            "expectedVersion",
            0,
            "vehicleId",
            vehicle,
            "assignments",
            List.of(Map.of("orderId", a, "compartmentIds", List.of(c)))),
        200);
    command(
        "POST",
        "/api/batches/" + second + "/loading/confirm",
        Map.of("expectedVersion", 1, "capacityConfirmed", true),
        200);
    String colleague = employee(), g = uuid();
    db.update(
        "INSERT INTO role_grant(id,employee_id,role,scope) VALUES(?,?,'dispatch','WAREHOUSES')",
        g,
        colleague);
    db.update("INSERT INTO grant_warehouse(grant_id,warehouse_id) VALUES(?,?)", g, warehouse);
    String otherToken = sessions.issue(colleague, "MINI").token();
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    var start = new java.util.concurrent.CountDownLatch(1);
    try {
      java.util.function.BiFunction<String, String, Integer> send =
          (batch, bearer) -> {
            try {
              start.await();
              return mvc.perform(
                      post("/api/batches/" + batch + "/dispatch")
                          .header("Authorization", "Bearer " + bearer)
                          .header("X-Workspace", "dispatch")
                          .header("Idempotency-Key", uuid())
                          .contentType("application/json")
                          .content("{\"expectedVersion\":2}"))
                  .andReturn()
                  .getResponse()
                  .getStatus();
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          };
      var x = pool.submit(() -> send.apply(first, token));
      var y = pool.submit(() -> send.apply(second, otherToken));
      start.countDown();
      var results =
          new ArrayList<>(
              List.of(
                  x.get(10, java.util.concurrent.TimeUnit.SECONDS),
                  y.get(10, java.util.concurrent.TimeUnit.SECONDS)));
      Collections.sort(results);
      assertEquals(List.of(200, 409), results);
    } finally {
      pool.shutdownNow();
    }
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM simulator_command c JOIN dispatch_request r ON r.id=c.request_id"
                + " WHERE r.vehicle_id=?",
            Integer.class,
            vehicle));
  }

  @Test
  void abandonedPendingBecomesUnknownWithoutAutomaticSend() throws Exception {
    String id = ready();
    db.update("UPDATE simulator_scenario SET outcome='TIMEOUT' WHERE operation='DISPATCH'");
    String request = dispatch(id, uuid(), 2, 200).path("id").asText();
    db.update("DELETE FROM simulator_command WHERE request_id=?", request);
    db.update(
        "UPDATE dispatch_request SET status='PENDING',created_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL"
            + " 5 MINUTE) WHERE id=?",
        request);
    mvc.perform(
            get("/api/dispatch-requests/" + request)
                .header("Authorization", "Bearer " + token)
                .header("X-Workspace", "dispatch"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("UNKNOWN"));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM simulator_command WHERE request_id=?", Integer.class, request));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM active_vehicle_task WHERE dispatch_request_id=?",
            Integer.class,
            request));
  }

  @Test
  void reconcileDoesNotAdoptEvidenceForAnotherVehicle() throws Exception {
    String id = ready();
    db.update("UPDATE simulator_scenario SET outcome='TIMEOUT' WHERE operation='DISPATCH'");
    String request = dispatch(id, uuid(), 2, 200).path("id").asText();
    db.update(
        "UPDATE simulator_command SET"
            + " command_json=JSON_SET(command_json,'$.vehicleId','different-vehicle'),result_json=?"
            + " WHERE request_id=?",
        json.writeValueAsString(
            Map.of("status", "ACCEPTED", "dispatchId", "wrong-task", "message", "受理")),
        request);
    mvc.perform(
            post("/api/dispatch-requests/" + request + "/reconcile")
                .header("Authorization", "Bearer " + token)
                .header("X-Workspace", "dispatch"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("UNKNOWN"));
    assertEquals(
        0,
        db.queryForObject("SELECT COUNT(*) FROM vehicle_task WHERE batch_id=?", Integer.class, id));
  }
}
