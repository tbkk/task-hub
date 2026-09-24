package com.taskhub.domain.identity;

import com.taskhub.api.*;
import com.taskhub.domain.identity.IdentityModels.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeService {
  private final EmployeeMapper employees;
  private final CredentialMapper credentials;
  private final SessionMapper sessions;
  private final AuthorizationService authorization;
  private final AdminAuthService auth;

  public EmployeeService(
      EmployeeMapper employees,
      CredentialMapper credentials,
      SessionMapper sessions,
      AuthorizationService authorization,
      AdminAuthService auth) {
    this.employees = employees;
    this.credentials = credentials;
    this.sessions = sessions;
    this.authorization = authorization;
    this.auth = auth;
  }

  public EmployeeView view(String id) {
    var e = employees.find(id);
    if (e == null) throw new ApiException(404, 40400, "员工不存在");
    return view(e);
  }

  private EmployeeView view(Employee e) {
    return new EmployeeView(
        e.id(),
        e.name(),
        e.phone(),
        e.enabled(),
        e.version(),
        e.createdAt(),
        e.updatedAt(),
        employees.verifiedPhone(e.id()),
        employees.homeWarehouse(e.id()),
        employees.wechatBound(e.id()),
        employees.roles(e.id()).stream()
            .map(g -> new Grant(g.role(), g.scope(), employees.roleWarehouses(g.id())))
            .toList(),
        employees.platforms(e.id()).stream()
            .map(
                g ->
                    new PlatformGrant(
                        g.capability(), g.scope(), employees.platformWarehouses(g.id())))
            .toList());
  }

  public PageResponse<EmployeeView> list(
      Actor actor,
      String keyword,
      Boolean enabled,
      String warehouseId,
      String role,
      Boolean phoneVerified,
      Boolean wechatBound,
      int page,
      int size) {
    authorization.requirePlatform(actor, "EMPLOYEE_MANAGE", null);
    if (page < 1 || page > 100000 || size < 1 || size > 100)
      throw IdentityValidation.bad("分页参数不正确");
    if (role != null && !AuthorizationService.ROLES.contains(role))
      throw IdentityValidation.bad("角色筛选无效");
    return new PageResponse<>(
        employees
            .list(
                keyword,
                enabled,
                warehouseId,
                role,
                phoneVerified,
                wechatBound,
                size,
                (page - 1) * size)
            .stream()
            .map(this::view)
            .toList(),
        employees.count(keyword, enabled, warehouseId, role, phoneVerified, wechatBound),
        page,
        size);
  }

  public PageResponse<WarehouseOption> warehouseOptions(Actor actor, int page, int size) {
    authorization.requirePlatform(actor, "EMPLOYEE_MANAGE", null);
    if (page < 1 || page > 100000 || size < 1 || size > 100)
      throw IdentityValidation.bad("分页参数不正确");
    return new PageResponse<>(
        employees.warehouseOptions(size, (page - 1) * size),
        employees.warehouseCount(),
        page,
        size);
  }

  public EmployeeView get(Actor actor, String id) {
    authorization.requirePlatform(actor, "EMPLOYEE_MANAGE", null);
    return view(id);
  }

  @Transactional
  public EmployeeView create(Actor actor, EmployeeInput input) {
    employees.lockAdministration();
    authorization.lockActor(actor);
    authorization.requirePlatform(actor, "EMPLOYEE_MANAGE", null);
    EmployeeInput value = validate(actor, input, null);
    String id = UUID.randomUUID().toString();
    try {
      employees.insert(id, value.name(), value.phone(), value.enabled());
    } catch (DuplicateKeyException e) {
      throw new ApiException(409, 40900, "手机号已准入");
    }
    employees.setHomeWarehouse(id, value.homeWarehouseId());
    saveGrants(id, value);
    return view(id);
  }

  @Transactional
  public EmployeeView update(Actor actor, String id, EmployeeInput input) {
    employees.lockAdministration();
    authorization.lockActor(actor);
    authorization.requirePlatform(actor, "EMPLOYEE_MANAGE", null);
    if (employees.lock(id) == null) throw new ApiException(404, 40400, "员工不存在");
    EmployeeInput value = validate(actor, input, employees.homeWarehouse(id));
    if (value.expectedVersion() == null || value.expectedVersion() < 0)
      throw IdentityValidation.bad("expectedVersion必填");
    if (employees.lock(id) == null) throw new ApiException(404, 40400, "员工不存在");
    int before = employees.usableAdministrators();
    try {
      if (employees.update(id, value) != 1) throw new ApiException(409, 40902, "版本已变化，请重新读取");
    } catch (DuplicateKeyException e) {
      throw new ApiException(409, 40900, "手机号已准入");
    }
    employees.setHomeWarehouse(id, value.homeWarehouseId());
    saveGrants(id, value);
    if (!value.enabled()) sessions.revokeAll(id);
    if (before > 0 && employees.usableAdministrators() == 0)
      throw new ApiException(409, 40900, "不能停用或撤销最后一名可用管理员");
    return view(id);
  }

  @Transactional
  public void credentials(Actor actor, String id, CredentialsInput input) {
    employees.lockAdministration();
    authorization.lockActor(actor);
    authorization.requirePlatform(actor, "EMPLOYEE_MANAGE", null);
    if (employees.lock(id) == null) throw new ApiException(404, 40400, "员工不存在");
    String username = IdentityValidation.username(input.username());
    String hash = auth.encode(input.temporaryPassword());
    var owner = credentials.byUsername(username);
    if (owner != null && !owner.employeeId().equals(id))
      throw new ApiException(409, 40900, "用户名已使用");
    if (credentials.byEmployee(id) == null) credentials.insert(id, username, hash, true);
    else credentials.update(id, username, hash, true);
    // A credential reset invalidates every channel so an old token cannot keep
    // accessing the account after the administrator assigns a new password.
    sessions.revokeAll(id);
  }

  private EmployeeInput validate(Actor actor, EmployeeInput input, String previousWarehouse) {
    String name = IdentityValidation.required(input.name(), 80, "姓名"),
        phone = IdentityValidation.phone(input.phone());
    if (input.enabled() == null || input.grants() == null || input.platformGrants() == null)
      throw IdentityValidation.bad("启用状态和授权列表必填");
    if (input.homeWarehouseId() != null
        && !Objects.equals(input.homeWarehouseId(), previousWarehouse)
        && !Boolean.TRUE.equals(employees.lockWarehouse(input.homeWarehouseId())))
      throw IdentityValidation.bad("所属仓库不存在或已停用");
    Set<String> roles = new HashSet<>(), capabilities = new HashSet<>();
    for (Grant grant : input.grants()) {
      if (grant == null
          || (grant.role() == null || !AuthorizationService.ROLES.contains(grant.role()))
          || !roles.add(grant.role())) throw IdentityValidation.bad("角色无效或重复");
      validateScope(grant.scope(), grant.warehouseIds(), grant.role().equals("worker"));
      // EMPLOYEE_MANAGE ALL explicitly delegates business roles; it does not confer business
      // execution rights.
    }
    var own = authorization.refresh(actor).platformGrants();
    for (PlatformGrant grant : input.platformGrants()) {
      if (grant == null
          || (grant.capability() == null
              || !AuthorizationService.PLATFORM_CAPABILITIES.contains(grant.capability()))
          || !capabilities.add(grant.capability())) throw IdentityValidation.bad("平台能力无效或重复");
      validateScope(grant.scope(), grant.warehouseIds(), false);
      var editor =
          own.stream()
              .filter(g -> g.capability().equals(grant.capability()))
              .findFirst()
              .orElseThrow(AuthorizationService::denied);
      if (!editor.scope().equals("ALL")
          && (!grant.scope().equals("WAREHOUSES")
              || !editor.warehouseIds().containsAll(grant.warehouseIds())))
        throw AuthorizationService.denied();
    }
    return new EmployeeInput(
        name,
        phone,
        input.homeWarehouseId(),
        input.enabled(),
        input.grants(),
        input.platformGrants(),
        input.expectedVersion());
  }

  private void validateScope(String scope, List<String> ids, boolean worker) {
    if (ids == null
        || ids.stream().anyMatch(id -> id == null || id.isBlank() || id.length() > 36)
        || new HashSet<>(ids).size() != ids.size()) throw IdentityValidation.bad("仓库范围无效");
    if (worker) {
      if (!"SELF".equals(scope) || !ids.isEmpty()) throw IdentityValidation.bad("工人仅允许SELF范围");
    } else if (!("ALL".equals(scope) && ids.isEmpty())
        && !("WAREHOUSES".equals(scope) && !ids.isEmpty())) throw IdentityValidation.bad("授权范围无效");
  }

  void saveGrants(String id, EmployeeInput input) {
    employees.deleteRoleWarehouses(id);
    employees.deleteRoles(id);
    employees.deletePlatformWarehouses(id);
    employees.deletePlatforms(id);
    for (Grant g : input.grants()) {
      String grant = UUID.randomUUID().toString();
      employees.addRole(grant, id, g.role(), g.scope());
      for (String warehouse : g.warehouseIds()) employees.addRoleWarehouse(grant, warehouse);
    }
    for (PlatformGrant g : input.platformGrants()) {
      String grant = UUID.randomUUID().toString();
      employees.addPlatform(grant, id, g.capability(), g.scope());
      for (String warehouse : g.warehouseIds()) employees.addPlatformWarehouse(grant, warehouse);
    }
  }
}
