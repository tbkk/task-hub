package com.taskhub.domain.identity;

import com.taskhub.api.ApiException;
import com.taskhub.domain.identity.IdentityModels.Credential;
import com.taskhub.domain.identity.IdentityModels.Session;
import com.taskhub.domain.identity.MiniAuthModels.BindInput;
import com.taskhub.domain.identity.MiniAuthModels.Binding;
import com.taskhub.domain.identity.MiniAuthModels.WechatExchange;
import com.taskhub.domain.integration.WechatProvider;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MiniAuthService {
  private final MiniAuthMapper mapper;
  private final CredentialMapper credentials;
  private final EmployeeMapper employees;
  private final SessionService sessions;
  private final WechatProvider wechat;
  private final TransactionTemplate transaction;
  private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

  public MiniAuthService(
      MiniAuthMapper mapper,
      CredentialMapper credentials,
      EmployeeMapper employees,
      SessionService sessions,
      WechatProvider wechat,
      PlatformTransactionManager manager) {
    this.mapper = mapper;
    this.credentials = credentials;
    this.employees = employees;
    this.sessions = sessions;
    this.wechat = wechat;
    this.transaction = new TransactionTemplate(manager);
  }

  public WechatExchange exchange(String code) {
    IdentityValidation.required(code, 512, "微信凭证");
    var identity = wechat.exchange(code);
    String owner = mapper.wechatOwner(identity.appId(), identity.openid());
    if (owner != null)
      return new WechatExchange("AUTHENTICATED", sessions.issue(owner, "MINI"), null, null);
    String token = TokenCodec.random();
    Instant expiry = Instant.now().plusSeconds(600);
    mapper.insertBinding(
        new Binding(TokenCodec.hash(token), identity.appId(), identity.openid(), expiry, null));
    return new WechatExchange("CREDENTIALS_REQUIRED", null, token, expiry);
  }

  public Session bind(BindInput input) {
    String token = IdentityValidation.required(input.bindingToken(), 128, "绑定凭证");
    String username = IdentityValidation.username(input.username());
    IdentityValidation.required(input.password(), 128, "密码");
    return transaction.execute(
        status -> {
          Binding binding = mapper.lockBinding(TokenCodec.hash(token));
          if (binding == null
              || binding.consumedAt() != null
              || !binding.expiresAt().isAfter(Instant.now())) throw SessionService.invalid();
          Credential credential = credentials.byUsername(username);
          if (credential == null || !bcrypt.matches(input.password(), credential.passwordHash()))
            throw new ApiException(401, 40101, "账号或密码错误");
          var employee = employees.lock(credential.employeeId());
          if (employee == null || !employee.enabled()) throw new ApiException(403, 40301, "账号已停用");
          if (credential.mustChangePassword())
            throw new ApiException(403, 40302, "请先使用账号密码登录并修改临时密码");
          String owner = mapper.wechatOwner(binding.appId(), binding.openid());
          String existing = mapper.employeeWechat(employee.id(), binding.appId());
          if ((owner != null && !owner.equals(employee.id()))
              || (existing != null && !existing.equals(binding.openid())))
            throw new ApiException(409, 40900, "微信绑定冲突，不能自动合并账号");
          if (owner == null)
            mapper.bind(UUID.randomUUID().toString(), employee.id(), binding.appId(), binding.openid());
          mapper.consumeBinding(binding.tokenHash());
          return sessions.issue(employee.id(), "MINI");
        });
  }
}
