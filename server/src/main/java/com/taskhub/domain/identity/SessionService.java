package com.taskhub.domain.identity;

import com.taskhub.api.ApiException;
import com.taskhub.domain.identity.IdentityModels.*;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {
  private final SessionMapper sessions;
  private final EmployeeMapper employees;
  private final CredentialMapper credentials;
  private final int ttl;

  public SessionService(
      SessionMapper sessions,
      EmployeeMapper employees,
      CredentialMapper credentials,
      @Value("${taskhub.auth.session-seconds:28800}") int ttl) {
    this.sessions = sessions;
    this.employees = employees;
    this.credentials = credentials;
    this.ttl = ttl;
    if (ttl < 60) throw new IllegalArgumentException("会话有效期不得少于60秒");
  }

  public Identity identity(String id) {
    var employee = employees.find(id);
    if (employee == null) throw invalid();
    if (!employee.enabled()) throw new ApiException(403, 40301, "账号已停用");
    var grants =
        employees.roles(id).stream()
            .map(g -> new Grant(g.role(), g.scope(), employees.roleWarehouses(g.id())))
            .toList();
    var platforms =
        employees.platforms(id).stream()
            .map(
                g ->
                    new PlatformGrant(
                        g.capability(), g.scope(), employees.platformWarehouses(g.id())))
            .toList();
    var credential = credentials.byEmployee(id);
    return new Identity(
        id,
        employee.name(),
        employees.verifiedPhone(id),
        grants,
        platforms,
        !platforms.isEmpty(),
        credential != null && credential.mustChangePassword());
  }

  @Transactional
  public Session issue(String id, String channel) {
    employees.lock(id);
    Identity identity = identity(id);
    String token = TokenCodec.random();
    Instant expires = Instant.now().plusSeconds(ttl);
    sessions.insert(new SessionRow(TokenCodec.hash(token), id, channel, expires));
    return new Session(token, expires, identity);
  }

  public Actor authenticate(String token) {
    if (token == null || token.length() != 43) throw invalid();
    String hash = TokenCodec.hash(token);
    var row = sessions.find(hash);
    if (row == null || !row.expiresAt().isAfter(Instant.now())) throw invalid();
    return new Actor(row.employeeId(), hash, null, identity(row.employeeId()));
  }

  public Identity refreshActor(Actor actor) {
    if (actor == null) throw invalid();
    var row = sessions.find(actor.sessionHash());
    if (row == null
        || !row.employeeId().equals(actor.employeeId())
        || !row.expiresAt().isAfter(Instant.now())) throw invalid();
    return identity(actor.employeeId());
  }

  public void revokeToken(String token) {
    if (token != null && token.length() == 43) sessions.revoke(TokenCodec.hash(token));
  }

  public static ApiException invalid() {
    return new ApiException(401, 40101, "会话或凭证已失效，请重新登录");
  }
}
