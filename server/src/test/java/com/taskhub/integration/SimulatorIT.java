package com.taskhub.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.domain.identity.SessionService;
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
@AutoConfigureMockMvc
class SimulatorIT {
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
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  String employee, token;
  @Autowired com.taskhub.domain.integration.VehicleGateway gateway;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

  @BeforeEach
  void fixture() {
    employee = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO employee(id,name,phone,enabled) VALUES(?,?,?,true)",
        employee,
        "模拟器测试",
        "136"
            + String.format(
                "%08d", Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100000000)));
    db.update(
        "INSERT INTO platform_grant(id,employee_id,capability,scope)"
            + " VALUES(?,?,'INTEGRATION_MANAGE','ALL')",
        UUID.randomUUID().toString(),
        employee);
    token = sessions.issue(employee, "ADMIN").token();
  }

  @Test
  void scenarioIsPersistedAndRequiresIntegrationGrant() throws Exception {
    String input = json.writeValueAsString(Map.of("operation", "OPEN", "outcome", "TIMEOUT"));
    mvc.perform(
            put("/api/dev/simulator/scenario")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(input))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.outcome").value("TIMEOUT"));
    assertEquals(
        "TIMEOUT",
        db.queryForObject(
            "SELECT outcome FROM simulator_scenario WHERE operation='OPEN'", String.class));
    db.update("DELETE FROM platform_grant WHERE employee_id=?", employee);
    mvc.perform(
            put("/api/dev/simulator/scenario")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(input))
        .andExpect(status().isForbidden());
  }

  @Test
  void commandsReplayPersistedOutcomeAndTimeoutDoesNotMeanExecution() throws Exception {
    for (String outcome : List.of("ACCEPTED", "FAILED", "TIMEOUT")) {
      mvc.perform(
              put("/api/dev/simulator/scenario")
                  .header("Authorization", "Bearer " + token)
                  .contentType("application/json")
                  .content(
                      json.writeValueAsString(Map.of("operation", "DISPATCH", "outcome", outcome))))
          .andExpect(status().isOk());
      String request = UUID.randomUUID().toString();
      var cmd =
          new com.taskhub.domain.integration.VehicleGateway.DispatchCommand(
              request, "vehicle-test", "origin", List.of("goal"));
      var result = gateway.dispatch(cmd);
      assertEquals(outcome.equals("TIMEOUT") ? "UNKNOWN" : outcome, result.status().name());
      assertEquals(result, gateway.queryCommand(request));
      mvc.perform(
              put("/api/dev/simulator/scenario")
                  .header("Authorization", "Bearer " + token)
                  .contentType("application/json")
                  .content(
                      json.writeValueAsString(
                          Map.of("operation", "DISPATCH", "outcome", "FAILED"))))
          .andExpect(status().isOk());
      assertEquals(result, gateway.dispatch(cmd));
      var error =
          assertThrows(
              com.taskhub.api.ApiException.class,
              () ->
                  gateway.dispatch(
                      new com.taskhub.domain.integration.VehicleGateway.DispatchCommand(
                          request, "other", "origin", List.of("goal"))));
      assertEquals(40901, error.code());
    }
  }

  @Test
  void emptyOpenIsRejectedAndProductionCannotEnableSimulator() {
    assertThrows(
        com.taskhub.api.ApiException.class,
        () ->
            gateway.open(
                new com.taskhub.domain.integration.VehicleGateway.OpenCommand(
                    UUID.randomUUID().toString(), "vehicle", null, List.of())));
    var env =
        new org.springframework.mock.env.MockEnvironment()
            .withProperty("taskhub.vehicle.simulator-enabled", "true");
    env.setActiveProfiles("production", "test");
    assertThrows(
        IllegalStateException.class,
        () -> new com.taskhub.domain.integration.VehicleProviderConfiguration(env));
  }

  @Test
  void newProviderInstanceAndConcurrentCallsKeepOneDurableResult() throws Exception {
    var cmd =
        new com.taskhub.domain.integration.VehicleGateway.OpenCommand(
            UUID.randomUUID().toString(), "vehicle", null, List.of("hardware-1"));
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    var start = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.Callable<com.taskhub.domain.integration.VehicleGateway.GatewayResult>
        action =
            () -> {
              start.await();
              return gateway.open(cmd);
            };
    try {
      var first = pool.submit(action);
      var second = pool.submit(action);
      start.countDown();
      var result = first.get(10, java.util.concurrent.TimeUnit.SECONDS);
      assertEquals(result, second.get(10, java.util.concurrent.TimeUnit.SECONDS));
      assertEquals(
          1,
          db.queryForObject(
              "SELECT COUNT(*) FROM simulator_command WHERE request_id=?",
              Integer.class,
              cmd.requestId()));
      var reopened =
          new com.taskhub.domain.integration.SimulatorVehicleGateway(db, json, transactionManager);
      assertEquals(result, reopened.queryCommand(cmd.requestId()));
      assertEquals(result, reopened.open(cmd));
    } finally {
      pool.shutdownNow();
    }
  }
}
