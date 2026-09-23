package com.taskhub.domain.dispatch;

import com.taskhub.api.ApiResponse;
import com.taskhub.domain.identity.IdentityModels.Actor;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class DispatchController {
  private final DispatchService service;

  public DispatchController(DispatchService service) {
    this.service = service;
  }

  @PostMapping("/batches/{id}/dispatch")
  public ApiResponse<Map<String, Object>> dispatch(
      @AuthenticationPrincipal Actor actor,
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody DispatchModels.Input input) {
    return ApiResponse.success(service.dispatch(actor, id, key, input));
  }

  @GetMapping("/dispatch-requests/{id}")
  public ApiResponse<Map<String, Object>> get(
      @AuthenticationPrincipal Actor actor, @PathVariable String id) {
    return ApiResponse.success(service.get(actor, id));
  }

  @PostMapping("/dispatch-requests/{id}/reconcile")
  public ApiResponse<Map<String, Object>> reconcile(
      @AuthenticationPrincipal Actor actor, @PathVariable String id) {
    return ApiResponse.success(service.reconcile(actor, id));
  }
}
