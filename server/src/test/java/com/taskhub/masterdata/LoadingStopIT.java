package com.taskhub.masterdata;

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

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.sql.init.mode=never"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class LoadingStopIT {
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

  @Autowired com.taskhub.domain.masterdata.LoadingStopService loadingStops;

  @Test
  void bindingRequiresAvailableLocalStopAndVersion() throws Exception {
    String employee = UUID.randomUUID().toString(),
        warehouse = UUID.randomUUID().toString(),
        stop = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO employee(id,name,phone,enabled) VALUES(?,?,?,true)",
        employee,
        "装货点管理员",
        "135"
            + String.format(
                "%08d", Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100000000)));
    db.update(
        "INSERT INTO platform_grant(id,employee_id,capability,scope)"
            + " VALUES(?,?,'MASTERDATA_MANAGE','ALL')",
        UUID.randomUUID().toString(),
        employee);
    db.update(
        "INSERT INTO warehouse(id,name,code,enabled) VALUES(?,?,?,true)",
        warehouse,
        "装货仓",
        warehouse);
    db.update(
        "INSERT INTO stop(id,name,external_stop_id,enabled) VALUES(?,?,?,true)", stop, "装货点", stop);
    String token = sessions.issue(employee, "ADMIN").token(),
        path = "/api/admin/warehouses/" + warehouse + "/loading-stop";
    mvc.perform(get(path).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());
    String input = json.writeValueAsString(Map.of("stopId", stop, "expectedVersion", 0));
    mvc.perform(
            put(path)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(input))
        .andExpect(status().isUnprocessableEntity());
    db.update("INSERT INTO stop_warehouse(stop_id,warehouse_id) VALUES(?,?)", stop, warehouse);
    mvc.perform(
            put(path)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(input))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.version").value(1));
    mvc.perform(
            put(path)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(input))
        .andExpect(status().isConflict());
    assertThrows(
        com.taskhub.api.ApiException.class, () -> loadingStops.requireChangeAllowed("stop", stop));
    db.update("UPDATE stop SET enabled=false WHERE id=?", stop);
    mvc.perform(
            put(path)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("stopId", stop, "expectedVersion", 1))))
        .andExpect(status().isUnprocessableEntity());
    db.update("UPDATE platform_grant SET scope='WAREHOUSES' WHERE employee_id=?", employee);
    mvc.perform(get(path).header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    mvc.perform(
            put(path)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("stopId", stop, "expectedVersion", 1))))
        .andExpect(status().isForbidden());
    assertEquals(
        stop,
        db.queryForObject(
            "SELECT stop_id FROM warehouse_loading_stop WHERE warehouse_id=?",
            String.class,
            warehouse));
  }
}
