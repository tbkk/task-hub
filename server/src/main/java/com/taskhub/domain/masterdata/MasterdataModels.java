package com.taskhub.domain.masterdata;

import java.time.Instant;
import java.util.List;

public final class MasterdataModels {
  private MasterdataModels() {}

  public record Warehouse(
      String id,
      String name,
      String code,
      boolean enabled,
      int version,
      Instant createdAt,
      Instant updatedAt) {}

  public record WarehouseInput(
      String name, String code, Boolean enabled, Integer expectedVersion) {}

  public record Stop(
      String id,
      String name,
      String externalStopId,
      boolean enabled,
      int version,
      Instant createdAt,
      Instant updatedAt) {}

  public record StopView(
      String id,
      String name,
      String externalStopId,
      List<String> warehouseIds,
      boolean enabled,
      int version,
      Instant createdAt,
      Instant updatedAt) {}

  public record StopInput(
      String name,
      String externalStopId,
      List<String> warehouseIds,
      Boolean enabled,
      Integer expectedVersion) {}

  public record Vehicle(
      String id,
      String name,
      String externalVehicleName,
      String warehouseId,
      boolean enabled,
      int version,
      Instant createdAt,
      Instant updatedAt) {}

  public record VehicleView(
      String id,
      String name,
      String externalVehicleName,
      String warehouseId,
      List<String> boundStopIds,
      boolean enabled,
      int version,
      Instant createdAt,
      Instant updatedAt) {}

  public record VehicleInput(
      String name,
      String externalVehicleName,
      String warehouseId,
      List<String> boundStopIds,
      Boolean enabled,
      Integer expectedVersion) {}

  public record Compartment(
      String id,
      String vehicleId,
      String hardwareNo,
      String label,
      boolean enabled,
      int version,
      Instant createdAt,
      Instant updatedAt) {}

  public record CompartmentInput(String label, Boolean enabled, Integer expectedVersion) {}

  public record VersionInput(Integer expectedVersion) {}

  public record ExternalResource(String id, String name) {}

  public record BusinessHour(Integer weekday, String start, String end) {}

  public record Rule(
      Integer version,
      List<BusinessHour> businessHours,
      Integer slotCapacity,
      Integer bookingDays,
      Integer descriptionMaxLength,
      Integer sizeMaxLength,
      Integer remarkMaxLength,
      Integer telemetryMaxAgeSeconds,
      Integer doorMaxAgeSeconds,
      Integer pickupTimeoutMinutes,
      Boolean sharedCompartmentEnabled,
      Integer expectedVersion) {}

  public record RuleRow(String warehouseId, int version, String config) {}
}
