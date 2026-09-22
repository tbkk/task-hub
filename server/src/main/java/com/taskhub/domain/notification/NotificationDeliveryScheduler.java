package com.taskhub.domain.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "taskhub.notifications.delivery-enabled", havingValue = "true")
public class NotificationDeliveryScheduler {
  private final BusinessEventDelivery delivery;

  public NotificationDeliveryScheduler(BusinessEventDelivery delivery) {
    this.delivery = delivery;
  }

  @Scheduled(fixedDelayString = "${taskhub.notifications.delay-ms:3000}")
  public void deliver() {
    delivery.deliverBatch(100);
  }
}
