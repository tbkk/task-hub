package com.taskhub.domain.audit;

import com.taskhub.api.*;
import com.taskhub.domain.identity.AuthorizationService;
import com.taskhub.domain.identity.IdentityModels.Actor;
import com.taskhub.domain.report.ReportModels;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/history")
public class HistoryController {
  private final JdbcTemplate db; private final AuthorizationService auth; private final CorrectionService corrections;
  public HistoryController(JdbcTemplate db, AuthorizationService auth, CorrectionService corrections) { this.db=db; this.auth=auth; this.corrections=corrections; }

  @GetMapping
  public ApiResponse<PageResponse<Map<String,Object>>> list(@AuthenticationPrincipal Actor actor, @RequestParam LocalDate from,
      @RequestParam LocalDate to, @RequestParam(required=false) String objectType, @RequestParam(required=false) String action,
      @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize) {
    var identity = auth.refresh(actor);
    if (to.isBefore(from) || to.isAfter(from.plusYears(1))) throw new ApiException(400,40001,"日期范围无效");
    var id = identity.platformGrants().stream().filter(g -> "AUDIT_VIEW".equals(g.capability())).findFirst().orElseThrow(AuthorizationService::denied);
    var args = new ArrayList<Object>(); StringBuilder where = new StringBuilder("a.occurred_at >= ? AND a.occurred_at < ?");
    args.add(java.sql.Timestamp.from(from.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()));
    args.add(java.sql.Timestamp.from(to.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()));
    if (objectType != null && !objectType.isBlank()) { where.append(" AND a.object_type=?"); args.add(objectType); }
    if (action != null && !action.isBlank()) { where.append(" AND a.action=?"); args.add(action); }
    if (!"ALL".equals(id.scope())) {
      if (id.warehouseIds().isEmpty()) return ApiResponse.success(new PageResponse<>(List.of(),0,page,pageSize));
      String marks = "?,".repeat(id.warehouseIds().size()).replaceAll(",$", ""); args.addAll(id.warehouseIds());
      where.append(" AND ((a.object_type='WAREHOUSE' AND a.object_id IN (").append(marks).append(")) OR EXISTS (SELECT 1 FROM delivery_order x WHERE a.object_type='ORDER' AND x.id=a.object_id AND x.warehouse_id IN (").append(marks).append(")) OR EXISTS (SELECT 1 FROM batch x WHERE a.object_type='BATCH' AND x.id=a.object_id AND x.warehouse_id IN (").append(marks).append(")) OR EXISTS (SELECT 1 FROM ticket x WHERE a.object_type='TICKET' AND x.id=a.object_id AND x.warehouse_id IN (").append(marks).append(")))");
      args.addAll(id.warehouseIds()); args.addAll(id.warehouseIds()); args.addAll(id.warehouseIds());
    }
    int safeSize=Math.min(Math.max(pageSize,1),100); int total=db.queryForObject("SELECT COUNT(*) FROM audit_event a WHERE "+where,Integer.class,args.toArray());
    var rowArgs=new ArrayList<>(args); rowArgs.add((page-1)*safeSize); rowArgs.add(safeSize);
    List<Map<String,Object>> rows=db.query("SELECT a.id,a.actor_id,a.actor_name,a.workspace,a.object_type,a.object_id,a.action,a.before_state,a.after_state,a.reason,a.occurred_at FROM audit_event a WHERE "+where+" ORDER BY a.occurred_at DESC,a.id DESC LIMIT ? OFFSET ?",rowArgs.toArray(),(rs,n)->{Map<String,Object> m=new LinkedHashMap<>(); m.put("id",rs.getString(1));m.put("actorId",rs.getString(2));m.put("actorName",rs.getString(3));m.put("workspace",rs.getString(4));m.put("objectType",rs.getString(5));m.put("objectId",rs.getString(6));m.put("action",rs.getString(7));m.put("before",rs.getString(8));m.put("after",rs.getString(9));m.put("reason",rs.getString(10));m.put("occurredAt",rs.getTimestamp(11).toInstant());return m;});
    return ApiResponse.success(new PageResponse<>(rows,total,page,safeSize));
  }

  @PostMapping("/{objectType}/{objectId}/corrections")
  public ApiResponse<ReportModels.Correction> correct(@AuthenticationPrincipal Actor actor,@PathVariable String objectType,@PathVariable String objectId,@RequestBody ReportModels.CorrectionInput input) {
    return ApiResponse.success(corrections.append(actor,objectType,objectId,input));
  }
}
