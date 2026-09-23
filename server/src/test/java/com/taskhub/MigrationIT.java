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
