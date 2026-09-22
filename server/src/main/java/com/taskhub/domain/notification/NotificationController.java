package com.taskhub.domain.notification;

import com.taskhub.api.*;
import com.taskhub.domain.identity.IdentityModels.Actor;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/messages")
public class NotificationController {
  private final NotificationService service;

  public NotificationController(NotificationService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<PageResponse<Map<String, Object>>> list(
      @AuthenticationPrincipal Actor actor,
      @RequestParam(defaultValue = "false") boolean unreadOnly,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.success(service.list(actor, unreadOnly, page, pageSize));
  }

  @GetMapping("/unread-count")
  public ApiResponse<Map<String, Integer>> count(@AuthenticationPrincipal Actor actor) {
    return ApiResponse.success(service.count(actor));
  }

  @GetMapping("/{id}")
  public ApiResponse<Map<String, Object>> get(
      @AuthenticationPrincipal Actor actor, @PathVariable String id) {
    return ApiResponse.success(service.get(actor, id));
  }

  @PutMapping("/{id}/read")
  public ApiResponse<Map<String, Object>> read(
      @AuthenticationPrincipal Actor actor, @PathVariable String id) {
    return ApiResponse.success(service.read(actor, id));
  }
}
