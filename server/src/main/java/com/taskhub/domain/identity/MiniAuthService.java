package com.taskhub.domain.identity;

import com.taskhub.api.ApiException;
import com.taskhub.domain.identity.IdentityModels.Session;
import com.taskhub.domain.identity.MiniAuthModels.*;
import com.taskhub.domain.integration.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MiniAuthService {
  private final MiniAuthMapper mapper;
  private final EmployeeMapper employees;
  private final SessionService sessions;
  private final WechatProvider wechat;
  private final SmsProvider sms;
  private final AuthRateLimiter limiter;
  private final TransactionTemplate transaction;
  private final String secret;
  private final int ttl, interval, phoneLimit, ipLimit, maxAttempts;

  public MiniAuthService(
      MiniAuthMapper mapper,
      EmployeeMapper employees,
      SessionService sessions,
      WechatProvider wechat,
      SmsProvider sms,
      AuthRateLimiter limiter,
      PlatformTransactionManager manager,
      @Value("${taskhub.auth.sms-hmac-secret:}") String secret,
      @Value("${taskhub.auth.sms-ttl-seconds:300}") int ttl,
      @Value("${taskhub.auth.sms-interval-seconds:60}") int interval,
      @Value("${taskhub.auth.sms-phone-hour-limit:10}") int phoneLimit,
      @Value("${taskhub.auth.sms-ip-hour-limit:30}") int ipLimit,
      @Value("${taskhub.auth.sms-max-attempts:5}") int maxAttempts) {
    this.mapper = mapper;
    this.employees = employees;
    this.sessions = sessions;
    this.wechat = wechat;
    this.sms = sms;
    this.limiter = limiter;
    this.transaction = new TransactionTemplate(manager);
    this.secret = secret;
    this.ttl = ttl;
    this.interval = interval;
    this.phoneLimit = phoneLimit;
    this.ipLimit = ipLimit;
    this.maxAttempts = maxAttempts;
    if (ttl < 1 || interval < 1 || phoneLimit < 1 || ipLimit < 1 || maxAttempts < 1)
      throw new IllegalArgumentException("短信限频参数必须为正数");
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
        new Binding(
            TokenCodec.hash(token), identity.appId(), identity.openid(), null, expiry, null));
    return new WechatExchange("PHONE_REQUIRED", null, token, expiry);
  }

  public ChallengeResponse send(SmsInput input, String ip) {
    String phone = IdentityValidation.phone(input.phone());
    if (!List.of("LOGIN", "BIND").contains(input.purpose() == null ? "" : input.purpose()))
      throw IdentityValidation.bad("验证码用途无效");
    String bindingHash = input.purpose().equals("BIND") ? bindingHash(input.bindingToken()) : null;
    if (bindingHash == null && input.bindingToken() != null)
      throw IdentityValidation.bad("短信登录无需绑定凭证");
    requireSecret();
    if (bindingHash != null)
      transaction.executeWithoutResult(
          status -> {
            var binding = validBinding(bindingHash, phone);
            mapper.bindingPhone(bindingHash, phone);
          });
    limiter.consume("sms-ip:" + ip, 3600, ipLimit);
    limiter.consume("sms-phone:" + phone, 3600, phoneLimit);
    limiter.consume("sms-interval:" + phone, interval, 1);
    var employee = employees.findByPhone(phone);
    if (employee == null) throw new ApiException(403, 40302, "手机号尚未准入");
    sessions.identity(employee.id());
    String id = UUID.randomUUID().toString(), code = sms.challengeCode();
    Instant expiry = Instant.now().plusSeconds(ttl);
    // 外部调用不持有数据库事务；发送失败时不落可消费挑战。
    sms.send(phone, code);
    mapper.insertChallenge(
        new Challenge(
            id, phone, input.purpose(), codeHash(id, phone, code), bindingHash, 0, expiry, null));
    return new ChallengeResponse(id, interval, expiry);
  }

  public Session verify(VerifyInput input) {
    String phone = IdentityValidation.phone(input.phone());
    String challengeId = IdentityValidation.required(input.challengeId(), 36, "验证码凭据");
    String code = IdentityValidation.required(input.code(), 128, "验证码");
    requireSecret();
    // 事务外查询仅定位员工；准入和启用状态必须由事务内当前读重新核验。
    var employeeHint = employees.findByPhone(phone);
    // 返回失败对象使失败计数提交；在事务外抛401，禁止异常回滚计数。
    Object result;
    try {
      result =
          transaction.execute(
              status -> {
                var challenge = mapper.lockChallenge(challengeId);
                if (challenge == null
                    || challenge.consumedAt() != null
                    || !challenge.expiresAt().isAfter(Instant.now())
                    || challenge.attempts() >= maxAttempts) return SessionService.invalid();
                if (!challenge.phone().equals(phone)
                    || !MessageDigest.isEqual(
                        challenge.codeHash().getBytes(StandardCharsets.UTF_8),
                        codeHash(challengeId, phone, code).getBytes(StandardCharsets.UTF_8))) {
                  mapper.failed(challengeId);
                  return SessionService.invalid();
                }
                Binding binding = null;
                if (challenge.purpose().equals("BIND")) {
                  if (input.bindingToken() == null
                      || !Objects.equals(
                          challenge.bindingHash(), TokenCodec.hash(input.bindingToken())))
                    return SessionService.invalid();
                  binding = validBinding(challenge.bindingHash(), phone);
                } else if (input.bindingToken() != null) return SessionService.invalid();
                // 按主键统一锁顺序，避免先锁手机号索引与管理员修改手机号形成死锁。
                var employee = employeeHint == null ? null : employees.lock(employeeHint.id());
                if (employee == null || !phone.equals(employee.phone()))
                  return new ApiException(403, 40302, "手机号尚未准入");
                if (!employee.enabled()) return new ApiException(403, 40301, "账号已停用");
                sessions.identity(employee.id());
                String verifiedOwner = employees.verifiedOwner(phone),
                    existingPhone = employees.verifiedPhone(employee.id());
                if ((verifiedOwner != null && !verifiedOwner.equals(employee.id()))
                    || (existingPhone != null && !existingPhone.equals(phone)))
                  return new ApiException(409, 40900, "手机号验证关系冲突，需管理员核对");
                if (binding != null) {
                  String owner = mapper.wechatOwner(binding.appId(), binding.openid()),
                      existing = mapper.employeeWechat(employee.id(), binding.appId());
                  if ((owner != null && !owner.equals(employee.id()))
                      || (existing != null && !existing.equals(binding.openid())))
                    return new ApiException(409, 40900, "微信绑定冲突，不能自动合并账号");
                  if (owner == null)
                    mapper.bind(
                        UUID.randomUUID().toString(),
                        employee.id(),
                        binding.appId(),
                        binding.openid());
                  mapper.consumeBinding(binding.tokenHash());
                }
                if (existingPhone == null) employees.verifyPhone(employee.id(), phone);
                mapper.consumeChallenge(challengeId);
                return sessions.issue(employee.id(), "MINI");
              });
    } catch (DuplicateKeyException conflict) {
      throw new ApiException(409, 40900, "身份验证关系冲突，需管理员核对");
    }
    if (result instanceof ApiException error) throw error;
    return (Session) result;
  }

  private Binding validBinding(String hash, String phone) {
    var binding = mapper.lockBinding(hash);
    if (binding == null
        || binding.consumedAt() != null
        || !binding.expiresAt().isAfter(Instant.now())
        || (binding.phone() != null && !binding.phone().equals(phone)))
      throw SessionService.invalid();
    return binding;
  }

  private String bindingHash(String token) {
    if (token == null || token.length() != 43) throw SessionService.invalid();
    return TokenCodec.hash(token);
  }

  private String codeHash(String id, String phone, String code) {
    return TokenCodec.hmac(secret, id + ":" + phone + ":" + code);
  }

  private void requireSecret() {
    if (secret.length() < 32) throw new ApiException(503, 50300, "短信验证服务尚未配置");
  }
}
