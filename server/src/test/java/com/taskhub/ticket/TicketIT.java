package com.taskhub.ticket;

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
class TicketIT {
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
  void ticketLifecyclePersistsWithoutChangingOrder() throws Exception {
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
    var result =
        write("/api/tickets", "worker", Map.of("orderId", order, "description", "物料标签有误"), uuid())
            .andExpect(status().isOk())
            .andReturn();
    var ticket = json.readTree(result.getResponse().getContentAsString()).path("data");
    String id = ticket.path("id").asText();
    write("/api/tickets", "worker", Map.of("orderId", order, "description", "重复反馈"), uuid())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(id));
    db.update(
        "INSERT INTO role_grant(id,employee_id,role,scope) VALUES(?,?,'warehouse','ALL')",
        uuid(),
        worker);
    write("/api/tickets/" + id + "/accept", "warehouse", Map.of("expectedVersion", 0), uuid())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.state").value("PROCESSING"));
    write(
            "/api/tickets/" + id + "/close",
            "warehouse",
            Map.of("expectedVersion", 1, "result", "  "),
            uuid())
        .andExpect(status().isBadRequest());
    write(
            "/api/tickets/" + id + "/notes",
            "warehouse",
            Map.of("expectedVersion", 1, "text", "正在核实标签"),
            uuid())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.notes.length()").value(1));
    String key = uuid();
    for (int i = 0; i < 2; i++)
      write(
              "/api/tickets/" + id + "/close",
              "warehouse",
              Map.of("expectedVersion", 2, "result", "标签已核对"),
              key)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.state").value("CLOSED"));
    assertEquals(
        "PENDING",
        db.queryForObject("SELECT status FROM delivery_order WHERE id=?", String.class, order));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM active_order_ticket WHERE order_id=?", Integer.class, order));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM audit_event WHERE object_id=? AND action='TICKET_CLOSE'",
            Integer.class,
            id));
    write(
            "/api/tickets/" + id + "/close",
            "warehouse",
            Map.of("expectedVersion", 2, "result", "重复处理"),
            uuid())
        .andExpect(status().isConflict());
  }

  @Test
  void foreignWorkerCannotReadTicketAndTwoClosersHaveOneWinner() throws Exception {
    String slot = slots().get(0).path("id").asText();
    var created =
        write("/api/orders", "worker", body(slot), uuid()).andExpect(status().isOk()).andReturn();
    String order =
        json.readTree(created.getResponse().getContentAsString()).path("data").path("id").asText();
    var result =
        write("/api/tickets", "worker", Map.of("orderId", order, "description", "需要仓库核对"), uuid())
            .andExpect(status().isOk())
            .andReturn();
    String id =
        json.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText();
    String other = employee(), otherToken = sessions.issue(other, "MINI").token();
    mvc.perform(
            get("/api/tickets/" + id)
                .header("Authorization", "Bearer " + otherToken)
                .header("X-Workspace", "worker"))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/tickets")
                .header("Authorization", "Bearer " + otherToken)
                .header("X-Workspace", "worker"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0));
    db.update(
        "INSERT INTO role_grant(id,employee_id,role,scope) VALUES(?,?,'warehouse','WAREHOUSES')",
        uuid(),
        other);
    mvc.perform(
            post("/api/tickets/" + id + "/close")
                .header("Authorization", "Bearer " + otherToken)
                .header("X-Workspace", "warehouse")
                .header("Idempotency-Key", uuid())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("expectedVersion", 0, "result", "越仓"))))
        .andExpect(status().isNotFound());
    db.update("UPDATE role_grant SET scope='ALL' WHERE employee_id=? AND role='warehouse'", other);
    db.update(
        "INSERT INTO role_grant(id,employee_id,role,scope) VALUES(?,?,'warehouse','ALL')",
        uuid(),
        worker);
    write(
            "/api/tickets/" + id + "/close",
            "warehouse",
            Map.of("expectedVersion", 0, "result", "长".repeat(1001)),
            uuid())
        .andExpect(status().isBadRequest());
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    var start = new java.util.concurrent.CountDownLatch(1);
    var index = new java.util.concurrent.atomic.AtomicInteger();
    java.util.concurrent.Callable<Integer> action =
        () -> {
          String bearer = index.getAndIncrement() == 0 ? token : otherToken;
          start.await();
          return mvc.perform(
                  post("/api/tickets/" + id + "/close")
                      .header("Authorization", "Bearer " + bearer)
                      .header("X-Workspace", "warehouse")
                      .header("Idempotency-Key", uuid())
                      .contentType("application/json")
                      .content(
                          json.writeValueAsString(Map.of("expectedVersion", 0, "result", "已经核对"))))
              .andReturn()
              .getResponse()
              .getStatus();
        };
    try {
      var a = pool.submit(action);
      var b = pool.submit(action);
      start.countDown();
      var results =
          new ArrayList<>(
              List.of(
                  a.get(10, java.util.concurrent.TimeUnit.SECONDS),
                  b.get(10, java.util.concurrent.TimeUnit.SECONDS)));
      Collections.sort(results);
      assertEquals(List.of(200, 409), results);
    } finally {
      pool.shutdownNow();
    }
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM audit_event WHERE object_id=? AND action='TICKET_CLOSE'",
            Integer.class,
            id));
  }

  org.springframework.test.web.servlet.ResultActions write(
      String path, String workspace, Object body, String key) throws Exception {
    return mvc.perform(
        post(path)
            .header("Authorization", "Bearer " + token)
            .header("X-Workspace", workspace)
            .header("Idempotency-Key", key)
            .contentType("application/json")
            .content(json.writeValueAsString(body)));
  }
}
