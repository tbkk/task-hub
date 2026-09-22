package com.taskhub.domain.ticket;

public final class TicketModels {
  private TicketModels() {}

  public record Create(String orderId, String description) {}

  public record Action(Integer expectedVersion, String text, String result) {}
}
