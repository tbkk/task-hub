package com.taskhub.domain.integration;

import com.taskhub.api.ApiResponse;
import com.taskhub.domain.identity.*;
import com.taskhub.domain.identity.IdentityModels.Actor;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("(local | test) & !prod & !production")
@ConditionalOnProperty(name = "taskhub.vehicle.simulator-enabled", havingValue = "true")
@RequestMapping("/api/dev/simulator/events")
public class SimulatorEventController {
  private final VehicleEventService service;
  private final AuthorizationService auth;

  public SimulatorEventController(VehicleEventService service, AuthorizationService auth) {
    this.service = service;
    this.auth = auth;
  }

  @PostMapping
  public ApiResponse<Map<String, Object>> event(
      @AuthenticationPrincipal Actor actor, @RequestBody VehicleEvent input) {
    return ApiResponse.success(service.acceptSimulator(actor, input));
  }
}
