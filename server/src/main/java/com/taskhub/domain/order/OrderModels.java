package com.taskhub.domain.order;

public final class OrderModels {
  private OrderModels() {}

  public record Create(
      String warehouseId,
      String stopId,
      String slotId,
      String description,
      String size,
      String receiverName,
      String receiverPhone,
      String remark) {}

  public record Cancellation(Integer expectedVersion, String reason) {}
}
