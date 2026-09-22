package com.taskhub.domain.identity;

import com.taskhub.api.ApiException;
import com.taskhub.domain.identity.IdentityModels.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {
  public static final Set<String> PLATFORM_CAPABILITIES =
      Set.of(
          "EMPLOYEE_MANAGE",
          "MASTERDATA_MANAGE",
          "RULE_MANAGE",
          "INTEGRATION_MANAGE",
          "REPORT_VIEW",
          "REPORT_EXPORT",
          "AUDIT_VIEW");
  public static final Set<String> ROLES = Set.of("worker", "warehouse", "dispatch", "overview");
  private final SessionService sessions;
  private final EmployeeMapper employees;

  public AuthorizationService(SessionService sessions, EmployeeMapper employees) {
    this.sessions = sessions;
    this.employees = employees;
  }

  public Identity refresh(Actor actor) {
    if (actor == null) throw SessionService.invalid();
    return sessions.refreshActor(actor);
  }

  /** 在业务事务内先锁员工，再锁业务对象；撤权提交后新命令不能沿用旧快照。 */
  public void lockActor(Actor actor) {
    if (actor == null) throw SessionService.invalid();
    employees.lock(actor.employeeId());
    refresh(actor);
  }

  public Grant requireWorkspace(Actor actor, String role) {
    var identity = refresh(actor);
    if (!Objects.equals(actor.workspace(), role)) throw denied();
    return identity.grants().stream()
        .filter(g -> g.role().equals(role))
        .findFirst()
        .orElseThrow(AuthorizationService::denied);
  }

  public Grant require(Actor actor, String capability, String warehouseId) {
    String role =
        switch (capability) {
          case "ORDER_CREATE", "ORDER_CANCEL", "PICKUP", "FAVORITES" -> "worker";
          case "ORDER_REVIEW", "BATCH_MANAGE", "LOAD", "TICKET_MANAGE", "HISTORY_CORRECT" ->
              "warehouse";
          case "DISPATCH", "VEHICLE_CONTROL" -> "dispatch";
          case "OVERVIEW" -> "overview";
          default -> throw denied();
        };
    Grant grant = requireWorkspace(actor, role);
    if (!role.equals("worker") && !covers(grant.scope(), grant.warehouseIds(), warehouseId))
      throw denied();
    return grant;
  }

  public PlatformGrant requirePlatform(Actor actor, String capability, String warehouseId) {
    var identity = refresh(actor);
    var grant =
        identity.platformGrants().stream()
            .filter(g -> g.capability().equals(capability))
            .findFirst()
            .orElseThrow(AuthorizationService::denied);
    if (!covers(grant.scope(), grant.warehouseIds(), warehouseId)) throw denied();
    return grant;
  }

  public static boolean covers(String scope, List<String> warehouses, String warehouse) {
    return "ALL".equals(scope)
        || ("WAREHOUSES".equals(scope) && warehouse != null && warehouses.contains(warehouse));
  }

  public static ApiException denied() {
    return new ApiException(403, 40302, "当前授权不足或已变化");
  }
}
