package com.taskhub.domain.integration;

import com.taskhub.api.ApiException;
import com.taskhub.domain.audit.AuditService;
import com.taskhub.domain.identity.AuthorizationService;
import com.taskhub.domain.identity.IdentityModels.Actor;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SimulatorScenarioService {
  public record Scenario(String operation, String outcome) {}

  private final JdbcTemplate db;
  private final AuthorizationService auth;
  private final AuditService audit;

  public SimulatorScenarioService(JdbcTemplate db, AuthorizationService auth, AuditService audit) {
    this.db = db;
    this.auth = auth;
    this.audit = audit;
  }

  @Transactional
  public Scenario save(Actor actor, Scenario input) {
    auth.lockActor(actor);
    auth.requirePlatform(actor, "INTEGRATION_MANAGE", null);
    if (input == null
        || !Set.of("DISPATCH", "OPEN", "GO", "CANCEL").contains(String.valueOf(input.operation()))
        || !Set.of("ACCEPTED", "FAILED", "TIMEOUT").contains(String.valueOf(input.outcome())))
      throw new ApiException(400, 40000, "模拟场景参数无效");
    db.update(
        "INSERT INTO simulator_scenario(operation,outcome) VALUES(?,?) ON DUPLICATE KEY UPDATE"
            + " outcome=?,updated_at=UTC_TIMESTAMP(3)",
        input.operation(),
        input.outcome(),
        input.outcome());
    audit.record(actor, "SIMULATOR", input.operation(), "SIMULATOR_SCENARIO", null, input, null);
    return input;
  }
}
