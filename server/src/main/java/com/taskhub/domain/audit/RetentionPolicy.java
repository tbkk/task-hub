package com.taskhub.domain.audit;

import java.time.Duration;
import java.time.Instant;

/** 业务记录留存判定；清理任务由交付环境显式启用。 */
public final class RetentionPolicy {
  public static final Duration MINIMUM = Duration.ofDays(31);

  private RetentionPolicy() {}

  public static boolean eligible(boolean ended, Instant endedAt, Instant now) {
    if (!ended || endedAt == null || now == null || endedAt.isAfter(now)) return false;
    return !endedAt.plus(MINIMUM).isAfter(now);
  }
}
