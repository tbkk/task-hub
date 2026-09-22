package com.taskhub.masterdata;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.sql.init.mode=never"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class MasterdataIT {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    String url = System.getenv("MYSQL_TEST_URL");
    if (url == null || !url.matches("jdbc:mysql://[^/]+/task_hub_[a-zA-Z0-9_]*_test(?:\\?.*)?"))
      throw new IllegalStateException("显式专用 MYSQL_TEST_URL 必填");
    r.add("spring.datasource.url", () -> url + (url.contains("?") ? "&" : "?") + "connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
    r.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    r.add(
        "spring.datasource.username",
        () -> System.getenv().getOrDefault("MYSQL_TEST_USER", "taskhub"));
    r.add(
        "spring.datasource.password",
        () -> System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", ""));
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  String id, username;
  final String ip =
      "2001:db8:"
          + UUID.randomUUID()
              .toString()
              .replace("-", "")
              .replaceAll("(.{4})", "$1:")
              .replaceAll(":$", "");
  final String password = "Test-only-" + UUID.randomUUID();

  @BeforeEach
  void fixture() {
    id = UUID.randomUUID().toString();
    username = "it-" + id;
    jdbc.update(
        "INSERT INTO employee(id,name,phone,enabled) VALUES(?,?,?,true)", id, "测试员工", phone());
    jdbc.update(
        "INSERT INTO admin_credential(employee_id,username,password_hash,must_change_password)"
            + " VALUES(?,?,?,false)",
        id,
        username,
        new BCryptPasswordEncoder().encode(password));
    for (String capability :
        List.of(
            "EMPLOYEE_MANAGE",
            "MASTERDATA_MANAGE",
            "RULE_MANAGE",
            "INTEGRATION_MANAGE",
            "REPORT_VIEW",
            "REPORT_EXPORT",
            "AUDIT_VIEW"))
      jdbc.update(
          "INSERT INTO platform_grant(id,employee_id,capability,scope) VALUES(?,?,?,'ALL')",
          UUID.randomUUID().toString(),
          id,
          capability);
  }

  String phone() {
    return "139"
        + String.format(
            "%08d", Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100000000));
  }

  ResultActions postJson(String path, String token, Object body) throws Exception {
    var req = post(path).contentType("application/json").content(json.writeValueAsString(body));
    req.with(
        request -> {
          request.setRemoteAddr(ip);
          return request;
        });
    if (token != null) req.header("Authorization", "Bearer " + token);
    return mvc.perform(req);
  }

  JsonNode data(ResultActions r) throws Exception {
    return json.readTree(r.andReturn().getResponse().getContentAsString()).path("data");
  }

  String login() throws Exception {
    return data(postJson(
                "/api/admin/auth/login", null, Map.of("username", username, "password", password))
            .andExpect(status().isOk()))
        .path("token")
        .asText();
  }

  @Test
  void warehousesPersistAndRejectDuplicateAndStaleVersion() throws Exception {
    String token = login();
    String code = UUID.randomUUID().toString();
    var body = new HashMap<String, Object>(Map.of("name", "测试仓", "code", code, "enabled", true));
    var w = data(postJson("/api/admin/warehouses", token, body).andExpect(status().isOk()));
    assertEquals(0, w.path("version").asInt());
    postJson("/api/admin/warehouses", token, body).andExpect(status().isConflict());
    body.put("expectedVersion", 0);
    update("/api/admin/warehouses/" + w.path("id").asText(), token, body)
        .andExpect(status().isOk());
    update("/api/admin/warehouses/" + w.path("id").asText(), token, body)
        .andExpect(status().isConflict());
  }

  ResultActions update(String path, String token, Object body) throws Exception {
    return mvc.perform(
        put(path)
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .content(json.writeValueAsString(body)));
  }

  String warehouse(String token) throws Exception {
    return data(postJson(
                "/api/admin/warehouses",
                token,
                Map.of("name", "仓库", "code", UUID.randomUUID().toString(), "enabled", true))
            .andExpect(status().isOk()))
        .path("id")
        .asText();
  }

  @Test
  void scopedListsDoNotBorrowRuleAllAndWorkerUsesHomeWarehouse() throws Exception {
    String token = login(), a = warehouse(token), b = warehouse(token);
    String grant =
        jdbc.queryForObject(
            "select id from platform_grant where employee_id=? and capability='MASTERDATA_MANAGE'",
            String.class,
            id);
    jdbc.update("update platform_grant set scope='WAREHOUSES' where id=?", grant);
    jdbc.update(
        "insert into platform_grant_warehouse(grant_id,warehouse_id) values(?,?)", grant, a);
    mvc.perform(get("/api/admin/warehouses").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].id").value(a));
    mvc.perform(get("/api/admin/warehouses/" + b).header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    jdbc.update(
        "insert into role_grant(id,employee_id,role,scope) values(?,?,'worker','SELF')",
        UUID.randomUUID().toString(),
        id);
    mvc.perform(
            get("/api/catalog/warehouses")
                .header("Authorization", "Bearer " + token)
                .header("X-Workspace", "worker"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0));
    jdbc.update("update employee set home_warehouse_id=? where id=?", a, id);
    mvc.perform(
            get("/api/catalog/warehouses")
                .header("Authorization", "Bearer " + token)
                .header("X-Workspace", "worker"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(a));
    mvc.perform(
            get("/api/catalog/stops")
                .param("warehouseId", b)
                .header("Authorization", "Bearer " + token)
                .header("X-Workspace", "worker"))
        .andExpect(status().isForbidden());
  }

  @Test
  void externalBindingsReachabilityAndHardwareAreRestricted() throws Exception {
    String token = login(), w = warehouse(token), external = UUID.randomUUID().toString();
    var stopBody =
        Map.of(
            "name", "站点", "externalStopId", external, "warehouseIds", List.of(w), "enabled", true);
    postJson("/api/admin/stops", token, stopBody).andExpect(status().isUnprocessableEntity());
    jdbc.update(
        "insert into external_catalog_resource(resource,id,name) values('stops',?,?)",
        external,
        "模拟站点");
    String stop =
        data(postJson("/api/admin/stops", token, stopBody).andExpect(status().isOk()))
            .path("id")
            .asText();
    jdbc.update(
        "insert into role_grant(id,employee_id,role,scope) values(?,?,'warehouse','ALL')",
        UUID.randomUUID().toString(),
        id);
    mvc.perform(
            get("/api/catalog/stops")
                .param("warehouseId", w)
                .header("Authorization", "Bearer " + token)
                .header("X-Workspace", "warehouse"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0));
    jdbc.update(
        "insert into external_catalog_resource(resource,id,name) values('vehicles',?,?)",
        external,
        external);
    var vehicleBody =
        Map.of(
            "name",
            "车辆",
            "externalVehicleName",
            external,
            "warehouseId",
            w,
            "boundStopIds",
            List.of(stop),
            "enabled",
            true);
    String vehicle =
        data(postJson("/api/admin/vehicles", token, vehicleBody).andExpect(status().isOk()))
            .path("id")
            .asText();
    postJson("/api/admin/vehicles", token, vehicleBody).andExpect(status().isConflict());
    mvc.perform(
            get("/api/catalog/stops")
                .param("warehouseId", w)
                .header("Authorization", "Bearer " + token)
                .header("X-Workspace", "warehouse"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(stop));
    jdbc.update(
        "insert into external_catalog_compartment(vehicle_name,hardware_no) values(?,'1')",
        external);
    String compartment =
        data(postJson(
                    "/api/admin/vehicles/" + vehicle + "/compartments/sync",
                    token,
                    Map.of("expectedVersion", 0))
                .andExpect(status().isOk()))
            .path(0)
            .path("id")
            .asText();
    postJson(
            "/api/admin/vehicles/" + vehicle + "/compartments/sync",
            token,
            Map.of("expectedVersion", 0))
        .andExpect(status().isConflict());
    update(
            "/api/admin/vehicles/" + vehicle + "/compartments/" + compartment,
            token,
            Map.of("expectedVersion", 0, "label", "首格", "enabled", true))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.hardwareNo").value("1"));
    update(
            "/api/admin/vehicles/" + vehicle + "/compartments/" + UUID.randomUUID(),
            token,
            Map.of("expectedVersion", 0, "label", "首格", "enabled", true))
        .andExpect(status().isNotFound());
    String other = UUID.randomUUID().toString();
    jdbc.update(
        "insert into vehicle(id,name,external_vehicle_name,warehouse_id,enabled) values(?,?,?,"
            + " ?,true)",
        other,
        "其他车",
        other,
        w);
    update(
            "/api/admin/vehicles/" + other + "/compartments/" + compartment,
            token,
            Map.of("expectedVersion", 1, "label", "越车修改", "enabled", true))
        .andExpect(status().isNotFound());
    assertThrows(
        org.springframework.dao.DuplicateKeyException.class,
        () ->
            jdbc.update(
                "insert into compartment(id,vehicle_id,hardware_no,label,enabled)"
                    + " values(?,?,'1','重复',true)",
                UUID.randomUUID().toString(),
                vehicle));
    var disabled = new HashMap<String, Object>(stopBody);
    disabled.put("enabled", false);
    disabled.put("expectedVersion", 0);
    update("/api/admin/stops/" + stop, token, disabled).andExpect(status().isOk());
    mvc.perform(
            get("/api/catalog/stops")
                .param("warehouseId", w)
                .header("Authorization", "Bearer " + token)
                .header("X-Workspace", "warehouse"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0));
  }

  Map<String, Object> rule() {
    var r = new HashMap<String, Object>();
    r.put("expectedVersion", 0);
    r.put("businessHours", List.of(Map.of("weekday", 1, "start", "08:00", "end", "17:00")));
    for (String k :
        List.of(
            "slotCapacity",
            "bookingDays",
            "descriptionMaxLength",
            "sizeMaxLength",
            "remarkMaxLength",
            "telemetryMaxAgeSeconds",
            "doorMaxAgeSeconds",
            "pickupTimeoutMinutes")) r.put(k, 10);
    r.put("sharedCompartmentEnabled", false);
    return r;
  }

  @Test
  void rulesRejectOverlappingHoursGranularityLimitsAndStaleUpdates() throws Exception {
    String token = login(), w = warehouse(token);
    var r = rule();
    r.put("businessHours", List.of(Map.of("weekday", 1, "start", "08:15", "end", "17:00")));
    update("/api/admin/rules/" + w, token, r).andExpect(status().isBadRequest());
    r = rule();
    r.put(
        "businessHours",
        List.of(
            Map.of("weekday", 1, "start", "08:00", "end", "17:00"),
            Map.of("weekday", 1, "start", "16:00", "end", "18:00")));
    update("/api/admin/rules/" + w, token, r).andExpect(status().isBadRequest());
    for (String k :
        List.of(
            "slotCapacity",
            "bookingDays",
            "descriptionMaxLength",
            "sizeMaxLength",
            "remarkMaxLength",
            "telemetryMaxAgeSeconds",
            "doorMaxAgeSeconds",
            "pickupTimeoutMinutes")) {
      r = rule();
      r.put(k, 0);
      update("/api/admin/rules/" + w, token, r).andExpect(status().isBadRequest());
    }
    r = rule();
    r.put("sharedCompartmentEnabled", true);
    update("/api/admin/rules/" + w, token, r).andExpect(status().isBadRequest());
    update("/api/admin/rules/" + w, token, rule()).andExpect(status().isOk());
    update("/api/admin/rules/" + w, token, rule()).andExpect(status().isConflict());
  }

  @Test
  void ruleCapabilityAndSelectedWorkspaceHaveIndependentScopes() throws Exception {
    String token = login(), a = warehouse(token), b = warehouse(token);
    String g =
        jdbc.queryForObject(
            "select id from platform_grant where employee_id=? and capability='RULE_MANAGE'",
            String.class,
            id);
    jdbc.update("update platform_grant set scope='WAREHOUSES' where id=?", g);
    jdbc.update("insert into platform_grant_warehouse(grant_id,warehouse_id) values(?,?)", g, a);
    update("/api/admin/rules/" + b, token, rule()).andExpect(status().isForbidden());
    update("/api/admin/rules/" + a, token, rule()).andExpect(status().isOk());
    String role = UUID.randomUUID().toString();
    jdbc.update(
        "insert into role_grant(id,employee_id,role,scope) values(?,?,'dispatch','WAREHOUSES')",
        role,
        id);
    jdbc.update("insert into grant_warehouse(grant_id,warehouse_id) values(?,?)", role, a);
    jdbc.update(
        "insert into role_grant(id,employee_id,role,scope) values(?,?,'overview','ALL')",
        UUID.randomUUID().toString(),
        id);
    mvc.perform(
            get("/api/catalog/warehouses")
                .header("Authorization", "Bearer " + token)
                .header("X-Workspace", "dispatch"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value(a));
    mvc.perform(
            get("/api/catalog/rules")
                .param("warehouseId", b)
                .header("Authorization", "Bearer " + token)
                .header("X-Workspace", "dispatch"))
        .andExpect(status().isForbidden());
  }

  @Test
  void removedHardwareStaysAsDisabledHistoryAndCannotBeReenabled() throws Exception {
    String token = login(), w = warehouse(token), ext = UUID.randomUUID().toString();
    jdbc.update(
        "insert into external_catalog_resource(resource,id,name) values('vehicles',?,?)", ext, ext);
    String v =
        data(postJson(
                    "/api/admin/vehicles",
                    token,
                    Map.of(
                        "name",
                        "测试车",
                        "externalVehicleName",
                        ext,
                        "warehouseId",
                        w,
                        "boundStopIds",
                        List.of(),
                        "enabled",
                        true))
                .andExpect(status().isOk()))
            .path("id")
            .asText();
    jdbc.update(
        "insert into external_catalog_compartment(vehicle_name,hardware_no) values(?,'h1')", ext);
    String c =
        data(postJson(
                    "/api/admin/vehicles/" + v + "/compartments/sync",
                    token,
                    Map.of("expectedVersion", 0))
                .andExpect(status().isOk()))
            .path(0)
            .path("id")
            .asText();
    jdbc.update("delete from external_catalog_compartment where vehicle_name=?", ext);
    postJson("/api/admin/vehicles/" + v + "/compartments/sync", token, Map.of("expectedVersion", 1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].enabled").value(false))
        .andExpect(jsonPath("$.data[0].id").value(c));
    update(
            "/api/admin/vehicles/" + v + "/compartments/" + c,
            token,
            Map.of("expectedVersion", 1, "label", "格口", "enabled", true))
        .andExpect(status().isUnprocessableEntity());
    assertEquals(
        1, jdbc.queryForObject("select count(*) from compartment where id=?", Integer.class, c));
  }

  @Test
  void productionCatalogNeverReadsSimulator() {
    var env = new org.springframework.mock.env.MockEnvironment();
    env.setActiveProfiles("production", "test");
    var catalog = new com.taskhub.domain.masterdata.ExternalCatalog(null, env);
    var error = assertThrows(com.taskhub.api.ApiException.class, () -> catalog.list("vehicles"));
    assertEquals(503, error.status());
  }

  @Test
  void localStopMaintenanceDoesNotRequireExternalResourceAvailability() throws Exception {
    String token = login(), w = warehouse(token), ext = UUID.randomUUID().toString();
    jdbc.update(
        "insert into external_catalog_resource(resource,id,name) values('stops',?,?)", ext, ext);
    var body =
        new HashMap<String, Object>(
            Map.of(
                "name", "站点", "externalStopId", ext, "warehouseIds", List.of(w), "enabled", true));
    String stop =
        data(postJson("/api/admin/stops", token, body).andExpect(status().isOk()))
            .path("id")
            .asText();
    jdbc.update("delete from external_catalog_resource where resource='stops' and id=?", ext);
    body.put("expectedVersion", 0);
    body.put("enabled", false);
    update("/api/admin/stops/" + stop, token, body).andExpect(status().isOk());
  }

  @Test
  void concurrentWarehouseWritesAcceptExactlyOneVersion() throws Exception {
    String token = login(), w = warehouse(token);
    var body =
        Map.of(
            "name",
            "并发更新",
            "code",
            UUID.randomUUID().toString(),
            "enabled",
            true,
            "expectedVersion",
            0);
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    var start = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.Callable<Integer> write =
        () -> {
          start.await();
          return update("/api/admin/warehouses/" + w, token, body)
              .andReturn()
              .getResponse()
              .getStatus();
        };
    try {
      var a = executor.submit(write);
      var b = executor.submit(write);
      start.countDown();
      var statuses =
          new ArrayList<>(
              List.of(
                  a.get(10, java.util.concurrent.TimeUnit.SECONDS),
                  b.get(10, java.util.concurrent.TimeUnit.SECONDS)));
      Collections.sort(statuses);
      assertEquals(List.of(200, 409), statuses);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void ruleWarehousePickerUsesOnlyRuleScope() throws Exception {
    String token = login(), a = warehouse(token), b = warehouse(token);
    String grant =
        jdbc.queryForObject(
            "select id from platform_grant where employee_id=? and capability='RULE_MANAGE'",
            String.class,
            id);
    jdbc.update("update platform_grant set scope='WAREHOUSES' where id=?", grant);
    jdbc.update(
        "insert into platform_grant_warehouse(grant_id,warehouse_id) values(?,?)", grant, a);
    mvc.perform(get("/api/admin/rules/warehouses").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].id").value(a));
  }
}
