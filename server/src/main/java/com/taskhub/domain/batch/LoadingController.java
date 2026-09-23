package com.taskhub.domain.batch;

import com.taskhub.api.ApiResponse;
import com.taskhub.domain.identity.IdentityModels.Actor;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/batches/{id}/loading")
public class LoadingController {
  private final LoadingService service;

  public LoadingController(LoadingService service) {
    this.service = service;
  }

  @PutMapping
  public ApiResponse<Map<String, Object>> assign(
      @AuthenticationPrincipal Actor actor,
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody BatchModels.Loading input) {
    return ApiResponse.success(service.assign(actor, id, key, input));
  }

  @PostMapping("/confirm")
  public ApiResponse<Map<String, Object>> confirm(
      @AuthenticationPrincipal Actor actor,
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody BatchModels.Confirm input) {
    return ApiResponse.success(service.confirm(actor, id, key, input));
  }
}
