package com.taskhub;

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
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class DeliveryFlowIT {
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
  String warehouse, stop, worker, token, vehicle;

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
    vehicle = uuid();
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
    token = sessions.issue(worker, "MINI").token();
  }

  @Autowired com.taskhub.domain.notification.BusinessEventDelivery delivery;

  // 仅身份和仓库/车辆主数据使用 SQL；业务状态均由生产 API 和模拟事件推进。
  private void grant(String role) {
    String grant = uuid();
    db.update("INSERT INTO role_grant(id,employee_id,role,scope) VALUES(?,?,?,'WAREHOUSES')", grant, worker, role);
    db.update("INSERT INTO grant_warehouse(grant_id,warehouse_id) VALUES(?,?)", grant, warehouse);
  }

  private JsonNode call(String method, String path, String bearer, String workspace,
                        Object body, String key, int expected) throws Exception {
    var request = switch (method) {
      case "GET" -> get(path);
      case "PUT" -> put(path);
      default -> post(path);
    };
    var response = mvc.perform(request.header("Authorization", "Bearer " + bearer)
        .header("X-Workspace", workspace).header("Idempotency-Key", key)
        .contentType("application/json").content(json.writeValueAsString(body)))
        .andReturn().getResponse();
    // 只输出错误响应，不输出请求或会话凭证。
    assertEquals(expected, response.getStatus(), method + " " + path + ": " + response.getContentAsString());
    return json.readTree(response.getContentAsString());
  }

  private JsonNode command(String method, String path, String bearer, String workspace, Object body) throws Exception {
    return call(method, path, bearer, workspace, body, uuid(), 200).path("data");
  }

  private JsonNode read(String path, String bearer, String workspace) throws Exception {
    return command("GET", path, bearer, workspace, Map.of());
  }

  private void event(String external, String business, String current, String next,
                     String firstComp, String firstDoor, String secondComp, String secondDoor) throws Exception {
    var body = new LinkedHashMap<String, Object>();
    body.put("eventId", uuid()); body.put("vehicleId", vehicle); body.put("dispatchId", external);
    body.put("reportedAt", Instant.now().toString()); body.put("businessStatus", business);
    body.put("currentStopId", current); body.put("nextStopId", next);
    body.put("online", true); body.put("speed", business.equals("ONWAY") ? 1 : 0);
    body.put("doors", List.of(Map.of("compartmentId", firstComp, "status", firstDoor),
        Map.of("compartmentId", secondComp, "status", secondDoor)));
    command("POST", "/api/dev/simulator/events", token, "dispatch", body);
  }

  private JsonNode scan(String bearer) throws Exception {
    return command("POST", "/api/pickup/scan", bearer, "worker", Map.of("qrText", "taskhub:vehicle:" + vehicle));
  }

  private void blockedGo(String task, int version, String code) throws Exception {
    JsonNode error = call("POST", "/api/tasks/" + task + "/go", token, "dispatch",
        Map.of("expectedVersion", version), uuid(), 422);
    JsonNode blockers = json.readTree(error.path("message").asText()).path("blockers");
    assertTrue(java.util.stream.StreamSupport.stream(blockers.spliterator(), false)
        .anyMatch(b -> code.equals(b.path("code").asText())), blockers.toString());
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM control_request WHERE task_id=? AND type='GO'", Integer.class, task));
  }

  @Test
  void twoReceiversCompleteDeliveryThroughProductionApisAndSimulatorEvents() throws Exception {
    grant("warehouse"); grant("dispatch");
    for (String capability : List.of("INTEGRATION_MANAGE", "REPORT_VIEW"))
      db.update("INSERT INTO platform_grant(id,employee_id,capability,scope) VALUES(?,?,?,'ALL')", uuid(), worker, capability);
    String adminToken = sessions.issue(worker, "ADMIN").token();
    String receiver1 = employee(), receiver2 = employee();
    String receiverToken1 = sessions.issue(receiver1, "MINI").token();
    String receiverToken2 = sessions.issue(receiver2, "MINI").token();
    for (String receiver : List.of(receiver1, receiver2))
      db.update("INSERT INTO verified_phone(employee_id,phone) SELECT id,phone FROM employee WHERE id=?", receiver);
    String comp1 = uuid(), comp2 = uuid(), loadingStop = uuid();
    for (String comp : List.of(comp1, comp2))
      db.update("INSERT INTO compartment(id,vehicle_id,hardware_no,label,enabled) VALUES(?,?,?,?,true)", comp, vehicle, comp, "验收格口");
    db.update("INSERT INTO stop(id,name,external_stop_id,enabled) VALUES(?,?,?,true)", loadingStop, "装货站", loadingStop);
    db.update("INSERT INTO stop_warehouse(stop_id,warehouse_id) VALUES(?,?)", loadingStop, warehouse);
    db.update("INSERT INTO vehicle_stop(vehicle_id,stop_id) VALUES(?,?)", vehicle, loadingStop);
    db.update("INSERT INTO warehouse_loading_stop(warehouse_id,stop_id) VALUES(?,?)", warehouse, loadingStop);
    for (String operation : List.of("DISPATCH", "OPEN", "GO"))
      command("PUT", "/api/dev/simulator/scenario", token, "dispatch", Map.of("operation", operation, "outcome", "ACCEPTED"));

    String date = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1).toString();
    String slot = read("/api/catalog/slots?warehouseId=" + warehouse + "&stopId=" + stop + "&date=" + date, token, "worker").get(0).path("id").asText();
    List<String> orders = new ArrayList<>();
    for (String receiver : List.of(receiver1, receiver2)) {
      String phone = db.queryForObject("SELECT phone FROM employee WHERE id=?", String.class, receiver);
      JsonNode order = command("POST", "/api/orders", token, "worker", Map.of("warehouseId", warehouse,
          "stopId", stop, "slotId", slot, "description", "全链路验收物料", "size", "小件",
          "receiverName", "验收接收人", "receiverPhone", phone, "remark", ""));
      assertEquals("PENDING", order.path("status").asText());
      String id = order.path("id").asText(); orders.add(id);
      JsonNode accepted = command("POST", "/api/orders/" + id + "/review", token, "warehouse",
          Map.of("expectedVersion", order.path("version").asInt(), "decision", "ACCEPT"));
      assertEquals("ACCEPTED", accepted.path("status").asText());
    }
    JsonNode batch = command("POST", "/api/batches", token, "warehouse", Map.of("orderIds", orders));
    String batchId = batch.path("id").asText();
    batch = command("PUT", "/api/batches/" + batchId + "/loading", token, "warehouse",
        Map.of("expectedVersion", batch.path("version").asInt(), "vehicleId", vehicle, "assignments",
            List.of(Map.of("orderId", orders.get(0), "compartmentIds", List.of(comp1)),
                Map.of("orderId", orders.get(1), "compartmentIds", List.of(comp2)))));
    batch = command("POST", "/api/batches/" + batchId + "/loading/confirm", token, "warehouse",
        Map.of("expectedVersion", batch.path("version").asInt(), "capacityConfirmed", true));
    event(null, "IDLE", loadingStop, stop, comp1, "CLOSED", comp2, "CLOSED");
    String dispatchKey = uuid();
    Object dispatchBody = Map.of("expectedVersion", batch.path("version").asInt());
    JsonNode dispatched = call("POST", "/api/batches/" + batchId + "/dispatch", token, "dispatch", dispatchBody, dispatchKey, 200).path("data");
    assertEquals("ACCEPTED", dispatched.path("status").asText());
    assertEquals(dispatched.path("id"), call("POST", "/api/batches/" + batchId + "/dispatch", token, "dispatch", dispatchBody, dispatchKey, 200).path("data").path("id"));
    String external = dispatched.path("dispatchId").asText();
    event(external, "ONWAY", loadingStop, stop, comp1, "CLOSED", comp2, "CLOSED");
    assertEquals("IN_TRANSIT", read("/api/orders/" + orders.get(0), token, "worker").path("status").asText());
    event(external, "ATSTOP", stop, loadingStop, comp1, "CLOSED", comp2, "CLOSED");
    JsonNode firstScan = scan(receiverToken1), secondScan = scan(receiverToken2);
    assertEquals(1, firstScan.path("orders").size()); assertEquals(1, secondScan.path("orders").size());
    assertEquals(orders.get(0), firstScan.path("orders").get(0).path("id").asText());
    assertEquals(orders.get(1), secondScan.path("orders").get(0).path("id").asText());
    assertTrue(firstScan.path("canContinue").asBoolean());
    String task = firstScan.path("taskId").asText();
    int version = read("/api/batches/" + batchId, token, "warehouse").path("version").asInt();
    blockedGo(task, version, "ORDER_NOT_PICKED");
    call("POST", "/api/orders/" + orders.get(1) + "/pickup/confirm", receiverToken1, "worker",
        Map.of("expectedVersion", secondScan.path("orders").get(0).path("version").asInt()), uuid(), 404);
    for (int i = 0; i < 2; i++) {
      String bearer = i == 0 ? receiverToken1 : receiverToken2;
      JsonNode own = scan(bearer).path("orders").get(0);
      Object pickupBody = Map.of("expectedVersion", own.path("version").asInt());
      String openKey = uuid();
      JsonNode open = call("POST", "/api/orders/" + orders.get(i) + "/pickup/open", bearer, "worker", pickupBody, openKey, 200).path("data");
      assertEquals("ACCEPTED", open.path("status").asText());
      assertEquals(List.of(i == 0 ? comp1 : comp2), json.convertValue(open.path("compartmentIds"), List.class));
      assertEquals(open.path("id"), call("POST", "/api/orders/" + orders.get(i) + "/pickup/open", bearer, "worker", pickupBody, openKey, 200).path("data").path("id"));
      event(external, "ATSTOP", stop, loadingStop, comp1, "OPEN", comp2, i == 0 ? "CLOSED" : "OPEN");
      String confirmKey = uuid();
      JsonNode completed = call("POST", "/api/orders/" + orders.get(i) + "/pickup/confirm", bearer, "worker", pickupBody, confirmKey, 200).path("data");
      assertEquals("COMPLETED", completed.path("status").asText());
      assertEquals(completed.path("id"), call("POST", "/api/orders/" + orders.get(i) + "/pickup/confirm", bearer, "worker", pickupBody, confirmKey, 200).path("data").path("id"));
      if (i == 0) blockedGo(task, version, "ORDER_NOT_PICKED");
    }
    blockedGo(task, version, "DOOR_OPEN");
    JsonNode ticket = command("POST", "/api/tickets", receiverToken1, "worker", Map.of("orderId", orders.get(0), "description", "出发前核对货物"));
    event(external, "ATSTOP", stop, loadingStop, comp1, "CLOSED", comp2, "CLOSED");
    blockedGo(task, version, "ACTIVE_TICKET");
    String ticketId = ticket.path("id").asText();
    ticket = command("POST", "/api/tickets/" + ticketId + "/accept", token, "warehouse", Map.of("expectedVersion", ticket.path("version").asInt(), "text", "仓库正在核实"));
    ticket = command("POST", "/api/tickets/" + ticketId + "/close", token, "warehouse", Map.of("expectedVersion", ticket.path("version").asInt(), "result", "货物核对正常"));
    assertEquals("CLOSED", ticket.path("state").asText());
    String goKey = uuid();
    JsonNode go = call("POST", "/api/tasks/" + task + "/go", token, "dispatch", Map.of("expectedVersion", version), goKey, 200).path("data");
    assertEquals("ACCEPTED", go.path("status").asText());
    assertEquals(go.path("id"), call("POST", "/api/tasks/" + task + "/go", token, "dispatch", Map.of("expectedVersion", version), goKey, 200).path("data").path("id"));
    assertEquals("AT_STOP", read("/api/tasks/" + task, token, "dispatch").path("state").asText());
    event(external, "ONWAY", stop, loadingStop, comp1, "CLOSED", comp2, "CLOSED");
    assertEquals("RUNNING", read("/api/tasks/" + task, token, "dispatch").path("state").asText());
    event(external, "FINISHED", loadingStop, null, comp1, "CLOSED", comp2, "CLOSED");
    assertEquals("FINISHED", read("/api/tasks/" + task, token, "dispatch").path("state").asText());
    assertEquals("CLOSED", read("/api/batches/" + batchId, token, "warehouse").path("status").asText());
    assertEquals(4, db.queryForObject("SELECT COUNT(*) FROM simulator_command WHERE request_id IN "
        + "(SELECT id FROM dispatch_request WHERE vehicle_id=? UNION ALL SELECT id FROM control_request WHERE vehicle_id=?)",
        Integer.class, vehicle, vehicle));
    assertEquals(receiver1, db.queryForObject("SELECT pickup_employee_id FROM delivery_order WHERE id=?", String.class, orders.get(0)));
    assertEquals(receiver2, db.queryForObject("SELECT pickup_employee_id FROM delivery_order WHERE id=?", String.class, orders.get(1)));

    // 同步执行真实 outbox 投递器，避免以调度等待代替消息持久化验证。
    for (int i = 0; i < 100 && delivery.deliverBatch(100) > 0; i++) { }
    JsonNode messages = read("/api/messages?pageSize=100", receiverToken1, "worker");
    JsonNode ticketMessage = null;
    for (JsonNode message : messages.path("items"))
      if ("异常工单已关闭".equals(message.path("title").asText()) && ticketId.equals(message.path("target").path("id").asText())) ticketMessage = message;
    assertNotNull(ticketMessage, "应投递工单关闭消息");
    String messageId = ticketMessage.path("id").asText();
    call("GET", "/api/messages/" + messageId, receiverToken2, "worker", Map.of(), uuid(), 404);
    command("PUT", "/api/messages/" + messageId + "/read", receiverToken1, "worker", Map.of());
    JsonNode readAt = read("/api/messages/" + messageId, receiverToken1, "worker").path("readAt");
    assertTrue(readAt.isTextual() && !readAt.asText().isBlank(), "已读时间必须存在且非空");
    assertDoesNotThrow(() -> Instant.parse(readAt.asText()));
    String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
    String filter = "?from=" + today + "&to=" + today + "&warehouseId=" + warehouse;
    JsonNode summary = read("/api/admin/reports/summary" + filter, adminToken, "admin");
    assertEquals(2, summary.path("orders").asInt()); assertEquals(1, summary.path("batches").asInt()); assertEquals(1, summary.path("tasks").asInt());
    JsonNode details = read("/api/admin/reports/details" + filter + "&status=COMPLETED", adminToken, "admin");
    assertEquals(2, details.path("total").asInt());
    for (JsonNode item : details.path("items")) assertTrue(orders.contains(item.path("id").asText()));
  }
}
