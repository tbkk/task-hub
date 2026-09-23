package com.taskhub.domain.identity;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BootstrapAdminService {
  private final EmployeeMapper employees;
  private final CredentialMapper credentials;
  private final AdminAuthService auth;

  public BootstrapAdminService(
      EmployeeMapper employees, CredentialMapper credentials, AdminAuthService auth) {
    this.employees = employees;
    this.credentials = credentials;
    this.auth = auth;
  }

  @Transactional
  public String initialize(String username, String password, String name, String phone) {
    if (employees.lockAdministration() || employees.administrators() > 0)
      throw new IllegalStateException("管理员已初始化，禁止重复执行");
    String normalizedUser = IdentityValidation.username(username),
        normalizedName = IdentityValidation.required(name, 80, "姓名"),
        normalizedPhone = IdentityValidation.phone(phone);
    IdentityValidation.password(password);
    String id = UUID.randomUUID().toString();
    employees.insert(id, normalizedName, normalizedPhone, true);
    credentials.insert(id, normalizedUser, auth.encode(password), true);
    for (String capability : AuthorizationService.PLATFORM_CAPABILITIES)
      employees.addPlatform(UUID.randomUUID().toString(), id, capability, "ALL");
    employees.initialized();
    return id;
  }
}
