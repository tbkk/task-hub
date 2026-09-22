package com.taskhub.infrastructure;

import java.sql.Timestamp;
import java.time.*;

/** 本项目 DATETIME 字段按 UTC 保存；兼容 JDBC 驱动返回 LocalDateTime 或 Timestamp。 */
public final class DatabaseTime {
  private DatabaseTime() {}

  public static Instant instant(Object value) {
    if (value instanceof Timestamp timestamp) return timestamp.toInstant();
    if (value instanceof LocalDateTime local) return local.toInstant(ZoneOffset.UTC);
    if (value instanceof Instant instant) return instant;
    throw new IllegalArgumentException("数据库时间类型不支持");
  }
}
