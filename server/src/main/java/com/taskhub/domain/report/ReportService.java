package com.taskhub.domain.report;

import com.taskhub.api.*;
import com.taskhub.domain.identity.AuthorizationService;
import com.taskhub.domain.identity.IdentityModels.Actor;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ReportService {
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
  private final JdbcTemplate db;
  private final AuthorizationService authorization;
  private final ExcelExportService excel;

  public ReportService(JdbcTemplate db, AuthorizationService authorization, ExcelExportService excel) {
    this.db = db; this.authorization = authorization; this.excel = excel;
  }

  public ReportModels.Summary summary(Actor actor, ReportModels.ReportFilter filter) {
    var scope = ReportScope.from(actor, filter, authorization);
    return new ReportModels.Summary(count("delivery_order", "created_at", scope), count("batch", "created_at", scope),
        count("vehicle_task", "created_at", scope));
  }
  public void requireExport(Actor actor) { authorization.requirePlatform(actor, "REPORT_EXPORT", null); }

  public PageResponse<Map<String,Object>> details(Actor actor, ReportModels.ReportFilter filter) {
    var scope = ReportScope.from(actor, filter, authorization);
    var args = new ArrayList<Object>();
    String where = scopeWhere("o", scope, args, "o.created_at");
    String sort = switch (filter.sort() == null ? "created_at" : filter.sort()) {
      case "status" -> "o.status"; case "updated_at" -> "o.updated_at"; default -> "o.created_at";
    };
    String status = "";
    if (filter.status() != null && !filter.status().isBlank()) { status = " AND o.status=?"; args.add(filter.status()); }
    int page = filter.pageValue(), size = filter.pageSizeValue();
    int total = db.queryForObject("SELECT COUNT(*) FROM delivery_order o WHERE " + where + status, Integer.class, args.toArray());
    var queryArgs = new ArrayList<>(args); queryArgs.add((page - 1) * size); queryArgs.add(size);
    List<Map<String,Object>> rows = db.query("SELECT o.id,o.number,o.warehouse_id,o.status,o.created_at FROM delivery_order o WHERE " + where + status
        + " ORDER BY " + sort + " DESC,o.id LIMIT ? OFFSET ?", queryArgs.toArray(), (rs, n) -> {
          var m = new LinkedHashMap<String,Object>(); m.put("id", rs.getString(1)); m.put("number", rs.getString(2));
          m.put("warehouseId", rs.getString(3)); m.put("status", rs.getString(4)); m.put("createdAt", rs.getTimestamp(5).toInstant()); return m;
        });
    return new PageResponse<>(rows, total, page, size);
  }

  public ReportModels.ExportResult export(Actor actor, ReportModels.ReportFilter filter) {
    var scope = ReportScope.from(actor, filter, authorization);
    var args = new ArrayList<Object>(); String where = scopeWhere("o", scope, args, "o.created_at");
    if (filter.status() != null && !filter.status().isBlank()) { where += " AND o.status=?"; args.add(filter.status()); }
    List<List<?>> rows = db.query("SELECT o.id,o.number,o.warehouse_id,o.status,o.created_at FROM delivery_order o WHERE " + where
        + " ORDER BY o.created_at DESC,o.id LIMIT 10001", args.toArray(), (rs,n) -> {
          List<Object> row = new ArrayList<>(); row.add(rs.getString(1)); row.add(rs.getString(2)); row.add(rs.getString(3)); row.add(rs.getString(4)); row.add(rs.getTimestamp(5).toInstant()); return row;
        });
    if (rows.size() > 10000) throw new ApiException(413, 41301, "导出结果超过10000行，请缩小日期范围");
    return new ReportModels.ExportResult(excel.csv(List.of("订单ID","订单号","仓库","状态","创建时间"), rows),
        excel.safeFilename("订单报表", filter.from(), filter.to()), ExcelExportService.MIME);
  }

  private long count(String table, String timeColumn, ReportModels.ScopedFilter scope) {
    var args = new ArrayList<Object>();
    String alias = table.substring(0, 1);
    String from = " FROM " + table + " " + alias;
    if ("vehicle_task".equals(table)) from += " JOIN batch b ON b.id=" + alias + ".batch_id";
    String where = scopeWhere("vehicle_task".equals(table) ? "b" : alias, scope, args, alias + "." + timeColumn);
    return db.queryForObject("SELECT COUNT(*)" + from + " WHERE " + where, Long.class, args.toArray());
  }

  private String scopeWhere(String alias, ReportModels.ScopedFilter scope, List<Object> args, String timeColumn) {
    args.add(Timestamp.from(scope.from().atZone(BUSINESS_ZONE).toInstant()));
    args.add(Timestamp.from(scope.to().atZone(BUSINESS_ZONE).toInstant()));
    StringBuilder where = new StringBuilder(timeColumn + " >= ? AND " + timeColumn + " < ?");
    if (scope.warehouseId() != null) { where.append(" AND ").append(alias).append(".warehouse_id=?"); args.add(scope.warehouseId()); }
    else if (!scope.warehouseIds().isEmpty()) { where.append(" AND ").append(alias).append(".warehouse_id IN (").append("?,".repeat(scope.warehouseIds().size()).replaceAll(",$", "")).append(")"); args.addAll(scope.warehouseIds()); }
    return where.toString();
  }
}
