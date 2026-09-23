package com.taskhub.domain.report;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ReportModels {
  private ReportModels() {}

  public record ReportFilter(
      LocalDate from, LocalDate to, String warehouseId, String status, String sort, Integer page, Integer pageSize) {
    public ReportFilter {
      if (from == null || to == null) throw new IllegalArgumentException("日期范围不能为空");
      if (to.isBefore(from)) throw new IllegalArgumentException("结束日期不能早于开始日期");
    }
    public int pageValue() { return page == null || page < 1 ? 1 : page; }
    public int pageSizeValue() { return pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100); }
  }

  public record ScopedFilter(LocalDateTime from, LocalDateTime to, String warehouseId, List<String> warehouseIds) {}
  public record Summary(long orders, long batches, long tasks) {}
  public record Detail(String id, String number, String warehouseId, String status, LocalDateTime createdAt) {}
  public record ExportResult(byte[] bytes, String filename, String contentType) {}
  public record CorrectionInput(String fieldName, String afterValue, String reason, Integer expectedVersion) {}
  public record Correction(String id, String objectType, String objectId, String fieldName, String beforeValue,
      String afterValue, String reason, int version, String actorId, LocalDateTime createdAt) {}
}
