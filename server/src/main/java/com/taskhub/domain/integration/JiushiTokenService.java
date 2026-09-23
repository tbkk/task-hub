package com.taskhub.domain.integration;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.web.client.RestClient;

/** 九识 token 缓存边界；真实凭证只从受限配置读取，绝不写日志。 */
public final class JiushiTokenService {
  private final RestClient client;
  private final String appId;
  private final String appKey;
  private final AtomicReference<Cached> cached = new AtomicReference<>();

  public JiushiTokenService(RestClient client, String appId, String appKey) {
    this.client = client;
    this.appId = appId;
    this.appKey = appKey;
  }

  public synchronized String token() {
    var current = cached.get();
    if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(30))) return current.value();
    return refresh();
  }

  public synchronized void invalidate() { cached.set(null); }

  @SuppressWarnings("unchecked")
  private String refresh() {
    Map<String, Object> envelope = client.post().uri("/app/accessToken").body(Map.of("appId", appId, "appKey", appKey)).retrieve().body(Map.class);
    if (envelope == null || !Boolean.TRUE.equals(envelope.get("success"))) throw new IllegalStateException("九识认证失败");
    var data = (Map<String, Object>) envelope.get("data");
    String value = String.valueOf(data.get("token"));
    long minutes = data.get("expiresAfter") instanceof Number n ? n.longValue() : 5L;
    cached.set(new Cached(value, Instant.now().plusSeconds(Math.max(60, minutes * 60 - 60))));
    return value;
  }

  private record Cached(String value, Instant expiresAt) {}
}
