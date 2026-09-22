package com.taskhub.report;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.taskhub.domain.identity.AuthorizationService;
import com.taskhub.domain.identity.IdentityModels.*;
import com.taskhub.domain.report.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ReportIT {
  private final AuthorizationService auth = mock(AuthorizationService.class);
  private Identity identity(String scope, List<String> warehouses) {
    return new Identity("e", "测试", null, List.of(), List.of(new PlatformGrant("REPORT_VIEW", scope, warehouses)), false, false);
  }
  private Actor actor(String scope, List<String> warehouses) { return new Actor("e", "s", "ADMIN", identity(scope, warehouses)); }

  @Test void shanghaiDateFilterUsesExclusiveNextDayBoundary() {
    when(auth.refresh(any())).thenReturn(identity("ALL", List.of()));
    var scoped = ReportScope.from(actor("ALL", List.of()), new ReportModels.ReportFilter(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 22), null, null, null, 1, 20), auth);
    assertEquals(LocalDateTime.of(2026, 9, 22, 0, 0), scoped.from());
    assertEquals(LocalDateTime.of(2026, 9, 23, 0, 0), scoped.to());
  }

  @Test void warehouseScopeRejectsAnotherWarehouse() {
    when(auth.refresh(any())).thenReturn(identity("WAREHOUSES", List.of("w1")));
    assertThrows(com.taskhub.api.ApiException.class, () -> ReportScope.from(actor("WAREHOUSES", List.of("w1")), new ReportModels.ReportFilter(LocalDate.now(), LocalDate.now(), "w2", null, null, 1, 20), auth));
  }

  @Test void exportCellsAreFormulaSafeAndFilenameDoesNotAllowPath() {
    var exporter = new ExcelExportService();
    assertTrue(new String(exporter.csv(List.of("值"), List.of(List.of("=SUM(A1)"))), java.nio.charset.StandardCharsets.UTF_8).contains("'=SUM(A1)"));
    assertFalse(exporter.safeFilename("../secret/报表", LocalDate.now(), LocalDate.now()).contains("/"));
  }
}
