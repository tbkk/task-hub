package com.taskhub.domain.identity;

import com.taskhub.domain.identity.IdentityModels.Session;
import java.time.Instant;

public final class MiniAuthModels {
  private MiniAuthModels() {}

  public record WechatInput(String code) {}

  public record WechatExchange(
      String status, Session session, String bindingToken, Instant expiresAt) {}

  public record SmsInput(String phone, String purpose, String bindingToken) {}

  public record VerifyInput(String challengeId, String phone, String code, String bindingToken) {}

  public record ChallengeResponse(String challengeId, int retryAfterSeconds, Instant expiresAt) {}

  public record Binding(
      String tokenHash,
      String appId,
      String openid,
      String phone,
      Instant expiresAt,
      Instant consumedAt) {}

  public record Challenge(
      String id,
      String phone,
      String purpose,
      String codeHash,
      String bindingHash,
      int attempts,
      Instant expiresAt,
      Instant consumedAt) {}
}
