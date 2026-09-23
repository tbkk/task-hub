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
class IdentityIT {
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
  void temporaryPasswordCannotBeChangedToItself() throws Exception {
    jdbc.update("update admin_credential set must_change_password=true where employee_id=?", id);
    String token = login();
    postJson("/api/identity/password", token,
        Map.of("currentPassword", password, "newPassword", password))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/identity/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.mustChangePassword").value(true));
    mvc.perform(get("/api/admin/employees").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void hashedSessionLogoutAndExpiry() throws Exception {
    String token = login();
    assertEquals(43, token.length());
    assertNotEquals(
        token,
        jdbc.queryForObject(
            "select token_hash from auth_session where employee_id=?", String.class, id));
    mvc.perform(get("/api/identity/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(id))
        .andExpect(
            header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    postJson("/api/identity/logout", token, Map.of()).andExpect(status().isOk());
    postJson("/api/identity/logout", token, Map.of()).andExpect(status().isOk());
    mvc.perform(get("/api/identity/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
    token = login();
    jdbc.update(
        "update auth_session set expires_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1 SECOND) where"
            + " employee_id=?",
        id);
    mvc.perform(get("/api/identity/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void disabledAccountPreservesGrantsAndRejectsExistingSession() throws Exception {
    String token = login();
    jdbc.update("update employee set enabled=false where id=?", id);
    mvc.perform(get("/api/identity/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(40301));
    assertEquals(
        7,
        jdbc.queryForObject(
            "select count(*) from platform_grant where employee_id=?", Integer.class, id));
  }

  @Test
  void temporaryPasswordOnlyAllowsIdentityAndChangingPasswordRevokesEverySession()
      throws Exception {
    jdbc.update("update admin_credential set must_change_password=true where employee_id=?", id);
    String token = login();
    mvc.perform(get("/api/admin/employees").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    String next = "New-password-" + UUID.randomUUID();
    postJson(
            "/api/identity/password",
            token,
            Map.of("currentPassword", password, "newPassword", next))
        .andExpect(status().isOk());
    mvc.perform(get("/api/identity/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
    postJson("/api/admin/auth/login", null, Map.of("username", username, "password", next))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.user.mustChangePassword").value(false));
  }

  @Test
  void employeeCrudVersionAndPhoneVerificationSeparation() throws Exception {
    String token = login();
    String phone = phone();
    var body =
        new HashMap<String, Object>(
            Map.of(
                "name",
                "新增员工",
                "phone",
                phone,
                "enabled",
                true,
                "grants",
                List.of(),
                "platformGrants",
                List.of()));
    var created = data(postJson("/api/admin/employees", token, body).andExpect(status().isOk()));
    String target = created.path("id").asText();
    assertTrue(created.path("verifiedPhone").isNull());
    jdbc.update("insert into verified_phone(employee_id,phone) values(?,?)", target, phone);
    body.put("expectedVersion", 0);
    body.put("phone", phone());
    mvc.perform(
            put("/api/admin/employees/" + target)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.verifiedPhone").value(phone))
        .andExpect(jsonPath("$.data.version").value(1));
    mvc.perform(
            put("/api/admin/employees/" + target)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
        .andExpect(status().isConflict());
  }

  @Test
  void platformPermissionRevocationTakesEffectOnExistingToken() throws Exception {
    String token = login();
    jdbc.update(
        "delete from platform_grant where employee_id=? and capability='EMPLOYEE_MANAGE'", id);
    mvc.perform(get("/api/admin/employees").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void incorrectCredentialsHaveUniformErrorAndAccountLimit() throws Exception {
    for (int n = 0; n < 5; n++)
      postJson("/api/admin/auth/login", null, Map.of("username", username, "password", "incorrect"))
          .andExpect(status().isUnauthorized());
    postJson("/api/admin/auth/login", null, Map.of("username", username, "password", password))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void passwordLeadingAndTrailingSpacesAreNotSilentlyChanged() throws Exception {
    String spaced = "  " + password + "  ";
    jdbc.update(
        "update admin_credential set password_hash=? where employee_id=?",
        new BCryptPasswordEncoder().encode(spaced),
        id);
    postJson("/api/admin/auth/login", null, Map.of("username", username, "password", spaced))
        .andExpect(status().isOk());
  }
}
