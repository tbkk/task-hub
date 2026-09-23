package com.taskhub.domain.dispatch;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true")
public class DispatchRecovery {
  private final DispatchService service;

  public DispatchRecovery(DispatchService service) {
    this.service = service;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recover() {
    service.recoverPending();
  }
}
