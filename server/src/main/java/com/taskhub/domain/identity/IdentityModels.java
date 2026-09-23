package com.taskhub.domain.identity;

import java.time.Instant;
import java.util.List;

public final class IdentityModels {
  private IdentityModels() {}

  public record Grant(String role, String scope, List<String> warehouseIds) {}

  public record PlatformGrant(String capability, String scope, List<String> warehouseIds) {}

  public record Identity(
      String id,
      String name,
      String verifiedPhone,
      List<Grant> grants,
      List<PlatformGrant> platformGrants,
      boolean admin,
      boolean mustChangePassword) {}

  public record Actor(String employeeId, String sessionHash, String workspace, Identity identity) {
    public Actor withWorkspace(String value) {
      return new Actor(employeeId, sessionHash, value, identity);
    }
  }

  public record Session(String token, Instant expiresAt, Identity user) {}

  public record Employee(
      String id,
      String name,
      String phone,
      boolean enabled,
      int version,
      Instant createdAt,
      Instant updatedAt) {}

  public record EmployeeView(
      String id,
      String name,
      String phone,
      boolean enabled,
      int version,
      Instant createdAt,
      Instant updatedAt,
      String verifiedPhone,
      String homeWarehouseId,
      boolean wechatBound,
      List<Grant> grants,
      List<PlatformGrant> platformGrants) {}

  public record EmployeeInput(
      String name,
      String phone,
      String homeWarehouseId,
      Boolean enabled,
      List<Grant> grants,
      List<PlatformGrant> platformGrants,
      Integer expectedVersion) {}

  public record WarehouseOption(String id, String name, boolean enabled) {}

  public record Credential(
      String employeeId, String username, String passwordHash, boolean mustChangePassword) {}

  public record GrantRow(String id, String role, String scope) {}

  public record PlatformGrantRow(String id, String capability, String scope) {}

  public record SessionRow(
      String tokenHash, String employeeId, String channel, Instant expiresAt) {}

  public record LoginInput(String username, String password) {}

  public record PasswordInput(String currentPassword, String newPassword) {}

  public record CredentialsInput(String username, String temporaryPassword) {}
}
