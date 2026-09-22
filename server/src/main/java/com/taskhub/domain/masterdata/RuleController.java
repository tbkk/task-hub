package com.taskhub.domain.masterdata;

import com.taskhub.api.ApiResponse;
import com.taskhub.domain.identity.IdentityModels.Actor;
import com.taskhub.domain.masterdata.MasterdataModels.Rule;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/rules")
public class RuleController {
  private final RuleService service;

  public RuleController(RuleService service) {
    this.service = service;
  }

  @GetMapping("/warehouses")
  public ApiResponse<
          com.taskhub.api.PageResponse<com.taskhub.domain.masterdata.MasterdataModels.Warehouse>>
      warehouses(
          @AuthenticationPrincipal Actor a,
          @RequestParam(defaultValue = "1") int page,
          @RequestParam(defaultValue = "100") int pageSize) {
    return ApiResponse.success(service.warehouses(a, page, pageSize));
  }

  @GetMapping("/{warehouse}")
  public ApiResponse<Rule> get(@AuthenticationPrincipal Actor a, @PathVariable String warehouse) {
    return ApiResponse.success(service.get(a, warehouse));
  }

  @PutMapping("/{warehouse}")
  public ApiResponse<Rule> update(
      @AuthenticationPrincipal Actor a, @PathVariable String warehouse, @RequestBody Rule v) {
    return ApiResponse.success(service.update(a, warehouse, v));
  }
}
