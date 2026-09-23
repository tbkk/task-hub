package com.taskhub;

import static org.junit.jupiter.api.Assertions.*;

import com.taskhub.domain.identity.SessionService;
import com.taskhub.infrastructure.idempotency.IdempotencyService;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.sql.init.mode=never"})
@ActiveProfiles("test")
class IdempotencyIT {
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
  @Autowired IdempotencyService service;
  @Autowired com.taskhub.domain.audit.AuditService audit;
  @Autowired com.taskhub.domain.notification.BusinessEventService events;
  com.taskhub.domain.identity.IdentityModels.Actor actor;

  @BeforeEach
  void fixture() {
    String id = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO employee(id,name,phone,enabled) VALUES(?,?,?,true)",
        id,
        "幂等员工",
        "137"
            + String.format(
                "%08d", Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100000000)));
    db.update(
        "INSERT INTO role_grant(id,employee_id,role,scope) VALUES(?,?,'worker','SELF')",
        UUID.randomUUID().toString(),
        id);
    actor = sessions.authenticate(sessions.issue(id, "MINI").token()).withWorkspace("worker");
  }

  @Test
  void replayAndConflictSurviveServiceCalls() {
    String key = UUID.randomUUID().toString();
    var body = Map.of("description", "物料");
    String result = service.execute(actor, "orders", key, body, String.class, () -> "order-1");
    assertEquals("order-1", result);
    assertEquals(
        result,
        service.execute(
            actor,
            "orders",
            key,
            body,
            String.class,
            () -> {
              throw new AssertionError("重复执行");
            }));
    var error =
        assertThrows(
            com.taskhub.api.ApiException.class,
            () ->
                service.execute(
                    actor,
                    "orders",
                    key,
                    Map.of("description", "另一物料"),
                    String.class,
                    () -> "bad"));
    assertEquals(40901, error.code());
  }

  @Test
  void failedBusinessRollsBackIdempotency() {
    String key = UUID.randomUUID().toString();
    assertThrows(
        IllegalStateException.class,
        () ->
            service.execute(
                actor,
                "orders",
                key,
                Map.of(),
                String.class,
                () -> {
                  throw new IllegalStateException("业务失败");
                }));
    assertEquals("ok", service.execute(actor, "orders", key, Map.of(), String.class, () -> "ok"));
  }

  @Test
  void concurrentDuplicateOnlyExecutesOnce() throws Exception {
    String key = UUID.randomUUID().toString();
    var count = new AtomicInteger();
    var start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      Callable<String> request =
          () -> {
            start.await();
            return service.execute(
                actor,
                "orders",
                key,
                Map.of("amount", 1),
                String.class,
                () -> "order-" + count.incrementAndGet());
          };
      var a = pool.submit(request);
      var b = pool.submit(request);
      start.countDown();
      assertEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
      assertEquals(1, count.get());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void auditAndBusinessEventsShareBusinessRollbackAndRedactSecrets() {
    String object = UUID.randomUUID().toString();
    assertThrows(
        IllegalStateException.class,
        () ->
            service.execute(
                actor,
                "audit",
                UUID.randomUUID().toString(),
                Map.of(),
                String.class,
                () -> {
                  audit.record(
                      actor,
                      "ORDER",
                      object,
                      "CREATE",
                      null,
                      Map.of("password", "secret", "description", "物料"),
                      null);
                  events.append("ORDER_CREATED", object, Map.of("orderId", object));
                  throw new IllegalStateException("rollback");
                }));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM audit_event WHERE object_id=?", Integer.class, object));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM business_event WHERE aggregate_id=?", Integer.class, object));
    service.execute(
        actor,
        "audit",
        UUID.randomUUID().toString(),
        Map.of(),
        String.class,
        () -> {
          audit.record(
              actor,
              "ORDER",
              object,
              "CREATE",
              null,
              Map.of(
                  "password", "secret", "nested", Map.of("token", "private"), "description", "物料"),
              null);
          return "ok";
        });
    String state =
        db.queryForObject(
            "SELECT after_state FROM audit_event WHERE object_id=?", String.class, object);
    assertFalse(state.contains("secret"));
    assertFalse(state.contains("private"));
    assertTrue(state.contains("物料"));
  }

  @Test
  void changedGrantsCannotReplayPreviousScope() {
    String key = UUID.randomUUID().toString();
    service.execute(actor, "orders", key, Map.of(), String.class, () -> "sensitive");
    db.update("DELETE FROM role_grant WHERE employee_id=?", actor.employeeId());
    assertThrows(
        com.taskhub.api.ApiException.class,
        () -> service.execute(actor, "orders", key, Map.of(), String.class, () -> "bad"));
  }

  @Test
  void mysqlDefaultsUseUtcForPersistedBusinessTimes() {
    assertEquals(
        0,
        db.queryForObject(
            "SELECT TIMESTAMPDIFF(SECOND,UTC_TIMESTAMP(),CURRENT_TIMESTAMP())", Integer.class));
  }
}
