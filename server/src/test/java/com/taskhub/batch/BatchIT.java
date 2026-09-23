package com.taskhub.batch;

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

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.sql.init.mode=never"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class BatchIT {
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
  void createProtectsOwnershipAndVersion() throws Exception {
    String a = order(), b = order();
    grant();
    var batch = command("POST", "/api/batches", Map.of("orderIds", List.of(a, b)), 200);
    assertEquals("DRAFT", batch.path("status").asText());
    command("POST", "/api/batches", Map.of("orderIds", List.of(a)), 409);
    command("POST", "/api/batches", Map.of("orderIds", List.of(a, a)), 400);
    command(
        "PUT",
        "/api/batches/" + batch.path("id").asText() + "/orders",
        Map.of("expectedVersion", 9, "orderIds", List.of(a)),
        409);
  }

  @Test
  void loadingRequiresCompleteIndependentCompartmentsAndCapacity() throws Exception {
    String a = order(), b = order();
    grant();
    var batch = command("POST", "/api/batches", Map.of("orderIds", List.of(a, b)), 200);
    String id = batch.path("id").asText();
    String
        vehicle =
            db.queryForObject(
                "SELECT id FROM vehicle WHERE warehouse_id=?", String.class, warehouse),
        c = uuid(),
        d = uuid();
    for (String compartment : List.of(c, d))
      db.update(
          "INSERT INTO compartment(id,vehicle_id,hardware_no,label,enabled) VALUES(?,?,?,?,true)",
          compartment,
          vehicle,
          compartment,
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
        422);
    command(
        "PUT",
        "/api/batches/" + id + "/loading",
        Map.of(
            "expectedVersion",
            0,
            "vehicleId",
            vehicle,
            "assignments",
            List.of(
                Map.of("orderId", a, "compartmentIds", List.of(c)),
                Map.of("orderId", b, "compartmentIds", List.of(c)))),
        422);
    command(
        "PUT",
        "/api/batches/" + id + "/loading",
        Map.of(
            "expectedVersion",
            0,
            "vehicleId",
            vehicle,
            "assignments",
            List.of(
                Map.of("orderId", a, "compartmentIds", List.of(c)),
                Map.of("orderId", b, "compartmentIds", List.of(d)))),
        200);
    command(
        "POST",
        "/api/batches/" + id + "/loading/confirm",
        Map.of("expectedVersion", 1, "capacityConfirmed", false),
        422);
    var confirmed =
        command(
            "POST",
            "/api/batches/" + id + "/loading/confirm",
            Map.of("expectedVersion", 1, "capacityConfirmed", true),
            200);
    assertEquals("READY", confirmed.path("status").asText());
    assertEquals("CONFIRMED", confirmed.path("loadStatus").asText());
    var replaced =
        command(
            "PUT",
            "/api/batches/" + id + "/orders",
            Map.of("expectedVersion", 2, "orderIds", List.of(a)),
            200);
    assertEquals("UNCONFIRMED", replaced.path("loadStatus").asText());
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM active_compartment WHERE batch_id=?", Integer.class, id));
    assertEquals(
        "ACCEPTED",
        db.queryForObject("SELECT status FROM delivery_order WHERE id=?", String.class, b));
  }

  @Test
  void cancellationClosesLastBatchAndLockedApprovalStaysPending() throws Exception {
    String a = order();
    grant();
    var batch = command("POST", "/api/batches", Map.of("orderIds", List.of(a)), 200);
    String id = batch.path("id").asText(), cancel = uuid();
    db.update(
        "INSERT INTO order_cancellation(id,order_id,status,reason) VALUES(?,?,'PENDING','取消')",
        cancel,
        a);
    db.update("UPDATE batch SET status='LOCKED' WHERE id=?", id);
    command(
        "POST",
        "/api/orders/" + a + "/cancellations/" + cancel + "/review",
        Map.of("expectedVersion", 1, "decision", "APPROVE", "reason", "核对"),
        409);
    assertEquals(
        "PENDING",
        db.queryForObject(
            "SELECT status FROM order_cancellation WHERE id=?", String.class, cancel));
    db.update("UPDATE batch SET status='DRAFT' WHERE id=?", id);
    command(
        "POST",
        "/api/orders/" + a + "/cancellations/" + cancel + "/review",
        Map.of("expectedVersion", 1, "decision", "APPROVE", "reason", "核对"),
        200);
    assertEquals(
        "CLOSED", db.queryForObject("SELECT status FROM batch WHERE id=?", String.class, id));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM active_order_batch WHERE batch_id=?", Integer.class, id));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM batch_order WHERE batch_id=? AND removed_at IS NOT NULL",
            Integer.class,
            id));
  }

  @Test
  void mixedDimensionsAndOutOfScopeAreRejected() throws Exception {
    String a = order(), b = order();
    grant();
    String otherStop = uuid();
    db.update(
        "INSERT INTO stop(id,name,external_stop_id,enabled) VALUES(?,?,?,true)",
        otherStop,
        "其他点",
        otherStop);
    db.update("UPDATE delivery_order SET stop_id=? WHERE id=?", otherStop, b);
    command("POST", "/api/batches", Map.of("orderIds", List.of(a, b)), 422);
    db.update("UPDATE delivery_order SET stop_id=? WHERE id=?", stop, b);
    String otherSlot = slots().get(1).path("id").asText();
    db.update("UPDATE delivery_order SET slot_id=? WHERE id=?", otherSlot, b);
    command("POST", "/api/batches", Map.of("orderIds", List.of(a, b)), 422);
    String otherWarehouse = uuid();
    db.update(
        "INSERT INTO warehouse(id,name,code,enabled) VALUES(?,?,?,true)",
        otherWarehouse,
        "其他仓",
        otherWarehouse);
    db.update("UPDATE delivery_order SET warehouse_id=? WHERE id=?", otherWarehouse, b);
    command("POST", "/api/batches", Map.of("orderIds", List.of(b)), 403);
  }

  @Test
  void loadingRejectsEmptyForeignDisabledAndOccupiedCompartments() throws Exception {
    String a = order(), b = order();
    grant();
    String id =
        command("POST", "/api/batches", Map.of("orderIds", List.of(a)), 200).path("id").asText();
    String other =
        command("POST", "/api/batches", Map.of("orderIds", List.of(b)), 200).path("id").asText();
    String
        vehicle =
            db.queryForObject(
                "SELECT id FROM vehicle WHERE warehouse_id=?", String.class, warehouse),
        c = uuid(),
        otherVehicle = uuid();
    db.update(
        "INSERT INTO vehicle(id,name,external_vehicle_name,warehouse_id,enabled)"
            + " VALUES(?,?,?,?,true)",
        otherVehicle,
        "另一车",
        otherVehicle,
        warehouse);
    db.update(
        "INSERT INTO compartment(id,vehicle_id,hardware_no,label,enabled) VALUES(?,?,?,?,true)",
        c,
        otherVehicle,
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
            List.of(Map.of("orderId", a, "compartmentIds", List.of()))),
        422);
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
        422);
    db.update("UPDATE compartment SET vehicle_id=?,enabled=false WHERE id=?", vehicle, c);
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
        422);
    db.update("UPDATE compartment SET enabled=true WHERE id=?", c);
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
        "PUT",
        "/api/batches/" + other + "/loading",
        Map.of(
            "expectedVersion",
            0,
            "vehicleId",
            vehicle,
            "assignments",
            List.of(Map.of("orderId", b, "compartmentIds", List.of(c)))),
        409);
    command(
        "POST",
        "/api/batches/" + id + "/loading/confirm",
        Map.of("expectedVersion", 1, "capacityConfirmed", true),
        200);
    String cancel = uuid();
    db.update(
        "INSERT INTO order_cancellation(id,order_id,status,reason) VALUES(?,?,'PENDING','取消')",
        cancel,
        a);
    int version =
        db.queryForObject("SELECT version FROM delivery_order WHERE id=?", Integer.class, a);
    command(
        "POST",
        "/api/orders/" + a + "/cancellations/" + cancel + "/review",
        Map.of("expectedVersion", version, "decision", "APPROVE", "reason", "已卸货现场核对"),
        200);
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM active_compartment WHERE batch_id=?", Integer.class, id));
  }

  @Test
  void concurrentBatchClaimsHaveOneWinnerAndWorkerCannotRead() throws Exception {
    String a = order();
    grant();
    String colleague = employee(), colleagueGrant = uuid();
    db.update(
        "INSERT INTO role_grant(id,employee_id,role,scope) VALUES(?,?,'warehouse','WAREHOUSES')",
        colleagueGrant,
        colleague);
    db.update(
        "INSERT INTO grant_warehouse(grant_id,warehouse_id) VALUES(?,?)",
        colleagueGrant,
        warehouse);
    String colleagueToken = sessions.issue(colleague, "MINI").token();
    var index = new java.util.concurrent.atomic.AtomicInteger();
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    var start = new java.util.concurrent.CountDownLatch(1);
    try {
      java.util.concurrent.Callable<Integer> task =
          () -> {
            String bearer = index.getAndIncrement() == 0 ? token : colleagueToken;
            start.await();
            return mvc.perform(
                    post("/api/batches")
                        .header("Authorization", "Bearer " + bearer)
                        .header("X-Workspace", "warehouse")
                        .header("Idempotency-Key", uuid())
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("orderIds", List.of(a)))))
                .andReturn()
                .getResponse()
                .getStatus();
          };
      var x = pool.submit(task);
      var y = pool.submit(task);
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
    String id =
        db.queryForObject(
            "SELECT batch_id FROM active_order_batch WHERE order_id=?", String.class, a);
    mvc.perform(
            get("/api/batches/" + id)
                .header("Authorization", "Bearer " + token)
                .header("X-Workspace", "worker"))
        .andExpect(status().isForbidden());
  }

  @Test
  void lockedBatchRejectsDirectEditsWithoutAnyMutation() throws Exception {
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
    var loading =
        Map.of(
            "expectedVersion",
            0,
            "vehicleId",
            vehicle,
            "assignments",
            List.of(Map.of("orderId", a, "compartmentIds", List.of(c))));
    command("PUT", "/api/batches/" + id + "/loading", loading, 200);
    db.update("UPDATE batch SET status='LOCKED' WHERE id=?", id);
    var before = db.queryForMap("SELECT * FROM batch WHERE id=?", id);
    command(
        "PUT",
        "/api/batches/" + id + "/orders",
        Map.of("expectedVersion", 1, "orderIds", List.of(a)),
        409);
    command(
        "PUT",
        "/api/batches/" + id + "/loading",
        Map.of(
            "expectedVersion",
            1,
            "vehicleId",
            vehicle,
            "assignments",
            List.of(Map.of("orderId", a, "compartmentIds", List.of(c)))),
        409);
    assertEquals(before, db.queryForMap("SELECT * FROM batch WHERE id=?", id));
    assertEquals(
        List.of(a),
        db.queryForList(
            "SELECT order_id FROM active_order_batch WHERE batch_id=?", String.class, id));
    assertEquals(
        List.of(c),
        db.queryForList(
            "SELECT compartment_id FROM active_compartment WHERE batch_id=?", String.class, id));
    assertEquals(
        List.of(c),
        db.queryForList(
            "SELECT compartment_id FROM batch_assignment WHERE batch_id=?", String.class, id));
  }

  @Test
  void changingVehicleInvalidatesConfirmationAndMovesOccupancy() throws Exception {
    String a = order();
    grant();
    String id =
        command("POST", "/api/batches", Map.of("orderIds", List.of(a)), 200).path("id").asText();
    String
        vehicle =
            db.queryForObject(
                "SELECT id FROM vehicle WHERE warehouse_id=?", String.class, warehouse),
        c = uuid(),
        other = uuid(),
        d = uuid();
    db.update(
        "INSERT INTO vehicle(id,name,external_vehicle_name,warehouse_id,enabled)"
            + " VALUES(?,?,?,?,true)",
        other,
        "第二车",
        other,
        warehouse);
    db.update("INSERT INTO vehicle_stop(vehicle_id,stop_id) VALUES(?,?)", other, stop);
    db.update(
        "INSERT INTO compartment(id,vehicle_id,hardware_no,label,enabled) VALUES(?,?,?,?,true)",
        c,
        vehicle,
        c,
        "格口");
    db.update(
        "INSERT INTO compartment(id,vehicle_id,hardware_no,label,enabled) VALUES(?,?,?,?,true)",
        d,
        other,
        d,
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
    var result =
        command(
            "PUT",
            "/api/batches/" + id + "/loading",
            Map.of(
                "expectedVersion",
                2,
                "vehicleId",
                other,
                "assignments",
                List.of(Map.of("orderId", a, "compartmentIds", List.of(d)))),
            200);
    assertEquals("DRAFT", result.path("status").asText());
    assertEquals("UNCONFIRMED", result.path("loadStatus").asText());
    assertEquals(other, result.path("vehicleId").asText());
    var row = db.queryForMap("SELECT * FROM batch WHERE id=?", id);
    for (String field :
        List.of("confirmed_by", "confirmed_at", "confirmed_version", "assignments_hash"))
      assertNull(row.get(field));
    assertEquals(
        List.of(d),
        db.queryForList(
            "SELECT compartment_id FROM active_compartment WHERE batch_id=?", String.class, id));
    assertEquals(
        "ACCEPTED",
        db.queryForObject("SELECT status FROM delivery_order WHERE id=?", String.class, a));
  }
}
