# MySQL 迁移和接口错误基础 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在保留无数据库存活检查的前提下引入显式 Flyway 迁移与真实 MySQL 测试，并提供结构化业务错误和分页。

**Architecture:** 默认关闭启动迁移，local profile 显式启用；真实业务运行要求迁移已执行。数据库验收使用独立 task_hub_delivery_test，不触碰其他数据库。API 业务错误由 advice 返回相符 HTTP 状态。

**Tech Stack:** Java 17、Spring Boot 3.5.6、MyBatis、Flyway、MySQL、JUnit 5。

## Global Constraints

- 当前 feature/base；不覆盖已有改动，不提交推送。
- 无 Docker；本地 MySQL 9.6，目标 MySQL 8.4 另行验证。
- 数据库只使用 task_hub 命名的专用库；无默认密码。
- ID 为字符串；total、page、pageSize 使用 int 保持 JSON number。

---

### Task 1: 迁移与 API 基础

**Files:** 修改 `server/pom.xml`、`server/src/main/resources/application.yml`；新增 `server/src/main/resources/application-local.yml`、`server/src/main/resources/db/migration/V1__application_metadata.sql`；新增下方 Java 文件。

**Interfaces:** 消费既有 `ApiResponse<T>` 和 application_metadata(meta_key,meta_value)；产出 `ApiException(int status,int code,String message)` 与 `PageResponse<T>(List<T>,int,int,int)`。

- [ ] **Step 1: 写入失败测试**

`server/src/test/java/com/taskhub/ApiErrorTest.java`：

```java
package com.taskhub;

import com.taskhub.api.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class ApiErrorTest {
    @RestController
    static class TestEndpoint {
        @GetMapping("/conflict") Object conflict() { throw new ApiException(409, 40902, "记录已更新，请刷新"); }
        @PostMapping("/body") Object body(@RequestBody java.util.Map<String,Object> input) { return input; }
    }
    @Test void businessErrorKeepsHttpStatusAndStableEnvelope() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new TestEndpoint()).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/conflict")).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(40902))
            .andExpect(jsonPath("$.message").value("记录已更新，请刷新"));
        mvc.perform(post("/body").contentType("application/json").content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
    }
    @Test void paginationRejectsUnboundedRequestsAndPreservesNumbers() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new PageResponse<>(List.of(), 0, 0, 20));
        assertThrows(IllegalArgumentException.class, () -> new PageResponse<>(List.of(), 0, 1, 101));
        var json = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(new PageResponse<>(List.of("id"), 1, 1, 20));
        assertTrue(json.get("total").isNumber());
        assertEquals(1, json.get("total").intValue());
    }
}
```

`server/src/test/java/com/taskhub/MigrationIT.java`：

```java
package com.taskhub;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class MigrationIT {
    @Test
    void migratesRealMysqlAndReplaysWithoutLosingExistingMetadata() throws Exception {
        String url = System.getenv("MYSQL_TEST_URL");
        assertNotNull(url, "必须显式设置 MYSQL_TEST_URL，数据库名须以 task_hub_ 开头并以 _test 结尾");
        assertTrue(url.matches("jdbc:mysql://[^/]+/task_hub_[a-zA-Z0-9_]*_test(?:\\?.*)?"), "拒绝连接非专用测试数据库");
        String user = System.getenv().getOrDefault("MYSQL_TEST_USER", "taskhub");
        String password = System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", "");
        Flyway flyway = Flyway.configure().dataSource(url, user, password)
            .locations("classpath:db/migration").cleanDisabled(true).load();
        flyway.migrate();
        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT meta_value FROM application_metadata WHERE meta_key='schema_version'")) {
                assertTrue(result.next());
                assertEquals("1", result.getString(1));
            }
            statement.executeUpdate("INSERT INTO application_metadata(meta_key,meta_value) VALUES('migration_test_sentinel','keep') ON DUPLICATE KEY UPDATE meta_value='keep'");
        }
        assertEquals(0, flyway.migrate().migrationsExecuted);
        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT meta_value FROM application_metadata WHERE meta_key='migration_test_sentinel'")) {
            assertTrue(result.next());
            assertEquals("keep", result.getString(1));
        }
    }
}
```

- [ ] **Step 2: 运行红灯**

`cd server && mvn -Dtest=ApiErrorTest test`；预期缺少 ApiException/PageResponse/Flyway 编译失败。

- [ ] **Step 3: 增加依赖与显式配置**

pom dependencies 增加以下 Boot 管理版本依赖：

```xml
<dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
<dependency><groupId>org.flywaydb</groupId><artifactId>flyway-mysql</artifactId></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
```

`application.yml` 的 spring 节点追加：

```yaml
  flyway:
    enabled: ${TASKHUB_MIGRATIONS_ENABLED:false}
    clean-disabled: true
```

新增 application-local.yml：

```yaml
spring:
  flyway:
    enabled: true
```

V1__application_metadata.sql 内容为现有 database/init/001_app_metadata.sql 的完整副本，保留字段和值、IF NOT EXISTS，不改其他库。

- [ ] **Step 4: 写入接口基础实现**

`server/src/main/java/com/taskhub/api/ApiException.java`：

```java
package com.taskhub.api;

public class ApiException extends RuntimeException {
    private final int status;
    private final int code;
    public ApiException(int status, int code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public int status() { return status; }
    public int code() { return code; }
}
```

`server/src/main/java/com/taskhub/api/ApiExceptionHandler.java`：

```java
package com.taskhub.api;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse<Void>> business(ApiException error) {
        return ResponseEntity.status(error.status()).body(ApiResponse.error(error.code(), error.getMessage()));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Void>> invalidJson() {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, "请求内容格式不正确"));
    }
}
```

`server/src/main/java/com/taskhub/api/PageResponse.java`：

```java
package com.taskhub.api;

import java.util.List;

public record PageResponse<T>(List<T> items, int total, int page, int pageSize) {
    public PageResponse {
        items = List.copyOf(items);
        if (total < 0 || page < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("分页参数无效");
        }
    }
}
```

- [ ] **Step 5: 运行绿灯与真实数据库迁移**

```bash
mysql -h 127.0.0.1 -u root -e "CREATE DATABASE IF NOT EXISTS task_hub_delivery_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
cd server
mvn test
MYSQL_TEST_URL='jdbc:mysql://127.0.0.1:3306/task_hub_delivery_test?serverTimezone=UTC' MYSQL_TEST_USER=root mvn -Dtest=MigrationIT test
```

预期原 6 测试和新增 2 API 测试通过；MigrationIT 成功应用 V1，重复执行零新迁移，sentinel 保留。专用本机测试库使用本机可用连接，不将真实凭证写进文件。

- [ ] **Step 6: Review 与进度记录**

核对错误信封、真实状态、非测试库防护、迁移默认关闭及 local 显式开启。记录数据库版本、迁移成功和目标 8.4 未验证，不将 H2 当 MySQL 验收。
