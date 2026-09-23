package com.taskhub.domain.masterdata;

import com.taskhub.api.*;
import com.taskhub.domain.identity.IdentityModels.Actor;
import com.taskhub.domain.masterdata.MasterdataModels.*;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CatalogController {
  private final CatalogService catalog;
  private final ExternalCatalog external;
  private final MasterdataAccess access;

  public CatalogController(
      CatalogService catalog, ExternalCatalog external, MasterdataAccess access) {
    this.catalog = catalog;
    this.external = external;
    this.access = access;
  }

  @GetMapping("/catalog/warehouses")
  public ApiResponse<List<Warehouse>> warehouses(@AuthenticationPrincipal Actor a) {
    return ApiResponse.success(catalog.warehouses(a));
  }

  @GetMapping("/catalog/stops")
  public ApiResponse<List<StopView>> stops(
      @AuthenticationPrincipal Actor a, @RequestParam String warehouseId) {
    return ApiResponse.success(catalog.stops(a, warehouseId));
  }

  @GetMapping("/catalog/rules")
  public ApiResponse<Rule> rules(
      @AuthenticationPrincipal Actor a, @RequestParam String warehouseId) {
    return ApiResponse.success(catalog.rules(a, warehouseId));
  }

  @GetMapping("/admin/integration-catalog/{resource}")
  public ApiResponse<PageResponse<ExternalResource>> external(
      @AuthenticationPrincipal Actor a,
      @PathVariable String resource,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    access.require(a, "INTEGRATION_MANAGE", null);
    return ApiResponse.success(MasterdataAccess.page(external.list(resource), page, pageSize));
  }
}
