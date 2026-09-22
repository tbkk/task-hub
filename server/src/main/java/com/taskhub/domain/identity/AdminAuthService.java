package com.taskhub.domain.identity;

import com.taskhub.api.ApiException;
import com.taskhub.domain.identity.IdentityModels.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuthService {
  private final CredentialMapper credentials;
  private final EmployeeMapper employees;
  private final SessionMapper sessions;
  private final SessionService service;
  private final AuthRateLimiter limiter;
  private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
  private final String dummyHash = bcrypt.encode(TokenCodec.random());
  private final int max;
  private final org.springframework.transaction.support.TransactionTemplate transaction;

  public AdminAuthService(
      CredentialMapper credentials,
      EmployeeMapper employees,
      SessionMapper sessions,
      SessionService service,
      AuthRateLimiter limiter,
      @Value("${taskhub.auth.login-attempts:5}") int max,
      org.springframework.transaction.PlatformTransactionManager manager) {
    this.credentials = credentials;
    this.employees = employees;
    this.sessions = sessions;
    this.service = service;
    this.limiter = limiter;
    this.max = max;
    this.transaction = new org.springframework.transaction.support.TransactionTemplate(manager);
  }

  public Session login(LoginInput input, String ip) {
    String username = IdentityValidation.username(input.username());
    IdentityValidation.required(input.password(), 128, "密码");
    String password = input.password();
    if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw invalidLogin();
    limiter.consume("login-ip:" + ip, 900, 100);
    limiter.consume("login-user:" + username, 900, max);
    Credential hint = credentials.byUsername(username);
    if (hint == null) {
      bcrypt.matches(password, dummyHash);
      throw invalidLogin();
    }
    Session session =
        transaction.execute(
            status -> {
              employees.lock(hint.employeeId());
              Credential credential = credentials.byUsername(username);
              if (credential == null
                  || !credential.employeeId().equals(hint.employeeId())
                  || !bcrypt.matches(password, credential.passwordHash())) throw invalidLogin();
              if (employees.platforms(credential.employeeId()).isEmpty())
                throw new ApiException(403, 40302, "没有管理平台授权");
              return service.issue(credential.employeeId(), "ADMIN");
            });
    limiter.clear("login-user:" + username);
    return session;
  }

  @Transactional
  public void changePassword(Actor actor, PasswordInput input) {
    employees.lock(actor.employeeId());
    service.refreshActor(actor);
    IdentityValidation.password(input.newPassword());
    var current = credentials.byEmployee(actor.employeeId());
    if (current == null
        || input.currentPassword() == null
        || !bcrypt.matches(input.currentPassword(), current.passwordHash()))
      throw new ApiException(401, 40101, "当前密码错误");
    if (bcrypt.matches(input.newPassword(), current.passwordHash()))
      throw IdentityValidation.bad("新密码不能与当前密码相同");
    credentials.change(actor.employeeId(), bcrypt.encode(input.newPassword()));
    sessions.revokeAll(actor.employeeId());
  }

  private ApiException invalidLogin() {
    return new ApiException(401, 40101, "账号或密码错误");
  }

  public String encode(String password) {
    IdentityValidation.password(password);
    return bcrypt.encode(password);
  }
}
