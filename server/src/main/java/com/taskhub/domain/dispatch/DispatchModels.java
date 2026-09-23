package com.taskhub.domain.dispatch;

public final class DispatchModels {
  private DispatchModels() {}

  public record Input(Integer expectedVersion) {}
}
