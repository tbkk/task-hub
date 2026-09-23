package com.taskhub.domain.identity;

import com.taskhub.api.*;
import com.taskhub.domain.identity.IdentityModels.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/employees")
public class EmployeeController {
  private final EmployeeService employees;

  public EmployeeController(EmployeeService employees) {
    this.employees = employees;
  }

  @GetMapping
  public ApiResponse<PageResponse<EmployeeView>> list(
      @AuthenticationPrincipal Actor actor,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Boolean enabled,
      @RequestParam(required = false) String warehouseId,
      @RequestParam(required = false) String role,
      @RequestParam(required = false) Boolean phoneVerified,
      @RequestParam(required = false) Boolean wechatBound,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.success(
        employees.list(
            actor,
            keyword,
            enabled,
            warehouseId,
            role,
            phoneVerified,
            wechatBound,
            page,
            pageSize));
  }

  @GetMapping("/warehouse-options")
  public ApiResponse<PageResponse<WarehouseOption>> warehouseOptions(
      @AuthenticationPrincipal Actor actor,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "100") int pageSize) {
    return ApiResponse.success(employees.warehouseOptions(actor, page, pageSize));
  }

  @GetMapping("/{id}")
  public ApiResponse<EmployeeView> get(
      @AuthenticationPrincipal Actor actor, @PathVariable String id) {
    return ApiResponse.success(employees.get(actor, id));
  }

  @PostMapping
  public ApiResponse<EmployeeView> create(
      @AuthenticationPrincipal Actor actor, @RequestBody EmployeeInput input) {
    return ApiResponse.success(employees.create(actor, input));
  }

  @PutMapping("/{id}")
  public ApiResponse<EmployeeView> update(
      @AuthenticationPrincipal Actor actor,
      @PathVariable String id,
      @RequestBody EmployeeInput input) {
    return ApiResponse.success(employees.update(actor, id, input));
  }

  @PostMapping("/{id}/credentials")
  public ApiResponse<Void> credentials(
      @AuthenticationPrincipal Actor actor,
      @PathVariable String id,
      @RequestBody CredentialsInput input) {
    employees.credentials(actor, id, input);
    return ApiResponse.success(null);
  }
}
