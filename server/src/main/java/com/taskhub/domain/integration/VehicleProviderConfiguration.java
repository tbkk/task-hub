package com.taskhub.domain.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.api.ApiException;
import java.util.Arrays;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class VehicleProviderConfiguration {
  private final boolean simulated;

  public VehicleProviderConfiguration(Environment env) {
    simulated = env.getProperty("taskhub.vehicle.simulator-enabled", Boolean.class, false);
    var profiles = Arrays.asList(env.getActiveProfiles());
    if (simulated
        && (profiles.contains("prod")
            || profiles.contains("production")
            || (!profiles.contains("local") && !profiles.contains("test"))))
      throw new IllegalStateException("车辆模拟仅允许local/test，生产禁止启用");
  }

  @Bean
  public VehicleGateway vehicleGateway(
      JdbcTemplate db, ObjectMapper json, PlatformTransactionManager manager) {
    if (simulated) return new SimulatorVehicleGateway(db, json, manager);
    return new VehicleGateway() {
      private ApiException unavailable() {
        return new ApiException(503, 50300, "车辆提供者尚未配置");
      }

      public GatewayResult dispatch(DispatchCommand c) {
        throw unavailable();
      }

      public GatewayResult open(OpenCommand c) {
        throw unavailable();
      }

      public GatewayResult go(TaskCommand c) {
        throw unavailable();
      }

      public GatewayResult cancel(TaskCommand c) {
        throw unavailable();
      }

      public GatewayResult queryCommand(String id) {
        throw unavailable();
      }
    };
  }
}
