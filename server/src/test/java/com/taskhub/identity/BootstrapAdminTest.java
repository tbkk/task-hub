package com.taskhub.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.taskhub.domain.identity.*;
import org.junit.jupiter.api.Test;

class BootstrapAdminTest {
  @Test
  void refusesReinitializationBeforeCreatingCredentials() {
    var employees = mock(EmployeeMapper.class);
    var credentials = mock(CredentialMapper.class);
    var auth = mock(AdminAuthService.class);
    when(employees.lockAdministration()).thenReturn(true);
    var service = new BootstrapAdminService(employees, credentials, auth);
    assertThrows(
        IllegalStateException.class,
        () -> service.initialize("valid-admin", "Long-test-password", "管理员", "13900000000"));
    verifyNoInteractions(credentials, auth);
  }

  @Test
  void missingPasswordNeverCreatesDefaultCredentials() {
    var employees = mock(EmployeeMapper.class);
    var credentials = mock(CredentialMapper.class);
    var auth = mock(AdminAuthService.class);
    var service = new BootstrapAdminService(employees, credentials, auth);
    assertThrows(
        com.taskhub.api.ApiException.class,
        () -> service.initialize("valid-admin", null, "管理员", "13900000000"));
    verifyNoInteractions(credentials);
  }
}
