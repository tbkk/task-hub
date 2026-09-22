package com.taskhub.domain.masterdata;

import com.taskhub.api.*;
import com.taskhub.domain.identity.IdentityModels.Actor;
import com.taskhub.domain.masterdata.MasterdataModels.*;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class MasterdataController {
  private final MasterdataService service;
  private final CompartmentService compartments;

  public MasterdataController(MasterdataService service, CompartmentService compartments) {
    this.service = service;
    this.compartments = compartments;
  }

  @GetMapping("/warehouses")
  public ApiResponse<PageResponse<Warehouse>> warehouses(
      @AuthenticationPrincipal Actor a,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.success(service.warehouses(a, page, pageSize));
  }

  @GetMapping("/warehouses/{id}")
  public ApiResponse<Warehouse> warehouse(
      @AuthenticationPrincipal Actor a, @PathVariable String id) {
    return ApiResponse.success(service.warehouse(a, id));
  }

  @PostMapping("/warehouses")
  public ApiResponse<Warehouse> createWarehouse(
      @AuthenticationPrincipal Actor a, @RequestBody WarehouseInput v) {
    return ApiResponse.success(service.saveWarehouse(a, null, v));
  }

  @PutMapping("/warehouses/{id}")
  public ApiResponse<Warehouse> updateWarehouse(
      @AuthenticationPrincipal Actor a, @PathVariable String id, @RequestBody WarehouseInput v) {
    return ApiResponse.success(service.saveWarehouse(a, id, v));
  }

  @GetMapping("/stops")
  public ApiResponse<PageResponse<StopView>> stops(
      @AuthenticationPrincipal Actor a,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.success(service.stops(a, page, pageSize));
  }

  @GetMapping("/stops/{id}")
  public ApiResponse<StopView> stop(@AuthenticationPrincipal Actor a, @PathVariable String id) {
    return ApiResponse.success(service.stop(a, id));
  }

  @PostMapping("/stops")
  public ApiResponse<StopView> createStop(
      @AuthenticationPrincipal Actor a, @RequestBody StopInput v) {
    return ApiResponse.success(service.saveStop(a, null, v));
  }

  @PutMapping("/stops/{id}")
  public ApiResponse<StopView> updateStop(
      @AuthenticationPrincipal Actor a, @PathVariable String id, @RequestBody StopInput v) {
    return ApiResponse.success(service.saveStop(a, id, v));
  }

  @GetMapping("/vehicles")
  public ApiResponse<PageResponse<VehicleView>> vehicles(
      @AuthenticationPrincipal Actor a,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.success(service.vehicles(a, page, pageSize));
  }

  @GetMapping("/vehicles/{id}")
  public ApiResponse<VehicleView> vehicle(
      @AuthenticationPrincipal Actor a, @PathVariable String id) {
    return ApiResponse.success(service.vehicle(a, id));
  }

  @PostMapping("/vehicles")
  public ApiResponse<VehicleView> createVehicle(
      @AuthenticationPrincipal Actor a, @RequestBody VehicleInput v) {
    return ApiResponse.success(service.saveVehicle(a, null, v));
  }

  @PutMapping("/vehicles/{id}")
  public ApiResponse<VehicleView> updateVehicle(
      @AuthenticationPrincipal Actor a, @PathVariable String id, @RequestBody VehicleInput v) {
    return ApiResponse.success(service.saveVehicle(a, id, v));
  }

  @GetMapping("/vehicles/{id}/compartments")
  public ApiResponse<List<Compartment>> compartments(
      @AuthenticationPrincipal Actor a, @PathVariable String id) {
    return ApiResponse.success(compartments.list(a, id));
  }

  @PostMapping("/vehicles/{id}/compartments/sync")
  public ApiResponse<List<Compartment>> sync(
      @AuthenticationPrincipal Actor a, @PathVariable String id, @RequestBody VersionInput v) {
    return ApiResponse.success(compartments.sync(a, id, v));
  }

  @PutMapping("/vehicles/{vehicle}/compartments/{id}")
  public ApiResponse<Compartment> compartment(
      @AuthenticationPrincipal Actor a,
      @PathVariable String vehicle,
      @PathVariable String id,
      @RequestBody CompartmentInput v) {
    return ApiResponse.success(compartments.update(a, vehicle, id, v));
  }
}
