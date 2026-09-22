package com.taskhub.domain.integration;

import com.taskhub.api.ApiResponse;
import com.taskhub.domain.identity.IdentityModels.Actor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("(local | test) & !prod & !production")
@ConditionalOnProperty(name = "taskhub.vehicle.simulator-enabled", havingValue = "true")
@RequestMapping("/api/dev/simulator")
public class SimulatorController {
  private final SimulatorScenarioService scenarios;

  public SimulatorController(SimulatorScenarioService scenarios) {
    this.scenarios = scenarios;
  }

  @PutMapping("/scenario")
  public ApiResponse<SimulatorScenarioService.Scenario> scenario(
      @AuthenticationPrincipal Actor actor, @RequestBody SimulatorScenarioService.Scenario input) {
    return ApiResponse.success(scenarios.save(actor, input));
  }
}
