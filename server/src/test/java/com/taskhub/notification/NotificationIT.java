package com.taskhub.notification;

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
class NotificationIT {
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

  @Autowired com.taskhub.domain.notification.BusinessEventDelivery delivery;

  @Test
  void messagesAreAccountOwnedDeduplicatedAndReadIdempotently() throws Exception {
    db.update(
        "INSERT INTO role_grant(id,employee_id,role,scope) VALUES(?,?,'warehouse','ALL')",
        uuid(),
        worker);
    String slot = slots().get(0).path("id").asText();
    mvc.perform(
            post("/api/orders")
                .header("Authorization", "Bearer " + token)
                .header("X-Workspace", "worker")
                .header("Idempotency-Key", uuid())
                .contentType("application/json")
                .content(json.writeValueAsString(body(slot))))
        .andExpect(status().isOk());
    String receiver = employee();
    db.update("UPDATE employee SET phone=? WHERE id=?", receiverPhone, receiver);
    db.update("INSERT INTO verified_phone(employee_id,phone) VALUES(?,?)", receiver, receiverPhone);
    for (int i = 0; i < 20; i++) if (delivery.deliverBatch(100) == 0) break;
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM notification WHERE employee_id=?", Integer.class, receiver));
    var result =
        mvc.perform(get("/api/messages").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andReturn();
    String id =
        json.readTree(result.getResponse().getContentAsString())
            .path("data")
            .path("items")
            .get(0)
            .path("id")
            .asText();
    String event =
        db.queryForObject("SELECT event_id FROM notification WHERE id=?", String.class, id);
    db.update("UPDATE business_event SET processed_at=NULL WHERE id=?", event);
    delivery.deliverBatch(100);
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM notification WHERE event_id=? AND employee_id=?",
            Integer.class,
            event,
            worker));
    var first =
        mvc.perform(put("/api/messages/" + id + "/read").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
    var second =
        mvc.perform(put("/api/messages/" + id + "/read").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals(
        json.readTree(first.getResponse().getContentAsString()).path("data").path("readAt"),
        json.readTree(second.getResponse().getContentAsString()).path("data").path("readAt"));
    String otherToken = sessions.issue(employee(), "MINI").token();
    mvc.perform(get("/api/messages/" + id).header("Authorization", "Bearer " + otherToken))
        .andExpect(status().isNotFound());
    db.update("DELETE FROM role_grant WHERE employee_id=?", worker);
    mvc.perform(get("/api/messages/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.target").isEmpty());
  }

  @Test
  void failedDeliveryRollsBackAndCanResumeAfterProviderRestart() throws Exception {
    String trigger = "notification_fail_" + uuid().replace("-", "");
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
    String event =
        db.queryForObject(
            "SELECT id FROM business_event WHERE aggregate_id=? AND event_type='ORDER_CREATED'",
            String.class,
            order);
    db.execute(
        "CREATE TRIGGER "
            + trigger
            + " BEFORE INSERT ON notification FOR EACH ROW BEGIN IF NEW.employee_id='"
            + worker
            + "' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='notification test failure'; END IF;"
            + " END");
    try {
      delivery.deliverBatch(100);
      assertEquals(
          1,
          db.queryForObject(
              "SELECT retry_count FROM business_event WHERE id=?", Integer.class, event));
      assertNull(
          db.queryForMap("SELECT processed_at FROM business_event WHERE id=?", event)
              .get("processed_at"));
      assertEquals(
          0,
          db.queryForObject(
              "SELECT COUNT(*) FROM notification WHERE event_id=?", Integer.class, event));
      assertEquals(
          0,
          db.queryForObject(
              "SELECT COUNT(*) FROM event_delivery WHERE event_id=?", Integer.class, event));
    } finally {
      db.execute("DROP TRIGGER " + trigger);
    }
    db.update("UPDATE business_event SET next_attempt_at=NULL WHERE id=?", event);
    delivery.deliverBatch(100);
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM event_delivery WHERE event_id=?", Integer.class, event));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM notification WHERE event_id=? AND employee_id=?",
            Integer.class,
            event,
            worker));
  }
}
