package com.taskhub.domain.integration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** provider只能由接入适配器确定；模拟HTTP入口强制使用SIMULATOR。 */
public record VehicleEvent(
    String eventId,
    String vehicleId,
    String dispatchId,
    Instant reportedAt,
    String businessStatus,
    String currentStopId,
    String nextStopId,
    Boolean online,
    BigDecimal speed,
    List<Door> doors,
    BigDecimal batteryPercent,
    BigDecimal longitude,
    BigDecimal latitude,
    BigDecimal distanceToNextStopMeters,
    BigDecimal historicalLegMinutes) {
  public record Door(String compartmentId, String status) {}
}
