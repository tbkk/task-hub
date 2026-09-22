package com.taskhub.domain.ticket;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SqlTicketBlocker implements TicketBlocker {
  private final JdbcTemplate db;

  public SqlTicketBlocker(JdbcTemplate db) {
    this.db = db;
  }

  /** 调用者须先锁车辆与任务，与创建工单共用同一顺序。 */
  public boolean hasActiveForTask(String taskId) {
    return db.queryForObject(
            "SELECT COUNT(*) FROM active_order_ticket a JOIN batch_order bo ON"
                + " bo.order_id=a.order_id JOIN vehicle_task vt ON vt.batch_id=bo.batch_id WHERE"
                + " vt.id=? AND bo.removed_at IS NULL",
            Integer.class,
            taskId)
        > 0;
  }
}
