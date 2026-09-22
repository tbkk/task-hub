package com.taskhub.domain.report;

import com.taskhub.api.*;
import com.taskhub.domain.identity.IdentityModels.Actor;
import java.time.LocalDate;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/reports")
public class ReportController {
  private final ReportService reports;
  public ReportController(ReportService reports) { this.reports = reports; }

  @GetMapping("/summary")
  public ApiResponse<ReportModels.Summary> summary(@AuthenticationPrincipal Actor actor, @RequestParam LocalDate from,
      @RequestParam LocalDate to, @RequestParam(required=false) String warehouseId, @RequestParam(required=false) String status) {
    return ApiResponse.success(reports.summary(actor, new ReportModels.ReportFilter(from,to,warehouseId,status,null,1,20)));
  }

  @GetMapping("/details")
  public ApiResponse<PageResponse<java.util.Map<String,Object>>> details(@AuthenticationPrincipal Actor actor,
      @RequestParam LocalDate from, @RequestParam LocalDate to, @RequestParam(required=false) String warehouseId,
      @RequestParam(required=false) String status, @RequestParam(required=false) String sort,
      @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize) {
    return ApiResponse.success(reports.details(actor, new ReportModels.ReportFilter(from,to,warehouseId,status,sort,page,pageSize)));
  }

  @GetMapping("/export")
  public ResponseEntity<byte[]> export(@AuthenticationPrincipal Actor actor, @RequestParam LocalDate from,
      @RequestParam LocalDate to, @RequestParam(required=false) String warehouseId, @RequestParam(required=false) String status) {
    var result = reports.export(actor, new ReportModels.ReportFilter(from,to,warehouseId,status,null,1,100));
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(result.contentType()))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" +
            java.net.URLEncoder.encode(result.filename(), java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"))
        .body(result.bytes());
  }
}
