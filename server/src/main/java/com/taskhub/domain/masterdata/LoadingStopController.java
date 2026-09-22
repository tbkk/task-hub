package com.taskhub.domain.masterdata;

import com.taskhub.api.ApiResponse;
import com.taskhub.domain.identity.IdentityModels.Actor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/warehouses/{id}/loading-stop")
public class LoadingStopController {
  private final LoadingStopService service;

  public LoadingStopController(LoadingStopService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<LoadingStopService.Binding> get(
      @AuthenticationPrincipal Actor actor, @PathVariable String id) {
    return ApiResponse.success(service.get(actor, id));
  }

  @PutMapping
  public ApiResponse<LoadingStopService.Binding> save(
      @AuthenticationPrincipal Actor actor,
      @PathVariable String id,
      @RequestBody LoadingStopService.Input input) {
    return ApiResponse.success(service.save(actor, id, input));
  }
}
