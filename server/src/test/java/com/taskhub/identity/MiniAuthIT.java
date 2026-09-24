package com.taskhub.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.domain.identity.TokenCodec;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.sql.init.mode=never"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class MiniAuthIT {
  static final String WECHAT_CODE = UUID.randomUUID().toString();
  static final String OPENID = UUID.randomUUID().toString();

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    String url = System.getenv("MYSQL_TEST_URL");
    if (url == null || !url.matches("jdbc:mysql://[^/]+/task_hub_[a-zA-Z0-9_]*_test(?:\\?.*)?"))
      throw new IllegalStateException("显式专用 MYSQL_TEST_URL 必填");
    r.add("taskhub.auth.mock-enabled", () -> "true");
    r.add("taskhub.auth.mock-wechat-code", () -> WECHAT_CODE);
    r.add("taskhub.auth.mock-wechat-openid", () -> OPENID);
    r.add("spring.datasource.url", () -> url + (url.contains("?") ? "&" : "?") + "connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
    r.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    r.add("spring.datasource.username", () -> System.getenv().getOrDefault("MYSQL_TEST_USER", "taskhub"));
    r.add("spring.datasource.password", () -> System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", ""));
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  String id, username;
  final String password = "Test-only-" + UUID.randomUUID();

  @BeforeEach
  void fixture() {
    jdbc.update("delete from auth_session");
    jdbc.update("delete from wechat_binding_challenge");
    jdbc.update("delete from wechat_binding");
    id = UUID.randomUUID().toString();
    username = "it-" + id;
    jdbc.update("INSERT INTO employee(id,name,phone,enabled) VALUES(?,?,?,true)", id, "测试员工", phone());
    jdbc.update("INSERT INTO admin_credential(employee_id,username,password_hash,must_change_password) VALUES(?,?,?,false)", id, username, new BCryptPasswordEncoder().encode(password));
  }

  String phone() { return "139" + String.format("%08d", Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100000000)); }

  ResultActions postJson(String path, Object body) throws Exception {
    return mvc.perform(post(path).contentType("application/json").content(json.writeValueAsString(body)));
  }

  JsonNode data(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsString()).path("data");
  }

  String bindingToken() throws Exception {
    return data(postJson("/api/mini/auth/wechat", java.util.Map.of("code", WECHAT_CODE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CREDENTIALS_REQUIRED"))).path("bindingToken").asText();
  }

  @Test
  void credentialsBindWechatAndConsumesTokenOnce() throws Exception {
    String token = bindingToken();
    postJson("/api/mini/auth/bind", java.util.Map.of("bindingToken", token, "username", username, "password", password))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.user.id").value(id));
    postJson("/api/mini/auth/bind", java.util.Map.of("bindingToken", token, "username", username, "password", password))
        .andExpect(status().isUnauthorized());
    postJson("/api/mini/auth/wechat", java.util.Map.of("code", WECHAT_CODE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("AUTHENTICATED"));
  }

  @Test
  void wrongPasswordCannotBind() throws Exception {
    postJson("/api/mini/auth/bind", java.util.Map.of("bindingToken", bindingToken(), "username", username, "password", "wrong-password-long"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void temporaryPasswordMustBeChangedBeforeWechatBind() throws Exception {
    jdbc.update("update admin_credential set must_change_password=true where employee_id=?", id);
    postJson("/api/mini/auth/bind", java.util.Map.of("bindingToken", bindingToken(), "username", username, "password", password))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("请先使用账号密码登录并修改临时密码"));
  }

  @Test
  void expiredBindingTokenCannotBind() throws Exception {
    String token = UUID.randomUUID().toString().replace("-", "") + "123456789012345678901234567890";
    token = token.substring(0, 43);
    jdbc.update("insert into wechat_binding_challenge(token_hash,app_id,openid,expires_at) values(?,?,?,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1 SECOND))", TokenCodec.hash(token), "expired", UUID.randomUUID().toString());
    postJson("/api/mini/auth/bind", java.util.Map.of("bindingToken", token, "username", username, "password", password))
        .andExpect(status().isUnauthorized());
  }
}
