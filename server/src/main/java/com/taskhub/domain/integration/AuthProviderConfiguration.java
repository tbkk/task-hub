package com.taskhub.domain.integration;

import com.taskhub.api.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration
public class AuthProviderConfiguration {
  private final boolean mock;
  private final Environment environment;

  public AuthProviderConfiguration(Environment environment) {
    this.environment = environment;
    mock = environment.getProperty("taskhub.auth.mock-enabled", Boolean.class, false);
    var profiles = Arrays.asList(environment.getActiveProfiles());
    if (mock
        && (profiles.contains("prod")
            || profiles.contains("production")
            || (!profiles.contains("local") && !profiles.contains("test"))))
      throw new IllegalStateException("模拟认证仅允许local/test，生产禁止启用");
  }

  @Bean
  public WechatProvider wechatProvider() {
    if (!mock)
      return code -> {
        throw new ApiException(503, 50300, "微信服务尚未配置");
      };
    String expected = required("taskhub.auth.mock-wechat-code");
    String openid = required("taskhub.auth.mock-wechat-openid");
    String appId = environment.getProperty("taskhub.auth.mock-wechat-app-id", "local-simulator");
    return code -> {
      if (code == null
          || !MessageDigest.isEqual(
              expected.getBytes(StandardCharsets.UTF_8), code.getBytes(StandardCharsets.UTF_8)))
        throw new ApiException(401, 40101, "微信凭证无效");
      return new WechatProvider.WechatIdentity(appId, openid);
    };
  }

  @Bean
  public SmsProvider smsProvider() {
    if (!mock)
      return (phone, code) -> {
        throw new ApiException(503, 50300, "短信服务尚未配置");
      };
    String configured = required("taskhub.auth.mock-sms-code");
    if (!configured.matches("[0-9]{6}")) throw new IllegalStateException("模拟验证码必须显式配置六位数字");
    return new SmsProvider() {
      public void send(String phone, String code) {}

      public String challengeCode() {
        return configured;
      }
    };
  }

  private String required(String key) {
    String value = environment.getProperty(key);
    if (value == null || value.isBlank()) throw new IllegalStateException("模拟身份配置不完整：" + key);
    return value;
  }
}
