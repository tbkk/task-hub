package com.taskhub.domain.order;

import com.taskhub.api.ApiResponse;
import com.taskhub.domain.identity.IdentityModels.Actor;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class WarehouseOrderController {
  private final WarehouseOrderService service;

  public WarehouseOrderController(WarehouseOrderService service) {
    this.service = service;
  }

  @PostMapping("/{id}/review")
  public ApiResponse<Map<String, Object>> review(
      @AuthenticationPrincipal Actor actor,
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody WarehouseOrderService.Review input) {
    return ApiResponse.success(service.review(actor, id, key, input));
  }

  @PostMapping("/{id}/cancellations/{cancellationId}/review")
  public ApiResponse<Map<String, Object>> cancellation(
      @AuthenticationPrincipal Actor actor,
      @PathVariable String id,
      @PathVariable String cancellationId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody WarehouseOrderService.Review input) {
    return ApiResponse.success(service.cancellation(actor, id, cancellationId, key, input));
  }
}
