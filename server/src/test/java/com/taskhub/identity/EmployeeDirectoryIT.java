package com.taskhub.identity;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.sql.init.mode=never"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class EmployeeDirectoryIT {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    String url = System.getenv("MYSQL_TEST_URL");
    if (url == null || !url.matches("jdbc:mysql://[^/]+/task_hub_[a-zA-Z0-9_]*_test(?:\\?.*)?"))
      throw new IllegalStateException("显式专用测试库必填");
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
  @Autowired SessionService sessions;
  @Autowired ObjectMapper json;
  String token;

  @BeforeEach
  void fixture() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO employee(id,name,phone,enabled) VALUES(?,?,?,true)", id, "目录管理员", phone());
    jdbc.update(
        "INSERT INTO platform_grant(id,employee_id,capability,scope)"
            + " VALUES(?,?,'EMPLOYEE_MANAGE','ALL')",
        UUID.randomUUID().toString(),
        id);
    token = sessions.issue(id, "ADMIN").token();
  }

  String phone() {
    return "138"
        + String.format(
            "%08d", Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100000000));
  }

  Map<String, Object> input(String warehouse) {
    var body = new HashMap<String, Object>();
    body.put("name", "目录员工");
    body.put("phone", phone());
    body.put("enabled", true);
    body.put(
        "grants", List.of(Map.of("role", "worker", "scope", "SELF", "warehouseIds", List.of())));
    body.put("platformGrants", List.of());
    body.put("homeWarehouseId", warehouse);
    return body;
  }

  @Test
  void rejectsUnknownHomeWarehouse() throws Exception {
    mvc.perform(
            post("/api/admin/employees")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json.writeValueAsString(input(UUID.randomUUID().toString()))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void persistsHomeWarehouseAndOffersDirectoryWithoutMasterdataPrivilege() throws Exception {
    String warehouse = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO warehouse(id,name,code,enabled) VALUES(?,?,?,true)",
        warehouse,
        "归属测试仓库",
        warehouse);
    var created =
        mvc.perform(
                post("/api/admin/employees")
                    .header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .content(json.writeValueAsString(input(warehouse))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.homeWarehouseId").value(warehouse))
            .andExpect(jsonPath("$.data.wechatBound").value(false))
            .andReturn();
    String employee =
        json.readTree(created.getResponse().getContentAsString()).path("data").path("id").asText();
    assertEquals(
        warehouse,
        jdbc.queryForObject(
            "SELECT home_warehouse_id FROM employee WHERE id=?", String.class, employee));
    mvc.perform(
            get("/api/admin/employees/warehouse-options")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").isNumber());
    jdbc.update("UPDATE warehouse SET enabled=false WHERE id=?", warehouse);
    var edit = input(warehouse);
    edit.put(
        "phone",
        json.readTree(created.getResponse().getContentAsString())
            .path("data")
            .path("phone")
            .asText());
    edit.put("expectedVersion", 0);
    edit.put("name", "保留原停用仓库");
    mvc.perform(
            put("/api/admin/employees/" + employee)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json.writeValueAsString(edit)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.homeWarehouseId").value(warehouse));
    mvc.perform(
            post("/api/admin/employees")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json.writeValueAsString(input(warehouse))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void roleAndBindingFiltersUseServerSideTotals() throws Exception {
    String marker = "筛选-" + UUID.randomUUID();
    var body = input(null);
    body.put("name", marker);
    var created =
        mvc.perform(
                post("/api/admin/employees")
                    .header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .content(json.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andReturn();
    String employee =
        json.readTree(created.getResponse().getContentAsString()).path("data").path("id").asText();
    mvc.perform(
            get("/api/admin/employees")
                .param("keyword", marker)
                .param("role", "dispatch")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0));
    mvc.perform(
            get("/api/admin/employees")
                .param("keyword", marker)
                .param("phoneVerified", "true")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0));
    jdbc.update(
        "INSERT INTO verified_phone(employee_id,phone) VALUES(?,?)", employee, body.get("phone"));
    mvc.perform(
            get("/api/admin/employees")
                .param("keyword", marker)
                .param("role", "worker")
                .param("phoneVerified", "true")
                .param("wechatBound", "false")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items.length()").value(1));
  }
}
