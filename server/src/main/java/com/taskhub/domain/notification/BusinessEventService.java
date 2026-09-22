package com.taskhub.domain.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class BusinessEventService {
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public BusinessEventService(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  /** payload 仅传业务对象引用和业务状态，不传凭据或验证码。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public String append(String type, String aggregateId, Object payload) {
    String id = UUID.randomUUID().toString();
    try {
      db.update(
          "INSERT INTO business_event(id,event_type,aggregate_id,payload) VALUES(?,?,?,?)",
          id,
          type,
          aggregateId,
          json.writeValueAsString(payload));
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("业务事件格式无效", e);
    }
    return id;
  }
}
