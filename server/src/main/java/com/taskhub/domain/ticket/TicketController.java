package com.taskhub.domain.ticket;

import com.taskhub.api.*;
import com.taskhub.domain.identity.IdentityModels.Actor;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {
  private final TicketService service;

  public TicketController(TicketService service) {
    this.service = service;
  }

  @PostMapping
  public ApiResponse<Map<String, Object>> create(
      @AuthenticationPrincipal Actor actor,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody TicketModels.Create input) {
    return ApiResponse.success(service.create(actor, key, input));
  }

  @GetMapping
  public ApiResponse<PageResponse<Map<String, Object>>> list(
      @AuthenticationPrincipal Actor actor,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String warehouseId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.success(service.list(actor, state, warehouseId, page, pageSize));
  }

  @GetMapping("/{id}")
  public ApiResponse<Map<String, Object>> get(
      @AuthenticationPrincipal Actor actor, @PathVariable String id) {
    return ApiResponse.success(service.get(actor, id));
  }

  @PostMapping("/{id}/{action:accept|notes|close}")
  public ApiResponse<Map<String, Object>> action(
      @AuthenticationPrincipal Actor actor,
      @PathVariable String id,
      @PathVariable String action,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody TicketModels.Action input) {
    return ApiResponse.success(service.action(actor, id, key, action, input));
  }
}
