package com.taskhub.identity;

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
class AuthorizationIT {
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

  @Autowired com.taskhub.domain.identity.AuthorizationService authorization;
  @Autowired com.taskhub.domain.identity.SessionService sessions;

  @Test
  void overviewAllCannotExpandWarehouseScopeAndReportViewCannotExpandExport() throws Exception {
    String a = "a-" + UUID.randomUUID().toString().substring(0, 20),
        b = "b-" + UUID.randomUUID().toString().substring(0, 20);
    String grant = UUID.randomUUID().toString();
    jdbc.update(
        "insert into role_grant(id,employee_id,role,scope) values(?,?,'warehouse','WAREHOUSES')",
        grant,
        id);
    jdbc.update("insert into grant_warehouse(grant_id,warehouse_id) values(?,?)", grant, a);
    jdbc.update(
        "insert into role_grant(id,employee_id,role,scope) values(?,?,'overview','ALL')",
        UUID.randomUUID().toString(),
        id);
    var actor = sessions.authenticate(login()).withWorkspace("warehouse");
    assertDoesNotThrow(() -> authorization.require(actor, "ORDER_REVIEW", a));
    assertThrows(
        com.taskhub.api.ApiException.class, () -> authorization.require(actor, "ORDER_REVIEW", b));
    assertThrows(
        com.taskhub.api.ApiException.class,
        () -> authorization.require(actor.withWorkspace("dispatch"), "DISPATCH", a));
    String exportGrant =
        jdbc.queryForObject(
            "select id from platform_grant where employee_id=? and capability='REPORT_EXPORT'",
            String.class,
            id);
    jdbc.update("update platform_grant set scope='WAREHOUSES' where id=?", exportGrant);
    jdbc.update(
        "insert into platform_grant_warehouse(grant_id,warehouse_id) values(?,?)", exportGrant, a);
    assertDoesNotThrow(() -> authorization.requirePlatform(actor, "REPORT_VIEW", b));
    assertThrows(
        com.taskhub.api.ApiException.class,
        () -> authorization.requirePlatform(actor, "REPORT_EXPORT", b));
    assertDoesNotThrow(() -> authorization.requirePlatform(actor, "REPORT_EXPORT", a));
  }

  @Test
  void cannotDelegatePlatformCapabilityNotHeld() throws Exception {
    jdbc.update(
        "delete from platform_grant where employee_id=? and capability='INTEGRATION_MANAGE'", id);
    postJson(
            "/api/admin/employees",
            login(),
            Map.of(
                "name",
                "越权目标",
                "phone",
                phone(),
                "enabled",
                true,
                "grants",
                List.of(),
                "platformGrants",
                List.of(
                    Map.of(
                        "capability",
                        "INTEGRATION_MANAGE",
                        "scope",
                        "ALL",
                        "warehouseIds",
                        List.of()))))
        .andExpect(status().isForbidden());
  }

  @Test
  void credentialsResetRevokesAdminAndRequiresPasswordChange() throws Exception {
    String token = login();
    String target = UUID.randomUUID().toString(), targetUser = "it-" + target;
    jdbc.update(
        "insert into employee(id,name,phone,enabled) values(?,?,?,true)", target, "目标", phone());
    jdbc.update(
        "insert into platform_grant(id,employee_id,capability,scope)"
            + " values(?,?,'REPORT_VIEW','ALL')",
        UUID.randomUUID().toString(),
        target);
    String first = "First-" + UUID.randomUUID();
    postJson(
            "/api/admin/employees/" + target + "/credentials",
            token,
            Map.of("username", targetUser, "temporaryPassword", first))
        .andExpect(status().isOk());
    String targetToken =
        data(postJson(
                    "/api/admin/auth/login",
                    null,
                    Map.of("username", targetUser, "password", first))
                .andExpect(status().isOk()))
            .path("token")
            .asText();
    postJson(
            "/api/admin/employees/" + target + "/credentials",
            token,
            Map.of("username", targetUser, "temporaryPassword", "Second-" + UUID.randomUUID()))
        .andExpect(status().isOk());
    mvc.perform(get("/api/identity/me").header("Authorization", "Bearer " + targetToken))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void revokedSessionCannotAuthorizeSensitiveCommands() throws Exception {
    String token = login();
    var actor = sessions.authenticate(token);
    postJson("/api/identity/logout", token, Map.of()).andExpect(status().isOk());
    assertThrows(
        com.taskhub.api.ApiException.class,
        () -> authorization.requirePlatform(actor, "EMPLOYEE_MANAGE", null));
  }

  @Test
  void disablingThenReenablingDoesNotRestoreOldSessions() throws Exception {
    String token = login();
    String target = UUID.randomUUID().toString();
    jdbc.update(
        "insert into employee(id,name,phone,enabled) values(?,?,?,true)", target, "目标", phone());
    String targetToken = sessions.issue(target, "MINI").token();
    String targetPhone =
        jdbc.queryForObject("select phone from employee where id=?", String.class, target);
    var input =
        new HashMap<String, Object>(
            Map.of(
                "name",
                "目标",
                "phone",
                targetPhone,
                "enabled",
                false,
                "grants",
                List.of(),
                "platformGrants",
                List.of(),
                "expectedVersion",
                0));
    mvc.perform(
            put("/api/admin/employees/" + target)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json.writeValueAsString(input)))
        .andExpect(status().isOk());
    input.put("enabled", true);
    input.put("expectedVersion", 1);
    mvc.perform(
            put("/api/admin/employees/" + target)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json.writeValueAsString(input)))
        .andExpect(status().isOk());
    mvc.perform(get("/api/identity/me").header("Authorization", "Bearer " + targetToken))
        .andExpect(status().isUnauthorized());
  }
}
