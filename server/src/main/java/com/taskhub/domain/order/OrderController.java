package com.taskhub.domain.order;

import com.taskhub.api.*;
import com.taskhub.domain.identity.IdentityModels.Actor;
import java.time.LocalDate;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class OrderController {
  private final OrderService orders;
  private final ReservationService reservations;

  public OrderController(OrderService orders, ReservationService reservations) {
    this.orders = orders;
    this.reservations = reservations;
  }

  @GetMapping("/catalog/slots")
  public ApiResponse<List<ReservationService.Slot>> slots(
      @AuthenticationPrincipal Actor actor,
      @RequestParam String warehouseId,
      @RequestParam String stopId,
      @RequestParam LocalDate date) {
    return ApiResponse.success(reservations.list(actor, warehouseId, stopId, date));
  }

  @PostMapping("/orders")
  public ApiResponse<Map<String, Object>> create(
      @AuthenticationPrincipal Actor actor,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody OrderModels.Create input) {
    return ApiResponse.success(orders.create(actor, key, input));
  }

  @GetMapping("/orders/{id}")
  public ApiResponse<Map<String, Object>> get(
      @AuthenticationPrincipal Actor actor, @PathVariable String id) {
    return ApiResponse.success(orders.get(actor, id));
  }

  @GetMapping("/orders")
  public ApiResponse<PageResponse<Map<String, Object>>> list(
      @AuthenticationPrincipal Actor actor,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String warehouseId,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.success(
        orders.list(actor, status, warehouseId, keyword, from, to, page, pageSize));
  }

  @PostMapping("/orders/{id}/cancellations")
  public ApiResponse<Map<String, Object>> cancel(
      @AuthenticationPrincipal Actor actor,
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody OrderModels.Cancellation input) {
    return ApiResponse.success(orders.cancel(actor, id, key, input));
  }

  @GetMapping("/history")
  public ApiResponse<PageResponse<Map<String, Object>>> history(
      @AuthenticationPrincipal Actor actor,
      @RequestParam String objectType,
      @RequestParam String objectId,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    if (!"ORDER".equals(objectType)) throw new ApiException(400, 40000, "当前履历类型不支持");
    return ApiResponse.success(orders.history(actor, objectId, from, to, page, pageSize));
  }
}
