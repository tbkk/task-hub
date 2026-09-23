package com.taskhub.domain.identity;

import com.taskhub.api.ApiException;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class AuthRateLimiter {
  private final RateLimitMapper mapper;

  public AuthRateLimiter(RateLimitMapper mapper) {
    this.mapper = mapper;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void consume(String rawKey, int seconds, int maximum) {
    String key = TokenCodec.hash(rawKey);
    Instant now = Instant.now();
    mapper.ensure(key, now);
    var row = mapper.lock(key);
    boolean reset = !row.windowStart().plusSeconds(seconds).isAfter(now);
    int attempts = reset ? 0 : row.attempts();
    if (attempts >= maximum) throw new ApiException(429, 42901, "操作过于频繁，请稍后重试");
    mapper.update(key, reset ? now : row.windowStart(), attempts + 1);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void clear(String rawKey) {
    mapper.clear(TokenCodec.hash(rawKey));
  }
}
