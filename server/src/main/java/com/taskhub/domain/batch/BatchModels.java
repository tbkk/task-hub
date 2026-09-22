package com.taskhub.domain.batch;

import java.util.List;

public final class BatchModels {
  private BatchModels() {}

  public record Create(List<String> orderIds) {}

  public record Members(Integer expectedVersion, List<String> orderIds) {}

  public record Assignment(String orderId, List<String> compartmentIds) {}

  public record Loading(Integer expectedVersion, String vehicleId, List<Assignment> assignments) {}

  public record Confirm(Integer expectedVersion, Boolean capacityConfirmed) {}
}
