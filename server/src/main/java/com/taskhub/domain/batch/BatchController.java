package com.taskhub.domain.batch;

import com.taskhub.api.*;
import com.taskhub.domain.identity.IdentityModels.Actor;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/batches")
public class BatchController {
  private final BatchService service;

  public BatchController(BatchService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<PageResponse<Map<String, Object>>> list(
      @AuthenticationPrincipal Actor actor,
      @RequestParam(required = false) String warehouseId,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.success(service.list(actor, warehouseId, status, page, pageSize));
  }

  @GetMapping("/{id}")
  public ApiResponse<Map<String, Object>> get(
      @AuthenticationPrincipal Actor actor, @PathVariable String id) {
    return ApiResponse.success(service.get(actor, id));
  }

  @PostMapping
  public ApiResponse<Map<String, Object>> create(
      @AuthenticationPrincipal Actor actor,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody BatchModels.Create input) {
    return ApiResponse.success(service.create(actor, key, input));
  }

  @PutMapping("/{id}/orders")
  public ApiResponse<Map<String, Object>> replace(
      @AuthenticationPrincipal Actor actor,
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody BatchModels.Members input) {
    return ApiResponse.success(service.replace(actor, id, key, input));
  }
}
