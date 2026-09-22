package com.taskhub.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.domain.identity.*;
import com.taskhub.domain.integration.*;
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
class MiniAuthIT {
  static final String MOCK_CODE =
      String.format("%06d", new java.security.SecureRandom().nextInt(1000000));
  static final String HMAC_SECRET = UUID.randomUUID().toString();
  static final String WECHAT_CODE = UUID.randomUUID().toString(),
      OPENID = UUID.randomUUID().toString();

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    String url = System.getenv("MYSQL_TEST_URL");
    if (url == null || !url.matches("jdbc:mysql://[^/]+/task_hub_[a-zA-Z0-9_]*_test(?:\\?.*)?"))
      throw new IllegalStateException("显式专用 MYSQL_TEST_URL 必填");
    r.add("taskhub.auth.mock-enabled", () -> "true");
    r.add("taskhub.auth.mock-sms-code", () -> MOCK_CODE);
    r.add("taskhub.auth.sms-hmac-secret", () -> HMAC_SECRET);
    r.add("taskhub.auth.mock-wechat-code", () -> WECHAT_CODE);
    r.add("taskhub.auth.mock-wechat-openid", () -> OPENID);
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
  @Autowired javax.sql.DataSource dataSource;
  @Autowired org.springframework.context.ApplicationContext context;
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

  String admittedPhone() {
    return jdbc.queryForObject("select phone from employee where id=?", String.class, id);
  }

  String challenge(String phone) throws Exception {
    return data(postJson("/api/mini/auth/sms", null, Map.of("phone", phone, "purpose", "LOGIN"))
            .andExpect(status().isOk()))
        .path("challengeId")
        .asText();
  }

  ResultActions verify(String challenge, String phone, String code) throws Exception {
    return postJson(
        "/api/mini/auth/verify",
        null,
        Map.of("challengeId", challenge, "phone", phone, "code", code));
  }

  @Test
  void verificationWaitsForPhoneChangeAndRejectsOldAdmission() throws Exception {
    verificationAfterEmployeeUpdate(false, false);
  }

  @Test
  void verificationWaitsForDisableAndCannotCreateSession() throws Exception {
    verificationAfterEmployeeUpdate(true, false);
  }

  @Test
  void bindingWaitsForPhoneChangeAndCannotBindOldAdmission() throws Exception {
    verificationAfterEmployeeUpdate(false, true);
  }

  void verificationAfterEmployeeUpdate(boolean disable, boolean bind) throws Exception {
    verificationAfterEmployeeUpdate(disable, bind, false);
  }

  @Test
  void existingWechatExchangeWaitsForDisableAndRejectsNewSession() throws Exception {
    verificationAfterEmployeeUpdate(true, false, true);
  }

  void verificationAfterEmployeeUpdate(boolean disable, boolean bind, boolean exchange) throws Exception {
    String phone = admittedPhone();
    String bindingToken = com.taskhub.domain.identity.TokenCodec.random();
    String openid = UUID.randomUUID().toString();
    if (exchange) jdbc.update("insert into wechat_binding(id,employee_id,app_id,openid) values(?,?,?,?)",
        UUID.randomUUID().toString(), id, "concurrency-test", openid);
    var exchangeService = new MiniAuthService(
        context.getBean(MiniAuthMapper.class), context.getBean(EmployeeMapper.class),
        context.getBean(SessionService.class), code -> new WechatProvider.WechatIdentity("concurrency-test", openid),
        context.getBean(SmsProvider.class), context.getBean(AuthRateLimiter.class),
        context.getBean(org.springframework.transaction.PlatformTransactionManager.class),
        HMAC_SECRET, 300, 60, 10, 30, 5);
    if (bind) jdbc.update("insert into wechat_binding_challenge(token_hash,app_id,openid,expires_at) values(?,?,?,DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 10 MINUTE))",
        com.taskhub.domain.identity.TokenCodec.hash(bindingToken), "concurrency-test", openid);
    var input = new HashMap<String,Object>(Map.of("phone", phone, "purpose", bind ? "BIND" : "LOGIN"));
    if (bind) input.put("bindingToken", bindingToken);
    String challenge = data(postJson("/api/mini/auth/sms", null, input).andExpect(status().isOk())).path("challengeId").asText();
    var verifyInput = new HashMap<String,Object>(Map.of("phone", phone, "challengeId", challenge, "code", MOCK_CODE));
    if (bind) verifyInput.put("bindingToken", bindingToken);
    var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
    try (var admin = dataSource.getConnection()) {
      admin.setAutoCommit(false);
      long connectionId;
      try (var statement = admin.createStatement(); var result = statement.executeQuery("select connection_id()")) {
        result.next(); connectionId = result.getLong(1);
      }
      try (var lock = admin.prepareStatement("select id from employee where id=? for update")) {
        lock.setString(1, id);
        try (var result = lock.executeQuery()) { assertTrue(result.next()); }
      }
      var started = new java.util.concurrent.CountDownLatch(1);
      var verification = pool.submit(() -> {
        started.countDown();
        if (exchange) {
          try { exchangeService.exchange("test-exchange"); return 200; }
          catch (com.taskhub.api.ApiException error) { return error.status(); }
        }
        return postJson("/api/mini/auth/verify", null, verifyInput).andReturn().getResponse().getStatus();
      });
      assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS));
      // 确认第二连接实际在等待本事务的员工锁，禁止靠固定延时猜测交错。
      long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
      boolean waiting = false;
      while (System.nanoTime() < deadline && !verification.isDone()) {
        waiting = jdbc.queryForObject("select count(*) from performance_schema.data_lock_waits w join performance_schema.threads t on t.THREAD_ID=w.BLOCKING_THREAD_ID where t.PROCESSLIST_ID=?", Integer.class, connectionId) > 0;
        if (waiting) break;
        Thread.sleep(10);
      }
      assertTrue(waiting, "验证码验证必须实际等待管理员持有的员工锁");
      try (var update = admin.prepareStatement(disable ? "update employee set enabled=false where id=?" : "update employee set phone=? where id=?")) {
        if (!disable) update.setString(1, phone());
        update.setString(disable ? 1 : 2, id);
        update.executeUpdate();
      }
      try (var revoke = admin.prepareStatement("delete from auth_session where employee_id=?")) {
        revoke.setString(1, id); revoke.executeUpdate();
      }
      admin.commit();
      assertEquals(403, verification.get(10, java.util.concurrent.TimeUnit.SECONDS));
      assertEquals(0, jdbc.queryForObject("select count(*) from verified_phone where employee_id=?", Integer.class, id));
      assertEquals(0, jdbc.queryForObject("select count(*) from auth_session where employee_id=?", Integer.class, id));
      assertEquals(exchange ? 1 : 0, jdbc.queryForObject("select count(*) from wechat_binding where employee_id=?", Integer.class, id));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void smsCreatesRealSessionAndConsumesCodeOnce() throws Exception {
    String phone = admittedPhone();
    String challenge = challenge(phone);
    assertNull(
        jdbc.queryForObject(
            "select (select phone from verified_phone where employee_id=?)", String.class, id));
    verify(challenge, phone, MOCK_CODE)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.user.id").value(id))
        .andExpect(jsonPath("$.data.user.verifiedPhone").value(phone));
    verify(challenge, phone, MOCK_CODE).andExpect(status().isUnauthorized());
  }

  @Test
  void smsFailureCounterPersistsAndExpiryIsEnforced() throws Exception {
    String phone = admittedPhone();
    String challenge = challenge(phone);
    for (int n = 0; n < 5; n++)
      verify(challenge, phone, "wrong").andExpect(status().isUnauthorized());
    assertEquals(
        5,
        jdbc.queryForObject(
            "select attempts from sms_challenge where id=?", Integer.class, challenge));
    verify(challenge, phone, MOCK_CODE).andExpect(status().isUnauthorized());
    jdbc.update(
        "update sms_challenge set attempts=0,expires_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1"
            + " SECOND) where id=?",
        challenge);
    verify(challenge, phone, MOCK_CODE).andExpect(status().isUnauthorized());
  }

  @Test
  void smsThrottleAndBindingRequirements() throws Exception {
    String phone = admittedPhone();
    challenge(phone);
    postJson("/api/mini/auth/sms", null, Map.of("phone", phone, "purpose", "LOGIN"))
        .andExpect(status().isTooManyRequests());
    postJson("/api/mini/auth/sms", null, Map.of("phone", phone, "purpose", "BIND"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void wechatBindRequiresServerTokenAndDoesNotMergeConflictingPhone() throws Exception {
    String phone = admittedPhone();
    var exchange =
        data(
            postJson("/api/mini/auth/wechat", null, Map.of("code", WECHAT_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PHONE_REQUIRED")));
    String binding = exchange.path("bindingToken").asText();
    String challenge =
        data(postJson(
                    "/api/mini/auth/sms",
                    null,
                    Map.of("phone", phone, "purpose", "BIND", "bindingToken", binding))
                .andExpect(status().isOk()))
            .path("challengeId")
            .asText();
    postJson(
            "/api/mini/auth/verify",
            null,
            Map.of(
                "challengeId",
                challenge,
                "phone",
                phone,
                "code",
                MOCK_CODE,
                "bindingToken",
                UUID.randomUUID().toString()))
        .andExpect(status().isUnauthorized());
    postJson(
            "/api/mini/auth/verify",
            null,
            Map.of(
                "challengeId",
                challenge,
                "phone",
                phone,
                "code",
                MOCK_CODE,
                "bindingToken",
                binding))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.user.id").value(id));
    postJson("/api/mini/auth/wechat", null, Map.of("code", WECHAT_CODE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("AUTHENTICATED"));
  }

  @Test
  void concurrentSmsConsumptionIssuesOnlyOneSession() throws Exception {
    String phone = admittedPhone(), challenge = challenge(phone);
    var start = new java.util.concurrent.CountDownLatch(1);
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var first =
          pool.submit(
              () -> {
                start.await();
                return verify(challenge, phone, MOCK_CODE).andReturn().getResponse().getStatus();
              });
      var second =
          pool.submit(
              () -> {
                start.await();
                return verify(challenge, phone, MOCK_CODE).andReturn().getResponse().getStatus();
              });
      start.countDown();
      var statuses =
          new ArrayList<>(
              List.of(
                  first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                  second.get(10, java.util.concurrent.TimeUnit.SECONDS)));
      Collections.sort(statuses);
      assertEquals(List.of(200, 401), statuses);
      assertEquals(
          1,
          jdbc.queryForObject(
              "select count(*) from auth_session where employee_id=?", Integer.class, id));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void changedAdmissionCannotStealAlreadyVerifiedPhone() throws Exception {
    String phone = admittedPhone();
    jdbc.update("insert into verified_phone(employee_id,phone) values(?,?)", id, phone);
    jdbc.update("update employee set phone=? where id=?", phone(), id);
    String other = UUID.randomUUID().toString();
    jdbc.update(
        "insert into employee(id,name,phone,enabled) values(?,?,?,true)", other, "冲突员工", phone);
    verify(challenge(phone), phone, MOCK_CODE).andExpect(status().isConflict());
    assertEquals(
        id,
        jdbc.queryForObject(
            "select employee_id from verified_phone where phone=?", String.class, phone));
  }
}
