package com.taskhub.integration;

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
class VehicleEventIT {
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

  JsonNode event(
      String vehicle,
      String dispatch,
      String event,
      Instant time,
      String status,
      String stopId,
      Object doors)
      throws Exception {
    var body = new LinkedHashMap<String, Object>();
    body.put("eventId", event);
    body.put("vehicleId", vehicle);
    body.put("dispatchId", dispatch);
    body.put("reportedAt", time.toString());
    body.put("businessStatus", status);
    body.put("currentStopId", stopId);
    body.put("online", true);
    body.put("speed", status.equals("ONWAY") ? 1 : 0);
    if (doors != null) body.put("doors", doors);
    var response =
        mvc.perform(
                post("/api/dev/simulator/events")
                    .header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .content(json.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andReturn();
    return json.readTree(response.getResponse().getContentAsString()).path("data");
  }

  void integrationGrant() {
    db.update(
        "INSERT INTO platform_grant(id,employee_id,capability,scope)"
            + " VALUES(?,?,'INTEGRATION_MANAGE','ALL')",
        uuid(),
        worker);
  }

  @Test
  void orderedEventsDeduplicateAndOnlyMatchingArrivalAdvancesOrders() throws Exception {
    String id = ready();
    var request = dispatch(id, uuid(), 2, 200);
    String vehicle = request.path("vehicleId").asText(),
        external = request.path("dispatchId").asText();
    integrationGrant();
    db.update(
        "UPDATE vehicle_snapshot SET reported_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 10"
            + " SECOND),business_reported_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 10 SECOND) WHERE"
            + " vehicle_id=?",
        vehicle);
    Instant time = Instant.now().minusSeconds(2);
    String event = uuid();
    event(vehicle, external, event, time, "ONWAY", stop, null);
    event(vehicle, external, event, time, "ONWAY", stop, null);
    assertEquals(
        "IN_TRANSIT",
        db.queryForObject(
            "SELECT o.status FROM delivery_order o JOIN active_order_batch a ON a.order_id=o.id"
                + " WHERE a.batch_id=?",
            String.class,
            id));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM integration_event WHERE event_key=?", Integer.class, event));
    event(vehicle, external, uuid(), time.minusSeconds(1), "IDLE", stop, null);
    assertEquals(
        "ONWAY",
        db.queryForObject(
            "SELECT business_status FROM vehicle_snapshot WHERE vehicle_id=?",
            String.class,
            vehicle));
    event(vehicle, "old-task", uuid(), time.plusMillis(100), "ATSTOP", stop, null);
    assertEquals(
        "ONWAY",
        db.queryForObject(
            "SELECT business_status FROM vehicle_snapshot WHERE vehicle_id=?",
            String.class,
            vehicle));
    Object doorTime =
        db.queryForObject(
            "SELECT reported_at FROM vehicle_door_snapshot WHERE vehicle_id=?",
            Object.class,
            vehicle);
    event(vehicle, external, uuid(), time.plusMillis(200), "ATSTOP", stop, null);
    assertEquals(
        "AWAITING_PICKUP",
        db.queryForObject(
            "SELECT o.status FROM delivery_order o JOIN active_order_batch a ON a.order_id=o.id"
                + " WHERE a.batch_id=?",
            String.class,
            id));
    assertEquals(
        doorTime,
        db.queryForObject(
            "SELECT reported_at FROM vehicle_door_snapshot WHERE vehicle_id=?",
            Object.class,
            vehicle));
    assertEquals(
        "AT_STOP",
        db.queryForObject("SELECT state FROM vehicle_task WHERE batch_id=?", String.class, id));
  }

  @Test
  void equalTimestampConflictRequiresReconciliationAndMissingDoorsRemainUnknown() throws Exception {
    String id = ready();
    var request = dispatch(id, uuid(), 2, 200);
    String vehicle = request.path("vehicleId").asText(),
        external = request.path("dispatchId").asText();
    integrationGrant();
    db.update("DELETE FROM vehicle_door_snapshot WHERE vehicle_id=?", vehicle);
    db.update(
        "UPDATE vehicle_snapshot SET reported_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 10"
            + " SECOND),business_reported_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 10 SECOND) WHERE"
            + " vehicle_id=?",
        vehicle);
    Instant time = Instant.now().minusSeconds(2);
    event(vehicle, external, uuid(), time, "ONWAY", stop, null);
    event(vehicle, external, uuid(), time, "ATSTOP", stop, null);
    assertEquals(
        "RECONCILIATION",
        db.queryForObject("SELECT state FROM vehicle_task WHERE batch_id=?", String.class, id));
    mvc.perform(
            get("/api/vehicles/" + vehicle)
                .header("Authorization", "Bearer " + token)
                .header("X-Workspace", "dispatch"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.doors[0].status").value("UNKNOWN"));
    assertEquals(
        "IN_TRANSIT",
        db.queryForObject(
            "SELECT o.status FROM delivery_order o JOIN active_order_batch a ON a.order_id=o.id"
                + " WHERE a.batch_id=?",
            String.class,
            id));
  }

  @Test
  void wrongStopNeverAllowsPickupAndTelemetryHasIndependentOrdering() throws Exception {
    String id = ready();
    var request = dispatch(id, uuid(), 2, 200);
    String vehicle = request.path("vehicleId").asText(),
        external = request.path("dispatchId").asText();
    integrationGrant();
    db.update(
        "UPDATE vehicle_snapshot SET reported_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 10"
            + " SECOND),business_reported_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 10 SECOND) WHERE"
            + " vehicle_id=?",
        vehicle);
    Instant time = Instant.now().minusSeconds(2);
    var body =
        Map.of(
            "eventId",
            uuid(),
            "vehicleId",
            vehicle,
            "dispatchId",
            external,
            "reportedAt",
            time.plusMillis(500).toString(),
            "online",
            true,
            "speed",
            2);
    mvc.perform(
            post("/api/dev/simulator/events")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
        .andExpect(status().isOk());
    event(vehicle, external, uuid(), time, "ONWAY", stop, null);
    assertEquals(
        2,
        db.queryForObject(
            "SELECT speed FROM vehicle_snapshot WHERE vehicle_id=?", Integer.class, vehicle));
    assertEquals(
        "RUNNING",
        db.queryForObject("SELECT state FROM vehicle_task WHERE batch_id=?", String.class, id));
    String wrong = uuid();
    db.update(
        "INSERT INTO stop(id,name,external_stop_id,enabled) VALUES(?,?,?,true)",
        wrong,
        "错误站",
        wrong);
    event(vehicle, external, uuid(), time.plusMillis(700), "ATSTOP", wrong, null);
    assertEquals(
        "RECONCILIATION",
        db.queryForObject("SELECT state FROM vehicle_task WHERE batch_id=?", String.class, id));
    assertEquals(
        "IN_TRANSIT",
        db.queryForObject(
            "SELECT o.status FROM delivery_order o JOIN active_order_batch a ON a.order_id=o.id"
                + " WHERE a.batch_id=?",
            String.class,
            id));
  }

  @Test
  void freshPartialTelemetryDoesNotInventZeroSpeedAndUnauthorizedInjectionFails() throws Exception {
    String id = ready();
    var request = dispatch(id, uuid(), 2, 200);
    String vehicle = request.path("vehicleId").asText(),
        external = request.path("dispatchId").asText();
    var body =
        Map.of(
            "eventId",
            uuid(),
            "vehicleId",
            vehicle,
            "dispatchId",
            external,
            "reportedAt",
            Instant.now().toString(),
            "online",
            true);
    mvc.perform(
            post("/api/dev/simulator/events")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
        .andExpect(status().isForbidden());
    integrationGrant();
    db.update(
        "UPDATE vehicle_snapshot SET reported_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 10 SECOND)"
            + " WHERE vehicle_id=?",
        vehicle);
    mvc.perform(
            post("/api/dev/simulator/events")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
        .andExpect(status().isOk());
    assertNull(
        db.queryForObject(
            "SELECT speed FROM vehicle_snapshot WHERE vehicle_id=?", Object.class, vehicle));
  }
}
