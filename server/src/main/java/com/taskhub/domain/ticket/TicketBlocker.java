package com.taskhub.domain.ticket;

public interface TicketBlocker {
  boolean hasActiveForTask(String taskId);
}
