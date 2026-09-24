package com.taskhub.identity;

import static org.junit.jupiter.api.Assertions.*;

import com.taskhub.domain.integration.AuthProviderConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AuthProviderTest {
  @Test
  void mockCannotStartInProductionEvenWithLocalProfile() {
    var env = new MockEnvironment().withProperty("taskhub.auth.mock-enabled", "true");
    env.setActiveProfiles("prod", "local");
    assertThrows(IllegalStateException.class, () -> new AuthProviderConfiguration(env));
  }

  @Test
  void mockRequiresExplicitLocalOrTestAndSecretConfiguration() {
    var env = new MockEnvironment().withProperty("taskhub.auth.mock-enabled", "true");
    assertThrows(IllegalStateException.class, () -> new AuthProviderConfiguration(env));
    env.setActiveProfiles("local");
    var config = new AuthProviderConfiguration(env);
    assertThrows(IllegalStateException.class, config::wechatProvider);
  }

  @Test
  void unconfiguredProductionProviderFailsClosed() {
    var config = new AuthProviderConfiguration(new MockEnvironment());
    assertThrows(
        com.taskhub.api.ApiException.class, () -> config.wechatProvider().exchange("untrusted"));
  }
}
