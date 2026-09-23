package com.taskhub.domain.vehicle;

import java.util.List;

public final class ControlModels {
  private ControlModels() {}
  public record Input(Integer expectedVersion) {}
  public record Scan(String qrText) {}
  public record ControlRequest(String id, String type, String status, String vehicleId, String taskId, String orderId, List<String> compartmentIds, String message) {}
}
