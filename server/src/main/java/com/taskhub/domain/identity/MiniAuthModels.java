package com.taskhub.domain.identity;

import com.taskhub.domain.identity.IdentityModels.Session;
import java.time.Instant;

public final class MiniAuthModels {
  private MiniAuthModels() {}

  public record WechatInput(String code) {}

  public record WechatExchange(
      String status, Session session, String bindingToken, Instant expiresAt) {}

  public record BindInput(String bindingToken, String username, String password) {}

  public record Binding(
      String tokenHash,
      String appId,
      String openid,
      Instant expiresAt,
      Instant consumedAt) {}
}
