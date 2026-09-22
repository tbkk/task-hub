package com.taskhub.audit;

import static org.junit.jupiter.api.Assertions.*;

import com.taskhub.domain.audit.RetentionPolicy;
import java.time.*;
import org.junit.jupiter.api.Test;

class RetentionPolicyTest {
  private final Instant now = Instant.parse("2026-09-22T00:00:00Z");

  @Test
  void unfinishedAndRecentRecordsAreRetained() {
    assertFalse(RetentionPolicy.eligible(false, now.minus(Duration.ofDays(90)), now));
    assertFalse(RetentionPolicy.eligible(true, now.minus(Duration.ofDays(30)), now));
  }

  @Test
  void terminalRecordsAtLeast31DaysOldAreEligible() {
    assertTrue(RetentionPolicy.eligible(true, now.minus(Duration.ofDays(31)), now));
    assertFalse(RetentionPolicy.eligible(true, now.plusSeconds(1), now));
  }
}
