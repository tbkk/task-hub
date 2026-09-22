package com.taskhub.domain.identity;

import java.time.Instant;
import org.apache.ibatis.annotations.*;

@Mapper
public interface RateLimitMapper {
  record Bucket(String rateKey, Instant windowStart, int attempts) {}

  @Insert(
      "INSERT INTO auth_rate_limit(rate_key,window_start,attempts) VALUES(#{key},#{now},0) ON"
          + " DUPLICATE KEY UPDATE rate_key=rate_key")
  void ensure(@Param("key") String key, @Param("now") Instant now);

  @Select(
      "SELECT rate_key,window_start,attempts FROM auth_rate_limit WHERE rate_key=#{key} FOR UPDATE")
  Bucket lock(String key);

  @Update(
      "UPDATE auth_rate_limit SET window_start=#{start},attempts=#{attempts} WHERE rate_key=#{key}")
  void update(
      @Param("key") String key, @Param("start") Instant start, @Param("attempts") int attempts);

  @Delete("DELETE FROM auth_rate_limit WHERE rate_key=#{key}")
  void clear(String key);
}
