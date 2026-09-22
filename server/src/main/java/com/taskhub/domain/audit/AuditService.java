package com.taskhub.domain.audit;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.taskhub.domain.identity.IdentityModels.Actor;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class AuditService {
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public AuditService(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(
      Actor actor,
      String objectType,
      String objectId,
      String action,
      Object before,
      Object after,
      String reason) {
    db.update(
        "INSERT INTO"
            + " audit_event(id,actor_id,actor_name,workspace,object_type,object_id,action,before_state,after_state,reason)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?)",
        UUID.randomUUID().toString(),
        actor.employeeId(),
        actor.identity().name(),
        actor.workspace() == null ? "ADMIN" : actor.workspace(),
        objectType,
        objectId,
        action,
        safeJson(before),
        safeJson(after),
        reason);
  }

  private String safeJson(Object value) {
    return value == null ? null : redact(json.valueToTree(value)).toString();
  }

  private JsonNode redact(JsonNode node) {
    if (node.isObject()) {
      ObjectNode result = json.createObjectNode();
      node.fields()
          .forEachRemaining(
              entry -> {
                String key = entry.getKey().toLowerCase(Locale.ROOT);
                if (key.contains("password")
                    || key.contains("token")
                    || key.contains("secret")
                    || key.contains("credential")
                    || key.equals("code")
                    || key.equals("openid")) result.put(entry.getKey(), "[已隐藏]");
                else result.set(entry.getKey(), redact(entry.getValue()));
              });
      return result;
    }
    if (node.isArray()) {
      ArrayNode result = json.createArrayNode();
      node.forEach(v -> result.add(redact(v)));
      return result;
    }
    return node;
  }
}
