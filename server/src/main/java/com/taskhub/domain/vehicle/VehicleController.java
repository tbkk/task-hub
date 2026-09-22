package com.taskhub.domain.vehicle;

import com.taskhub.api.*;
import com.taskhub.domain.identity.IdentityModels.Actor;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class VehicleController {
  private final VehicleQueryService service;

  public VehicleController(VehicleQueryService service) {
    this.service = service;
  }

  @GetMapping("/vehicles")
  public ApiResponse<PageResponse<Map<String, Object>>> vehicles(
      @AuthenticationPrincipal Actor actor,
      @RequestParam(required = false) String warehouseId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.success(service.list(actor, warehouseId, page, pageSize));
  }

  @GetMapping("/vehicles/{id}")
  public ApiResponse<Map<String, Object>> vehicle(
      @AuthenticationPrincipal Actor actor, @PathVariable String id) {
    return ApiResponse.success(service.get(actor, id));
  }

  @GetMapping("/tasks")
  public ApiResponse<PageResponse<Map<String, Object>>> tasks(
      @AuthenticationPrincipal Actor actor,
      @RequestParam(required = false) String warehouseId,
      @RequestParam(required = false) String state,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.success(service.tasks(actor, warehouseId, state, page, pageSize));
  }

  @GetMapping("/tasks/{id}")
  public ApiResponse<Map<String, Object>> task(
      @AuthenticationPrincipal Actor actor, @PathVariable String id) {
    return ApiResponse.success(service.task(actor, id));
  }
}
