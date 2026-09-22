package com.taskhub.domain.masterdata;

import static com.taskhub.domain.masterdata.MasterdataAccess.*;

import com.taskhub.api.*;
import com.taskhub.domain.identity.IdentityModels.*;
import com.taskhub.domain.masterdata.MasterdataModels.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MasterdataService {
  private static final String CAP = "MASTERDATA_MANAGE";
  final MasterdataMapper db;
  final MasterdataAccess access;
  final ExternalCatalog external;
  final List<MasterdataUsage> usages;

  public MasterdataService(
      MasterdataMapper db,
      MasterdataAccess access,
      ExternalCatalog external,
      List<MasterdataUsage> usages) {
    this.db = db;
    this.access = access;
    this.external = external;
    this.usages = usages;
  }

  public void change(String resource, String id) {
    usages.forEach(u -> u.requireChangeAllowed(resource, id));
  }

  public PageResponse<Warehouse> warehouses(Actor actor, int p, int s) {
    var g = access.grant(actor, CAP);
    return page(db.warehouses().stream().filter(w -> access.covers(g, w.id())).toList(), p, s);
  }

  public Warehouse warehouse(Actor actor, String id) {
    access.require(actor, CAP, id);
    return found(db.warehouse(id));
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Warehouse saveWarehouse(Actor actor, String id, WarehouseInput v) {
    access.lock(actor);
    access.require(actor, CAP, id);
    text(v.name(), 80);
    text(v.code(), 80);
    enabled(v.enabled());
    try {
      if (id == null) {
        id = UUID.randomUUID().toString();
        db.insertWarehouse(id, v);
      } else {
        var old = found(db.lockWarehouse(id));
        version(v.expectedVersion(), old.version());
        if (old.enabled() && !v.enabled()) change("warehouse", id);
        db.updateWarehouse(id, v);
      }
      return db.warehouse(id);
    } catch (DuplicateKeyException e) {
      throw conflict();
    }
  }

  public StopView stopView(Stop v) {
    return new StopView(
        v.id(),
        v.name(),
        v.externalStopId(),
        db.stopWarehouses(v.id()),
        v.enabled(),
        v.version(),
        v.createdAt(),
        v.updatedAt());
  }

  private boolean visible(PlatformGrant grant, List<String> warehouses) {
    return !warehouses.isEmpty() && warehouses.stream().allMatch(w -> access.covers(grant, w));
  }

  private void stopAccess(Actor actor, String id) {
    var scopes = db.stopWarehouses(id);
    if (!visible(access.grant(actor, CAP), scopes))
      throw com.taskhub.domain.identity.AuthorizationService.denied();
  }

  public PageResponse<StopView> stops(Actor actor, int p, int s) {
    var g = access.grant(actor, CAP);
    return page(
        db.stops().stream()
            .filter(v -> visible(g, db.stopWarehouses(v.id())))
            .map(this::stopView)
            .toList(),
        p,
        s);
  }

  public StopView stop(Actor actor, String id) {
    access.grant(actor, CAP);
    var v = found(db.stop(id));
    stopAccess(actor, id);
    return stopView(v);
  }

  private void enabledWarehouse(String id) {
    if (!found(db.lockWarehouse(id)).enabled()) throw new ApiException(422, 422, "仓库已停用");
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public StopView saveStop(Actor actor, String id, StopInput v) {
    access.lock(actor);
    access.grant(actor, CAP);
    text(v.name(), 80);
    text(v.externalStopId(), 100);
    enabled(v.enabled());
    var warehouses = ids(v.warehouseIds(), true);
    for (String w : warehouses.stream().sorted().toList()) {
      access.require(actor, CAP, w);
      enabledWarehouse(w);
    }
    if (id != null) {
      var old = found(db.lockStop(id));
      stopAccess(actor, id);
      version(v.expectedVersion(), old.version());
      if (!old.externalStopId().equals(v.externalStopId())) throw bad("已绑定外部点位不可替换");
      if (!v.enabled() || !new HashSet<>(warehouses).equals(new HashSet<>(db.stopWarehouses(id))))
        change("stop", id);
      for (var vehicle : db.vehicles())
        if (db.vehicleStops(vehicle.id()).contains(id)
            && !warehouses.contains(vehicle.warehouseId())) throw conflict();
    }
    if (id == null || v.enabled()) external.require("stops", v.externalStopId());
    try {
      if (id == null) {
        id = UUID.randomUUID().toString();
        db.insertStop(id, v);
      } else db.updateStop(id, v);
      db.clearStopWarehouses(id);
      for (String w : warehouses) db.bindWarehouse(id, w);
      return stopView(db.stop(id));
    } catch (DuplicateKeyException e) {
      throw conflict();
    }
  }

  public VehicleView vehicleView(Vehicle v) {
    return new VehicleView(
        v.id(),
        v.name(),
        v.externalVehicleName(),
        v.warehouseId(),
        db.vehicleStops(v.id()),
        v.enabled(),
        v.version(),
        v.createdAt(),
        v.updatedAt());
  }

  public PageResponse<VehicleView> vehicles(Actor actor, int p, int s) {
    var g = access.grant(actor, CAP);
    return page(
        db.vehicles().stream()
            .filter(v -> access.covers(g, v.warehouseId()))
            .map(this::vehicleView)
            .toList(),
        p,
        s);
  }

  public Vehicle authorizedVehicle(Actor actor, String id) {
    access.grant(actor, CAP);
    var v = found(db.vehicle(id));
    access.require(actor, CAP, v.warehouseId());
    return v;
  }

  public VehicleView vehicle(Actor actor, String id) {
    return vehicleView(authorizedVehicle(actor, id));
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public VehicleView saveVehicle(Actor actor, String id, VehicleInput v) {
    access.lock(actor);
    access.require(actor, CAP, v.warehouseId());
    text(v.name(), 80);
    text(v.externalVehicleName(), 100);
    enabled(v.enabled());
    text(v.warehouseId(), 36);
    var stops = ids(v.boundStopIds(), false);
    if (id != null) authorizedVehicle(actor, id);
    enabledWarehouse(v.warehouseId());
    for (String stop : stops.stream().sorted().toList()) {
      var target = found(db.lockStop(stop));
      if (!target.enabled() || !db.stopWarehouses(stop).contains(v.warehouseId()))
        throw new ApiException(422, 422, "车辆点位未在该仓库启用");
    }
    if (id != null) {
      var old = found(db.lockVehicle(id));
      access.require(actor, CAP, old.warehouseId());
      version(v.expectedVersion(), old.version());
      if (!old.externalVehicleName().equals(v.externalVehicleName())) throw bad("已绑定外部车辆不可替换");
      if (!v.enabled()
          || !old.warehouseId().equals(v.warehouseId())
          || !new HashSet<>(stops).equals(new HashSet<>(db.vehicleStops(id))))
        change("vehicle", id);
    }
    if (id == null || v.enabled()) external.require("vehicles", v.externalVehicleName());
    try {
      if (id == null) {
        id = UUID.randomUUID().toString();
        db.insertVehicle(id, v);
      } else db.updateVehicle(id, v);
      db.clearVehicleStops(id);
      for (String stop : stops) db.bindStop(id, stop);
      return vehicleView(db.vehicle(id));
    } catch (DuplicateKeyException e) {
      throw conflict();
    }
  }
}
