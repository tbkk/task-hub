package com.taskhub.domain.audit;

import com.taskhub.api.ApiException;
import com.taskhub.domain.identity.AuthorizationService;
import com.taskhub.domain.identity.IdentityModels.Actor;
import com.taskhub.domain.report.ReportModels;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CorrectionService {
  private static final Set<String> FORBIDDEN = Set.of("receiver", "receiverid", "receivername", "receiverphone", "status", "pickuptime", "pickupat");
  private final JdbcTemplate db; private final AuthorizationService auth;
  public CorrectionService(JdbcTemplate db, AuthorizationService auth) { this.db=db; this.auth=auth; }
  @Transactional
  public ReportModels.Correction append(Actor actor,String objectType,String objectId,ReportModels.CorrectionInput input) {
    var grant = auth.refresh(actor).platformGrants().stream().filter(g -> "AUDIT_VIEW".equals(g.capability())).findFirst().orElseThrow(AuthorizationService::denied);
    if (!"ALL".equals(grant.scope())) throw AuthorizationService.denied();
    if (input == null || input.fieldName()==null || input.fieldName().isBlank() || input.afterValue()==null || input.reason()==null || input.reason().isBlank()) throw new ApiException(400,40001,"更正字段、值和原因必填");
    String field=input.fieldName().toLowerCase(Locale.ROOT).replaceAll("[^a-z]", ""); if (FORBIDDEN.contains(field)) throw new ApiException(400,40003,"接收人、状态和取货时间不可更正");
    Integer version=db.queryForObject("SELECT COALESCE(MAX(correction_version),0) FROM correction_record WHERE object_type=? AND object_id=?",Integer.class,objectType,objectId); int next=version+1;
    if (input.expectedVersion()!=null && input.expectedVersion()!=version) throw new ApiException(409,40902,"更正版本已变化，请刷新后重试");
    String before=null; try { before=db.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(COALESCE(after_state,before_state), CONCAT('$.',?))) FROM audit_event WHERE object_type=? AND object_id=? ORDER BY occurred_at DESC LIMIT 1",String.class,input.fieldName(),objectType,objectId); } catch(Exception ignored) {}
    String id=UUID.randomUUID().toString(); db.update("INSERT INTO correction_record(id,object_type,object_id,field_name,before_value,after_value,reason,correction_version,actor_id) VALUES(?,?,?,?,?,?,?,?,?)",id,objectType,objectId,input.fieldName(),before,input.afterValue(),input.reason(),next,actor.employeeId());
    return new ReportModels.Correction(id,objectType,objectId,input.fieldName(),before,input.afterValue(),input.reason(),next,actor.employeeId(),LocalDateTime.now(Clock.systemUTC()));
  }
}
