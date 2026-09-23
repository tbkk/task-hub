package com.taskhub.order;

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
class OrderIT {
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
            1,
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

  @Test
  void persistentCreateReplayPrivacyAndIndependentCancellation() throws Exception {
    String slot = slots().get(0).path("id").asText(), key = uuid();
    String content = json.writeValueAsString(body(slot));
    var r =
        mvc.perform(
                post("/api/orders")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Workspace", "worker")
                    .header("Idempotency-Key", key)
                    .contentType("application/json")
                    .content(content))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.receiverBound").value(false))
            .andReturn();
    String id =
        json.readTree(r.getResponse().getContentAsString()).path("data").path("id").asText();
    mvc.perform(
            post("/api/orders")
                .header("Authorization", "Bearer " + token)
                .header("X-Workspace", "worker")
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content(content))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(id));
    String other = sessions.issue(employee(), "MINI").token();
    mvc.perform(
            get("/api/orders/" + id)
                .header("Authorization", "Bearer " + other)
                .header("X-Workspace", "worker"))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/orders/" + id + "/cancellations")
                .header("Authorization", "Bearer " + token)
                .header("X-Workspace", "worker")
                .header("Idempotency-Key", uuid())
                .contentType("application/json")
                .content("{\"expectedVersion\":0,\"reason\":\"计划调整\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PENDING"))
        .andExpect(jsonPath("$.data.cancellation.status").value("PENDING"));
    mvc.perform(
            post("/api/orders/" + id + "/cancellations")
                .header("Authorization", "Bearer " + token)
                .header("X-Workspace", "worker")
                .header("Idempotency-Key", uuid())
                .contentType("application/json")
                .content("{\"expectedVersion\":1,\"reason\":\"再次申请\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.version").value(1));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM order_cancellation WHERE order_id=?", Integer.class, id));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM delivery_order WHERE applicant_id=?", Integer.class, worker));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM audit_event WHERE object_id=? AND action='ORDER_CREATE'",
            Integer.class,
            id));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM business_event WHERE aggregate_id=? AND event_type='ORDER_CREATED'",
            Integer.class,
            id));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM audit_event WHERE object_id=? AND action='ORDER_CANCEL_REQUEST'",
            Integer.class,
            id));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM business_event WHERE aggregate_id=? AND event_type='ORDER_CANCEL_REQUESTED'",
            Integer.class,
            id));
  }

  @Test
  void capacityOneHasOnlyOneConcurrentWinner() throws Exception {
    String slot = slots().get(0).path("id").asText(),
        other = sessions.issue(employee(), "MINI").token();
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    var start = new java.util.concurrent.CountDownLatch(1);
    try {
      var a =
          pool.submit(
              () -> {
                start.await();
                return submit(token, slot);
              });
      var b =
          pool.submit(
              () -> {
                start.await();
                return submit(other, slot);
              });
      start.countDown();
      var statuses =
          new ArrayList<>(
              List.of(
                  a.get(10, java.util.concurrent.TimeUnit.SECONDS),
                  b.get(10, java.util.concurrent.TimeUnit.SECONDS)));
      Collections.sort(statuses);
      assertEquals(List.of(200, 422), statuses);
      assertEquals(
          1,
          db.queryForObject("SELECT used FROM reservation_slot WHERE id=?", Integer.class, slot));
    } finally {
      pool.shutdownNow();
    }
  }

  int submit(String bearer, String slot) throws Exception {
    return mvc.perform(
            post("/api/orders")
                .header("Authorization", "Bearer " + bearer)
                .header("X-Workspace", "worker")
                .header("Idempotency-Key", uuid())
                .contentType("application/json")
                .content(json.writeValueAsString(body(slot))))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  String createOrder(String slot) throws Exception {
    var created =
        mvc.perform(
                post("/api/orders")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Workspace", "worker")
                    .header("Idempotency-Key", uuid())
                    .contentType("application/json")
                    .content(json.writeValueAsString(body(slot))))
            .andExpect(status().isOk())
            .andReturn();
    return json.readTree(created.getResponse().getContentAsString()).path("data").path("id").asText();
  }

  String grantWarehouse(String employee, String warehouseId) {
    String grant = uuid();
    db.update(
        "INSERT INTO role_grant(id,employee_id,role,scope) VALUES(?,?,'warehouse','WAREHOUSES')",
        grant,
        employee);
    db.update("INSERT INTO grant_warehouse(grant_id,warehouse_id) VALUES(?,?)", grant, warehouseId);
    return sessions.issue(employee, "MINI").token();
  }

  @Test
  void historyFiltersInclusiveBusinessDatesAndRejectsInvalidRanges() throws Exception {
    String order = createOrder(slots().get(0).path("id").asText());
    db.update(
        "INSERT INTO audit_event(id,actor_id,actor_name,workspace,object_type,object_id,action,occurred_at)"
            + " VALUES(?,?,?,'worker','ORDER',?,'OLD_EVENT','2025-01-31 15:59:59')",
        uuid(), worker, "订单工人", order);
    db.update(
        "INSERT INTO audit_event(id,actor_id,actor_name,workspace,object_type,object_id,action,occurred_at)"
            + " VALUES(?,?,?,'worker','ORDER',?,'IN_RANGE','2025-01-31 16:00:00')",
        uuid(), worker, "订单工人", order);
    db.update(
        "INSERT INTO audit_event(id,actor_id,actor_name,workspace,object_type,object_id,action,occurred_at)"
            + " VALUES(?,?,?,'worker','ORDER',?,'NEW_EVENT','2025-02-01 16:00:00')",
        uuid(), worker, "订单工人", order);

    mvc.perform(
            get("/api/history")
                .param("objectType", "ORDER")
                .param("objectId", order)
                .param("from", "2025-02-01")
                .param("to", "2025-02-01")
                .header("Authorization", "Bearer " + token)
                .header("X-Workspace", "worker"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].action").value("IN_RANGE"));
    mvc.perform(
            get("/api/history")
                .param("objectType", "ORDER")
                .param("objectId", order)
                .param("from", "bad-date")
                .header("Authorization", "Bearer " + token)
                .header("X-Workspace", "worker"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/history")
                .param("objectType", "ORDER")
                .param("objectId", order)
                .param("from", "2025-02-02")
                .param("to", "2025-02-01")
                .header("Authorization", "Bearer " + token)
                .header("X-Workspace", "worker"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void warehouseScopeReasonsAndCancellationRejectionAreEnforced() throws Exception {
    String order = createOrder(slots().get(0).path("id").asText());
    String otherWarehouse = uuid();
    db.update(
        "INSERT INTO warehouse(id,name,code,enabled) VALUES(?,?,?,true)",
        otherWarehouse, "另一仓库", otherWarehouse);
    String outsider = employee();
    String outsiderToken = grantWarehouse(outsider, otherWarehouse);
    mvc.perform(
            post("/api/orders/" + order + "/review")
                .header("Authorization", "Bearer " + outsiderToken)
                .header("X-Workspace", "warehouse")
                .header("Idempotency-Key", uuid())
                .contentType("application/json")
                .content("{\"expectedVersion\":0,\"decision\":\"ACCEPT\"}"))
        .andExpect(status().isNotFound());

    String warehouseToken = grantWarehouse(worker, warehouse);
    mvc.perform(
            post("/api/orders/" + order + "/review")
                .header("Authorization", "Bearer " + warehouseToken)
                .header("X-Workspace", "warehouse")
                .header("Idempotency-Key", uuid())
                .contentType("application/json")
                .content("{\"expectedVersion\":0,\"decision\":\"REJECT\",\"reason\":\"  \"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/orders/" + order + "/cancellations")
                .header("Authorization", "Bearer " + token)
                .header("X-Workspace", "worker")
                .header("Idempotency-Key", uuid())
                .contentType("application/json")
                .content("{\"expectedVersion\":0,\"reason\":\"计划调整\"}"))
        .andExpect(status().isOk());
    String cancellation =
        db.queryForObject(
            "SELECT id FROM order_cancellation WHERE order_id=?", String.class, order);
    mvc.perform(
            post("/api/orders/" + order + "/cancellations/" + cancellation + "/review")
                .header("Authorization", "Bearer " + warehouseToken)
                .header("X-Workspace", "warehouse")
                .header("Idempotency-Key", uuid())
                .contentType("application/json")
                .content("{\"expectedVersion\":1,\"decision\":\"REJECT\",\"reason\":\"  \"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/orders/" + order + "/cancellations/" + cancellation + "/review")
                .header("Authorization", "Bearer " + warehouseToken)
                .header("X-Workspace", "warehouse")
                .header("Idempotency-Key", uuid())
                .contentType("application/json")
                .content("{\"expectedVersion\":1,\"decision\":\"REJECT\",\"reason\":\"继续配送\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PENDING"))
        .andExpect(jsonPath("$.data.cancellation.status").value("REJECTED"));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM audit_event WHERE object_id=? AND action='CANCELLATION_REJECT'",
            Integer.class,
            order));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM business_event WHERE aggregate_id=? AND event_type='CANCELLATION_REVIEWED'",
            Integer.class,
            order));
  }

  @Test
  void concurrentWarehouseReviewHasOneWinnerAndOneEvent() throws Exception {
    String order = createOrder(slots().get(0).path("id").asText());
    String first = employee(), second = employee();
    String firstToken = grantWarehouse(first, warehouse), secondToken = grantWarehouse(second, warehouse);
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    var start = new java.util.concurrent.CountDownLatch(1);
    try {
      java.util.concurrent.Callable<Integer> a =
          () -> review(firstToken, order, start, "ACCEPT", "");
      java.util.concurrent.Callable<Integer> b =
          () -> review(secondToken, order, start, "ACCEPT", "");
      var one = pool.submit(a);
      var two = pool.submit(b);
      start.countDown();
      var statuses = new ArrayList<>(List.of(one.get(), two.get()));
      Collections.sort(statuses);
      assertEquals(List.of(200, 409), statuses);
    } finally {
      pool.shutdownNow();
    }
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM audit_event WHERE object_id=? AND action='ORDER_ACCEPT'",
            Integer.class,
            order));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM business_event WHERE aggregate_id=? AND event_type='ORDER_REVIEWED'",
            Integer.class,
            order));
  }

  int review(
      String bearer,
      String order,
      java.util.concurrent.CountDownLatch start,
      String decision,
      String reason)
      throws Exception {
    start.await();
    return mvc.perform(
            post("/api/orders/" + order + "/review")
                .header("Authorization", "Bearer " + bearer)
                .header("X-Workspace", "warehouse")
                .header("Idempotency-Key", uuid())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "expectedVersion", 0,
                            "decision", decision,
                            "reason", reason))))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  @Test
  void auditFailureRollsBackOrderCapacityEventAndIdempotency() throws Exception {
    String slot = slots().get(0).path("id").asText(), key = uuid();
    String trigger = "fail_audit_" + uuid().replace("-", "");
    db.execute(
        "CREATE TRIGGER "
            + trigger
            + " BEFORE INSERT ON audit_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test audit failure'");
    try {
      mvc.perform(
              post("/api/orders")
                  .header("Authorization", "Bearer " + token)
                  .header("X-Workspace", "worker")
                  .header("Idempotency-Key", key)
                  .contentType("application/json")
                  .content(json.writeValueAsString(body(slot))))
          .andExpect(status().is5xxServerError());
    } finally {
      db.execute("DROP TRIGGER IF EXISTS " + trigger);
    }
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM delivery_order WHERE applicant_id=?", Integer.class, worker));
    assertEquals(
        0, db.queryForObject("SELECT used FROM reservation_slot WHERE id=?", Integer.class, slot));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM audit_event WHERE actor_id=? AND action='ORDER_CREATE'",
            Integer.class,
            worker));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM business_event WHERE event_type='ORDER_CREATED' AND"
                + " JSON_UNQUOTE(JSON_EXTRACT(payload,'$.warehouseId'))=?",
            Integer.class,
            warehouse));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM idempotency_record WHERE actor_id=? AND idempotency_key=?",
            Integer.class,
            worker,
            key));
  }

  @Test
  void favoritesArePersistentAndUnavailableStopsAreRejected() throws Exception {
    for (int i = 0; i < 2; i++)
      mvc.perform(
              put("/api/favorites/" + stop)
                  .header("Authorization", "Bearer " + token)
                  .header("X-Workspace", "worker"))
          .andExpect(status().isOk());
    mvc.perform(
            get("/api/favorites")
                .header("Authorization", "Bearer " + token)
                .header("X-Workspace", "worker"))
        .andExpect(jsonPath("$.data.length()").value(1));
    db.update("UPDATE stop SET enabled=false WHERE id=?", stop);
    mvc.perform(
            put("/api/favorites/" + stop)
                .header("Authorization", "Bearer " + token)
                .header("X-Workspace", "worker"))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(
            get("/api/favorites")
                .header("Authorization", "Bearer " + token)
                .header("X-Workspace", "worker"))
        .andExpect(jsonPath("$.data.length()").value(0));
  }

  @Test
  void warehouseRejectionReleasesCapacityOnce() throws Exception {
    String slot = slots().get(0).path("id").asText();
    var created =
        mvc.perform(
                post("/api/orders")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Workspace", "worker")
                    .header("Idempotency-Key", uuid())
                    .contentType("application/json")
                    .content(json.writeValueAsString(body(slot))))
            .andExpect(status().isOk())
            .andReturn();
    String order =
        json.readTree(created.getResponse().getContentAsString()).path("data").path("id").asText();
    String grant = uuid();
    db.update(
        "INSERT INTO role_grant(id,employee_id,role,scope) VALUES(?,?,'warehouse','WAREHOUSES')",
        grant,
        worker);
    db.update("INSERT INTO grant_warehouse(grant_id,warehouse_id) VALUES(?,?)", grant, warehouse);
    String key = uuid();
    for (int i = 0; i < 2; i++)
      mvc.perform(
              post("/api/orders/" + order + "/review")
                  .header("Authorization", "Bearer " + token)
                  .header("X-Workspace", "warehouse")
                  .header("Idempotency-Key", key)
                  .contentType("application/json")
                  .content("{\"expectedVersion\":0,\"decision\":\"REJECT\",\"reason\":\"库存不足\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.status").value("REJECTED"));
    assertEquals(
        0, db.queryForObject("SELECT used FROM reservation_slot WHERE id=?", Integer.class, slot));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM audit_event WHERE object_id=? AND action='ORDER_REJECT'",
            Integer.class,
            order));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM business_event WHERE aggregate_id=? AND event_type='ORDER_REVIEWED'",
            Integer.class,
            order));
  }

  @Test
  void warehouseAcceptAndCancellationKeepIndependentState() throws Exception {
    String slot = slots().get(0).path("id").asText();
    var created =
        mvc.perform(
                post("/api/orders")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Workspace", "worker")
                    .header("Idempotency-Key", uuid())
                    .contentType("application/json")
                    .content(json.writeValueAsString(body(slot))))
            .andExpect(status().isOk())
            .andReturn();
    String
        order =
            json.readTree(created.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asText(),
        grant = uuid();
    db.update(
        "INSERT INTO role_grant(id,employee_id,role,scope) VALUES(?,?,'warehouse','WAREHOUSES')",
        grant,
        worker);
    db.update("INSERT INTO grant_warehouse(grant_id,warehouse_id) VALUES(?,?)", grant, warehouse);
    mvc.perform(
            post("/api/orders/" + order + "/review")
                .header("Authorization", "Bearer " + token)
                .header("X-Workspace", "warehouse")
                .header("Idempotency-Key", uuid())
                .contentType("application/json")
                .content("{\"expectedVersion\":0,\"decision\":\"ACCEPT\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ACCEPTED"));
    var cancellation =
        mvc.perform(
                post("/api/orders/" + order + "/cancellations")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Workspace", "worker")
                    .header("Idempotency-Key", uuid())
                    .contentType("application/json")
                    .content("{\"expectedVersion\":1,\"reason\":\"计划调整\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
            .andReturn();
    String cancel =
        json.readTree(cancellation.getResponse().getContentAsString())
            .path("data")
            .path("cancellation")
            .path("id")
            .asText();
    String approvalKey = uuid();
    for (int i = 0; i < 2; i++)
      mvc.perform(
              post("/api/orders/" + order + "/cancellations/" + cancel + "/review")
                  .header("Authorization", "Bearer " + token)
                  .header("X-Workspace", "warehouse")
                  .header("Idempotency-Key", approvalKey)
                  .contentType("application/json")
                  .content("{\"expectedVersion\":2,\"decision\":\"APPROVE\",\"reason\":\"已核对未装货\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.status").value("CANCELLED"))
          .andExpect(jsonPath("$.data.cancellation.status").value("APPROVED"));
    assertEquals(
        0, db.queryForObject("SELECT used FROM reservation_slot WHERE id=?", Integer.class, slot));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM audit_event WHERE object_id=? AND action='CANCELLATION_APPROVE'",
            Integer.class,
            order));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM business_event WHERE aggregate_id=? AND event_type='CANCELLATION_REVIEWED'",
            Integer.class,
            order));
  }
}
