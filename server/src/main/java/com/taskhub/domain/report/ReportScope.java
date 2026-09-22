package com.taskhub.domain.report;

import com.taskhub.api.ApiException;
import com.taskhub.domain.identity.AuthorizationService;
import com.taskhub.domain.identity.IdentityModels.Actor;
import com.taskhub.domain.identity.IdentityModels.PlatformGrant;
import java.time.*;
import java.util.*;

public final class ReportScope {
  private ReportScope() {}

  public static ReportModels.ScopedFilter from(Actor actor, ReportModels.ReportFilter filter,
      AuthorizationService authorization) {
    var identity = authorization.refresh(actor);
    PlatformGrant grant = identity.platformGrants().stream()
        .filter(g -> "REPORT_VIEW".equals(g.capability())).findFirst()
        .orElseThrow(AuthorizationService::denied);
    if (filter.warehouseId() != null && !AuthorizationService.covers(grant.scope(), grant.warehouseIds(), filter.warehouseId()))
      throw AuthorizationService.denied();
    if (filter.to().isAfter(filter.from().plusYears(1)))
      throw new ApiException(400, 40001, "报表日期范围不能超过一年");
    var ids = "ALL".equals(grant.scope()) ? List.<String>of() : List.copyOf(grant.warehouseIds());
    return new ReportModels.ScopedFilter(
        filter.from().atStartOfDay(ZoneId.of("Asia/Shanghai")).toLocalDateTime(),
        filter.to().plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toLocalDateTime(),
        filter.warehouseId(), ids);
  }
}
